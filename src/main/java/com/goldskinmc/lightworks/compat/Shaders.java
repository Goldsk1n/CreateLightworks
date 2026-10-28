package com.goldskinmc.lightworks.compat;

import java.lang.reflect.Method;
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
}
