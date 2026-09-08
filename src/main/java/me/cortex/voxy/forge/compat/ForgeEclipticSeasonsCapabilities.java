package me.cortex.voxy.forge.compat;

import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Inspects the optional mod's bytecode without loading its config, client or legacy Voxy classes. */
public final class ForgeEclipticSeasonsCapabilities {
    private static final String ROOT = "com/teamtea/eclipticseasons/";
    private static final String CONFIG = ROOT + "compat/CompatModule$CommonConfig";
    private static final String ADAPTER = "me/cortex/voxy/forge/EclipticSeasonsIntegration.class";

    private ForgeEclipticSeasonsCapabilities() {}

    private static final class Holder {
        static final boolean HAS_VOXY_API = detect();
    }

    public static boolean hasVoxyIntegration() {
        return Holder.HAS_VOXY_API;
    }

    private static boolean detect() {
        LoadingModList mods = LoadingModList.get();
        if (mods == null) {
            throw new IllegalStateException("Ecliptic capabilities requested before Forge mod discovery");
        }
        var mod = mods.getModFileById("eclipticseasons");
        if (mod == null) {
            return false;
        }
        Map<String, ClassNode> classes = new HashMap<>();
        Function<String, ClassNode> readClass = name -> {
            if (!name.startsWith(ROOT)) {
                return null;
            }
            if (!classes.containsKey(name)) {
                var path = mod.getFile().findResource(name + ".class");
                ClassNode node = null;
                if (Files.isRegularFile(path)) {
                    try (InputStream input = Files.newInputStream(path)) {
                        node = read(input, true);
                    } catch (IOException failure) {
                        throw new IllegalStateException("Cannot inspect Ecliptic API " + name, failure);
                    }
                }
                classes.put(name, node);
            }
            return classes.get(name);
        };
        ClassNode config = readClass.apply(CONFIG);
        boolean declaresVoxyFields = config != null && config.fields.stream()
                .anyMatch(field -> field.name.startsWith("voxy"));
        ClassNode tool = readClass.apply(ROOT + "compat/voxy/VoxyTool");
        ClassNode handler = readClass.apply(ROOT + "compat/voxy/VoxyEsHandler");
        ClassNode clientTool = readClass.apply(ROOT + "compat/voxy/VoxyClientTool");
        if (!declaresVoxyFields && tool == null && handler == null && clientTool == null) {
            // 0.10-pre10-2 predates the entire Voxy extension, not just one renamed option.
            // Leave that mod's existing seasons/render hooks and the normal Voxy owners intact.
            LogManager.getLogger("Voxy").info(
                    "Ecliptic Seasons predates its Voxy extension API; retaining pre-integration behavior.");
            return false;
        }
        if (tool == null || handler == null || clientTool == null) {
            throw new IllegalStateException("Incomplete Ecliptic Seasons Voxy extension API");
        }

        // Validate every Ecliptic field/method that the actual adapter will link, not merely
        // voxyTest. Read our class as a resource so its optional references remain unresolved.
        try (InputStream input = ForgeEclipticSeasonsCapabilities.class.getResourceAsStream("/" + ADAPTER)) {
            if (input == null) {
                throw new IllegalStateException("Missing native Ecliptic adapter bytecode");
            }
            ClassNode adapter = read(input, false);
            for (var method : adapter.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.owner.startsWith(ROOT)) {
                        requireMember(readClass, field.owner, field.name, field.desc, true,
                                field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC);
                    } else if (instruction instanceof MethodInsnNode call && call.owner.startsWith(ROOT)) {
                        requireMember(readClass, call.owner, call.name, call.desc, false,
                                call.getOpcode() == Opcodes.INVOKESTATIC);
                    }
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect native Ecliptic adapter bytecode", failure);
        }
        LogManager.getLogger("Voxy").info("Ecliptic Seasons Voxy API matched; native Forge adapter available.");
        return true;
    }

    private static ClassNode read(InputStream input, boolean headersOnly) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
                | (headersOnly ? ClassReader.SKIP_CODE : 0));
        return node;
    }

    private static void requireMember(Function<String, ClassNode> readClass, String owner,
            String name, String descriptor, boolean field, boolean isStatic) {
        if (!hasMember(readClass, owner, name, descriptor, field, isStatic)) {
            // Partial/new-but-incompatible ABIs are errors, not permission to disable features.
            throw new IllegalStateException("Unsupported Ecliptic Seasons Voxy API: "
                    + owner + "." + name + descriptor);
        }
    }

    private static boolean hasMember(Function<String, ClassNode> readClass, String owner,
            String name, String descriptor, boolean field, boolean isStatic) {
        ClassNode node = readClass.apply(owner);
        if (node == null) {
            return false;
        }
        boolean declared = field
                ? node.fields.stream().anyMatch(member -> member.name.equals(name) && member.desc.equals(descriptor)
                        && ((member.access & Opcodes.ACC_STATIC) != 0) == isStatic)
                : node.methods.stream().anyMatch(member -> member.name.equals(name) && member.desc.equals(descriptor)
                        && ((member.access & Opcodes.ACC_STATIC) != 0) == isStatic);
        if (declared) {
            return true;
        }
        if (node.superName != null && hasMember(readClass, node.superName, name, descriptor, field, isStatic)) {
            return true;
        }
        return node.interfaces.stream()
                .anyMatch(parent -> hasMember(readClass, parent, name, descriptor, field, isStatic));
    }
}
