package fr0nick.sabledynlights;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import fr0nick.sabledynlights.lamb.create.ContraptionEntityEventHandler;
import fr0nick.sabledynlights.lamb.sable.SableSubLevelEventHandler;

@EventBusSubscriber(modid = SableDynLightsMod.MODID, value = Dist.CLIENT)
public class LambModEventHandler {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            Entity entity = event.getEntity();
            if (entity instanceof AbstractContraptionEntity contraptionEntity) {
                ContraptionEntityEventHandler.onContraptionEntityJoin(contraptionEntity);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveWorld(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            Entity entity = event.getEntity();
            if (entity instanceof AbstractContraptionEntity contraptionEntity) {
                ContraptionEntityEventHandler.onContraptionEntityLeave(contraptionEntity);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) {
            ContraptionEntityEventHandler.onTick(event.getLevel());
            SableSubLevelEventHandler.onTick(event.getLevel());
        }
    }
}