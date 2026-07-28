package fr0nick.sabledynlights.util;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;
import fr0nick.sabledynlights.lamb.sable.SableReflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WorldPosResolver {
    private static final ConcurrentMap<Class<?>, Method> TO_GLOBAL_VEC_METHODS = new ConcurrentHashMap<>();

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

    private static BlockPos getSableWorldPos(BlockAndTintGetter level, BlockPos plotPos) {
        try {
            if (SableReflection.isAvailable()) {
                Vec3 center = Vec3.atCenterOf(plotPos);
                Vec3 worldVec = SableReflection.transformPosition(level, center);
                if (worldVec != null && !worldVec.equals(center)) {
                    return BlockPos.containing(worldVec);
                }
            }
        } catch (Throwable ignored) {}
        return plotPos;
    }

    private static BlockPos getCreateWorldPos(BlockAndTintGetter level, BlockPos localPos) {
        try {
            Vec3 center = Vec3.atCenterOf(localPos);

            Method toGlobalVec = TO_GLOBAL_VEC_METHODS.computeIfAbsent(level.getClass(), clazz -> {
                for (Method m : clazz.getMethods()) {
                    if (m.getName().equals("toGlobalVector")) {
                        if (m.getParameterCount() == 2 || m.getParameterCount() == 1) return m;
                    } else if (m.getName().equals("toGlobalPos") && m.getParameterCount() == 1) {
                        return m;
                    }
                }
                return null;
            });

            if (toGlobalVec != null) {
                if (toGlobalVec.getParameterCount() == 2) {
                    Object res = toGlobalVec.invoke(level, center, 1.0f);
                    if (res instanceof Vec3 vec) return BlockPos.containing(vec);
                } else if (toGlobalVec.getParameterCount() == 1) {
                    Object res = toGlobalVec.invoke(level, center);
                    if (res instanceof Vec3 vec) return BlockPos.containing(vec);
                    if (res instanceof BlockPos bp) return bp;
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Field f : level.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object obj = f.get(level);
                if (obj instanceof AbstractContraptionEntity entity) {
                    Vec3 vec = entity.toGlobalVector(Vec3.atCenterOf(localPos), 1.0f);
                    return BlockPos.containing(vec);
                }
            }
        } catch (Throwable ignored) {}

        return localPos;
    }
}