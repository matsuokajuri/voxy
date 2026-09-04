package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldSection;
import org.lwjgl.opengl.GL;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL46C.*;

/** Production MDIC programs executed in the root test's single real GL context. */
final class Round10MdicGpuCases {
    private static final int GUARD = 0x4A71_B93D;
    private static final int BUCKETS = GpuBufferLayout.TRANSLUCENT_BUCKETS;
    private static final int FRAME_ID = 71;

    static void runAll() {
        compileProductionSizedPrograms();
        try (Fixture fixture = new Fixture(13, 8, 7, 17, false)) {
            for (int count : new int[]{0, 12, 13, 14}) {
                fixture.resetAndGenerate(count, Shape.ONE_OPAQUE, 0);
                int opaque = Math.min(count, 13);
                int temporal = Math.min(opaque, 7);
                fixture.assertCounts(opaque, 0, temporal);
                fixture.assertOpaqueCommands(opaque, temporal, Shape.ONE_OPAQUE);
                fixture.assertUnwrittenCommands(opaque, 0, temporal);
                fixture.assertGuards();
            }
            fixture.resetAndGenerate(3, Shape.SEVEN_OPAQUE, 0);
            fixture.assertCounts(7, 0, 7); // Six spare opaque slots cannot admit half a section.
            fixture.assertOpaqueCommands(7, 7, Shape.SEVEN_OPAQUE);
            fixture.assertUnwrittenCommands(7, 0, 7);
            int[] counts = fixture.readCounts();
            assertEquals(14, counts[12], "two seven-command batches were rejected");
            assertEquals(1, counts[11] & 1);
            fixture.assertGuards();
        }
        try (Fixture fixture = new Fixture(14, 8, 6, 17, false)) {
            fixture.resetAndGenerate(3, Shape.SEVEN_OPAQUE, 0);
            fixture.assertCounts(14, 0, 0); // A seven-command temporal batch cannot fit a six-command bucket.
            fixture.assertOpaqueCommands(14, 0, Shape.SEVEN_OPAQUE);
            fixture.assertUnwrittenCommands(14, 0, 0);
            assertEquals(14, fixture.readCounts()[14]);
            fixture.assertGuards();
        }
        try (Fixture fixture = new Fixture(511, 257, 255, 513, false)) {
            // Five workgroups compete for the last slots, not just lanes in one subgroup.
            fixture.resetAndGenerate(513, Shape.ONE_OPAQUE, 0);
            fixture.assertCounts(511, 0, 255);
            fixture.assertOpaqueCommands(511, 255, Shape.ONE_OPAQUE);
            fixture.assertGuards();
            fixture.resetAndGenerate(513, Shape.TRANSLUCENT, -1);
            fixture.assertHistogram(257, -1);
            fixture.sortAndBuild();
            fixture.assertTranslucentCommands(257);
            fixture.assertGuards();
        }
        for (boolean subgroup : new boolean[]{false, true}) {
            if (subgroup && !GL.getCapabilities().GL_KHR_shader_subgroup) continue;
            try (Fixture fixture = new Fixture(14, 8, 7, 17, subgroup)) {
                for (int x : new int[]{0, 512, 1023}) {
                    for (int count : new int[]{0, 7, 8, 9}) {
                        fixture.resetAndGenerate(count, Shape.TRANSLUCENT, x);
                        int admitted = Math.min(count, 8);
                        fixture.assertCounts(0, admitted, 0);
                        fixture.assertHistogram(admitted, BUCKETS - 1 - Math.min(x, BUCKETS - 1));
                        fixture.sortAndBuild();
                        fixture.assertTranslucentCommands(admitted);
                        fixture.assertUnwrittenCommands(0, admitted, 0);
                        fixture.assertGuards();
                    }
                }
                fixture.resetAndGenerate(17, Shape.TRANSLUCENT, -1); // Several distinct distance buckets.
                fixture.assertHistogram(8, -1);
                fixture.sortAndBuild();
                fixture.assertTranslucentCommands(8);
                fixture.assertGuards();
                fixture.verifyInvalidBuilderAndRecovery();
                fixture.verifyInvalidInputAndRenderListClamp();
                fixture.verifyActualRasterVisibilityOwner();
            }
        }
        glUseProgram(0);
        assertEquals(GL_NO_ERROR, glGetError());
        System.out.println("ROUND10_GPU_MDIC=production prep/cmdgen/prefix/builder/cull; bounded CAS, sentinels, reset/recovery passed");
    }

