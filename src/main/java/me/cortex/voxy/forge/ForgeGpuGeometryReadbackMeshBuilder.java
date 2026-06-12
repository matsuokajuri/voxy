package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ForgeGpuGeometryReadbackMeshBuilder {
    private static final int SECTION_SIZE = 32;

    private ForgeGpuGeometryReadbackMeshBuilder() {
    }

    public static ForgeGpuGeometryReadbackMeshResult buildSample(ForgeVoxyInstance instance) {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return ForgeGpuGeometryReadbackMeshResult.failure("not-render-thread");
        }
        if (!ForgeGpuGeometryUploadManager.isEnabled()) {
            return ForgeGpuGeometryReadbackMeshResult.failure("geometry-gpu-upload-disabled");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return ForgeGpuGeometryReadbackMeshResult.failure("no-client-world");
        }

        ForgeGpuGeometryUploadManager uploadManager = instance.getGpuGeometryUploadManager();
        ForgeGpuGeometryHeap heap = uploadManager.getHeapForDebugReadback();
        if (!heap.isCreated()) {
            return ForgeGpuGeometryReadbackMeshResult.failure("heap-not-created");
        }

        List<Integer> sectionIds = uploadManager.createUploadedSectionIdSnapshot();
        if (sectionIds.isEmpty()) {
            return ForgeGpuGeometryReadbackMeshResult.failure("no-uploaded-sections");
        }
        Collections.sort(sectionIds);

        String dimension = minecraft.level.dimension().location().toString();
        int maxSections = getConfiguredMaxSections();
        int remainingRecords = getConfiguredMaxRecords();
        int builtSections = 0;
        int recordsRead = 0;
        int quads = 0;
        int vertices = 0;
        int invalidMetadata = 0;
        int invalidRecords = 0;
        int lastSectionId = -1;
        long lastPosition = 0L;
        int lastGeometryPtr = -1;
        String lastError = "none";
        String lastDecodedRecord = "none";
        ArrayList<ForgeCpuBuiltSection> sections = new ArrayList<>();

        for (Integer sectionId : sectionIds) {
            if (sectionId == null || sectionId < 0) {
                continue;
            }
            if (builtSections >= maxSections || remainingRecords <= 0) {
                break;
            }

            lastSectionId = sectionId;
            try {
                int[] words = heap.readbackMetadata(sectionId);
                ForgeGpuGeometryDecodedMetadata metadata = ForgeGpuGeometryDecodedMetadata.decode(words);
                lastPosition = metadata.position();
                lastGeometryPtr = metadata.geometryPtr();
                String metadataError = metadata.validate(heap, instance.getSectionGeometryManager(), sectionId, words);
                if (!"none".equals(metadataError)) {
                    invalidMetadata++;
                    lastError = metadataError;
                    continue;
                }

                SectionBuilder builder = new SectionBuilder(dimension, sectionId, metadata.position(), words);
                for (int bucket = 0; bucket < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT && remainingRecords > 0; bucket++) {
                    int startOffset = metadata.offsets()[bucket];
                    int endOffset = bucket + 1 < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT
                            ? metadata.offsets()[bucket + 1]
                            : metadata.itemCount();
                    int bucketSize = endOffset - startOffset;
                    if (bucketSize <= 0) {
                        continue;
                    }
                    if (bucket == 0) {
                        continue;
                    }

                    int count = Math.min(bucketSize, remainingRecords);
                    long[] records = heap.readbackGeometry(metadata.geometryPtr() + startOffset, count);
                    for (long record : records) {
                        recordsRead++;
                        remainingRecords--;
                        builder.mixRecordHash(record);
                        if (!isRecordValid(record)) {
                            invalidRecords++;
                            lastError = "invalid quad record in section " + sectionId + " bucket " + bucket;
                            continue;
                        }
                        builder.emit(record, bucket);
                        lastDecodedRecord = "bucket=" + bucket + ' ' + ForgeVoxyQuadEncoder.formatRecordHex(record) + ' ' + ForgeVoxyQuadEncoder.decodeRecord(record);
                    }
                }

                ForgeCpuBuiltSection section = builder.build((System.nanoTime() - start) / 1_000_000.0D);
                if (section != null && section.quadCount() > 0) {
                    sections.add(section);
                    builtSections++;
                    quads += section.quadCount();
                    vertices += section.vertexCount();
                }
            } catch (RuntimeException e) {
                invalidMetadata++;
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        double durationMs = (System.nanoTime() - start) / 1_000_000.0D;
        ForgeGpuGeometryReadbackMeshResult result = new ForgeGpuGeometryReadbackMeshResult(
                quads > 0,
                quads > 0 ? "ok" : (lastError.equals("none") ? "no-quads" : lastError),
                builtSections,
                recordsRead,
                quads,
                vertices,
                invalidMetadata,
                invalidRecords,
                durationMs,
                lastSectionId,
                lastPosition,
                lastGeometryPtr,
                lastError,
                lastDecodedRecord,
                ForgeGpuGeometryReadbackMeshCache.SOURCE
        );
        instance.getGpuGeometryReadbackMeshCache().replaceAll(sections, result);
        return result;
    }

    public static int getConfiguredMaxSections() {
        return Math.min(64, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_MAX_SECTIONS.get()));
    }

    public static int getConfiguredMaxRecords() {
        return Math.min(131072, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_MAX_RECORDS.get()));
    }

    private static boolean isRecordValid(long record) {
        int face = ForgeVoxyQuadEncoder.extractFace(record);
        int length = ForgeVoxyQuadEncoder.extractLength(record);
        int width = ForgeVoxyQuadEncoder.extractWidth(record);
        int x = ForgeVoxyQuadEncoder.extractLocalX(record);
        int y = ForgeVoxyQuadEncoder.extractLocalY(record);
        int z = ForgeVoxyQuadEncoder.extractLocalZ(record);
        return face >= 0 && face <= 5
                && length >= 1 && length <= 16
                && width >= 1 && width <= 16
                && x >= 0 && x <= 31
                && y >= 0 && y <= 31
                && z >= 0 && z <= 31;
    }

    private static int stableRecordColor(int sectionId, long position, int modelId, int biomeId, int light, int face, int bucket) {
        int seed = sectionId * 0x45D9F3B
                ^ (int) position
                ^ (int) (position >>> 32)
                ^ modelId * 0x27D4EB2D
                ^ biomeId * 0x165667B1
                ^ face * 0x85EBCA6B
                ^ bucket * 0x9E3779B9;
        seed ^= seed >>> 16;
        int brightness = 96 + Math.min(159, Math.max(0, light));
        int red = scaleColor(80 + ((seed >>> 16) & 0x7F), brightness);
        int green = scaleColor(80 + ((seed >>> 8) & 0x7F), brightness);
        int blue = scaleColor(80 + (seed & 0x7F), brightness);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int scaleColor(int value, int brightness) {
        return Math.max(32, Math.min(255, value * brightness / 255));
    }

    private static final class SectionBuilder {
        private final String dimension;
        private final int sectionId;
        private final long position;
        private final int chunkX;
        private final int chunkZ;
        private final int scale;
        private final float baseX;
        private final float baseY;
        private final float baseZ;
        private final ForgeCpuMeshBuffer.Builder builder = new ForgeCpuMeshBuffer.Builder();
        private long sourceHash;

        private SectionBuilder(String dimension, int sectionId, long position, int[] metadataWords) {
            this.dimension = dimension;
            this.sectionId = sectionId;
            this.position = position;
            int level = Math.max(0, WorldEngine.getLevel(position));
            this.scale = 1 << Math.min(12, level);
            this.baseX = WorldEngine.getX(position) * (float) SECTION_SIZE * this.scale;
            this.baseY = WorldEngine.getY(position) * (float) SECTION_SIZE * this.scale;
            this.baseZ = WorldEngine.getZ(position) * (float) SECTION_SIZE * this.scale;
            this.chunkX = Math.floorDiv(WorldEngine.getX(position) * SECTION_SIZE * this.scale, 16);
            this.chunkZ = Math.floorDiv(WorldEngine.getZ(position) * SECTION_SIZE * this.scale, 16);
            this.sourceHash = position * 31L + sectionId;
            if (metadataWords != null) {
                for (int word : metadataWords) {
                    this.sourceHash = this.sourceHash * 31L + word;
                }
            }
        }

        private void mixRecordHash(long record) {
            this.sourceHash = this.sourceHash * 31L + record;
        }

        private void emit(long record, int bucket) {
            int face = ForgeVoxyQuadEncoder.extractFace(record);
            int localX = ForgeVoxyQuadEncoder.extractLocalX(record);
            int localY = ForgeVoxyQuadEncoder.extractLocalY(record);
            int localZ = ForgeVoxyQuadEncoder.extractLocalZ(record);
            int length = ForgeVoxyQuadEncoder.extractLength(record);
            int width = ForgeVoxyQuadEncoder.extractWidth(record);
            int light = ForgeVoxyQuadEncoder.extractLightId(record);
            int modelId = ForgeVoxyQuadEncoder.extractModelId(record);
            int biomeId = ForgeVoxyQuadEncoder.extractBiomeId(record);
            int color = stableRecordColor(this.sectionId, this.position, modelId, biomeId, light, face, bucket);
            float x0 = this.baseX + localX * this.scale;
            float y0 = this.baseY + localY * this.scale;
            float z0 = this.baseZ + localZ * this.scale;
            float x1 = x0;
            float y1 = y0;
            float z1 = z0;
            float x2 = x0;
            float y2 = y0;
            float z2 = z0;
            float x3 = x0;
            float y3 = y0;
            float z3 = z0;

            if (face == 0 || face == 1) {
                x1 = x0 + length * this.scale;
                z2 = z0 + width * this.scale;
                x3 = x1;
                z3 = z2;
            } else if (face == 2 || face == 3) {
                x1 = x0 + length * this.scale;
                y2 = y0 + width * this.scale;
                x3 = x1;
                y3 = y2;
            } else {
                y1 = y0 + length * this.scale;
                z2 = z0 + width * this.scale;
                y3 = y1;
                z3 = z2;
            }

            this.putVertex(x0, y0, z0, color, light, face, bucket, modelId, biomeId);
            this.putVertex(x1, y1, z1, color, light, face, bucket, modelId, biomeId);
            this.putVertex(x3, y3, z3, color, light, face, bucket, modelId, biomeId);
            this.putVertex(x2, y2, z2, color, light, face, bucket, modelId, biomeId);
        }

        private void putVertex(float x, float y, float z, int color, int light, int face, int bucket, int modelId, int biomeId) {
            this.builder.putVertex(
                    x,
                    y,
                    z,
                    color,
                    0.0F,
                    0.0F,
                    light,
                    face,
                    ForgeCpuMeshLayer.OTHER.id,
                    bucket,
                    modelId,
                    biomeId
            );
        }

        private ForgeCpuBuiltSection build(double elapsedMs) {
            if (this.builder.quadCount() == 0) {
                return null;
            }
            return new ForgeCpuBuiltSection(
                    this.dimension,
                    this.chunkX,
                    this.chunkZ,
                    this.position,
                    ForgeCpuMeshLayer.OTHER,
                    this.sourceHash,
                    System.currentTimeMillis(),
                    elapsedMs,
                    this.builder.build()
            );
        }
    }
}
