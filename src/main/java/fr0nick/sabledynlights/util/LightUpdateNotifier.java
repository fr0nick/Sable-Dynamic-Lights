package fr0nick.sabledynlights.util;

import net.minecraft.core.BlockPos;

import java.lang.reflect.Method;

/**
 * Notifies other renderers that light changed at a world position.
 * <p>
 * Sable sub-level interiors are <strong>not</strong> handled reactively from here. Instead
 * {@link fr0nick.sabledynlights.virtual.VirtualSableLightManager} proactively polls active dynamic lights every
 * client tick and transforms them into each nearby sub-level's local coordinate space itself, going through the
 * real light engine (see that class' docs for why). That approach doesn't need a per-chunk-crossing callback like
 * this one to work correctly, so this class is only left with the Flywheel (Create's renderer) cache invalidation.
 */
public final class LightUpdateNotifier {
    private static Method flywheelUpdateMethod;
    private static Object flywheelUpdater;
    private static boolean flywheelChecked = false;

    private LightUpdateNotifier() {}

    public static void notifyLightChanged(BlockPos worldPos) {
        if (worldPos == null) return;
        notifyFlywheel(worldPos);
    }

    private static void notifyFlywheel(BlockPos worldPos) {
        if (!flywheelChecked) {
            flywheelChecked = true;
            try {
                Class<?> updaterCls = Class.forName("dev.engine_room.flywheel.backend.engine.light.LightUpdater");
                flywheelUpdater = updaterCls.getMethod("get").invoke(null);
                flywheelUpdateMethod = updaterCls.getMethod("onLightUpdate", BlockPos.class);
            } catch (Throwable ignored) {}
        }
        if (flywheelUpdateMethod != null && flywheelUpdater != null) {
            try { flywheelUpdateMethod.invoke(flywheelUpdater, worldPos); } catch (Throwable ignored) {}
        }
    }
}
