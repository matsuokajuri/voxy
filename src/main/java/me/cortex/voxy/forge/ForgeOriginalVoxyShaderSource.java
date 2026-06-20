package me.cortex.voxy.forge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ForgeOriginalVoxyShaderSource {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<namespace>.*):(?<path>.*)>");

    private ForgeOriginalVoxyShaderSource() {
    }

    static String parse(String id) {
        return "#version 460 core\n" + String.join("\n", parseRoot(id));
    }

    private static List<String> parseRoot(String id) {
        List<String> out = new ArrayList<>();
        for (String line : lines(load(id))) {
            if (line.startsWith("#version")) {
                continue;
            }
            if (line.startsWith("#import")) {
                var match = IMPORT_PATTERN.matcher(line);
                if (!match.matches()) {
                    throw new IllegalArgumentException("Unknown shader import: " + line);
                }
                out.addAll(parseRoot(match.group("namespace") + ":" + match.group("path")));
            } else {
                out.add(line);
            }
        }
        return out;
    }

    private static String load(String id) {
        int split = id.indexOf(':');
        if (split <= 0 || split == id.length() - 1) {
            throw new IllegalArgumentException("Invalid shader id " + id);
        }
        String namespace = id.substring(0, split);
        String path = id.substring(split + 1);
        String resourcePath = "/assets/" + namespace + "/shaders/" + path;
        try (InputStream stream = ForgeOriginalVoxyShaderSource.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read shader resource " + resourcePath, e);
        }

        Path devPath = Path.of("src", "main", "resources", "assets", namespace, "shaders", path);
        if (Files.exists(devPath)) {
            try {
                return Files.readString(devPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read shader file " + devPath, e);
            }
        }
        throw new IllegalStateException("Shader not found: " + resourcePath);
    }

    private static List<String> lines(String source) {
        try {
            List<String> result = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new StringReader(source));
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to split shader source", e);
        }
    }
}
