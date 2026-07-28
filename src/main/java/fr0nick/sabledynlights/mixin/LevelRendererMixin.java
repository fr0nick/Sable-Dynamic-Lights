package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.util.WorldPosResolver;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelRenderer.class, priority = 100)
public class LevelRendererMixin {

    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private static void sabledynlights$getLightColorWithState(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        sabledynlights$applyDynamicLight(level, pos, cir);
    }

    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private static void sabledynlights$getLightColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        sabledynlights$applyDynamicLight(level, pos, cir);
    }

    private static void sabledynlights$applyDynamicLight(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (pos == null) return;
        int packed = cir.getReturnValueI();
        int currentBlockLight = LightTexture.block(packed);
        if (currentBlockLight >= 15) return;

        int newLight = WorldPosResolver.getCombinedWorldLight(level, pos, currentBlockLight);
        if (newLight > currentBlockLight) {
            int skyLight = LightTexture.sky(packed);
            cir.setReturnValue(LightTexture.pack(newLight, skyLight));
        }
    }
}