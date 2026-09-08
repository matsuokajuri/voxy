package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.cortex.voxy.forge.Round11NodeTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL46C.*;

/**
 * Auxiliary GPU contract proof, never client readiness: actual NodeManager -> Async publication
 * -> production memcpy/scatter -> HOC request kernel / complete Cleaner -> DownloadStream
 * -> actual Async worker -> NodeManager. Only the external geometry producer is recorded.
 */
final class Round11NodeGpuCases {
    static void runAll() throws Exception {
        // Reuse the CPU fixture's real AsyncNodeManager constructor and external producer seam.
        // The renderer's memory policy is not under test: these bounded buffers supply actual
        // GL storage to its unchanged BasicSectionGeometryData descriptor.
        Constructor<?> constructor = Class.forName("me.cortex.voxy.forge.Round11AsyncLifecycleTest$Fixture")
                .getDeclaredConstructor();
        constructor.setAccessible(true);
        AutoCloseable fixture = (AutoCloseable) constructor.newInstance();
        try (fixture) {
            runWithFixture(fixture);
        }
    }

    private static void runWithFixture(AutoCloseable fixture) throws Exception {
        AsyncNodeManager async = field(fixture, "manager");
        NodeManager nodes = field(async, "nodeManager");
        NodeStore store = field(nodes, "nodeData");
        SectionUpdateRouter router = field(async, "router");
        BasicSectionGeometryData data = field(async, "geometryData");
        // Replace only the fixture's deliberately unstarted external producer callbacks.
        // The router itself, its lifetime tokens, and all hierarchy/publication owners remain real.
        set(router, "initialRenderMeshGen", (SectionUpdateRouter.MeshUpdate) (position, token) -> { });
        set(router, "renderMeshGen", (SectionUpdateRouter.MeshUpdate) (position, token) -> { });
        GlBuffer metadata = new GlBuffer(128L * BasicSectionGeometryData.SECTION_METADATA_SIZE, false).fill(0);
        GlBuffer geometry = new GlBuffer(data.getGeometryCapacityBytes(), false);
        set(data, "sectionMetadataBufferId", metadata.id);
        set(data, "geometryBufferId", geometry.id);
        NodeCleaner cleaner = new NodeCleaner(async);
        cleaner.buildOnRenderThread();
        HierarchicalOcclusionTraverser hoc = new HierarchicalOcclusionTraverser(async, cleaner, null);
        String source = ForgeOriginalVoxyShaderCompiler.prepareSource(HierarchicalOcclusionTraverser.buildTraversalSource(
                new RenderProperties(false, false, false), null));
        // Keep the production addRequest implementation intact; only select which real node
        // reaches that kernel, independent of frustum/camera policies covered by client QA.
        String requestSource = source.replaceFirst("void main\\(\\)", "void originalTraversalMain()") + """

                layout(location=20) uniform uint selectedNode;
                void main() {
                    if (gl_GlobalInvocationID.x != 0u) return;
                    UnpackedNode node;
                    unpackNode(node, selectedNode);
                    addRequest(node);
                }
                """;
        int requestProgram = Round10GpuExecutionTest.computeProgram(requestSource);
        boolean ownedDownloadStream = !DownloadStream.isReady();
        try {
            long root = pos(2, 0, 0, 0);
            insert(nodes, root, 1, false);
            publish(async, hoc, cleaner);
            int rootId = nodes.diagnosticSnapshot().positions().get(root).id();
            assertGpuRows(nodes, hoc.getNodeBuffer());
            int[] request = dispatchRequest(hoc, source, requestProgram, rootId);
            assertEquals(1, request[0]);
            invoke(hoc, "downloadResetRequestQueue");
            assertFalse(store.isNodeRequestInFlight(rootId), "readback callback is deliberately delayed");
            DownloadStream.instance().flushWaitClear();
            invoke(async, "workerRun");
            assertTrue(store.isNodeRequestInFlight(rootId));
            replayRequest(hoc, request);
            invoke(async, "workerRun");
            assertEquals(1, nodes.diagnosticSnapshot().activeChildRequestCount());
            result(nodes, child(root, 0), 1, false);
            publish(async, hoc, cleaner);
            assertGpuRows(nodes, hoc.getNodeBuffer());

            long middle = child(root, 0);
            int middleId = nodes.diagnosticSnapshot().positions().get(middle).id();
            dispatchRequest(hoc, source, requestProgram, middleId);
            invoke(hoc, "downloadResetRequestQueue");
            DownloadStream.instance().flushWaitClear();
            invoke(async, "workerRun");
            long bottom = child(middle, 0);
            result(nodes, bottom, 0, false);
            publish(async, hoc, cleaner);
            assertEquals(0, gpuRow(hoc.getNodeBuffer(), rootId)[2] & (1 << 29),
                    "split must publish containing parent's AllChildrenAreLeaf=false");

            // Run the complete production sorter/transformer and its actual asynchronous copy.
            int bottomId = nodes.diagnosticSnapshot().positions().get(bottom).id();
            int[] ages = new int[128];
            Arrays.fill(ages, -1);
            ages[bottomId] = 0;
            uploadInts(cleaner.visibilityBufferId(), ages);
            cleaner.tick(hoc.getNodeBuffer());
            assertTrue(nodes.diagnosticSnapshot().positions().containsKey(bottom));
            GlBuffer output = field(cleaner, "outputBuffer");
            int[] removal = new int[(int) GpuBufferLayout.CLEANER_POSITION_BYTES / 4];
            glGetNamedBufferSubData(output.id, GpuBufferLayout.CLEANER_ID_BYTES, removal);
            DownloadStream.instance().flushWaitClear();
            invoke(async, "workerRun");
            assertFalse(nodes.diagnosticSnapshot().positions().containsKey(bottom));
            assertTrue(store.getAllChildrenAreLeaf(rootId));
            MemoryBuffer replayRemoval = ints(removal);
            async.submitRemoveBatch(replayRemoval);
            invoke(async, "workerRun");
            assertTrue(replayRemoval.isFreed());
            publish(async, hoc, cleaner);
            assertNotEquals(0, gpuRow(hoc.getNodeBuffer(), rootId)[2] & (1 << 29));
            assertArrayEquals(new int[]{-1, -1, -1, -1}, gpuRow(hoc.getNodeBuffer(), bottomId),
                    "retired rows beyond the new live prefix still need GPU tombstones");
            assertGpuRows(nodes, hoc.getNodeBuffer());

            // The new CPU-only pending flag must leave NULL and the real child pointer intact.
            nodes.removeNodeGeometry(root);
            nodes.processChildChange(root, (byte) 0);
            publish(async, hoc, cleaner);
            assertTrue(store.isEmptyCollapsePending(rootId));
            assertEquals(0xFFFFFF, gpuRow(hoc.getNodeBuffer(), rootId)[2] & 0xFFFFFF);
            assertNotEquals(0xFFFFFF, gpuRow(hoc.getNodeBuffer(), rootId)[3] & 0xFFFFFF);
            assertGpuRows(nodes, hoc.getNodeBuffer());
            result(nodes, root, 0, true);
            publish(async, hoc, cleaner);
            assertEquals(0xFFFFFE, gpuRow(hoc.getNodeBuffer(), rootId)[2] & 0xFFFFFF);
            nodes.removeTopLevelNode(root);
            publish(async, hoc, cleaner);

            // Reach the physical boundary through actual allocation, then reuse that same ID.
            long last = 0;
            for (int index = 0; index < 128; index++) {
                last = pos(WorldEngine.MAX_LOD_LAYER, index, 0, 8);
                insert(nodes, last, 0, true);
            }
            publish(async, hoc, cleaner);
            assertEquals(127, nodes.getCurrentMaxNodeId());
            assertGpuRows(nodes, hoc.getNodeBuffer());
            int[] delayed = dispatchRequest(hoc, source, requestProgram, 127);
            invoke(hoc, "downloadResetRequestQueue");
            nodes.removeTopLevelNode(last);
            long replacement = pos(WorldEngine.MAX_LOD_LAYER, 129, 0, 8);
            insert(nodes, replacement, 0, true);
            assertEquals(127, nodes.diagnosticSnapshot().positions().get(replacement).id());
            publish(async, hoc, cleaner);
            DownloadStream.instance().flushWaitClear();
            invoke(async, "workerRun");
            replayRequest(hoc, delayed);
            invoke(async, "workerRun");
            assertFalse(store.isNodeRequestInFlight(127), "old position must not alias the reused GPU node ID");
            dispatchRequest(hoc, source, requestProgram, 127);
            invoke(hoc, "downloadResetRequestQueue");
            DownloadStream.instance().flushWaitClear();
            invoke(async, "workerRun");
            assertTrue(store.isNodeRequestInFlight(127), "current empty top-level request parks normally");
            publish(async, hoc, cleaner);
            assertGpuRows(nodes, hoc.getNodeBuffer());
            nodes.verifyIntegrity();
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glUseProgram(0);
            closeAll(
                    () -> { if (DownloadStream.isReady()) DownloadStream.instance().flushWaitClear(); },
                    hoc::free, cleaner::free, () -> glDeleteProgram(requestProgram),
                    metadata::free, geometry::free,
                    () -> { set(data, "sectionMetadataBufferId", 0); set(data, "geometryBufferId", 0); },
                    () -> { if (ownedDownloadStream && DownloadStream.isReady()) DownloadStream.instance().free(); });
        }
    }

