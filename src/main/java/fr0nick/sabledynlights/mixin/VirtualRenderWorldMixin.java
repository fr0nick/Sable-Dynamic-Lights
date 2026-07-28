package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.util.WorldPosResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {
    "net.createmod.catnip.level.VirtualRenderWorld",
    "com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld",
    "com.simibubi.create.content.contraptions.render.VirtualRenderWorld"
}, priority = 100)
public class VirtualRenderWorldMixin {

    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    private void sabledynlights$getBrightness(LightLayer layer, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (layer == LightLayer.BLOCK && pos != null) {
            int vanilla = cir.getReturnValueI();
            if (vanilla < 15) {
                BlockAndTintGetter self = (BlockAndTintGetter) (Object) this;
                int combinedLight = WorldPosResolver.getCombinedWorldLight(self, pos, vanilla);
                if (combinedLight > vanilla) {
                    cir.setReturnValue(combinedLight);
                }
            }
        }
    }
}