package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ForgeTexturedReadbackMeshBuilder {
    private static final int SECTION_SIZE = 32;
    private static final int MAX_SECTIONS = 64;
    private static final int MAX_RECORDS = 4096;
    private static final int MAX_QUADS = 1024;
    private static final int FLOATS_PER_VERTEX = 5;

    private ForgeTexturedReadbackMeshBuilder() {
    }

    static ForgeTexturedReadbackMesh buildSample(ForgeVoxyInstance instance) {
        if (!RenderSystem.isOnRenderThread()) {
            return ForgeTexturedReadbackMesh.failure("not-render-thread");
        }

        ForgeModelAtlasUploadStats uploadStatus = instance.getModelAtlasPixelUploader().createStatusSnapshot();
        if (!uploadStatus.sampleAtlasPixelsUploaded()) {
            uploadStatus = instance.getModelAtlasPixelUploader().uploadSample();
        }
        if (!uploadStatus.sampleAtlasPixelsUploaded()) {
            return ForgeTexturedReadbackMesh.failure("atlas-pixels-missing");
        }

        ForgeTexturedDebugQuadSample sample = instance.getModelAtlasPixelUploader().createTexturedDebugQuadSample(0);
        if (!sample.ready()) {
            return ForgeTexturedReadbackMesh.failure("sample-missing");
        }

        ForgeGpuGeometryUploadManager uploadManager = instance.getGpuGeometryUploadManager();
        ForgeGpuGeometryHeap heap = uploadManager.getHeapForDebugReadback();
        boolean geometryHeapReady = heap.isCreated();
        List<Integer> sectionIds = uploadManager.createUploadedSectionIdSnapshot();
        boolean metadataReady = !sectionIds.isEmpty();
        if (!geometryHeapReady || !metadataReady) {
            return fallback(sample, geometryHeapReady, metadataReady, "heap-or-metadata-missing");
        }

        Collections.sort(sectionIds);
        ArrayList<Float> vertices = new ArrayList<>();
        int matchingSections = 0;
        int matchingRecords = 0;
        int builtQuads = 0;
        int scannedRecords = 0;

        for (Integer sectionId : sectionIds) {
            if (sectionId == null || sectionId < 0 || matchingSections >= MAX_SECTIONS || scannedRecords >= MAX_RECORDS || builtQuads >= MAX_QUADS) {
                break;
            }
            try {
                int[] words = heap.readbackMetadata(sectionId);
                ForgeGpuGeometryDecodedMetadata metadata = ForgeGpuGeometryDecodedMetadata.decode(words);
                String metadataError = metadata.validate(heap, instance.getSectionGeometryManager(), sectionId, words);
                if (!"none".equals(metadataError)) {
                    continue;
                }

                boolean sectionMatched = false;
                for (int bucket = 0; bucket < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT && scannedRecords < MAX_RECORDS && builtQuads < MAX_QUADS; bucket++) {
                    int startOffset = metadata.offsets()[bucket];
                    int endOffset = bucket + 1 < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT
                            ? metadata.offsets()[bucket + 1]
                            : metadata.itemCount();
                    int bucketSize = endOffset - startOffset;
                    if (bucketSize <= 0 || bucket == 0) {
                        continue;
                    }

                    int count = Math.min(bucketSize, MAX_RECORDS - scannedRecords);
                    long[] records = heap.readbackGeometry(metadata.geometryPtr() + startOffset, count);
                    for (long record : records) {
                        scannedRecords++;
                        if (!isRecordValid(record) || ForgeVoxyQuadEncoder.extractModelId(record) != sample.modelId()) {
                            continue;
                        }
                        matchingRecords++;
                        sectionMatched = true;
                        emitTexturedRecord(metadata.position(), record, instance, vertices);
                        builtQuads++;
                        if (builtQuads >= MAX_QUADS || scannedRecords >= MAX_RECORDS) {
                            break;
                        }
                    }
                }
                if (sectionMatched) {
                    matchingSections++;
                }
            } catch (RuntimeException ignored) {
                // This debug-only builder skips malformed sections; the status reports the matched/built counts.
            }
        }

        if (builtQuads == 0) {
            return fallback(sample, geometryHeapReady, metadataReady, "sample-modelid-not-present-in-heap");
        }

        float[] vertexArray = toArray(vertices);
        return new ForgeTexturedReadbackMesh(
                true,
                "ok",
                true,
                true,
                true,
                false,
                sample.modelId(),
                sample.blockStateId(),
                sample.blockState(),
                sample.sourceSprite(),
                sample.sourceSpriteAtlas(),
                matchingSections,
                matchingRecords,
                builtQuads,
                vertexArray.length / FLOATS_PER_VERTEX,
                vertexArray,
                false
        );
    }

    private static ForgeTexturedReadbackMesh fallback(ForgeTexturedDebugQuadSample sample, boolean geometryHeapReady, boolean metadataReady, String reason) {
        ArrayList<Float> vertices = new ArrayList<>();
        float u0 = sample.tileX() / (float) sample.textureWidth();
        float v0 = sample.tileY() / (float) sample.textureHeight();
        float u1 = (sample.tileX() + ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE) / (float) sample.textureWidth();
        float v1 = (sample.tileY() + ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE) / (float) sample.textureHeight();
        float x0 = -0.35F;
        float x1 = 0.35F;
        float y0 = -0.35F;
        float y1 = 0.35F;
        putVertex(vertices, x0, y0, 0.0F, u0, v0);
        putVertex(vertices, x1, y0, 0.0F, u1, v0);
        putVertex(vertices, x1, y1, 0.0F, u1, v1);
        putVertex(vertices, x1, y1, 0.0F, u1, v1);
        putVertex(vertices, x0, y1, 0.0F, u0, v1);
        putVertex(vertices, x0, y0, 0.0F, u0, v0);
        float[] vertexArray = toArray(vertices);
        return new ForgeTexturedReadbackMesh(
                true,
                reason,
                geometryHeapReady,
                metadataReady,
                false,
                true,
                sample.modelId(),
                sample.blockStateId(),
                sample.blockState(),
                sample.sourceSprite(),
                sample.sourceSpriteAtlas(),
                0,
                0,
                1,
                vertexArray.length / FLOATS_PER_VERTEX,
                vertexArray,
                true
        );
    }

    private static void emitTexturedRecord(long sectionPosition, long record, ForgeVoxyInstance instance, ArrayList<Float> vertices) {
        int face = ForgeVoxyQuadEncoder.extractFace(record);
        ForgeTexturedDebugQuadSample faceSample = instance.getModelAtlasPixelUploader().createTexturedDebugQuadSample(face);
        if (!faceSample.ready()) {
            faceSample = instance.getModelAtlasPixelUploader().createTexturedDebugQuadSample(0);
        }
        float u0 = faceSample.tileX() / (float) faceSample.textureWidth();
        float v0 = faceSample.tileY() / (float) faceSample.textureHeight();
        float u1 = (faceSample.tileX() + ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE) / (float) faceSample.textureWidth();
        float v1 = (faceSample.tileY() + ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE) / (float) faceSample.textureHeight();

        int level = Math.max(0, WorldEngine.getLevel(sectionPosition));
        int scale = 1 << Math.min(12, level);
        float baseX = WorldEngine.getX(sectionPosition) * (float) SECTION_SIZE * scale;
        float baseY = WorldEngine.getY(sectionPosition) * (float) SECTION_SIZE * scale;
        float baseZ = WorldEngine.getZ(sectionPosition) * (float) SECTION_SIZE * scale;
        int localX = ForgeVoxyQuadEncoder.extractLocalX(record);
        int localY = ForgeVoxyQuadEncoder.extractLocalY(record);
        int localZ = ForgeVoxyQuadEncoder.extractLocalZ(record);
        int length = ForgeVoxyQuadEncoder.extractLength(record);
        int width = ForgeVoxyQuadEncoder.extractWidth(record);

        float x0 = baseX + localX * scale;
        float y0 = baseY + localY * scale;
        float z0 = baseZ + localZ * scale;
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
            x1 = x0 + length * scale;
            z2 = z0 + width * scale;
            x3 = x1;
            z3 = z2;
        } else if (face == 2 || face == 3) {
            x1 = x0 + length * scale;
            y2 = y0 + width * scale;
            x3 = x1;
            y3 = y2;
        } else {
            y1 = y0 + length * scale;
            z2 = z0 + width * scale;
            y3 = y1;
            z3 = z2;
        }

        putVertex(vertices, x0, y0, z0, u0, v0);
        putVertex(vertices, x1, y1, z1, u1, v0);
        putVertex(vertices, x3, y3, z3, u1, v1);
        putVertex(vertices, x3, y3, z3, u1, v1);
        putVertex(vertices, x2, y2, z2, u0, v1);
        putVertex(vertices, x0, y0, z0, u0, v0);
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

    private static void putVertex(ArrayList<Float> vertices, float x, float y, float z, float u, float v) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(u);
        vertices.add(v);
    }

    private static float[] toArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
