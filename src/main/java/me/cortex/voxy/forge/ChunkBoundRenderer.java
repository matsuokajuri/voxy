package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.TrackedObject;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import org.embeddedt.embeddium.render.ShaderModBridge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_CCW;
import static org.lwjgl.opengl.GL11C.GL_CW;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glFrontFace;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31C.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL42C.glDrawElementsInstancedBaseInstance;
import static org.lwjgl.opengl.GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;

final class ChunkBoundRenderer extends TrackedObject {
    private static final int INITIAL_MAX_CHUNK_COUNT = 1 << 12;
    private static final int CUBES_PER_BATCH = 32;
    private static final int CUBE_INDEX_BYTES = 6 * 2 * 3;

    private GlBuffer chunkPosBuffer = new GlBuffer(INITIAL_MAX_CHUNK_COUNT * 8L);
    private final GlBuffer uniformBuffer = new GlBuffer(128L);
    private final Long2IntOpenHashMap chunkToIndex = new Long2IntOpenHashMap(INITIAL_MAX_CHUNK_COUNT);
    private final LongOpenHashSet addQueue = new LongOpenHashSet();
    private final LongOpenHashSet removeQueue = new LongOpenHashSet();
    private long[] indexToChunk = new long[INITIAL_MAX_CHUNK_COUNT];
    private final RenderProperties properties;
    private final ForgeOriginalVoxyRenderPipeline pipeline;
    private final int programId;
    private final int vertexArrayId;
    private boolean previousFrameWasExact;

    ChunkBoundRenderer(
            RenderProperties properties,
            ForgeOriginalVoxyRenderPipeline pipeline) {
        this.properties = properties;
        this.chunkToIndex.defaultReturnValue(-1);
        this.pipeline = pipeline.hasTAA() ? pipeline : null;
        int createdProgram = 0;
        int createdVertexArray = 0;
        try {
            createdProgram = this.compileProgram(pipeline);
            this.programId = createdProgram;
            createdVertexArray = glGenVertexArrays();
            this.vertexArrayId = createdVertexArray;
        } catch (RuntimeException e) {
            if (createdVertexArray != 0) {
                glDeleteVertexArrays(createdVertexArray);
            }
            if (createdProgram != 0) {
                glDeleteProgram(createdProgram);
            }
            this.uniformBuffer.free();
            this.chunkPosBuffer.free();
            this.free0();
            throw e;
        }
    }

    void addSection(long pos) {
        if (!this.removeQueue.remove(pos)) {
            this.addQueue.add(pos);
        }
    }

    void removeSection(long pos) {
        if (!this.addQueue.remove(pos)) {
            this.removeQueue.add(pos);
        }
    }

    void reset() {
        this.chunkToIndex.clear();
        this.addQueue.clear();
        this.removeQueue.clear();
        this.previousFrameWasExact = false;
    }

    void render(MDICViewport viewport, boolean renderExactBounds) {
        if (this.previousFrameWasExact && !renderExactBounds) {
            long address = UploadStream.instance().upload(
                    this.chunkPosBuffer.id,
                    0L,
                    this.chunkToIndex.size() * 8L);
            for (int i = 0; i < this.chunkToIndex.size(); i++) {
                putPos(address, this.indexToChunk[i]);
                address += 8L;
            }
            UploadStream.instance().commit();
        }
        if (!this.removeQueue.isEmpty()) {
            boolean wasEmpty = this.chunkToIndex.isEmpty();
            this.removeQueue.forEach(this::removeSectionNow);
            this.removeQueue.clear();
            if (this.chunkToIndex.isEmpty() && !wasEmpty) {
                viewport.clearDepthBounding(this.properties.inverseClearDepth());
            }
        }

        int count = this.chunkToIndex.size();
        float renderDistance = embeddiumSearchDistanceBlocks();
        if (renderExactBounds) {
            long address = UploadStream.instance().upload(
                    this.chunkPosBuffer.id,
                    0L,
                    this.chunkPosBuffer.size());
            count = findEmitBoundingChunks(
                    viewport,
                    renderDistance,
                    (int) (this.chunkPosBuffer.size() / 8L),
                    address);
            UploadStream.instance().commit();
            if (count < 0) {
                this.chunkPosBuffer.free();
                MemoryBuffer chunkPositions = new MemoryBuffer((-count) * 8L);
                count = findEmitBoundingChunks(viewport, renderDistance, -count, chunkPositions.address);
                if (count < 0) {
                    chunkPositions.free();
                    throw new IllegalStateException("Unable to allocate original chunk-bound exact buffer: " + count);
                }
                this.chunkPosBuffer = new GlBuffer(chunkPositions.size, false);
                UploadStream.instance().upload(this.chunkPosBuffer.id, 0L, chunkPositions);
                UploadStream.instance().commit();
                chunkPositions.free();
            }
        }

        this.renderInner(viewport, renderDistance, count);

        if (!this.addQueue.isEmpty()) {
            this.addQueue.forEach(this::addSectionNow);
            this.addQueue.clear();
            UploadStream.instance().commit();
        }
        this.previousFrameWasExact = renderExactBounds;
    }

