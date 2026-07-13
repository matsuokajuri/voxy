package me.cortex.voxy.forge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCleanerShaderSourceTest {
    private static final Pattern ACTIVE_PRINTF = Pattern.compile("(?m)^\\s*printf\\(");

    @BeforeAll
    static void disableShaderPrintfDebugging() {
        System.setProperty("voxy.enableShaderDebugPrintf", "false");
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
}
