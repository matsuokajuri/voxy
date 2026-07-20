package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Round8ModelDataLayoutContractTest {
    @Test
    void cpuMetadataKeepsTheOriginalSixFaceAndGlobalBitContract() {
        int face = 4;
        long faceMetadata = 0b1_1101L << (face * 8);
        assertTrue(ModelQueries.faceExists(faceMetadata, face));
        assertTrue(ModelQueries.faceOccludes(faceMetadata, face));
        assertTrue(ModelQueries.faceCanBeOccluded(faceMetadata, face));
        assertTrue(ModelQueries.faceUsesSelfLighting(faceMetadata, face));
        assertTrue(ModelQueries.faceUsesOcclusionMask(faceMetadata, face));

        long absentFace = 0xFFL << (face * 8);
        assertFalse(ModelQueries.faceExists(absentFace, face));
        assertFalse(ModelQueries.faceOccludes(absentFace, face));
        assertFalse(ModelQueries.faceUsesOcclusionMask(absentFace, face));

        long global = (1L | 2L | 4L | 8L | 16L | 32L | 64L | (13L << 7))
                << (ForgeModelAtlasLayout.FACE_COUNT * 8);
        assertTrue(ModelQueries.isBiomeColoured(global));
        assertTrue(ModelQueries.isTranslucent(global));
        assertTrue(ModelQueries.isDoubleSided(global));
        assertTrue(ModelQueries.containsFluid(global));
        assertTrue(ModelQueries.isFluid(global));
        assertTrue(ModelQueries.cullsSame(global));
        assertTrue(ModelQueries.isFullyOpaque(global));
        assertEquals(13L, ModelQueries.lightEmission(global));
    }

    @Test
    void gpuRecordAndAtlasRemainSixteenBitModelIdLayouts() {
        assertEquals(64, ForgeModelStoreLayoutSpec.MODEL_RECORD_BYTES);
        assertEquals(16, ForgeModelStoreLayoutSpec.MODEL_RECORD_WORDS);
        assertEquals(6, ForgeModelStoreLayoutSpec.FACE_DATA_WORDS);
        assertEquals(6, ForgeModelStoreLayoutSpec.WORD_FLAGS_A);
        assertEquals(7, ForgeModelStoreLayoutSpec.WORD_COLOUR_TINT);
        assertEquals(8, ForgeModelStoreLayoutSpec.WORD_CUSTOM_ID);
        assertEquals(ForgeModelStoreLayoutSpec.MODEL_RECORD_BYTES, ModelStore.MODEL_SIZE);

        assertEquals(1 << 16, ForgeModelAtlasLayout.MODEL_ID_LIMIT);
        assertEquals(ForgeModelAtlasLayout.MODEL_ID_LIMIT, ModelStore.MODEL_CAPACITY);
        assertTrue(ForgeModelAtlasLayout.isValidModelId(0xFFFF));
        assertFalse(ForgeModelAtlasLayout.isValidModelId(1 << 16));
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            assertTrue(ForgeModelAtlasLayout.isValidFaceTile(
                    ForgeModelAtlasLayout.faceTile(0xFFFF, face)));
        }
    }

    @Test
    void activeShaderDecodeMatchesTheCpuAndAtlasLayout() throws IOException {
        String blockModel = source("src/main/resources/assets/voxy/shaders/lod/block_model.glsl");
        assertTrue(blockModel.contains("uint faceData[6];"));
        assertTrue(blockModel.contains("uint flagsA;"));
        assertTrue(blockModel.contains("uint colourTint;"));
        assertTrue(blockModel.contains("uint customId;"));
        assertTrue(blockModel.contains("uint _pad[7];"));
        assertTrue(blockModel.contains("(faceData>>16)&63u"));
        assertTrue(blockModel.contains("(faceData>>22)&1u"));
        assertTrue(blockModel.contains("(faceData>>23)&1u"));
        assertTrue(blockModel.contains("(faceData>>24)&3u"));

        String quadFormat = source("src/main/resources/assets/voxy/shaders/lod/quad_format.glsl");
        assertTrue(quadFormat.contains("return Eu32(quad, 16, 26);"));
        assertTrue(quadFormat.contains("return Eu32(quad, 9, 46);"));
        assertTrue(quadFormat.contains("return Eu32(quad, 8, 55);"));

        String quadUtil = source("src/main/resources/assets/voxy/shaders/lod/quad_util.glsl");
        assertTrue(quadUtil.contains("flags |= modelId<<16;//Model id"));

        String fragment = source("src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag");
        assertTrue(fragment.contains("uint modelId = interData.x>>16;"));
        assertTrue(fragment.contains("modelId&0xFFu, (modelId>>8)&0xFFu"));
    }

    @Test
    void productionUploadUsesTheSameExactSixtyFourByteSerializer() throws IOException {
        String modelFactory = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        assertTrue(modelFactory.contains("this.model = serializeModelRecord(build.words());"));
        assertTrue(modelFactory.contains("this.texture = build.mipChain();"));
        assertTrue(modelFactory.contains(
                "words.length != ForgeModelStoreLayoutSpec.MODEL_RECORD_WORDS"));
        assertThrows(IllegalArgumentException.class, () -> ModelFactory.serializeModelRecord(new int[15]));
    }

    @Test
    void gatedRuntimeAuditCommitsBeforeExactBufferAndMipReadback() throws IOException {
        String modelFactory = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        int audit = modelFactory.indexOf("if (AUDIT_MODEL_GPU_UPLOAD");
        int commit = modelFactory.indexOf("UploadStream.instance().commit();", audit);
        int verify = modelFactory.indexOf("modelUpload.verifyGpu(this.store)", audit);
        int free = modelFactory.indexOf("upload.free();", audit);
        assertTrue(audit >= 0);
        assertTrue(commit > audit);
        assertTrue(verify > commit);
        assertTrue(free > verify);

        String modelStore = source("src/main/java/me/cortex/voxy/forge/ModelStore.java");
        assertTrue(modelStore.contains("ARBDirectStateAccess.glGetNamedBufferSubData("));
        assertTrue(modelStore.contains("GL45C.glGetTextureSubImage("));
        assertTrue(modelStore.contains("original-model-record-readback-mismatch-"));
        assertTrue(modelStore.contains("original-model-texture-readback-mismatch-"));

        String build = source("build.gradle");
        assertTrue(build.contains("voxyAuditRound8ModelGpuUpload"));
        assertTrue(build.contains("voxy.forge.auditRound8ModelGpuUpload"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
