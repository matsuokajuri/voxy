package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ForgeMdicCommandPlanner {
    private static final int SECTION_SIZE = 32;
    private static final int MAX_PLAN_CANDIDATES = 256;
    private static final int RENDER_DISTANCE_CHUNKS = 12;

    private ForgeMdicCommandPlanner() {
    }

    static PlanResult plan(ForgeVoxyInstance instance, int maxSections, int maxRecords) {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return failure(start, "NOT_RENDER_THREAD", "not-render-thread", 0, 0);
        }
        if (!ForgeMdicCommandConfig.isEnabled()) {
            return failure(start, "DISABLED", "mdic-command-skeleton-disabled", 0, 0);
        }

        ForgeGpuGeometryUploadManager uploadManager = instance.getGpuGeometryUploadManager();
        ForgeGpuGeometryHeap heap = uploadManager.getHeapForDebugReadback();
        if (heap == null || !heap.isCreated()) {
            return failure(start, "HEAP_MISSING", "heap-not-created", 0, 0);
        }

        List<Integer> sectionIds = uploadManager.createUploadedSectionIdSnapshot();
        Collections.sort(sectionIds);
        if (sectionIds.isEmpty()) {
            return failure(start, "NO_UPLOADED_SECTIONS", "no-uploaded-sections", 0, 0);
        }

        Minecraft minecraft = Minecraft.getInstance();
        CameraContext camera = CameraContext.create(minecraft);
        String selectionMode = camera.available() ? "RADIUS" : "FALLBACK_FIRST_N";
        ArrayList<Candidate> candidates = new ArrayList<>();
        int invalidMetadata = 0;
        int skippedSections = 0;
        long skippedRecords = 0L;
        int skippedTranslucentCommands = 0;
        int skippedEmptyBuckets = 0;
        int skippedBucketCommands = 0;
        String lastError = "none";

        int inspected = 0;
        int inspectLimit = Math.max(1, MAX_PLAN_CANDIDATES);
        for (Integer sectionId : sectionIds) {
            if (sectionId == null || sectionId < 0) {
                skippedSections++;
                continue;
            }
            if (inspected >= inspectLimit) {
                skippedSections += Math.max(0, sectionIds.size() - inspected);
                break;
            }
            inspected++;
            try {
                int[] words = heap.readbackMetadata(sectionId);
                ForgeGpuGeometryDecodedMetadata metadata = ForgeGpuGeometryDecodedMetadata.decode(words);
                String validation = metadata.validate(heap, instance.getSectionGeometryManager(), sectionId, words);
                if (!"none".equals(validation)) {
                    invalidMetadata++;
                    lastError = "sectionId=" + sectionId + ' ' + validation;
                    continue;
                }
                int distanceChunks = camera.available() ? distanceChunks(metadata.position(), camera.chunkX(), camera.chunkZ()) : 0;
                if (camera.available() && distanceChunks > RENDER_DISTANCE_CHUNKS) {
                    skippedSections++;
                    continue;
                }
                double distanceSquared = camera.available() ? sectionDistanceSquared(metadata.position(), camera.x(), camera.y(), camera.z()) : sectionId;
                candidates.add(new Candidate(sectionId, metadata, distanceSquared));
            } catch (RuntimeException e) {
                invalidMetadata++;
                lastError = "sectionId=" + sectionId + ' ' + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        if (camera.available()) {
            candidates.sort((left, right) -> {
                int distanceCompare = Double.compare(left.distanceSquared(), right.distanceSquared());
                if (distanceCompare != 0) {
                    return distanceCompare;
                }
                return Integer.compare(left.sectionId(), right.sectionId());
            });
        }

        boolean bucketAware = ForgeMdicCommandConfig.bucketAware();
        boolean includeTranslucent = ForgeMdicCommandConfig.includeTranslucent();
        boolean includeDoubleSided = ForgeMdicCommandConfig.includeDoubleSided();
        boolean includeDirectional = ForgeMdicCommandConfig.includeDirectional();
        int sectionLimit = Math.max(1, maxSections);
        int commandLimit = bucketAware ? Math.max(1, ForgeMdicCommandConfig.maxCommands()) : sectionLimit;
        int maxCommandsPerSection = Math.max(1, ForgeMdicCommandConfig.maxCommandsPerSection());
        int remainingRecords = Math.max(1, maxRecords);
        ArrayList<ForgeMdicCommand> commands = new ArrayList<>();
        long commandRecords = 0L;
        int generation = (int) heap.generation();
        int acceptedSections = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (acceptedSections >= sectionLimit) {
                skippedSections += candidates.size() - i;
                break;
            }
            if (remainingRecords <= 0 || commands.size() >= commandLimit) {
                skippedSections += candidates.size() - i;
                break;
            }

            Candidate candidate = candidates.get(i);
            if (bucketAware) {
                BucketPlan bucketPlan = planBucketCommands(
                        commands,
                        candidate,
                        generation,
                        remainingRecords,
                        commandLimit,
                        maxCommandsPerSection,
                        includeTranslucent,
                        includeDoubleSided,
                        includeDirectional
                );
                remainingRecords -= bucketPlan.acceptedRecords();
                commandRecords += bucketPlan.acceptedRecords();
                skippedRecords += bucketPlan.skippedRecords();
                skippedTranslucentCommands += bucketPlan.skippedTranslucentCommands();
                skippedEmptyBuckets += bucketPlan.skippedEmptyBuckets();
                skippedBucketCommands += bucketPlan.skippedBucketCommands();
                if (bucketPlan.acceptedCommands() > 0) {
                    acceptedSections++;
                } else {
                    skippedSections++;
                }
            } else {
                int records = Math.min(candidate.metadata().itemCount(), remainingRecords);
                if (records <= 0) {
                    skippedSections++;
                    continue;
                }
                if (candidate.metadata().itemCount() > records) {
                    skippedRecords += candidate.metadata().itemCount() - records;
                }
                commands.add(createSectionCommand(candidate.sectionId(), candidate.metadata(), records, generation));
                acceptedSections++;
                commandRecords += records;
                remainingRecords -= records;
            }
        }

        String dimension = currentDimensionId(minecraft);
        ForgeMdicCommandList commandList = ForgeMdicCommandList.of(
                commands,
                heap.generation(),
                dimension,
                commandRecords,
                selectionMode,
                skippedSections,
                skippedRecords,
                candidates.size(),
                acceptedSections,
                bucketAware,
                includeTranslucent,
                includeDoubleSided,
                includeDirectional,
                skippedTranslucentCommands,
                skippedEmptyBuckets,
                skippedBucketCommands
        );
        boolean success = commandList.isValid();
        return new PlanResult(
                success,
                success ? "none" : "NO_VALID_SECTIONS",
                success ? "none" : lastError,
                commandList,
                sectionIds.size(),
                candidates.size(),
                invalidMetadata,
                skippedSections,
                skippedRecords,
                elapsedMs(start)
        );
    }

    private static BucketPlan planBucketCommands(
            ArrayList<ForgeMdicCommand> commands,
            Candidate candidate,
            int generation,
            int remainingRecords,
            int commandLimit,
            int maxCommandsPerSection,
            boolean includeTranslucent,
            boolean includeDoubleSided,
            boolean includeDirectional
    ) {
        int acceptedCommands = 0;
        int acceptedRecords = 0;
        int skippedTranslucentCommands = 0;
        int skippedEmptyBuckets = 0;
        int skippedBucketCommands = 0;
        long skippedRecords = 0L;
        for (int bucket = 0; bucket < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT; bucket++) {
            int start = candidate.metadata().offsets()[bucket];
            int end = bucket == ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT - 1
                    ? candidate.metadata().itemCount()
                    : candidate.metadata().offsets()[bucket + 1];
            int bucketRecords = Math.max(0, end - start);
            if (bucketRecords <= 0) {
                skippedEmptyBuckets++;
                continue;
            }
            if (!bucketAllowed(bucket, includeTranslucent, includeDoubleSided, includeDirectional)) {
                if (bucket == 0) {
                    skippedTranslucentCommands++;
                } else {
                    skippedBucketCommands++;
                }
                skippedRecords += bucketRecords;
                continue;
            }
            if (acceptedCommands >= maxCommandsPerSection || commands.size() >= commandLimit || bucketRecords > remainingRecords - acceptedRecords) {
                skippedBucketCommands++;
                skippedRecords += bucketRecords;
                continue;
            }
            commands.add(createBucketCommand(candidate.sectionId(), candidate.metadata(), start, bucketRecords, bucket, generation));
            acceptedCommands++;
            acceptedRecords += bucketRecords;
        }
        return new BucketPlan(acceptedCommands, acceptedRecords, skippedRecords, skippedTranslucentCommands, skippedEmptyBuckets, skippedBucketCommands);
    }

    private static boolean bucketAllowed(int bucket, boolean includeTranslucent, boolean includeDoubleSided, boolean includeDirectional) {
        if (bucket == 0) {
            return includeTranslucent;
        }
        if (bucket == 1) {
            return includeDoubleSided;
        }
        return includeDirectional;
    }

    private static ForgeMdicCommand createSectionCommand(int sectionId, ForgeGpuGeometryDecodedMetadata metadata, int recordCount, int generation) {
        return createCommand(sectionId, metadata, 0, Math.max(0, recordCount), bucketMask(metadata), generation, ForgeMdicCommandLayout.FLAG_DEBUG_SKELETON);
    }

    private static ForgeMdicCommand createBucketCommand(int sectionId, ForgeGpuGeometryDecodedMetadata metadata, int recordStart, int recordCount, int bucket, int generation) {
        return createCommand(sectionId, metadata, recordStart, Math.max(0, recordCount), 1 << bucket, generation, ForgeMdicCommandLayout.FLAG_DEBUG_SKELETON | ForgeMdicCommandLayout.FLAG_BUCKET_COMMAND);
    }

    private static ForgeMdicCommand createCommand(int sectionId, ForgeGpuGeometryDecodedMetadata metadata, int recordStart, int recordCount, int bucketMask, int generation, int flags) {
        int level = Math.max(0, WorldEngine.getLevel(metadata.position()));
        int scale = 1 << Math.min(12, level);
        float originX = WorldEngine.getX(metadata.position()) * (float) SECTION_SIZE * scale;
        float originY = WorldEngine.getY(metadata.position()) * (float) SECTION_SIZE * scale;
        float originZ = WorldEngine.getZ(metadata.position()) * (float) SECTION_SIZE * scale;
        return new ForgeMdicCommand(
                sectionId,
                metadata.geometryPtr(),
                Math.max(0, recordStart),
                Math.max(0, recordCount),
                Float.floatToRawIntBits(originX),
                Float.floatToRawIntBits(originY),
                Float.floatToRawIntBits(originZ),
                bucketMask,
                sectionId,
                level,
                flags,
                generation
        );
    }

    private static int bucketMask(ForgeGpuGeometryDecodedMetadata metadata) {
        int mask = 0;
        for (int bucket = 0; bucket < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT; bucket++) {
            int start = metadata.offsets()[bucket];
            int end = bucket == ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT - 1
                    ? metadata.itemCount()
                    : metadata.offsets()[bucket + 1];
            if (end > start) {
                mask |= 1 << bucket;
            }
        }
        return mask;
    }

    private static PlanResult failure(long start, String skippedReason, String error, int candidates, int invalidMetadata) {
        return new PlanResult(false, skippedReason, error, ForgeMdicCommandList.empty(), candidates, 0, invalidMetadata, 0, 0L, elapsedMs(start));
    }

    private static String currentDimensionId(Minecraft minecraft) {
        if (minecraft.level == null) {
            return "none";
        }
        return minecraft.level.dimension().location().toString();
    }

    private static int distanceChunks(long position, int playerChunkX, int playerChunkZ) {
        int level = Math.max(0, WorldEngine.getLevel(position));
        int scale = 1 << Math.min(12, level);
        int sectionChunkX = WorldEngine.getX(position) * 2 * scale;
        int sectionChunkZ = WorldEngine.getZ(position) * 2 * scale;
        return Math.max(Math.abs(sectionChunkX - playerChunkX), Math.abs(sectionChunkZ - playerChunkZ));
    }

    private static double sectionDistanceSquared(long position, double cameraX, double cameraY, double cameraZ) {
        int level = Math.max(0, WorldEngine.getLevel(position));
        int scale = 1 << Math.min(12, level);
        double sectionSize = 32.0D * scale;
        double centerX = WorldEngine.getX(position) * sectionSize + sectionSize * 0.5D;
        double centerY = WorldEngine.getY(position) * sectionSize + sectionSize * 0.5D;
        double centerZ = WorldEngine.getZ(position) * sectionSize + sectionSize * 0.5D;
        double dx = centerX - cameraX;
        double dy = centerY - cameraY;
        double dz = centerZ - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Vec3 cameraPositionOrPlayer(Minecraft minecraft) {
        if (minecraft.gameRenderer != null && minecraft.gameRenderer.getMainCamera() != null) {
            return minecraft.gameRenderer.getMainCamera().getPosition();
        }
        return minecraft.player == null ? Vec3.ZERO : minecraft.player.position();
    }

    private static double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0D;
    }

    record PlanResult(
            boolean success,
            String skippedReason,
            String error,
            ForgeMdicCommandList commandList,
            int uploadedSectionCandidates,
            int candidateSections,
            int invalidMetadata,
            int skippedSections,
            long skippedRecords,
            double durationMs
    ) {
    }

    private record Candidate(int sectionId, ForgeGpuGeometryDecodedMetadata metadata, double distanceSquared) {
    }

    private record BucketPlan(int acceptedCommands, int acceptedRecords, long skippedRecords, int skippedTranslucentCommands, int skippedEmptyBuckets, int skippedBucketCommands) {
    }

    private record CameraContext(boolean available, double x, double y, double z, int chunkX, int chunkZ) {
        private static CameraContext create(Minecraft minecraft) {
            if (minecraft.level == null || minecraft.player == null) {
                return new CameraContext(false, 0.0D, 0.0D, 0.0D, 0, 0);
            }
            Vec3 position = cameraPositionOrPlayer(minecraft);
            return new CameraContext(true, position.x, position.y, position.z, floorDiv16(position.x), floorDiv16(position.z));
        }

        private static int floorDiv16(double value) {
            return (int) Math.floor(value / 16.0D);
        }
    }
}