    private static void compileProductionSizedPrograms() {
        for (String resource : new String[]{"prep.comp", "cmdgen.comp", "buildtranslucents.comp"}) {
            String source = MDICSectionRenderer.withCommandCapacityDefines(
                    ShaderLoader.parse("voxy:lod/gl46/" + resource));
            if (resource.equals("cmdgen.comp")) source = define(source, "TRANSLUCENT_DISTANCE_BUFFER_BINDING", 7);
            if (resource.equals("buildtranslucents.comp")) source = define(source, "TRANSLUCENT_DISTANCE_BUFFER_BINDING", 5);
            int program = Round10GpuExecutionTest.computeProgram(source);
            glDeleteProgram(program);
            if (resource.equals("cmdgen.comp")) {
                program = Round10GpuExecutionTest.computeProgram(
                        define(define(source, "HAS_STATISTICS", 1), "STATISTICS_BUFFER_BINDING", 8));
                glDeleteProgram(program);
            }
        }
    }

    private static String define(String source, String name, int value) {
        int line = source.indexOf('\n') + 1;
        return source.substring(0, line) + "#define " + name + " " + value + "\n" + source.substring(line);
    }

    private static int graphicsProgram(String vertex, String fragment) {
        int[] shaders = {glCreateShader(GL_VERTEX_SHADER), glCreateShader(GL_FRAGMENT_SHADER)};
        int program = glCreateProgram();
        try {
            for (int i = 0; i < shaders.length; i++) {
                glShaderSource(shaders[i], i == 0 ? vertex : fragment);
                glCompileShader(shaders[i]);
                assertNotEquals(GL_FALSE, glGetShaderi(shaders[i], GL_COMPILE_STATUS), glGetShaderInfoLog(shaders[i]));
                glAttachShader(program, shaders[i]);
            }
            glLinkProgram(program);
            assertNotEquals(GL_FALSE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            return program;
        } catch (Throwable failure) {
            glDeleteProgram(program);
            throw failure;
        } finally {
            for (int shader : shaders) glDeleteShader(shader);
        }
    }

    private enum Shape { ONE_OPAQUE, SEVEN_OPAQUE, TRANSLUCENT }

    private static final class Fixture implements AutoCloseable {
        final int opaqueCapacity, translucentCapacity, temporalCapacity, renderCapacity;
        final int translucentOffset, temporalOffset, commandSlots, prefixSnapshot;
        final int uniform = glCreateBuffers(), counts = glCreateBuffers(), commands = glCreateBuffers();
        final int metadata = glCreateBuffers(), visibility = glCreateBuffers(), lookup = glCreateBuffers();
        final int positions = glCreateBuffers(), distance = glCreateBuffers();
        final int indirectSnapshot = glCreateBuffers();
        final int prep, cmdgen, prefix, builder;
        final int[] positionX;
        int currentCount;
        String generationBindingEvidence;
        int activeFrameId = FRAME_ID;

        Fixture(int opaqueCapacity, int translucentCapacity, int temporalCapacity, int renderCapacity, boolean subgroup) {
            this.opaqueCapacity = opaqueCapacity;
            this.translucentCapacity = translucentCapacity;
            this.temporalCapacity = temporalCapacity;
            this.renderCapacity = renderCapacity;
            // Guards separate logical buckets in this auxiliary stress allocation.
            // Only production capacity/offset defines change; every shader body is unchanged.
            this.translucentOffset = opaqueCapacity + 1;
            this.temporalOffset = this.translucentOffset + translucentCapacity + 1;
            this.commandSlots = this.temporalOffset + temporalCapacity;
            this.prefixSnapshot = BUCKETS + translucentCapacity + 1;
            this.positionX = new int[renderCapacity];
            this.prep = Round10GpuExecutionTest.computeProgram(this.source("prep.comp"));
            this.cmdgen = Round10GpuExecutionTest.computeProgram(define(this.source("cmdgen.comp"), "TRANSLUCENT_DISTANCE_BUFFER_BINDING", 7));
            this.builder = Round10GpuExecutionTest.computeProgram(define(this.source("buildtranslucents.comp"), "TRANSLUCENT_DISTANCE_BUFFER_BINDING", 5));
            this.prefix = Round10GpuExecutionTest.computeProgram(define(ShaderLoader.parse(subgroup
                    ? "voxy:util/prefixsum/inital3.comp" : "voxy:util/prefixsum/simple.comp"), "IO_BUFFER", 0));
        }

        String source(String file) {
            String source = MDICSectionRenderer.withCommandCapacityDefines(ShaderLoader.parse("voxy:lod/gl46/" + file));
            String[] names = {"OPAQUE_DRAW_CAPACITY", "TRANSLUCENT_DRAW_CAPACITY", "TEMPORAL_DRAW_CAPACITY",
                    "RENDER_LIST_CAPACITY", "TRANSLUCENT_OFFSET", "TEMPORAL_OFFSET", "TRANSLUCENT_PREFIX_SNAPSHOT_BASE"};
            int[] values = {this.opaqueCapacity, this.translucentCapacity, this.temporalCapacity,
                    this.renderCapacity, this.translucentOffset, this.temporalOffset, this.prefixSnapshot};
            for (int i = 0; i < names.length; i++) {
                source = source.replaceAll("(?m)^#define " + names[i] + " [0-9]+$", "#define " + names[i] + " " + values[i]);
            }
            return source;
        }

        void resetAndGenerate(int attempted, Shape shape, int x) {
            this.currentCount = Math.min(attempted, this.renderCapacity);
            this.activeFrameId++;
            int[] scene = new int[256];
            scene[0] = scene[5] = scene[10] = scene[15] = Float.floatToRawIntBits(1f);
            scene[19] = this.activeFrameId;
            upload(this.uniform, scene);
            int[] countWords = new int[256 + 4];
            Arrays.fill(countWords, 17, countWords.length, GUARD);
            upload(this.counts, countWords);
            upload(this.commands, filled((this.commandSlots + 1) * 5));
            int[] meta = new int[this.renderCapacity * 8 + 4];
            for (int i = 0; i < this.renderCapacity; i++) {
                int sectionX = x == -1 ? i * 139 : x;
                this.positionX[i] = sectionX;
                long key = WorldSection.getWorldSectionId(0, sectionX, 0, 0);
                meta[i * 8] = (int) (key >>> 32);
                meta[i * 8 + 1] = (int) key;
                meta[i * 8 + 3] = i * (shape == Shape.SEVEN_OPAQUE ? 7 : 1);
                meta[i * 8 + 4] = shape == Shape.TRANSLUCENT ? 1 : 1 << 16;
                if (shape == Shape.SEVEN_OPAQUE) meta[i * 8 + 5] = meta[i * 8 + 6] = meta[i * 8 + 7] = 0x0001_0001;
            }
            Arrays.fill(meta, this.renderCapacity * 8, meta.length, GUARD);
            upload(this.metadata, meta);
            int[] visible = filled(this.renderCapacity + 4);
            Arrays.fill(visible, 0, this.renderCapacity, this.activeFrameId);
            upload(this.visibility, visible);
            int[] look = filled(this.renderCapacity + 1 + 4);
            look[0] = attempted;
            for (int i = 0; i < this.renderCapacity; i++) look[i + 1] = i;
            upload(this.lookup, look);
            upload(this.positions, filled(this.renderCapacity * 2 + 4));
            upload(this.distance, filled(this.prefixSnapshot + BUCKETS + 4));
            if (glGetNamedBufferParameteri(this.indirectSnapshot, GL_BUFFER_SIZE) == 0) {
                upload(this.indirectSnapshot, filled(7));
            }
            this.generateNextFrame();
        }

        void generateNextFrame() {
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
            glNamedBufferSubData(this.distance, 0, new int[BUCKETS]);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform);
            range(1, this.counts, 1024);
            range(2, this.lookup, (this.renderCapacity + 1) * 4L);
            glUseProgram(this.prep);
            glDispatchCompute(1, 1, 1);
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
            MDICSectionRenderer.copyDispatchArguments(this.counts, this.indirectSnapshot);
            this.bindGenerationBuffers();
            glUseProgram(this.cmdgen);
            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, this.indirectSnapshot);
            this.generationBindingEvidence = this.verifyGenerationBindings();
            glDispatchComputeIndirect(0);
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
            assertEquals(GL_NO_ERROR, glGetError());
            assertEquals((this.currentCount + 127) / 128, this.readCounts()[0]);
            assertEquals(this.currentCount, this.readCounts()[7]);
        }

