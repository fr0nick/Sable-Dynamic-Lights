package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.util.WorldPosResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Pseudo
@Mixin(targets = {
    "net.caffeinemc.sodium.client.model.light.data.LightDataAccess",
    "me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess"
}, priority = 100, remap = false)
public class SodiumLightDataAccessMixin {
    /**
     * Sodium reuses a small pool of {@code LightDataAccess} instances across chunk mesh builds, calling
     * {@code getLightmap} once per vertex - so the field holding its current {@link BlockAndTintGetter} is found
     * via reflection once per class here and cached (including the "no such field" case, via {@link Optional},
     * since a plain map can't cache a {@code null} result), rather than re-scanned on every single call.
     */
    private static final ConcurrentMap<Class<?>, Optional<Field>> LEVEL_FIELDS = new ConcurrentHashMap<>();

    @Inject(method = "getLightmap", at = @At("RETURN"), cancellable = true, remap = false)
    private void sabledynlights$onGetLightmap(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (pos != null) {
            int packed = cir.getReturnValueI();
            int blockLight = packed & 0xFF;
            if (blockLight < 15) {
                BlockAndTintGetter level = sabledynlights$findLevel();

                int combinedLight = WorldPosResolver.getCombinedWorldLight(level, pos, blockLight);
                if (combinedLight > blockLight) {
                    int skyLight = (packed >> 16) & 0xFF;
                    cir.setReturnValue((skyLight << 16) | combinedLight);
                }
            }
        }
    }

    private BlockAndTintGetter sabledynlights$findLevel() {
        Optional<Field> field = LEVEL_FIELDS.computeIfAbsent(this.getClass(), clazz -> {
            for (Field f : clazz.getDeclaredFields()) {
                if (BlockAndTintGetter.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return Optional.of(f);
                }
            }
            return Optional.empty();
        });

        if (field.isEmpty()) return null;
        try {
            return (BlockAndTintGetter) field.get().get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }
}