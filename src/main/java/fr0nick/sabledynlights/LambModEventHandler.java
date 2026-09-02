package fr0nick.sabledynlights;

import fr0nick.sabledynlights.compat.CreateCompat;
import fr0nick.sabledynlights.virtual.VirtualLightManager;
import fr0nick.sabledynlights.virtual.VirtualSableLightManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import fr0nick.sabledynlights.lamb.create.ContraptionEntityEventHandler;
import fr0nick.sabledynlights.lamb.sable.SableSubLevelEventHandler;

import java.util.List;

@EventBusSubscriber(modid = SableDynLightsMod.MODID, value = Dist.CLIENT)
public class LambModEventHandler {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (CreateCompat.LOADED && event.getLevel().isClientSide()
                && ContraptionEntityEventHandler.onEntityJoin(event.getEntity())) {
            VirtualLightManager.onClientTick();
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveWorld(EntityLeaveLevelEvent event) {
        if (CreateCompat.LOADED && event.getLevel().isClientSide()) {
            ContraptionEntityEventHandler.onEntityLeave(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) {
            if (CreateCompat.LOADED) {
                ContraptionEntityEventHandler.onTick(event.getLevel());
            }
            SableSubLevelEventHandler.onTick(event.getLevel());

            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                List<VirtualLightManager.LightCandidate> candidates = VirtualLightManager.collectActiveLightCandidates(mc);
                VirtualLightManager.onClientTick(candidates);
                VirtualSableLightManager.onClientTick(candidates);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VirtualLightManager.clearAll();
        VirtualSableLightManager.clearAll();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            VirtualLightManager.clearAll();
            VirtualSableLightManager.clearAll();
        }
    }
}