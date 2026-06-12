package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ForgeGpuGeometryVisualizationBuilder {
    private static final int SECTION_SIZE = 32;

    private ForgeGpuGeometryVisualizationBuilder() {
    }

    public static ForgeGpuGeometryVisualizationResult buildSample(ForgeVoxyInstance instance) {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return ForgeGpuGeometryVisualizationResult.failure("not-render-thread");
        }
        if (!ForgeVoxyRuntimeOverrides.enableGeometryGpuVisualization()) {
            return ForgeGpuGeometryVisualizationResult.failure("geometry-gpu-visualization-disabled");
        }
        if (!ForgeGpuGeometryUploadManager.isEnabled()) {
            return ForgeGpuGeometryVisualizationResult.failure("geometry-gpu-upload-disabled");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return ForgeGpuGeometryVisualizationResult.failure("no-client-world");
        }

        ForgeGpuGeometryUploadManager uploadManager = instance.getGpuGeometryUploadManager();
        ForgeGpuGeometryHeap heap = uploadManager.getHeapForDebugReadback();
        if (!heap.isCreated()) {
            return ForgeGpuGeometryVisualizationResult.failure("heap-not-created");
        }

        List<Integer> sectionIds = uploadManager.createUploadedSectionIdSnapshot();
        if (sectionIds.isEmpty()) {
            return ForgeGpuGeometryVisualizationResult.failure("no-uploaded-sections");
        }
        Collections.sort(sectionIds);

        String dimension = minecraft.level.dimension().location().toString();
        int maxSections = getConfiguredMaxSections();
        int maxRecords = getConfiguredMaxRecords();
        int remainingRecords = maxRecords;
        int builtSections = 0;
        int recordsRead = 0;
        int quads = 0;
        int vertices = 0;
        int invalidMetadata = 0;
        int invalidRecords = 0;
        int skippedBuckets = 0;
        int lastSectionId = -1;
        long lastPosition = 0L;
        int lastGeometryPtr = -1;
        String lastError = "none";
        String lastDecodedRecord = "none";
        ArrayList<ForgeGpuGeometryVisualizationCache.Entry> entries = new ArrayList<>();

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

                EntryBuilder builder = new EntryBuilder(dimension, sectionId, metadata.position());
                for (int bucket = 0; bucket < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT && remainingRecords > 0; bucket++) {
                    int startOffset = metadata.offsets()[bucket];
                    int endOffset = bucket + 1 < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT
                            ? metadata.offsets()[bucket + 1]
                            : metadata.itemCount();
                    int bucketSize = endOffset - startOffset;
                    if (bucketSize <= 0) {
                        skippedBuckets++;
                        continue;
                    }
                    int count = Math.min(bucketSize, remainingRecords);
                    long[] records = heap.readbackGeometry(metadata.geometryPtr() + startOffset, count);
                    for (long record : records) {
                        recordsRead++;
                        remainingRecords--;
                        if (!isRecordValid(record)) {
                            invalidRecords++;
                            lastError = "invalid quad record in section " + sectionId + " bucket " + bucket;
                            continue;
                        }
                        builder.emit(record, bucket);
                        lastDecodedRecord = "bucket=" + bucket + ' ' + ForgeVoxyQuadEncoder.formatRecordHex(record) + ' ' + ForgeVoxyQuadEncoder.decodeRecord(record);
                    }
                }

                ForgeGpuGeometryVisualizationCache.Entry entry = builder.build();
                if (entry.quadCount() > 0) {
                    entries.add(entry);
                    builtSections++;
                    quads += entry.quadCount();
                    vertices += entry.vertexCount();
                }
            } catch (RuntimeException e) {
                invalidMetadata++;
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        double durationMs = (System.nanoTime() - start) / 1_000_000.0D;
        ForgeGpuGeometryVisualizationResult result = new ForgeGpuGeometryVisualizationResult(
                quads > 0,
                quads > 0 ? "ok" : (lastError.equals("none") ? "no-quads" : lastError),
                builtSections,
                recordsRead,
                quads,
                vertices,
                invalidMetadata,
                invalidRecords,
                skippedBuckets,
                durationMs,
                lastSectionId,
                lastPosition,
                lastGeometryPtr,
                lastError,
                lastDecodedRecord,
                ForgeGpuGeometryVisualizationCache.SOURCE
        );
        instance.getGpuGeometryVisualizationCache().replaceAll(entries, result);
        return result;
    }

    public static int getConfiguredMaxSections() {
        return Math.min(32, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_VISUALIZATION_MAX_SECTIONS.get()));
    }

    public static int getConfiguredMaxRecords() {
        return Math.min(65536, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_VISUALIZATION_MAX_RECORDS.get()));
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

    private static int stableRecordColor(int sectionId, long position, int bucket, long record) {
        int seed = sectionId * 0x45D9F3B
                ^ (int) position
                ^ (int) (position >>> 32)
                ^ bucket * 0x9E3779B9
                ^ ForgeVoxyQuadEncoder.extractModelId(record) * 0x27D4EB2D
                ^ ForgeVoxyQuadEncoder.extractBiomeId(record) * 0x165667B1
                ^ ForgeVoxyQuadEncoder.extractFace(record) * 0x85EBCA6B;
        seed ^= seed >>> 16;
        int light = ForgeVoxyQuadEncoder.extractLightId(record);
        int brightness = 128 + Math.min(127, Math.max(0, light));
        int red = scaleColor(80 + ((seed >>> 16) & 0x7F), brightness);
        int green = scaleColor(80 + ((seed >>> 8) & 0x7F), brightness);
        int blue = scaleColor(80 + (seed & 0x7F), brightness);
        return (red << 16) | (green << 8) | blue;
    }

    private static int scaleColor(int value, int brightness) {
        return Math.max(48, Math.min(255, value * brightness / 255));
    }

    private static final class EntryBuilder {
        private final String dimension;
        private final int sectionId;
        private final long position;
        private final int chunkX;
        private final int chunkZ;
        private final int scale;
        private final float baseX;
        private final float baseY;
        private final float baseZ;
        private int[] vertexData = new int[ForgeGpuGeometryVisualizationCache.VERTEX_STRIDE_INTS * 512];
        private int vertexCount;
        private int quadCount;

        private EntryBuilder(String dimension, int sectionId, long position) {
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
        }

        private void emit(long record, int bucket) {
            int face = ForgeVoxyQuadEncoder.extractFace(record);
            int localX = ForgeVoxyQuadEncoder.extractLocalX(record);
            int localY = ForgeVoxyQuadEncoder.extractLocalY(record);
            int localZ = ForgeVoxyQuadEncoder.extractLocalZ(record);
            int length = ForgeVoxyQuadEncoder.extractLength(record);
            int width = ForgeVoxyQuadEncoder.extractWidth(record);
            int color = stableRecordColor(this.sectionId, this.position, bucket, record);
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

            this.putVertex(x0, y0, z0, color);
            this.putVertex(x1, y1, z1, color);
            this.putVertex(x3, y3, z3, color);
            this.putVertex(x0, y0, z0, color);
            this.putVertex(x3, y3, z3, color);
            this.putVertex(x2, y2, z2, color);
            this.quadCount++;
        }

        private void putVertex(float x, float y, float z, int color) {
            this.ensureVertexCapacity(this.vertexCount + 1);
            int offset = this.vertexCount * ForgeGpuGeometryVisualizationCache.VERTEX_STRIDE_INTS;
            this.vertexData[offset + ForgeGpuGeometryVisualizationCache.X_OFFSET] = Float.floatToRawIntBits(x);
            this.vertexData[offset + ForgeGpuGeometryVisualizationCache.Y_OFFSET] = Float.floatToRawIntBits(y);
            this.vertexData[offset + ForgeGpuGeometryVisualizationCache.Z_OFFSET] = Float.floatToRawIntBits(z);
            this.vertexData[offset + ForgeGpuGeometryVisualizationCache.COLOR_OFFSET] = color;
            this.vertexCount++;
        }

        private ForgeGpuGeometryVisualizationCache.Entry build() {
            int[] data = new int[this.vertexCount * ForgeGpuGeometryVisualizationCache.VERTEX_STRIDE_INTS];
            System.arraycopy(this.vertexData, 0, data, 0, data.length);
            return new ForgeGpuGeometryVisualizationCache.Entry(
                    this.dimension,
                    this.sectionId,
                    this.position,
                    this.chunkX,
                    this.chunkZ,
                    this.quadCount,
                    data
            );
        }

        private void ensureVertexCapacity(int requestedVertices) {
            int required = requestedVertices * ForgeGpuGeometryVisualizationCache.VERTEX_STRIDE_INTS;
            if (required <= this.vertexData.length) {
                return;
            }
            int next = this.vertexData.length;
            while (next < required) {
                next *= 2;
            }
            int[] copy = new int[next];
            System.arraycopy(this.vertexData, 0, copy, 0, this.vertexData.length);
            this.vertexData = copy;
        }
    }
}