        void bindGenerationBuffers() {
            range(1, this.commands, this.commandSlots * 20L);
            range(2, this.counts, 1024);
            range(3, this.metadata, this.renderCapacity * 32L);
            range(4, this.visibility, this.renderCapacity * 4L);
            range(5, this.lookup, (this.renderCapacity + 1) * 4L);
            range(6, this.positions, this.renderCapacity * 8L);
            range(7, this.distance, (this.prefixSnapshot + BUCKETS) * 4L);
        }

        String verifyGenerationBindings() {
            int[] expected = {this.commands, this.counts, this.metadata, this.visibility,
                    this.lookup, this.positions, this.distance};
            long[] sizes = {this.commandSlots * 20L, 1024L, this.renderCapacity * 32L, this.renderCapacity * 4L,
                    (this.renderCapacity + 1) * 4L, this.renderCapacity * 8L, (this.prefixSnapshot + BUCKETS) * 4L};
            String[] blocks = {"DrawBuffer", "DrawCommandCountBuffer", "SectionBuffer", "VisibilityBuffer",
                    "IndirectSectionLookupBuffer", "PositionScratchBuffer", "TranslucentCommandCount"};
            StringBuilder evidence = new StringBuilder(" program=" + glGetInteger(GL_CURRENT_PROGRAM)
                    + "/expected=" + this.cmdgen + " dispatchBuffer=" + glGetInteger(GL_DISPATCH_INDIRECT_BUFFER_BINDING)
                    + "/expected=" + (this.indirectSnapshot));
            assertEquals(this.cmdgen, glGetInteger(GL_CURRENT_PROGRAM));
            assertEquals(this.indirectSnapshot, glGetInteger(GL_DISPATCH_INDIRECT_BUFFER_BINDING));
            for (int i = 0; i < expected.length; i++) {
                int id = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i + 1);
                long size = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, i + 1);
                int block = glGetProgramResourceIndex(this.cmdgen, GL_SHADER_STORAGE_BLOCK, blocks[i]);
                assertNotEquals(GL_INVALID_INDEX, block, blocks[i] + " must be active in the cmdgen program");
                int[] binding = {-1};
                glGetProgramResourceiv(this.cmdgen, GL_SHADER_STORAGE_BLOCK, block, new int[]{GL_BUFFER_BINDING}, null, binding);
                evidence.append(" SSBO").append(i + 1).append('=').append(id).append('/').append(size)
                        .append(" programBinding=").append(binding[0]);
                assertEquals(expected[i], id, blocks[i]);
                assertEquals(sizes[i], size, blocks[i]);
                assertEquals(i + 1, binding[0], blocks[i]);
            }
            int sceneBlock = glGetUniformBlockIndex(this.cmdgen, "SceneUniform");
            assertNotEquals(GL_INVALID_INDEX, sceneBlock);
            evidence.append(" SceneUniformSize=").append(glGetActiveUniformBlocki(this.cmdgen, sceneBlock, GL_UNIFORM_BLOCK_DATA_SIZE))
                    .append(" SceneUniformBinding=").append(glGetActiveUniformBlocki(this.cmdgen, sceneBlock, GL_UNIFORM_BLOCK_BINDING));
            for (String name : new String[]{"frameId", "baseSectionPos", "cameraSubPos"}) {
                int member = glGetProgramResourceIndex(this.cmdgen, GL_UNIFORM, name);
                if (member == GL_INVALID_INDEX) member = glGetProgramResourceIndex(this.cmdgen, GL_UNIFORM, "SceneUniform." + name);
                evidence.append(' ').append(name).append('=');
                if (member == GL_INVALID_INDEX) {
                    evidence.append("inactive");
                } else {
                    int[] description = new int[3];
                    glGetProgramResourceiv(this.cmdgen, GL_UNIFORM, member,
                            new int[]{GL_OFFSET, GL_BLOCK_INDEX, GL_TYPE}, null, description);
                    evidence.append(Arrays.toString(description));
                }
            }
            return evidence.toString();
        }

        void sortAndBuild() {
            this.prefixAndSnapshot();
            this.buildTranslucent();
        }

        void prefixAndSnapshot() {
            range(0, this.distance, (this.prefixSnapshot + BUCKETS) * 4L);
            glUseProgram(this.prefix);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchCompute(1, 1, 1);
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
            glCopyNamedBufferSubData(this.distance, this.distance, 0, this.prefixSnapshot * 4L, BUCKETS * 4L);
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
            int[] values = read(this.distance, this.prefixSnapshot + BUCKETS + 4);
            for (int i = 0; i < BUCKETS; i++) assertEquals(values[i], values[this.prefixSnapshot + i]);
        }

        void buildTranslucent() {
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform);
            range(1, this.commands, this.commandSlots * 20L);
            range(2, this.counts, 1024);
            range(3, this.metadata, this.renderCapacity * 32L);
            range(4, this.lookup, (this.renderCapacity + 1) * 4L);
            range(5, this.distance, (this.prefixSnapshot + BUCKETS) * 4L);
            glUseProgram(this.builder);
            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, this.indirectSnapshot);
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchComputeIndirect(0);
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
            assertEquals(GL_NO_ERROR, glGetError());
        }

        void assertCounts(int opaque, int translucent, int temporal) {
            int[] values = this.readCounts();
            String evidence = " counts=" + Arrays.toString(values);
            if (values[3] != opaque || values[4] != translucent || values[5] != temporal) {
                evidence += " uniform=" + Arrays.toString(read(this.uniform, 24))
                        + " visibility=" + Arrays.toString(read(this.visibility, this.renderCapacity))
                        + " lookup=" + Arrays.toString(read(this.lookup, this.renderCapacity + 1))
                        + " metadata0=" + Arrays.toString(read(this.metadata, 8))
                        + " UBO0=" + glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, 0) + "/expected=" + this.uniform
                        + this.generationBindingEvidence;
            }
            assertEquals(opaque, values[3], "opaque admitted count" + evidence);
            assertEquals(translucent, values[4], "translucent admitted count" + evidence);
            assertEquals(temporal, values[5], "temporal admitted count" + evidence);
        }

        void assertOpaqueCommands(int opaque, int temporal, Shape shape) {
            int[] data = read(this.commands, (this.commandSlots + 1) * 5);
            int perSection = shape == Shape.SEVEN_OPAQUE ? 7 : 1;
            for (int pass = 0; pass < 2; pass++) {
                int offset = pass == 0 ? 0 : this.temporalOffset;
                int count = pass == 0 ? opaque : temporal;
                for (int i = 0; i < count; i++) {
                    int address = (offset + i) * 5;
                    int drawId = data[address + 4];
                    assertTrue(drawId >= 0 && drawId < this.currentCount);
                    assertEquals(6, data[address]);
                    assertEquals(1, data[address + 1]);
                    assertEquals(0, data[address + 2]);
                    assertEquals((drawId * perSection + i % perSection) * 4, data[address + 3]);
                }
            }
        }

        void assertHistogram(int expected, int onlyBucket) {
            int[] data = read(this.distance, this.prefixSnapshot + BUCKETS + 4);
            assertEquals(expected, Arrays.stream(data, 0, BUCKETS).sum());
            if (onlyBucket >= 0) assertEquals(expected, data[onlyBucket]);
            boolean[] admitted = new boolean[this.renderCapacity];
            for (int i = 0; i < expected; i++) {
                int drawId = data[BUCKETS + i];
                assertTrue(drawId >= 0 && drawId < this.currentCount);
                assertFalse(admitted[drawId]);
                admitted[drawId] = true;
            }
            for (int i = expected; i < this.translucentCapacity; i++) assertEquals(GUARD, data[BUCKETS + i]);
        }

        void assertTranslucentCommands(int expected) {
            this.assertCounts(0, expected, 0);
            int[] data = read(this.commands, (this.commandSlots + 1) * 5);
            int[] dist = read(this.distance, this.prefixSnapshot + BUCKETS + 4);
            boolean[] found = new boolean[this.renderCapacity];
            for (int i = 0; i < expected; i++) {
                int address = (this.translucentOffset + i) * 5;
                int drawId = data[address + 4];
                assertTrue(drawId >= 0 && drawId < this.currentCount, "unwritten or invalid draw command");
                assertFalse(found[drawId], "duplicate translucent command");
                found[drawId] = true;
                assertEquals(6, data[address]);
                assertEquals(1, data[address + 1]);
                assertEquals(0, data[address + 2]);
                assertEquals(drawId * 4, data[address + 3]);
                int bucket = BUCKETS - 1 - Math.min(Math.abs(this.positionX[drawId]), BUCKETS - 1);
                int start = dist[this.prefixSnapshot + bucket];
                int end = bucket == BUCKETS - 1 ? expected : dist[this.prefixSnapshot + bucket + 1];
                assertTrue(i >= start && i < end, "command escaped its immutable distance range");
            }
        }

        void assertUnwrittenCommands(int opaque, int translucent, int temporal) {
            int[] data = read(this.commands, (this.commandSlots + 1) * 5);
            for (int i = opaque; i < this.opaqueCapacity; i++) assertGuard(data, i * 5, (i + 1) * 5);
            for (int i = translucent; i < this.translucentCapacity; i++) assertGuard(data, (this.translucentOffset + i) * 5, (this.translucentOffset + i + 1) * 5);
            for (int i = temporal; i < this.temporalCapacity; i++) assertGuard(data, (this.temporalOffset + i) * 5, (this.temporalOffset + i + 1) * 5);
        }

        void assertGuards() {
            int[] cmd = read(this.commands, (this.commandSlots + 1) * 5);
            assertGuard(cmd, this.opaqueCapacity * 5, this.translucentOffset * 5);
            assertGuard(cmd, (this.translucentOffset + this.translucentCapacity) * 5, this.temporalOffset * 5);
            assertGuard(cmd, this.commandSlots * 5, cmd.length);
            int[] dist = read(this.distance, this.prefixSnapshot + BUCKETS + 4);
            assertEquals(GUARD, dist[BUCKETS + this.translucentCapacity]);
            assertGuard(dist, this.prefixSnapshot + BUCKETS, dist.length);
            int[] counter = read(this.counts, 256 + 4);
            assertGuard(counter, 17, counter.length);
            int[] dispatch = read(this.indirectSnapshot, 7);
            assertArrayEquals(Arrays.copyOf(counter, 3), Arrays.copyOf(dispatch, 3));
            assertGuard(dispatch, 3, dispatch.length);
            for (int[] pair : new int[][]{{this.metadata, this.renderCapacity * 8}, {this.visibility, this.renderCapacity},
                    {this.lookup, this.renderCapacity + 1}, {this.positions, this.renderCapacity * 2}}) {
                int[] words = read(pair[0], pair[1] + 4);
                assertGuard(words, pair[1], words.length);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }

        void verifyInvalidBuilderAndRecovery() {
            this.resetAndGenerate(2, Shape.TRANSLUCENT, -1);
            this.prefixAndSnapshot();
            // Bucket 1023 has the last admitted command. Its end is the total count.
            glNamedBufferSubData(this.distance, (BUCKETS - 1) * 4L, new int[]{2});
            this.buildTranslucent();
            assertEquals(0, this.readCounts()[4]);
            assertEquals(16, this.readCounts()[11] & 16);
            this.assertGuards();
            this.generateNextFrame();
            this.sortAndBuild();
            this.assertTranslucentCommands(2);
            this.assertGuards();
            assertEquals(16, this.readCounts()[11] & 16, "overflow evidence survives recovery");

            this.resetAndGenerate(2, Shape.TRANSLUCENT, 0);
            this.prefixAndSnapshot();
            glNamedBufferSubData(this.distance, BUCKETS * 4L, new int[]{this.renderCapacity});
            this.buildTranslucent();
            assertEquals(0, this.readCounts()[4], "invalid accepted-list item cannot produce an unwritten draw");
            this.assertGuards();
            this.generateNextFrame();
            this.sortAndBuild();
            this.assertTranslucentCommands(2);
        }

        void verifyInvalidInputAndRenderListClamp() {
            this.resetAndGenerate(this.renderCapacity + 1, Shape.TRANSLUCENT, 0);
            assertEquals(8, this.readCounts()[11] & 8);
            assertEquals(1, this.readCounts()[15]);
            this.assertGuards();
            this.resetAndGenerate(1, Shape.ONE_OPAQUE, 0);
            glNamedBufferSubData(this.lookup, 4, new int[]{-1});
            this.generateNextFrame();
            this.assertCounts(0, 0, 0);
            assertEquals(8, this.readCounts()[11] & 8);
            this.assertGuards();
        }

        void verifyActualRasterVisibilityOwner() {
            this.resetAndGenerate(1, Shape.ONE_OPAQUE, 0);
            RenderProperties properties = new RenderProperties(false, false, false);
            int program = graphicsProgram(properties.injectDefines(this.source("cull/raster.vert")),
                    ShaderLoader.parse("voxy:lod/gl46/cull/raster.frag"));
            int vao = glCreateVertexArrays();
            int texture = glCreateTextures(GL_TEXTURE_2D);
            int framebuffer = glCreateFramebuffers();
            try {
                glTextureStorage2D(texture, 1, GL_RGBA8, 8, 8);
                glNamedFramebufferTexture(framebuffer, GL_COLOR_ATTACHMENT0, texture, 0);
                assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckNamedFramebufferStatus(framebuffer, GL_FRAMEBUFFER));
                glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
                glViewport(0, 0, 8, 8);
                glDisable(GL_DEPTH_TEST);
                glDisable(GL_STENCIL_TEST);
                glDisable(GL_SCISSOR_TEST);
                glDisable(GL_RASTERIZER_DISCARD);
                glBindVertexArray(vao);
                glUseProgram(program);
                int[] scene = new int[256];
                scene[0] = scene[5] = scene[10] = Float.floatToRawIntBits(0.25f);
                scene[12] = scene[13] = Float.floatToRawIntBits(0.25f);
                scene[15] = Float.floatToRawIntBits(1f);
                scene[19] = FRAME_ID;
                glNamedBufferSubData(this.uniform, 0, scene);
                glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform);
                range(1, this.metadata, this.renderCapacity * 32L);
                range(2, this.visibility, this.renderCapacity * 4L);
                range(3, this.lookup, (this.renderCapacity + 1) * 4L);
                for (int invalid : new int[]{-1, this.renderCapacity}) {
                    glNamedBufferSubData(this.lookup, 4, new int[]{invalid});
                    glNamedBufferSubData(this.visibility, 0, new int[]{0});
                    glDrawArraysInstanced(GL_POINTS, 0, 1, 2); // The extra instance is also outside published count.
                    glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
                    assertEquals(0, read(this.visibility, 1)[0]);
                    this.assertGuards();
                }
                glNamedBufferSubData(this.lookup, 4, new int[]{0});
                glDrawArraysInstanced(GL_POINTS, 0, 1, 1);
                glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
                assertEquals(FRAME_ID, read(this.visibility, 1)[0], "legal raster sample must still publish visibility");
                this.assertGuards();
            } finally {
                glUseProgram(0);
                glBindVertexArray(0);
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteFramebuffers(framebuffer);
                glDeleteTextures(texture);
                glDeleteVertexArrays(vao);
                glDeleteProgram(program);
            }
        }

        int[] readCounts() {
            return read(this.counts, 17);
        }

        @Override
        public void close() {
            glUseProgram(0);
            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
            for (int program : new int[]{this.prep, this.cmdgen, this.prefix, this.builder}) glDeleteProgram(program);
            for (int buffer : new int[]{this.uniform, this.counts, this.commands, this.metadata,
                    this.visibility, this.lookup, this.positions, this.distance, this.indirectSnapshot}) glDeleteBuffers(buffer);
        }
    }

    private static void range(int binding, int buffer, long bytes) {
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, binding, buffer, 0, bytes);
    }

    private static int[] filled(int size) {
        int[] words = new int[size];
        Arrays.fill(words, GUARD);
        return words;
    }

    private static void upload(int buffer, int[] data) {
        // Match formal GlBuffer's one-allocation lifetime. Test updates use
        // SubData rather than orphaning already bound mutable stores between cases.
        int size = glGetNamedBufferParameteri(buffer, GL_BUFFER_SIZE);
        if (size == 0) {
            glNamedBufferStorage(buffer, data, GL_DYNAMIC_STORAGE_BIT);
        } else {
            assertEquals(data.length * Integer.BYTES, size);
            glNamedBufferSubData(buffer, 0, data);
        }
    }

    private static int[] read(int buffer, int words) {
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        int[] data = new int[words];
        glGetNamedBufferSubData(buffer, 0, data);
        return data;
    }

    private static void assertGuard(int[] words, int from, int end) {
        for (int i = from; i < end; i++) assertEquals(GUARD, words[i], "GPU guard word " + i);
    }

    private Round10MdicGpuCases() {
    }
}