    private static void insert(NodeManager nodes, long position, int mask, boolean empty) {
        assertTrue(nodes.insertTopLevelNode(position));
        result(nodes, position, mask, empty);
    }

    private static void result(NodeManager nodes, long position, int mask, boolean empty) {
        BuiltSection section = empty ? BuiltSection.emptyWithChildren(position, (byte) mask)
                : new BuiltSection(position, (byte) mask, 0, new MemoryBuffer(8).zero(), new int[8], null);
        assertTrue(nodes.processGeometryResult(section));
        nodes.verifyIntegrity();
    }

    private static void publish(AsyncNodeManager async, HierarchicalOcclusionTraverser hoc, NodeCleaner cleaner) throws Exception {
        invoke(async, "publishSyncResults");
        async.tick(hoc.getNodeBuffer(), cleaner);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
    }

    private static int[] dispatchRequest(HierarchicalOcclusionTraverser hoc, String source, int program, int nodeId) {
        GlBuffer request = field(hoc, "requestBuffer");
        GlBuffer uniform = field(hoc, "uniformBuffer");
        int[] scene = new int[256];
        scene[48] = GpuBufferLayout.HOC_RENDER_LIST_CAPACITY;
        scene[49] = 1;
        scene[50] = 50;
        uploadInts(uniform.id, scene);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, define(source, "NODE_DATA_BINDING"), hoc.getNodeBuffer().id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, define(source, "REQUEST_QUEUE_BINDING"), request.id);
        glBindBufferBase(GL_UNIFORM_BUFFER, define(source, "SCENE_UNIFORM_BINDING"), uniform.id);
        glUseProgram(program);
        glUniform1ui(20, nodeId);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        int[] values = new int[(int) request.size() / 4];
        glGetNamedBufferSubData(request.id, 0L, values);
        assertEquals(1, values[0]);
        assertEquals(0, values[1]);
        return values;
    }

    private static void replayRequest(HierarchicalOcclusionTraverser hoc, int[] request) throws Exception {
        MemoryBuffer buffer = ints(request);
        try {
            Method method = hoc.getClass().getDeclaredMethod("forwardDownloadResult", long.class, long.class);
            method.setAccessible(true);
            method.invoke(hoc, buffer.address, buffer.size);
        } finally {
            buffer.free();
        }
    }

    private static MemoryBuffer ints(int[] values) {
        MemoryBuffer buffer = new MemoryBuffer(values.length * 4L);
        for (int index = 0; index < values.length; index++) MemoryUtil.memPutInt(buffer.address + index * 4L, values[index]);
        return buffer;
    }

    private static void uploadInts(int buffer, int[] values) {
        // Production GlBuffer storage is immutable without DYNAMIC_STORAGE_BIT. Use the
        // real upload-copy owner, not glNamedBufferSubData on its immutable destination.
        long address = UploadStream.instance().upload(buffer, 0L, values.length * 4L);
        for (int index = 0; index < values.length; index++) MemoryUtil.memPutInt(address + index * 4L, values[index]);
        UploadStream.instance().commit();
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_UNIFORM_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        assertEquals(GL_NO_ERROR, glGetError(), "test input upload follows immutable production storage");
    }

    private static void assertGpuRows(NodeManager nodes, GlBuffer gpu) {
        MemoryBuffer scratch = new MemoryBuffer(16);
        try {
            for (int id = 0; id <= nodes.getCurrentMaxNodeId(); id++) {
                nodes.writeNode(id, scratch.address);
                int[] expected = new int[4];
                for (int index = 0; index < 4; index++) expected[index] = MemoryUtil.memGetInt(scratch.address + index * 4L);
                assertArrayEquals(expected, gpuRow(gpu, id), "16-byte production scatter row " + id);
            }
        } finally {
            scratch.free();
        }
    }

    private static int[] gpuRow(GlBuffer gpu, int id) {
        int[] row = new int[4];
        glGetNamedBufferSubData(gpu.id, id * 16L, row);
        return row;
    }

    private static int define(String source, String name) {
        Matcher matcher = Pattern.compile("(?m)^#define\\s+" + Pattern.quote(name) + "\\s+(\\d+)\\s*$").matcher(source);
        assertTrue(matcher.find());
        return Integer.parseInt(matcher.group(1));
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void invoke(Object owner, String name) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        try {
            method.invoke(owner);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw (Error) exception.getCause();
        }
    }

    private static void closeAll(AutoCloseable... resources) throws Exception {
        Throwable failure = null;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Throwable current) {
                if (failure == null) failure = current;
                else failure.addSuppressed(current);
            }
        }
        if (failure instanceof Exception exception) throw exception;
        if (failure != null) throw (Error) failure;
    }
}
