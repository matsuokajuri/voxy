package me.cortex.voxy.forge;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pass3ParityRepairTest {
    @Test
    void commonLoggerClassDoesNotLinkClientOnlyMinecraft() throws IOException {
        String outerClass = classFileText("/me/cortex/voxy/common/Logger.class");
        String clientOnlyClass = classFileText("/me/cortex/voxy/common/Logger$ClientOnly.class");

        assertFalse(outerClass.contains("net/minecraft/client/Minecraft"));
        assertTrue(clientOnlyClass.contains("net/minecraft/client/Minecraft"));

        String source = Files.readString(Path.of("src/main/java/me/cortex/voxy/common/Logger.java"));
        assertTrue(source.contains("FMLEnvironment.dist == Dist.CLIENT"));
    }

    @Test
    void commonParityClassesRouteThroughOriginalLogger() throws IOException {
        String[][] classes = {
                {"src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java",
                        "/me/cortex/voxy/common/config/section/SectionSerializationStorage.class"},
                {"src/main/java/me/cortex/voxy/common/util/TrackedObject.java",
                        "/me/cortex/voxy/common/util/TrackedObject.class"},
                {"src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java",
                        "/me/cortex/voxy/common/world/ActiveSectionTracker.class"},
                {"src/main/java/me/cortex/voxy/common/world/other/Mapper.java",
                        "/me/cortex/voxy/common/world/other/Mapper.class"},
                {"src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java",
                        "/me/cortex/voxy/common/world/SaveLoadSystem3.class"},
                {"src/main/java/me/cortex/voxy/common/world/WorldEngine.java",
                        "/me/cortex/voxy/common/world/WorldEngine.class"}
        };

        for (String[] entry : classes) {
            String source = Files.readString(Path.of(entry[0]));
            assertTrue(source.contains("import me.cortex.voxy.common.Logger;"), entry[0]);
            assertFalse(source.contains("LoggerFactory"), entry[0]);

            String bytecode = classFileText(entry[1]);
            assertTrue(bytecode.contains("me/cortex/voxy/common/Logger"), entry[1]);
            assertFalse(bytecode.contains("org/slf4j/LoggerFactory"), entry[1]);
        }

        String mapper = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/common/world/other/Mapper.java"));
        assertTrue(mapper.contains("Logger::error"));
        assertFalse(mapper.contains("LOGGER::error"));
    }

    @Test
    void mapperRejectsWrongTypeIdAndBlockStateTags() throws IOException {
        CompoundTag wrongId = new CompoundTag();
        wrongId.putString("id", "0");
        assertThrows(IllegalStateException.class,
                () -> Mapper.StateEntry.deserialize(0, compressed(wrongId), new boolean[1]));

        CompoundTag wrongBlockState = new CompoundTag();
        wrongBlockState.putInt("id", 0);
        wrongBlockState.putString("block_state", "minecraft:air");
        assertThrows(IllegalStateException.class,
                () -> Mapper.StateEntry.deserialize(0, compressed(wrongBlockState), new boolean[1]));
    }

    @Test
    void mapperPreservesOriginalNumericAndStringFallbackSemantics() throws IOException {
        CompoundTag valid = new CompoundTag();
        valid.putLong("id", 42L);
        valid.putString("biome_id", "minecraft:plains");
        Mapper.BiomeEntry decoded = Mapper.BiomeEntry.deserialize(42, compressed(valid));
        assertEquals("minecraft:plains", decoded.biome);

        CompoundTag wrongBiomeType = new CompoundTag();
        wrongBiomeType.putInt("id", 42);
        wrongBiomeType.putInt("biome_id", 7);
        assertNull(Mapper.BiomeEntry.deserialize(42, compressed(wrongBiomeType)).biome);
    }

    @Test
    void depthStageRestoresDrawAndReadFramebuffersSeparately() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java"));

        assertTrue(source.contains("int readFramebuffer"));
        assertTrue(source.contains("glGetInteger(GL_READ_FRAMEBUFFER_BINDING)"));
        assertTrue(source.contains("glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.drawFramebuffer)"));
        assertTrue(source.contains("glBindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer)"));
    }

    @Test
    void embeddiumBoundaryRoundTripsEveryActiveVoxyGlMutation() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java"));
        String restore = source.substring(source.indexOf("private static void restoreOriginalVoxyRenderState"));

        int renderStart = source.indexOf("public void renderEmbeddiumCutout(");
        int renderEnd = source.indexOf(
                "public MultiThreadPrioritySemaphore.Block createEmbeddiumBuilderSemaphoreBlock()",
                renderStart);
        assertTrue(renderStart >= 0 && renderEnd > renderStart);
        String render = source.substring(renderStart, renderEnd);
        int capture = render.indexOf("oldRenderState = captureOriginalVoxyRenderState(renderPipeline);");
        int armed = render.indexOf("capturedRenderState = true;", capture);
        int firstRenderMutation = render.indexOf("glViewport(0, 0, viewport.width, viewport.height);");
        assertTrue(capture >= 0 && capture < armed && armed < firstRenderMutation);

        int successfulRender = render.indexOf(
                "this.lastLifecycleEvent = \"embeddium-cutout-original-run-pipeline-order\";");
        int outerFinally = render.indexOf("} finally {", successfulRender);
        int conditionalRestore = render.indexOf("if (capturedRenderState)", outerFinally);
        int restoreCall = render.indexOf(
                "restoreOriginalVoxyRenderState(oldRenderState);",
                conditionalRestore);
        assertTrue(successfulRender >= 0 && successfulRender < outerFinally);
        assertTrue(outerFinally < conditionalRestore && conditionalRestore < restoreCall);

        int captureStateStart = source.indexOf(
                "private static OriginalVoxyRenderState captureOriginalVoxyRenderState(");
        int restoreStateStart = source.indexOf(
                "private static void restoreOriginalVoxyRenderState",
                captureStateStart);
        String captureState = source.substring(captureStateStart, restoreStateStart);
        assertTrue(captureState.contains("renderPipeline.captureOculusNon2DTextureBindings()"));
        assertTrue(restore.contains(
                "ForgeOriginalVoxyTextureBindings.restore(state.oculusNon2DTextureBindings())"));

        String renderPipeline = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java"));
        assertTrue(renderPipeline.contains(
                "return this.oculusPipelineData.getImageSet().captureNon2DTextureBindings("));
        assertTrue(renderPipeline.contains(
                "ForgeOriginalVoxyOculusRenderPipelineData.BASE_SAMPLER_BINDING_INDEX);"));

        assertTrue(source.contains("glGetInteger64i(startPname, bindingIndex)"));
        assertTrue(source.contains("glGetInteger64i(sizePname, bindingIndex)"));
        assertTrue(source.contains(
                "glBindBufferRange(target, bindingIndex, buffer, start, size)"));
        assertTrue(restore.indexOf("restoreIndexedBufferBindings(GL_SHADER_STORAGE_BUFFER")
                < restore.indexOf("glBindBuffer(GL_SHADER_STORAGE_BUFFER, state.shaderStorageBufferBinding())"));
        assertTrue(restore.indexOf("restoreIndexedBufferBindings(GL_UNIFORM_BUFFER")
                < restore.indexOf("glBindBuffer(GL_UNIFORM_BUFFER, state.uniformBufferBinding())"));
        assertTrue(source.contains("glGetInteger(GL_ARRAY_BUFFER_BINDING)"));
        assertTrue(restore.contains("glBindBuffer(GL_ARRAY_BUFFER, state.arrayBuffer())"));
        assertTrue(source.contains("glGetInteger(GL_DISPATCH_INDIRECT_BUFFER_BINDING)"));
        assertTrue(restore.contains("glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, state.dispatchIndirectBuffer())"));

        for (String unpackState : new String[]{
                "GL_UNPACK_ROW_LENGTH",
                "GL_UNPACK_SKIP_PIXELS",
                "GL_UNPACK_SKIP_ROWS",
                "GL_UNPACK_IMAGE_HEIGHT",
                "GL_UNPACK_SKIP_IMAGES",
                "GL_UNPACK_ALIGNMENT"}) {
            assertTrue(source.contains("glGetInteger(" + unpackState + ")"), unpackState);
            assertTrue(restore.contains("glPixelStorei(" + unpackState), unpackState);
        }

        for (String imageState : new String[]{
                "GL_IMAGE_BINDING_NAME",
                "GL_IMAGE_BINDING_LEVEL",
                "GL_IMAGE_BINDING_LAYERED",
                "GL_IMAGE_BINDING_LAYER",
                "GL_IMAGE_BINDING_ACCESS",
                "GL_IMAGE_BINDING_FORMAT"}) {
            assertTrue(source.contains("glGetIntegeri(" + imageState + ", unit)"), imageState);
        }
        assertTrue(restore.contains("glBindImageTexture("));

        assertTrue(source.contains("GL_STENCIL_BACK_FUNC"));
        assertTrue(source.contains("GL_STENCIL_BACK_WRITEMASK"));
        assertTrue(restore.contains("restoreStencilFace(GL_FRONT, state.frontStencil())"));
        assertTrue(restore.contains("restoreStencilFace(GL_BACK, state.backStencil())"));
        assertTrue(source.contains("glStencilFuncSeparate(face"));
        assertTrue(source.contains("glStencilMaskSeparate(face"));
        assertTrue(source.contains("glStencilOpSeparate(face"));
        assertTrue(restore.contains("glPolygonMode(GL_FRONT_AND_BACK, state.polygonMode())"));
        assertFalse(restore.contains("glPolygonMode(GL_FRONT,"));
        assertFalse(restore.contains("glPolygonMode(GL_BACK,"));

        assertTrue(restore.contains("glBindTexture(GL_TEXTURE_2D, textureBindings[i])"));
        assertFalse(restore.contains("glBindTextureUnit(i, textureBindings[i])"));

        assertTrue(captureState.contains("renderPipeline.embeddiumTextureBindingCount()"));
        assertTrue(captureState.contains("renderPipeline.embeddiumUniformBufferBindingIndices()"));
        assertTrue(captureState.contains(
                "renderPipeline.embeddiumShaderStorageBufferBindingIndices()"));
        assertFalse(captureState.contains("GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS"));
        assertFalse(captureState.contains("GL_MAX_UNIFORM_BUFFER_BINDINGS"));
        assertFalse(captureState.contains("GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS"));
        assertTrue(renderPipeline.contains("ORIGINAL_TEXTURE_SAMPLER_BINDING_COUNT = 12"));
        assertTrue(renderPipeline.contains("data.getImageSet().bindingCount()"));
        assertTrue(renderPipeline.contains("data.getSsboSet().bindingCount()"));
        assertTrue(renderPipeline.contains("PrintfDebugUtil.activeBindingIndex()"));

        String oculusPipelineData = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java"));
        assertTrue(oculusPipelineData.contains("public int bindingCount()"));
        assertTrue(oculusPipelineData.contains(
                "record SSBOSet(String layout, IntConsumer bindingFunction, int bindingCount)"));
    }

    @Test
    void textureBindingPlanIsTargetSpecificAndLimitedToOculusTextureTypes()
            throws IOException, ReflectiveOperationException {
        assertEquals(
                org.lwjgl.opengl.GL11C.GL_TEXTURE_1D,
                oculusTextureTarget("TEXTURE_1D"));
        assertEquals(
                org.lwjgl.opengl.GL11C.GL_TEXTURE_2D,
                oculusTextureTarget("TEXTURE_2D"));
        assertEquals(
                org.lwjgl.opengl.GL12C.GL_TEXTURE_3D,
                oculusTextureTarget("TEXTURE_3D"));
        assertEquals(
                org.lwjgl.opengl.GL31C.GL_TEXTURE_RECTANGLE,
                oculusTextureTarget("TEXTURE_RECTANGLE"));

        assertEquals(
                org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_1D,
                ForgeOriginalVoxyTextureBindings.bindingQuery(org.lwjgl.opengl.GL11C.GL_TEXTURE_1D));
        assertEquals(
                org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D,
                ForgeOriginalVoxyTextureBindings.bindingQuery(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D));
        assertEquals(
                org.lwjgl.opengl.GL12C.GL_TEXTURE_BINDING_3D,
                ForgeOriginalVoxyTextureBindings.bindingQuery(org.lwjgl.opengl.GL12C.GL_TEXTURE_3D));
        assertEquals(
                org.lwjgl.opengl.GL31C.GL_TEXTURE_BINDING_RECTANGLE,
                ForgeOriginalVoxyTextureBindings.bindingQuery(org.lwjgl.opengl.GL31C.GL_TEXTURE_RECTANGLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> ForgeOriginalVoxyTextureBindings.bindingQuery(org.lwjgl.opengl.GL13C.GL_TEXTURE_CUBE_MAP));

        String oculus = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java"));
        assertTrue(oculus.contains("BASE_SAMPLER_BINDING_INDEX = 6"));
        assertTrue(oculus.contains("textureTarget(type)"));
        assertTrue(oculus.contains("textureTarget(TextureType.TEXTURE_2D)"));
        assertFalse(oculus.contains(".getGlType()"));
        assertTrue(oculus.contains("captureNon2DTextureBindings(int base)"));
        assertTrue(oculus.contains(
                "return ForgeOriginalVoxyTextureBindings.captureNon2D(base, this.textureTargets);"));
        assertTrue(oculus.contains("ForgeOriginalVoxyTextureBindings.bind(unit, sampler.textureTarget, 0)"));

        Pattern allTargetZero = Pattern.compile("glBindTextureUnit\\([^,]+,\\s*0\\)");
        for (String path : new String[]{
                "src/main/java/me/cortex/voxy/forge/Capabilities.java",
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java",
                "src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java",
                "src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java",
                "src/main/java/me/cortex/voxy/forge/SSAO.java"}) {
            String routeSource = Files.readString(Path.of(path));
            assertFalse(allTargetZero.matcher(routeSource).find(), path);
        }
    }

    private static int oculusTextureTarget(String constantName) throws ReflectiveOperationException {
        Class<?> textureType = Class.forName("net.irisshaders.iris.gl.texture.TextureType");
        Object type = textureType.getField(constantName).get(null);
        Method mapping = ForgeOriginalVoxyOculusRenderPipelineData.class.getDeclaredMethod(
                "textureTarget",
                textureType);
        mapping.setAccessible(true);
        return (int) mapping.invoke(null, type);
    }

    private static byte[] compressed(CompoundTag tag) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, output);
        return output.toByteArray();
    }

    private static String classFileText(String resource) throws IOException {
        try (InputStream stream = Pass3ParityRepairTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing class resource " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
