package fr0nick.sabledynlights.lamb.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContraptionEntityEventHandler {
    private static final List<AbstractContraptionEntity> scheduledToAddContraptionEntities = new ArrayList<>();
    private static final List<AbstractContraptionEntity> contraptionEntities = new ArrayList<>();

    /**
     * Entry point from {@code LambModEventHandler}, which must not itself reference any Create type (see
     * {@link fr0nick.sabledynlights.compat.CreateCompat}) and so cannot do this {@code instanceof} check.
     *
     * @return whether {@code entity} was a contraption, i.e. whether anything was actually tracked.
     */
    public static boolean onEntityJoin(Entity entity) {
        if (entity instanceof AbstractContraptionEntity contraptionEntity) {
            onContraptionEntityJoin(contraptionEntity);
            return true;
        }
        return false;
    }

    /** Entry point from {@code LambModEventHandler}; see {@link #onEntityJoin(Entity)}. */
    public static void onEntityLeave(Entity entity) {
        if (entity instanceof AbstractContraptionEntity contraptionEntity) {
            onContraptionEntityLeave(contraptionEntity);
        }
    }

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