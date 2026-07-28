package fr0nick.sabledynlights.lamb;

import dev.lambdaurora.lambdynlights.DynamicLightSource;
import dev.lambdaurora.lambdynlights.engine.source.EntityDynamicLightSourceBehavior;
import fr0nick.sabledynlights.SableDynLightsMod;
import fr0nick.sabledynlights.util.LightUpdateNotifier;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * Wrapper around LambDynamicLights' public API.
 * <p>
 * LambDynamicLights is a required (hard) runtime dependency of this mod (see {@code neoforge.mods.toml}), and the
 * light-source-facing interfaces ({@link DynamicLightSource}, {@link EntityDynamicLightSourceBehavior}, and their
 * parents in {@code dev.lambdaurora.lambdynlights.engine.source}) are referenced directly, with full compile-time
 * type safety - {@code AbstractLambDynamicLightSource} already implements {@link DynamicLightSource} directly the
 * same way, and always has.
 * <p>
 * The {@code dev.lambdaurora.lambdynlights.LambDynLights} singleton class itself, however, is accessed here via
 * targeted reflection (exact method names/parameter types looked up once and cached - not a scan or a guess)
 * rather than a direct compile-time reference: it implements a Fabric-loader "entrypoint" interface
 * ({@code dev.yumi.mc.core.api.entrypoint.client.ClientModInitializer}) that the Modrinth Maven artifact this
 * project compiles against doesn't publish as a resolvable dependency, so referencing that class directly fails
 * to compile ("cannot access ClientModInitializer") even though everything resolves fine at runtime (Yumi Core is
 * genuinely present then, bundled alongside the real LambDynamicLights jar). Looking the singleton and its methods
 * up by exact signature once keeps nearly all of the performance benefit a direct call would have had, and avoids
 * needing an extra compile-only dependency just to satisfy javac.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class LambDynLightsDelegate {
    private static boolean initialized = false;
    private static Object ldlInstance;
    private static Method getDynamicLightLevelMethod;
    private static Method updateTrackingMethod;
    private static Field dynamicLightSourcesField;
    private static Field dynamicLightSourcesMapField;

    private LambDynLightsDelegate() {}

    /** @return whether the (required) LambDynamicLights integration initialized successfully. */
    public static boolean isAvailable() {
        ensureInitialized();
        return ldlInstance != null;
    }

    /**
     * @return the dynamic light level (0-15) LambDynamicLights currently computes for {@code pos}, using its own
     * spatially-indexed lookup - this is a real method handle resolved once, not a per-call reflection scan, so
     * it's safe to call from hot paths like the light engine.
     */
    public static int getDynamicLightLevel(BlockPos pos) {
        ensureInitialized();
        if (pos == null || ldlInstance == null || getDynamicLightLevelMethod == null) return 0;
        try {
            Object result = getDynamicLightLevelMethod.invoke(ldlInstance, pos);
            if (result instanceof Number number) {
                return Math.clamp(Math.round(number.doubleValue()), 0, 15);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * @return every currently active {@link DynamicLightSource} (players, mobs, held/dropped glowing items, and
     * any registered via {@link #addLightSource}), or {@code null} if enumeration isn't available.
     */
    public static Collection<?> getActiveSources() {
        ensureInitialized();
        if (ldlInstance == null) return null;
        if (dynamicLightSourcesField != null) {
            try { return (Collection<?>) dynamicLightSourcesField.get(ldlInstance); } catch (Exception ignored) {}
        }
        if (dynamicLightSourcesMapField != null) {
            try {
                Map<?, ?> map = (Map<?, ?>) dynamicLightSourcesMapField.get(ldlInstance);
                return map != null ? map.values() : null;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> ldlClass = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");

            Method getMethod = ldlClass.getMethod("get");
            ldlInstance = getMethod.invoke(null);

            getDynamicLightLevelMethod = ldlClass.getMethod("getDynamicLightLevel", BlockPos.class);
            updateTrackingMethod = ldlClass.getMethod("updateTracking", EntityDynamicLightSourceBehavior.class);

            for (Field f : ldlClass.getDeclaredFields()) {
                if (Collection.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    dynamicLightSourcesField = f;
                    break;
                } else if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    dynamicLightSourcesMapField = f;
                    break;
                }
            }
            if (dynamicLightSourcesField == null && dynamicLightSourcesMapField == null) {
                SableDynLightsMod.LOGGER.warn("Could not locate LambDynamicLights' active light source collection; " +
                        "world-space dynamic lights won't reach Create contraptions or Sable sub-levels.");
            }
        } catch (Throwable t) {
            ldlInstance = null;
            SableDynLightsMod.LOGGER.warn("Could not initialize LambDynamicLights integration: " + t);
        }
    }

    /** Notifies Flywheel (Create's renderer) that light changed at a world position. */
    public static void notifyLightChanged(BlockPos pos) {
        LightUpdateNotifier.notifyLightChanged(pos);
    }

    public static void updateTracking(DynamicLightSource source) {
        ensureInitialized();
        if (updateTrackingMethod == null) return;
        try { updateTrackingMethod.invoke(null, source); } catch (Exception ignored) {}
    }

    public static void addLightSource(DynamicLightSource source) {
        source.setDynamicLightEnabled(true);
    }

    public static void removeLightSource(DynamicLightSource source) {
        source.setDynamicLightEnabled(false);
    }

    public static void tickLightSource(DynamicLightSource source) {
        source.dynamicLightTick();
    }
}
