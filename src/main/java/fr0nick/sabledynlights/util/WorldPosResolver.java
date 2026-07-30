package fr0nick.sabledynlights.util;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WorldPosResolver {
    /**
     * Per-class lookup results, cached (including the "not found" case, via {@link Optional} - a plain map can't
     * cache a {@code null} result, and this is queried once per rendered block face across the entire world, not
     * just Create/Sable content, so re-scanning on every miss would be a severe hot-path cost).
     */
    private static final ConcurrentMap<Class<?>, Optional<Method>> TO_GLOBAL_VEC_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Field[]> DECLARED_FIELDS_CACHE = new ConcurrentHashMap<>();

    private WorldPosResolver() {}

    public static int getCombinedWorldLight(BlockAndTintGetter level, BlockPos pos, int currentLight) {
        if (pos == null || currentLight >= 15) return currentLight;

        BlockPos worldPos = resolveWorldPos(level, pos);
        
        int dynamicLight = LambDynLightsDelegate.getDynamicLightLevel(worldPos);

        int worldStaticLight = 0;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel mainWorld = mc.level;
        if (mainWorld != null && worldPos != pos) {
            try {
                worldStaticLight = mainWorld.getBrightness(LightLayer.BLOCK, worldPos);
            } catch (Throwable ignored) {}
        }

        int maxExternal = Math.max(worldStaticLight, dynamicLight);
        return Math.max(currentLight, maxExternal);
    }

    public static BlockPos resolveWorldPos(BlockAndTintGetter level, BlockPos pos) {
        if (level == null || pos == null) return pos;
        try {
            BlockPos sablePos = getSableWorldPos(level, pos);
            if (sablePos != pos) return sablePos;

            BlockPos createPos = getCreateWorldPos(level, pos);
            if (createPos != pos) return createPos;
        } catch (Throwable ignored) {}

        return pos;
    }

    /**
     * If {@code plotPos} falls inside some Sable sub-level's plot, returns the corresponding world/visual
     * position (via that sub-level's pose); otherwise returns {@code plotPos} unchanged.
     * <p>
     * Sub-level plot chunks render through the same shared {@link ClientLevel} as normal terrain (see
     * {@code VirtualSableLightManager}'s docs for why) - they aren't given some separate per-sub-level
     * {@link BlockAndTintGetter} the way Create contraptions get their own {@code VirtualRenderWorld}. So this
     * only needs to (and only correctly can) do anything when {@code level} genuinely <em>is</em> that shared
     * level; for anything else (e.g. a contraption's own virtual world), there's nothing to check here, and this
     * bails out via one cheap reference comparison rather than paying for a lookup that could never match.
     */
    private static BlockPos getSableWorldPos(BlockAndTintGetter level, BlockPos plotPos) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel mainLevel = mc.level;
        if (mainLevel == null || level != mainLevel) return plotPos;

        try {
            SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(mainLevel, plotPos.getX() >> 4, plotPos.getZ() >> 4);
            if (subLevel != null) {
                Vec3 worldVec = subLevel.logicalPose().transformPosition(Vec3.atCenterOf(plotPos));
                return BlockPos.containing(worldVec);
            }
        } catch (Throwable ignored) {}

        return plotPos;
    }

    private static BlockPos getCreateWorldPos(BlockAndTintGetter level, BlockPos localPos) {
        Vec3 center = Vec3.atCenterOf(localPos);
        Class<?> levelClass = level.getClass();

        Optional<Method> methodLookup = TO_GLOBAL_VEC_METHODS.computeIfAbsent(levelClass, clazz -> {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals("toGlobalVector")) {
                    if (m.getParameterCount() == 2 || m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        return Optional.of(m);
                    }
                } else if (m.getName().equals("toGlobalPos") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    return Optional.of(m);
                }
            }
            return Optional.empty();
        });

        if (methodLookup.isPresent()) {
            try {
                Method toGlobalVec = methodLookup.get();
                if (toGlobalVec.getParameterCount() == 2) {
                    Object res = toGlobalVec.invoke(level, center, 1.0f);
                    if (res instanceof Vec3 vec) return BlockPos.containing(vec);
                } else {
                    Object res = toGlobalVec.invoke(level, center);
                    if (res instanceof Vec3 vec) return BlockPos.containing(vec);
                    if (res instanceof BlockPos bp) return bp;
                }
            } catch (Throwable ignored) {}
            return localPos;
        }

        // Fields are cached (setAccessible'd, pre-filtered to only those whose declared type could possibly hold
        // an AbstractContraptionEntity - a normal ClientLevel has none, so this is empty for the common case) once
        // per class, then checked by runtime value below - matching the original behavior, without re-enumerating
        // every field of the class on every single call.
        Field[] fields = DECLARED_FIELDS_CACHE.computeIfAbsent(levelClass, clazz -> {
            List<Field> candidates = new ArrayList<>();
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType().isAssignableFrom(AbstractContraptionEntity.class)) {
                    f.setAccessible(true);
                    candidates.add(f);
                }
            }
            return candidates.toArray(new Field[0]);
        });

        for (Field f : fields) {
            try {
                Object obj = f.get(level);
                if (obj instanceof AbstractContraptionEntity entity) {
                    return BlockPos.containing(entity.toGlobalVector(center, 1.0f));
                }
            } catch (Throwable ignored) {}
        }

        return localPos;
    }
}