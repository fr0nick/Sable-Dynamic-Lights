package fr0nick.sabledynlights.lamb.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContraptionEntityEventHandler {
    private static final List<AbstractContraptionEntity> scheduledToAddContraptionEntities = new ArrayList<>();
    private static final List<AbstractContraptionEntity> contraptionEntities = new ArrayList<>();

    public static void onContraptionEntityJoin(AbstractContraptionEntity contraptionEntity) {
        if (!contraptionEntities.contains(contraptionEntity)) {
            contraptionEntities.add(contraptionEntity);
        }

        Contraption contraption = contraptionEntity.getContraption();
        if (contraption != null) {
            addLightSourcesOfContraption(contraption);
        } else {
            scheduledToAddContraptionEntities.add(contraptionEntity);
        }
    }

    public static void onContraptionEntityLeave(AbstractContraptionEntity contraptionEntity) {
        contraptionEntities.remove(contraptionEntity);
        scheduledToAddContraptionEntities.remove(contraptionEntity);
        CreateDynLightSourceHolder.INSTANCE.removeAll(contraptionEntity);
    }

    private static void addLightSourcesOfContraption(Contraption contraption) {
        Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks = contraption.getBlocks();
        blocks.forEach((pos, blockInfo) -> {
            int lightEmission = blockInfo.state().getLightEmission();
            if (lightEmission > 0) {
                CreateDynLightSourceHolder.INSTANCE.getOrCreate(contraption.entity, blockInfo.pos(), lightEmission);
            }
        });
    }

    public static void onTick(Level level) {
        LambDynLightsDelegate.flushPendingSources();
        ArrayList<AbstractContraptionEntity> toRemove = new ArrayList<>();

        for (AbstractContraptionEntity entity : scheduledToAddContraptionEntities) {
            Contraption contraption = entity.getContraption();
            if (contraption != null) {
                addLightSourcesOfContraption(contraption);
                toRemove.add(entity);
            }
        }

        scheduledToAddContraptionEntities.removeAll(toRemove);
        CreateDynLightSourceHolder.INSTANCE.tickAll();
    }
}