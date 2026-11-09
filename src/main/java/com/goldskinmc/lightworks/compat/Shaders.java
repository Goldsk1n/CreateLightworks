package com.goldskinmc.lightworks.compat;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Whether an Iris shader pack is in use, through Iris' public API found by reflection, so Iris stays an
 * undeclared optional dependency. False when Iris is not installed.
 */
public final class Shaders {
    @Nullable
    private static final Object API;
    @Nullable
    private static final Method IN_USE;

    static {
        Object api = null;
        Method inUse = null;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // No Iris
        }
        API = api;
        IN_USE = inUse;
    }

    private Shaders() {
    }

    public static boolean inUse() {
        if (IN_USE == null) {
            return false;
        }
        try {
            return (boolean) IN_USE.invoke(API);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Where Iris keeps its settings, the selected pack among them. */
    private static final Path IRIS_CONFIG = FMLPaths.CONFIGDIR.get().resolve("iris.properties");
    private static long packReadAt = Long.MIN_VALUE;
    /** Asked every frame by every beam: the file is looked at again a second after the last look at most. */
    private static long packCheckedAt = Long.MIN_VALUE;
    private static String packName = "";

    /**
     * The pack Iris has selected, as its settings name it (the zip's or folder's name), or "" without Iris. Iris has no
     * API for it; it writes the file whenever a pack is chosen, so it is read again when the file changes.
     */
    public static synchronized String packName() {
        if (IN_USE == null) {
            return "";
        }
        long now = System.nanoTime();
        if (packCheckedAt != Long.MIN_VALUE && now - packCheckedAt < 1_000_000_000L) {
            return packName;
        }
        packCheckedAt = now;
        try {
            long modified = Files.exists(IRIS_CONFIG) ? Files.getLastModifiedTime(IRIS_CONFIG).toMillis() : 0;
            if (modified != packReadAt) {
                packReadAt = modified;
                Properties properties = new Properties();
                if (modified != 0) {
                    try (Reader reader = Files.newBufferedReader(IRIS_CONFIG)) {
                        properties.load(reader);
                    }
                }
                packName = properties.getProperty("shaderPack", "");
            }
        } catch (IOException | RuntimeException e) {
            packName = "";
        }
        return packName;
    }
}
