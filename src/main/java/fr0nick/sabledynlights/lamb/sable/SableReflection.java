package fr0nick.sabledynlights.lamb.sable;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import fr0nick.sabledynlights.SableDynLightsMod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SableReflection {
    private static boolean initialized;
    private static boolean available;
    private static Method getContainerMethod;
    private static Method getAllSubLevelsMethod;
    private static Method getUniqueIdMethod;
    private static Method isRemovedMethod;
    private static Method logicalPoseMethod;
    private static Method getPlotMethod;
    private static Method getLoadedChunksMethod;
    private static Method getChunkMethod;
    private static final ConcurrentMap<Class<?>, Method> TRANSFORM_POSITION_METHODS = new ConcurrentHashMap<>();

    private SableReflection() {}

    public static boolean isSableLoaded() {
        return ModList.get().isLoaded("sable");
    }

    public static boolean isAvailable() {
        initialize();
        return available;
    }

    public static Optional<Object> getContainer(Level level) {
        if (!isAvailable()) return Optional.empty();
        try {
            return Optional.ofNullable(getContainerMethod.invoke(null, level));
        } catch (InvocationTargetException | IllegalAccessException e) {
            SableDynLightsMod.LOGGER.debug("Unable to access Sable sub-level container", e);
            return Optional.empty();
        }
    }

    public static List<?> getAllSubLevels(Object container) {
        if (!isAvailable()) return List.of();
        try {
            Object result = getAllSubLevelsMethod.invoke(container);
            return result instanceof List<?> list ? list : List.of();
        } catch (InvocationTargetException | IllegalAccessException e) {
            SableDynLightsMod.LOGGER.debug("Unable to list Sable sub-levels", e);
            return List.of();
        }
    }

    public static @Nullable UUID getSubLevelId(Object subLevel) {
        if (!isAvailable()) return null;
        try {
            Object result = getUniqueIdMethod.invoke(subLevel);
            return result instanceof UUID uuid ? uuid : null;
        } catch (InvocationTargetException | IllegalAccessException e) {
            SableDynLightsMod.LOGGER.debug("Unable to read Sable sub-level id", e);
            return null;
        }
    }

    public static boolean isSubLevelRemoved(Object subLevel) {
        if (!isAvailable()) return true;
        try {
            Object result = isRemovedMethod.invoke(subLevel);
            return result instanceof Boolean bool && bool;
        } catch (InvocationTargetException | IllegalAccessException e) {
            SableDynLightsMod.LOGGER.debug("Unable to read Sable sub-level removal state", e);
            return true;
        }
    }

    public static Collection<?> getLoadedChunkHolders(Object subLevel) {
        if (!isAvailable()) return List.of();
        try {
            Object plot = getPlotMethod.invoke(subLevel);
            if (plot == null) return List.of();
            Object result = getLoadedChunksMethod.invoke(plot);
            return result instanceof Collection<?> collection ? collection : Collections.emptyList();
        } catch (InvocationTargetException | IllegalAccessException e) {
            SableDynLightsMod.LOGGER.debug("Unable to read Sable loaded chunks", e);
            return List.of();
        }
    }

    public static @Nullable LevelChunk getChunk(Object plotChunkHolder) {
        if (!isAvailable()) return null;
        try {
            Object result = getChunkMethod.invoke(plotChunkHolder);
            return result instanceof LevelChunk chunk ? chunk : null;
        } catch (InvocationTargetException | IllegalAccessException e) {
            SableDynLightsMod.LOGGER.debug("Unable to read Sable plot chunk", e);
            return null;
        }
    }

    public static Vec3 transformPosition(Object subLevel, Vec3 plotPosition) {
        if (!isAvailable()) return plotPosition;
        try {
            Object pose = logicalPoseMethod.invoke(subLevel);
            if (pose == null) return plotPosition;

            Method transformPosition = TRANSFORM_POSITION_METHODS.computeIfAbsent(pose.getClass(), clazz -> {
                try {
                    return clazz.getMethod("transformPosition", Vec3.class);
                } catch (NoSuchMethodException e) {
                    SableDynLightsMod.LOGGER.debug("Sable pose type [{}] has no Vec3 transformPosition method", clazz.getName(), e);
                    return null;
                }
            });

            if (transformPosition == null) return plotPosition;
            Object result = transformPosition.invoke(pose, plotPosition);
            return result instanceof Vec3 vec3 ? vec3 : plotPosition;
        } catch (InvocationTargetException | IllegalAccessException e) {
            SableDynLightsMod.LOGGER.debug("Unable to transform Sable plot position", e);
            return plotPosition;
        }
    }

    private static synchronized void initialize() {
        if (!initialized) {
            initialized = true;
            if (!isSableLoaded()) {
                available = false;
            } else {
                try {
                    Class<?> subLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                    Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                    Class<?> levelPlotClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
                    Class<?> plotChunkHolderClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder");

                    getContainerMethod = subLevelContainerClass.getMethod("getContainer", Level.class);
                    getAllSubLevelsMethod = subLevelContainerClass.getMethod("getAllSubLevels");
                    getUniqueIdMethod = subLevelClass.getMethod("getUniqueId");
                    isRemovedMethod = subLevelClass.getMethod("isRemoved");
                    logicalPoseMethod = subLevelClass.getMethod("logicalPose");
                    getPlotMethod = subLevelClass.getMethod("getPlot");
                    getLoadedChunksMethod = levelPlotClass.getMethod("getLoadedChunks");
                    getChunkMethod = plotChunkHolderClass.getMethod("getChunk");

                    available = true;
                    SableDynLightsMod.LOGGER.info("Sable Dynamic Lights detected Sable; sub-level dynamic lights are available.");
                } catch (LinkageError | ReflectiveOperationException e) {
                    available = false;
                    SableDynLightsMod.LOGGER.warn("Sable is installed, but its dynamic-light bridge could not initialize. Sable support will stay disabled.", e);
                }
            }
        }
    }
}