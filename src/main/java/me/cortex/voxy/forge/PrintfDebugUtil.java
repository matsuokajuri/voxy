package me.cortex.voxy.forge;

import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferSubData;

/** Forge GL-owner adaptation of the original PrintfDebugUtil/PrintfInjector pair. */
final class PrintfDebugUtil {
    static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("voxy.enableShaderDebugPrintf", "false"));

    private static final int BUFFER_WORDS = 50_000;
    private static final int BINDING_INDEX = 20;
    private static final HashMap<String, Integer> FORMAT_TO_ID = new HashMap<>();
    private static final HashMap<Integer, String> ID_TO_FORMAT = new HashMap<>();
    private static final List<String> CURRENT_QUEUE = new ArrayList<>();
    private static final List<String> DISPLAY_QUEUE = new ArrayList<>();
    private static GlBuffer outputBuffer;

    private PrintfDebugUtil() {
    }

    static String processShader(String source) {
        if (!ENABLED) {
            return source.replace("printf", "//printf");
        }
        ensureBuffer();
        return transformInject(source);
    }

    static void bind() {
        if (ENABLED) {
            ensureBuffer();
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, BINDING_INDEX, outputBuffer.id);
        }
    }

    static void tick() {
        if (!ENABLED || outputBuffer == null) {
            return;
        }
        DISPLAY_QUEUE.clear();
        DISPLAY_QUEUE.addAll(CURRENT_QUEUE);
        CURRENT_QUEUE.clear();
        DownloadStream.instance().download(
                outputBuffer.id,
                outputBuffer.size(),
                0L,
                outputBuffer.size(),
                (pointer, size) -> processResult(pointer, size));
        nglClearNamedBufferSubData(
                outputBuffer.id,
                GL_R32UI,
                0L,
                Integer.BYTES,
                GL_RED_INTEGER,
                GL_UNSIGNED_INT,
                0L);
    }

    static void addToOut(List<String> output) {
        if (!ENABLED) {
            return;
        }
        output.add("Printf Queue:");
        output.addAll(DISPLAY_QUEUE);
    }

    static void free() {
        if (outputBuffer != null) {
            outputBuffer.free();
            outputBuffer = null;
        }
        CURRENT_QUEUE.clear();
        DISPLAY_QUEUE.clear();
        FORMAT_TO_ID.clear();
        ID_TO_FORMAT.clear();
    }

    private static void ensureBuffer() {
        if (outputBuffer == null) {
            outputBuffer = new GlBuffer(BUFFER_WORDS * (long) Integer.BYTES + Integer.BYTES);
        }
    }

    private static int findNextCall(String source, int after) {
        while (true) {
            int index = source.indexOf("printf", after);
            if (index == -1) {
                return -1;
            }
            boolean lineComment = false;
            boolean blockComment = false;
            for (int i = 0; i < index; i++) {
                if (i + 1 < source.length() && source.charAt(i) == '/' && source.charAt(i + 1) == '/') {
                    lineComment = true;
                }
                if (source.charAt(i) == '\n') {
                    lineComment = false;
                }
                if (!lineComment && i + 1 < source.length()
                        && source.charAt(i) == '/' && source.charAt(i + 1) == '*') {
                    blockComment = true;
                }
                if (!lineComment && i + 1 < source.length()
                        && source.charAt(i) == '*' && source.charAt(i + 1) == '/') {
                    blockComment = false;
                }
            }
            if (lineComment || blockComment) {
                after = index + 1;
                continue;
            }
            return index;
        }
    }

    private static void parsePrintfTypes(String format, List<Character> types) {
        for (int i = 0; i < format.length() - 1; i++) {
            if (format.charAt(i) == '%' && (i == 0 || format.charAt(i - 1) != '%')) {
                types.add(format.charAt(i + 1));
            }
        }
    }

    private static String transformInject(String source) {
        String original = source;
        if (!source.contains("printf")) {
            return source;
        }
        int injectionPoint = Math.max(source.lastIndexOf("#version"), source.lastIndexOf("#extension"));
        injectionPoint = source.indexOf('\n', injectionPoint);
        if (injectionPoint < 0) {
            throw new IllegalStateException("Shader printf injection requires a version or extension line");
        }

        StringBuilder result = new StringBuilder();
        result.append(source, 0, injectionPoint + 1);
        result.append("layout(binding = ")
                .append(BINDING_INDEX)
                .append(", std430) restrict buffer PrintfOutputStream {\n")
                .append("    uint index;\n")
                .append("    uint stream[];\n")
                .append("} printfOutputStruct;\n");
        source = source.substring(injectionPoint + 1);

        int position = 0;
        boolean usedPrintf = false;
        List<String> arguments = new ArrayList<>();
        List<Character> types = new ArrayList<>();
        while (true) {
            int nextCall = findNextCall(source, position);
            if (nextCall == -1) {
                result.append(source, position, source.length());
                break;
            }
            result.append(source, position, nextCall);
            String call = source.substring(nextCall);
            call = call.substring(call.indexOf('"') + 1);
            call = call.substring(0, call.indexOf(';'));
            String format = call.substring(0, call.indexOf('"'));
            String rawArguments = call.substring(call.indexOf('"'));

            int previous = 0;
            int braces = 0;
            arguments.clear();
            for (int i = 0; i < rawArguments.length(); i++) {
                char value = rawArguments.charAt(i);
                if (value == '(' || value == '[') {
                    braces++;
                }
                if (value == ')' || value == ']') {
                    braces--;
                }
                if ((value == ',' && braces == 0) || braces == -1) {
                    if (previous == 0) {
                        previous = i;
                        continue;
                    }
                    arguments.add(rawArguments.substring(previous + 1, i));
                    previous = i;
                    if (braces == -1) {
                        break;
                    }
                }
            }

            types.clear();
            parsePrintfTypes(format, types);
            if (types.size() != arguments.size()) {
                throw new IllegalStateException("Printf argument count does not match format count");
            }
            int formatId = FORMAT_TO_ID.computeIfAbsent(format, value -> {
                int id = FORMAT_TO_ID.size();
                ID_TO_FORMAT.put(id, value);
                return id;
            });
            result.append("{uint printfWriteIndex = atomicAdd(printfOutputStruct.index,")
                    .append(types.size() + 1)
                    .append(");printfOutputStruct.stream[printfWriteIndex]=")
                    .append(formatId)
                    .append(';');
            for (int i = 0; i < types.size(); i++) {
                result.append("printfOutputStruct.stream[printfWriteIndex+")
                        .append(i + 1)
                        .append("]=");
                if (types.get(i) == 'd') {
                    result.append("uint(").append(arguments.get(i)).append(')');
                } else if (types.get(i) == 'f') {
                    result.append("floatBitsToUint(").append(arguments.get(i)).append(')');
                } else {
                    throw new IllegalStateException("Unknown printf type " + types.get(i));
                }
                result.append(';');
            }
            result.append('}');
            usedPrintf = true;
            position = source.indexOf(';', nextCall) + 1;
        }
        return usedPrintf ? result.toString() : original;
    }

    private static void processResult(long pointer, long ignoredSize) {
        int total = MemoryUtil.memGetInt(pointer);
        pointer += Integer.BYTES;
        if (total == 0) {
            return;
        }
        int count = 0;
        List<Character> types = new ArrayList<>();
        while (count < total) {
            int id = MemoryUtil.memGetInt(pointer);
            pointer += Integer.BYTES;
            count++;
            String format = ID_TO_FORMAT.get(id);
            if (format == null) {
                throw new IllegalStateException("Unknown shader printf format id " + id);
            }
            types.clear();
            parsePrintfTypes(format, types);
            Object[] arguments = new Object[types.size()];
            for (int i = 0; i < types.size(); i++) {
                int raw = MemoryUtil.memGetInt(pointer);
                pointer += Integer.BYTES;
                count++;
                if (types.get(i) == 'f') {
                    arguments[i] = Float.intBitsToFloat(raw);
                } else {
                    arguments[i] = raw;
                }
            }
            String line = String.format(format, arguments);
            if (line.startsWith("LOG")) {
                VoxyForge.LOGGER.info(line);
            }
            CURRENT_QUEUE.add(line);
        }
    }
}
