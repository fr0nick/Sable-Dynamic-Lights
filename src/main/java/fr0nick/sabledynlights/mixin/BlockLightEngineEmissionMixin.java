package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.virtual.VirtualLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockLightEngine.class, priority = 100)
public class BlockLightEngineEmissionMixin {

    @Inject(method = "getEmission", at = @At("HEAD"), cancellable = true)
    private void sabledynlights$getVirtualEmission(long blockPosLong, BlockState state, CallbackInfoReturnable<Integer> cir) {
        BlockPos pos = BlockPos.of(blockPosLong);
        int virtualLight = VirtualLightManager.getVirtualEmission(pos);
        if (virtualLight > 0) {
            int vanillaEmission = state.getLightEmission();
            if (virtualLight > vanillaEmission) {
                cir.setReturnValue(virtualLight);
            }
        }
    }
}