    @Override
    public void free() {
        this.free0();
        glDeleteProgram(this.programId);
        glDeleteVertexArrays(this.vertexArrayId);
        this.uniformBuffer.free();
        this.chunkPosBuffer.free();
    }

    private void renderInner(MDICViewport viewport, float renderDistanceBlocks, int chunkCount) {
        viewport.clearDepthBounding(this.properties.inverseClearDepth());
        if (chunkCount == 0) {
            return;
        }

        long ptr = UploadStream.instance().upload(this.uniformBuffer.id, 0L, 128L);
        long matrixPtr = ptr;
        ptr += 4L * 4L * 4L;

        int blockX = (int) Math.floor(viewport.cameraX);
        int blockY = (int) Math.floor(viewport.cameraY);
        int blockZ = (int) Math.floor(viewport.cameraZ);
        new Vector3i(blockX, blockY, blockZ).getToAddress(ptr);
        ptr += 4L * 4L;

        Vector3f negInnerBlock = new Vector3f(
                (float) (viewport.cameraX - blockX),
                (float) (viewport.cameraY - blockY),
                (float) (viewport.cameraZ - blockZ));
        negInnerBlock.getToAddress(ptr);
        ptr += 4L * 3L;
        viewport.MVP.translate(negInnerBlock.negate(), new Matrix4f()).getToAddress(matrixPtr);
        MemoryUtil.memPutFloat(ptr, renderDistanceBlocks);
        UploadStream.instance().commit();

        boolean oldDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        try {
            glFrontFace(GL_CW);
            glEnable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDepthFunc(this.properties.furtherDepthCompare());

            glBindVertexArray(this.vertexArrayId);
            viewport.bindDepthBoundingFramebuffer();
            glUseProgram(this.programId);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.chunkPosBuffer.id);
            if (this.pipeline != null) {
                this.pipeline.bindUniforms();
            }
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE_BB_BYTE.id());

