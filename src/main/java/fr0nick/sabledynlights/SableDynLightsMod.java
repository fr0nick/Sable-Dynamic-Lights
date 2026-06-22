package fr0nick.sabledynlights;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(SableDynLightsMod.MODID)
public class SableDynLightsMod {
    public static final String MODID = "sabledynlights";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SableDynLightsMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Sable Dynamic Lights initialized.");
    }
}