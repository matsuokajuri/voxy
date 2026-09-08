package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Round11GeometryFastutilRuntimeAbiTest {
    @Test
    void actualMinecraft859SupportsReservationUploadReplacementAndReclamation() throws Exception {
        String artifact = System.getProperty("voxy.test.minecraftFastutilAbiJar");
        assertNotNull(artifact, "Gradle must supply the detached Minecraft runtime ABI artifact");
        Path jar = Path.of(artifact);
        assertTrue(Files.isRegularFile(jar), "Actual Minecraft fastutil 8.5.9 artifact is required, not compile-version substitution");
        URL classes = BasicAsyncGeometryManager.class.getProtectionDomain().getCodeSource().getLocation();
        try (RuntimeLoader loader = new RuntimeLoader(new URL[]{jar.toUri().toURL(), classes})) {
            Class<?> actualMap = loader.loadClass("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap");
            Class<?> actualSet = loader.loadClass("it.unimi.dsi.fastutil.ints.IntOpenHashSet");
            assertTrue(Modifier.isPrivate(actualMap.getDeclaredMethod("ensureCapacity", int.class).getModifiers()));
            assertTrue(Modifier.isPrivate(actualSet.getDeclaredMethod("ensureCapacity", int.class).getModifiers()));
            assertTrue(Modifier.isProtected(actualMap.getDeclaredMethod("rehash", int.class).getModifiers()));
            assertTrue(Modifier.isProtected(actualSet.getDeclaredMethod("rehash", int.class).getModifiers()));
            assertEquals(jar.toUri().toURL(), actualMap.getProtectionDomain().getCodeSource().getLocation());

            Class<?> managerType = loader.loadClass(BasicAsyncGeometryManager.class.getName());
            Class<?> sectionType = loader.loadClass(BuiltSection.class.getName());
            Constructor<?> managerConstructor = managerType.getDeclaredConstructor(int.class, long.class);
            managerConstructor.setAccessible(true);
            Object manager = managerConstructor.newInstance(4096, 16L << 20);
            Constructor<?> sectionConstructor = sectionType.getDeclaredConstructor(long.class, byte.class,
                    int.class, MemoryBuffer.class, int[].class, MemoryBuffer.class);
            sectionConstructor.setAccessible(true);
            Method upload = method(managerType, "uploadSection", sectionType);
            Method replace = method(managerType, "uploadReplaceSection", int.class, sectionType);
            Method remove = method(managerType, "removeSection", int.class);
            int before = MemoryBuffer.getCount();
            int[] ids = new int[2000]; // crosses both map/set initial hash-table growth thresholds
            try {
                for (int i = 0; i < ids.length; i++) {
                    MemoryBuffer vertices = new MemoryBuffer(64);
                    MemoryBuffer occupancy = new MemoryBuffer(8);
                    Object section = sectionConstructor.newInstance((long) i, (byte) 0, 0, vertices, new int[8], occupancy);
                    ids[i] = (int) upload.invoke(manager, section);
                    assertFalse(vertices.isFreed());
                    assertTrue(occupancy.isFreed());
                }
                Object replacement = sectionConstructor.newInstance(0L, (byte) 0, 0,
                        new MemoryBuffer(2048), new int[8], null);
                assertEquals(ids[0], replace.invoke(manager, ids[0], replacement));
                method(managerType, "verifyIntegrity").invoke(manager);
                Map<?, ?> uploads = (Map<?, ?>) method(managerType, "getUploads").invoke(manager);
                assertEquals(ids.length, uploads.size());
                for (int id : ids) remove.invoke(manager, id);
                assertEquals(0, method(managerType, "getSectionCount").invoke(manager));
                assertEquals(0L, method(managerType, "getGeometryUsedBytes").invoke(manager));
                method(managerType, "verifyIntegrity").invoke(manager);
            } finally {
                method(managerType, "clear").invoke(manager);
            }
            assertEquals(before, MemoryBuffer.getCount(), "fastutil 8.5.9 path leaked native buffers");
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... types) throws Exception {
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static final class RuntimeLoader extends URLClassLoader {
        RuntimeLoader(URL[] urls) { super(urls, Round11GeometryFastutilRuntimeAbiTest.class.getClassLoader()); }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            boolean local = name.startsWith("it.unimi.dsi.fastutil.")
                    || name.startsWith("me.cortex.voxy.forge.BasicAsyncGeometryManager")
                    || name.equals("me.cortex.voxy.forge.BuiltSection")
                    || name.equals("me.cortex.voxy.common.util.AllocationArena")
                    || name.equals("me.cortex.voxy.common.util.HierarchicalBitSet");
            if (!local) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name); // never fall back to compile-time fastutil
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
