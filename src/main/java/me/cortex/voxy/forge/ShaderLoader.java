package me.cortex.voxy.forge;

import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ShaderLoader {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<namespace>.*):(?<path>.*)>");

    private ShaderLoader() {
    }

    static String parse(String id) {
        return "#version 460 core\n" + String.join("\n", parseRoot(new ResourceLocation(id)));
    }

    private static List<String> parseRoot(ResourceLocation id) {
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
                out.addAll(parseRoot(new ResourceLocation(match.group("namespace"), match.group("path"))));
            } else {
                out.add(line);
            }
        }
        return out;
    }

    private static String load(ResourceLocation id) {
        String resourcePath = "/assets/" + id.getNamespace() + "/shaders/" + id.getPath();
        try (InputStream stream = ShaderLoader.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read shader resource " + resourcePath, e);
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