            if (chunkCount >= CUBES_PER_BATCH) {
                glDrawElementsInstanced(
                        GL_TRIANGLES,
                        CUBE_INDEX_BYTES * CUBES_PER_BATCH,
                        GL_UNSIGNED_BYTE,
                        0L,
                        chunkCount / CUBES_PER_BATCH);
            }
            if (chunkCount % CUBES_PER_BATCH != 0) {
                glDrawElementsInstancedBaseInstance(
                        GL_TRIANGLES,
                        CUBE_INDEX_BYTES * (chunkCount % CUBES_PER_BATCH),
                        GL_UNSIGNED_BYTE,
                        0L,
                        1,
                        (chunkCount / CUBES_PER_BATCH) * CUBES_PER_BATCH);
            }
        } finally {
            glFrontFace(GL_CCW);
            glDepthFunc(this.properties.closerEqualDepthCompare());
            glDepthMask(oldDepthMask);
            glEnable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
            glBindVertexArray(0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            glUseProgram(0);
        }
    }

    private void addSectionNow(long pos) {
        if (this.chunkToIndex.containsKey(pos)) {
            return;
        }
        this.ensureChunkCapacity();
        int index = this.chunkToIndex.size();
        this.chunkToIndex.put(pos, index);
        this.indexToChunk[index] = pos;
        this.put(index, pos);
    }

    private void removeSectionNow(long pos) {
        int index = this.chunkToIndex.remove(pos);
        if (index == -1) {
            return;
        }
        if (index == this.chunkToIndex.size()) {
            return;
        }
        long endPos = this.indexToChunk[this.chunkToIndex.size()];
        if (this.chunkToIndex.put(endPos, index) == -1) {
            throw new IllegalStateException("Original chunk-bound tracker lost compacted section");
        }
        this.indexToChunk[index] = endPos;
        this.put(index, endPos);
    }

    private void ensureChunkCapacity() {
        if (this.chunkToIndex.size() < this.indexToChunk.length) {
            return;
        }
        UploadStream.instance().commit();
        int size = (int) (this.indexToChunk.length * 1.5D);
        GlBuffer old = this.chunkPosBuffer;
        this.chunkPosBuffer = new GlBuffer(size * 8L);
        glCopyNamedBufferSubData(old.id, this.chunkPosBuffer.id, 0L, 0L, old.size());
        old.free();
        long[] oldIndexToChunk = this.indexToChunk;
        this.indexToChunk = new long[size];
        System.arraycopy(oldIndexToChunk, 0, this.indexToChunk, 0, oldIndexToChunk.length);
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
    }

    private void put(int index, long pos) {
        long ptr = UploadStream.instance().upload(this.chunkPosBuffer.id, 8L * index, 8L);
        putPos(ptr, pos);
    }

    private int compileProgram(ForgeOriginalVoxyRenderPipeline pipeline) {
        String vertexSource = this.properties.injectDefines(ShaderLoader.parse("voxy:chunkoutline/outline.vsh"));
        String taa = pipeline.taaFunction("getTAA");
        if (taa != null) {
            vertexSource += "\n\n\n" + taa;
            vertexSource = withDefine(vertexSource, "TAA", 1);
        }
        String fragmentSource = ShaderLoader.parse("voxy:chunkoutline/outline.fsh");
        return compileProgram(vertexSource, fragmentSource, "chunk-bound");
    }

    private static int findEmitBoundingChunks(
            MDICViewport viewport,
            float searchDistance,
            int capacity,
            long writePtr) {
        if (capacity == -1) {
            writePtr = 0L;
            capacity = Integer.MAX_VALUE;
        }
        float distanceSquared = searchDistance * searchDistance;
        int blockX = (int) Math.floor(viewport.cameraX);
        int blockY = (int) Math.floor(viewport.cameraY);
        int blockZ = (int) Math.floor(viewport.cameraZ);
        float fracY = (float) (viewport.cameraY - (int) viewport.cameraY);
        float fracX = (float) (viewport.cameraX - (int) viewport.cameraX);
        float fracZ = (float) (viewport.cameraZ - (int) viewport.cameraZ);

        int minChunkY = blockY >> 4;
        int maxChunkY = blockY >> 4;
        while (testYPos(blockY, fracY, minChunkY, searchDistance)) {
            minChunkY--;
        }
        minChunkY++;
        while (testYPos(blockY, fracY, maxChunkY, searchDistance)) {
            maxChunkY++;
        }
        maxChunkY--;

        int count = 0;
        long blockPos = Integer.toUnsignedLong(blockX) | (Integer.toUnsignedLong(blockZ) << 32);
        int minChunkX = ((int) Math.floor(viewport.cameraX - searchDistance) >> 4) - 2;
        int maxChunkX = ((int) Math.ceil(viewport.cameraX + searchDistance) >> 4) + 2;
        int minChunkZ = ((int) Math.floor(viewport.cameraZ - searchDistance) >> 4) - 2;
        int maxChunkZ = ((int) Math.ceil(viewport.cameraZ + searchDistance) >> 4) + 2;
        for (int chunkX = minChunkX; chunkX < maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ < maxChunkZ; chunkZ++) {
                if (!testXZPos(blockPos, fracX, fracZ, chunkX, chunkZ, distanceSquared)) {
                    continue;
                }
                if (!testXZPos(blockPos, fracX, fracZ, chunkX + 1, chunkZ + 1, distanceSquared)
                        || !testXZPos(blockPos, fracX, fracZ, chunkX - 1, chunkZ + 1, distanceSquared)
                        || !testXZPos(blockPos, fracX, fracZ, chunkX + 1, chunkZ - 1, distanceSquared)
                        || !testXZPos(blockPos, fracX, fracZ, chunkX - 1, chunkZ - 1, distanceSquared)) {
                    for (int chunkY = minChunkY + 1; chunkY < maxChunkY; chunkY++) {
                        if (count++ < capacity && writePtr != 0L) {
                            putPos(writePtr, SectionPos.asLong(chunkX, chunkY, chunkZ));
                            writePtr += 8L;
                        }
                    }
                }
                if (maxChunkY != minChunkY) {
                    if (count++ < capacity && writePtr != 0L) {
                        putPos(writePtr, SectionPos.asLong(chunkX, maxChunkY, chunkZ));
                        writePtr += 8L;
                    }
                    if (count++ < capacity && writePtr != 0L) {
                        putPos(writePtr, SectionPos.asLong(chunkX, minChunkY, chunkZ));
                        writePtr += 8L;
                    }
                } else if (count++ < capacity && writePtr != 0L) {
                    putPos(writePtr, SectionPos.asLong(chunkX, minChunkY, chunkZ));
                    writePtr += 8L;
                }
            }
        }
        return count > capacity ? -count : count;
    }

    private static boolean testYPos(int blockY, float fracY, int chunkY, float distance) {
        int relativeY = chunkY * 16 - blockY;
        float dy = nearestToZero(relativeY - 1, relativeY + 17) - fracY;
        return Math.abs(dy) < distance;
    }

    private static boolean testXZPos(long blockPos, float fracX, float fracZ, int chunkX, int chunkZ, float distanceSquared) {
        int relativeX = chunkX * 16 - (int) blockPos;
        int relativeZ = chunkZ * 16 - (int) (blockPos >> 32);
        float dx = nearestToZero(relativeX - 1, relativeX + 17) - fracX;
        float dz = nearestToZero(relativeZ - 1, relativeZ + 17) - fracZ;
        return dx * dx + dz * dz < distanceSquared;
    }

    private static int nearestToZero(int min, int max) {
        if (min > 0) {
            return min;
        }
        if (max < 0) {
            return max;
        }
        return 0;
    }

    private static void putPos(long ptr, long pos) {
        MemoryUtil.memPutInt(ptr, (int) (pos & 0xFFFFFFFFL));
        MemoryUtil.memPutInt(ptr + 4L, (int) ((pos >>> 32) & 0xFFFFFFFFL));
    }

    //The mask cull radius must equal the circle Embeddium actually draws, i.e.
    // RenderSectionManager.getSearchDistance(): with fog occlusion enabled and no shaderpack the
    // drawn circle shrinks to the opaque-fog end, and masking beyond it leaves masked-but-never-
    // drawn holes. Original Voxy always used the full option render distance here.
    private static float embeddiumSearchDistanceBlocks() {
        float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
        if (!SodiumClientMod.options().performance.useFogOcclusion || ShaderModBridge.areShadersEnabled()) {
            return renderDistance;
        }
        float[] fogColor = RenderSystem.getShaderFogColor();
        if (!Mth.equal(fogColor[3], 1.0F)) {
            return renderDistance;
        }
        return Math.min(renderDistance, RenderSystem.getShaderFogEnd() + 0.5F);
    }

    private static String withDefine(String source, String name, int value) {
        int split = source.indexOf('\n');
        String define = "#define " + name + ' ' + value + '\n';
        return split < 0 ? source + '\n' + define : source.substring(0, split + 1) + define + source.substring(split + 1);
    }

    private static int compileProgram(String vertexSource, String fragmentSource, String name) {
        int vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource, name + " vertex");
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource, name + " fragment");
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException(name + " link failed: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source, String name) {
        int shader = glCreateShader(type);
        glShaderSource(shader, ForgeOriginalVoxyShaderCompiler.prepareSource(source));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(name + " compile failed: " + log);
        }
        return shader;
    }
}
