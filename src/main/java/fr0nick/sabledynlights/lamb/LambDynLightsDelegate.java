package fr0nick.sabledynlights.lamb;

import dev.lambdaurora.lambdynlights.DynamicLightSource;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import fr0nick.sabledynlights.SableDynLightsMod;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@SuppressWarnings({"deprecation", "removal"})
public class LambDynLightsDelegate {
    private static final Set<DynamicLightSource> pendingSources = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean initialized = false;
    private static Object ldlInstance;
    
    private static Method addLightSourceMethod;
    private static Method removeLightSourceMethod;
    private static Method scheduleChunkRebuildMethodPos;
    private static Method scheduleChunkRebuildMethodLong;
    private static Method updateTrackingMethod;

    public static boolean isAvailable() {
        if (!initialized) {
            try {
                Class<?> ldlClass = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
                ldlInstance = ldlClass.getMethod("get").invoke(null);
                
                for (Method m : ldlClass.getMethods()) {
                    if (m.getName().equals("addLightSource") && m.getParameterCount() == 1) {
                        addLightSourceMethod = m;
                    } else if (m.getName().equals("removeLightSource") && m.getParameterCount() == 1) {
                        removeLightSourceMethod = m;
                    } else if (m.getName().equals("scheduleChunkRebuild") && m.getParameterCount() == 2) {
                        if (m.getParameterTypes()[1] == BlockPos.class) {
                            scheduleChunkRebuildMethodPos = m;
                        } else if (m.getParameterTypes()[1] == long.class) {
                            scheduleChunkRebuildMethodLong = m;
                        }
                    } else if (m.getName().equals("updateTracking") && m.getParameterCount() == 1) {
                        updateTrackingMethod = m;
                    }
                }
                SableDynLightsMod.LOGGER.info("Successfully hooked into LambDynamicLights!");
            } catch (Throwable t) {
                SableDynLightsMod.LOGGER.error("Failed to hook into LambDynamicLights via reflection", t);
                ldlInstance = null;
            }
            initialized = true;
        }
        return ldlInstance != null;
    }

    public static void scheduleChunkRebuild(LevelRenderer renderer, BlockPos pos) {
        if (isAvailable()) {
            try {
                if (scheduleChunkRebuildMethodPos != null) {
                    scheduleChunkRebuildMethodPos.invoke(null, renderer, pos);
                } else if (scheduleChunkRebuildMethodLong != null) {
                    scheduleChunkRebuildMethodLong.invoke(null, renderer, pos.asLong());
                }
            } catch (Exception e) {
                SableDynLightsMod.LOGGER.debug("Failed to schedule chunk rebuild", e);
            }
        }
    }

    public static void scheduleChunkRebuild(LevelRenderer renderer, long pos) {
        if (isAvailable()) {
            try {
                if (scheduleChunkRebuildMethodLong != null) {
                    scheduleChunkRebuildMethodLong.invoke(null, renderer, pos);
                } else if (scheduleChunkRebuildMethodPos != null) {
                    scheduleChunkRebuildMethodPos.invoke(null, renderer, BlockPos.of(pos));
                }
            } catch (Exception e) {
                SableDynLightsMod.LOGGER.debug("Failed to schedule chunk rebuild", e);
            }
        }
    }

    public static void updateTrackedChunks(BlockPos pos, LongOpenHashSet oldSet, LongOpenHashSet newSet, LevelRenderer renderer) {
        long packed = pos.asLong();
        if (!oldSet.contains(packed)) {
            scheduleChunkRebuild(renderer, pos);
        }
        newSet.add(packed);
    }

    public static void updateTracking(DynamicLightSource source) {
        if (isAvailable() && updateTrackingMethod != null) {
            try {
                updateTrackingMethod.invoke(ldlInstance, source);
            } catch (Exception e) {
                SableDynLightsMod.LOGGER.debug("Failed to update tracking for source", e);
            }
        }
    }

    public static synchronized void addLightSource(DynamicLightSource source) {
        if (!isAvailable()) {
            pendingSources.add(source);
        } else {
            pendingSources.remove(source);
            source.setDynamicLightEnabled(true);
            try {
                if (addLightSourceMethod != null) addLightSourceMethod.invoke(ldlInstance, source);
            } catch (Exception e) {
                SableDynLightsMod.LOGGER.error("Error adding light source", e);
            }
        }
    }

    public static synchronized void removeLightSource(DynamicLightSource source) {
        pendingSources.remove(source);
        if (isAvailable()) {
            source.setDynamicLightEnabled(false);
            try {
                if (removeLightSourceMethod != null) removeLightSourceMethod.invoke(ldlInstance, source);
            } catch (Exception e) {
                SableDynLightsMod.LOGGER.error("Error removing light source", e);
            }
        }
    }

    public static synchronized void flushPendingSources() {
        if (isAvailable() && !pendingSources.isEmpty()) {
            for (DynamicLightSource source : Set.copyOf(pendingSources)) {
                source.setDynamicLightEnabled(true);
                try {
                    if (addLightSourceMethod != null) addLightSourceMethod.invoke(ldlInstance, source);
                } catch (Exception e) {}
            }
            pendingSources.clear();
        }
    }

    public static void tickLightSource(DynamicLightSource source) {
        if (isAvailable()) {
            source.dynamicLightTick();
        }
    }
}