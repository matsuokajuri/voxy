package me.cortex.voxy.forge;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCleanerShaderSourceTest {
    private static final Pattern ACTIVE_PRINTF = Pattern.compile("(?m)^\\s*printf\\(");
    private static String previousShaderPrintfSetting;

    @BeforeAll
    static void disableShaderPrintfDebugging() {
        previousShaderPrintfSetting = System.getProperty("voxy.enableShaderDebugPrintf");
        System.setProperty("voxy.enableShaderDebugPrintf", "false");
    }

    @AfterAll
    static void restoreShaderPrintfDebuggingSetting() {
        if (previousShaderPrintfSetting == null) {
            System.clearProperty("voxy.enableShaderDebugPrintf");
        } else {
            System.setProperty("voxy.enableShaderDebugPrintf", previousShaderPrintfSetting);
        }
    }

    @Test
    void sorterSourceUsesOriginalPrintfProcessorBeforeDefines() {
        assertFalse(PrintfDebugUtil.ENABLED);

        String source = NodeCleaner.buildSorterSource();

        assertTrue(source.contains("#define WORK_SIZE 64"));
        assertTrue(source.contains("void debugDumpNode"));
        assertTrue(source.contains("//printf("));
        assertFalse(ACTIVE_PRINTF.matcher(source).find());
    }

    @Test
    @SuppressWarnings("unchecked")
    void printfReadbackKeepsOnlyTheLatestNonEmptyBatchLikeOriginalPreRun() throws Exception {
        var formatsField = PrintfDebugUtil.class.getDeclaredField("ID_TO_FORMAT");
        formatsField.setAccessible(true);
        Map<Integer, String> formats = (Map<Integer, String>) formatsField.get(null);
        var queueField = PrintfDebugUtil.class.getDeclaredField("CURRENT_QUEUE");
        queueField.setAccessible(true);
        List<String> queue = (List<String>) queueField.get(null);
        var processResult = PrintfDebugUtil.class.getDeclaredMethod("processResult", long.class, long.class);
        processResult.setAccessible(true);

        Map<Integer, String> previousFormats = new HashMap<>(formats);
        List<String> previousQueue = new ArrayList<>(queue);
        long pointer = MemoryUtil.nmemAlloc(3L * Integer.BYTES);
        try {
            formats.clear();
            formats.put(0, "batch=%d");
            queue.clear();

            writePrintfBatch(pointer, 1);
            processResult.invoke(null, pointer, 3L * Integer.BYTES);
            assertEquals(List.of("batch=1"), queue);

            writePrintfBatch(pointer, 2);
            processResult.invoke(null, pointer, 3L * Integer.BYTES);
            assertEquals(List.of("batch=2"), queue);

            MemoryUtil.memPutInt(pointer, 0);
            processResult.invoke(null, pointer, Integer.BYTES);
            assertEquals(List.of("batch=2"), queue, "An empty batch must not run the original preRun clear");
        } finally {
            MemoryUtil.nmemFree(pointer);
            formats.clear();
            formats.putAll(previousFormats);
            queue.clear();
            queue.addAll(previousQueue);
        }
    }

    private static void writePrintfBatch(long pointer, int value) {
        MemoryUtil.memPutInt(pointer, 2);
        MemoryUtil.memPutInt(pointer + Integer.BYTES, 0);
        MemoryUtil.memPutInt(pointer + 2L * Integer.BYTES, value);
    }
}
