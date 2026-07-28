package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;
import fr0nick.sabledynlights.util.WorldPosResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockAndTintGetter.class, priority = 100)
public interface BlockAndTintGetterMixin {

    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    default void sabledynlights$getBrightness(LightLayer layer, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (layer == LightLayer.BLOCK && pos != null) {
            int vanilla = cir.getReturnValueI();
            if (vanilla < 15) {
                BlockPos worldPos = WorldPosResolver.resolveWorldPos((BlockAndTintGetter) this, pos);
                int dynamic = LambDynLightsDelegate.getDynamicLightLevel(worldPos);
                if (dynamic > vanilla) {
                    cir.setReturnValue(dynamic);
                }
            }
        }
    }
}