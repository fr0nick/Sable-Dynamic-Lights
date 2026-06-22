package fr0nick.sabledynlights.lamb.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import fr0nick.sabledynlights.lamb.AbstractLambDynamicLightSource;

@SuppressWarnings({"deprecation", "removal"})
public class CreateDynLightSource extends AbstractLambDynamicLightSource {
    private final AbstractContraptionEntity contraptionEntity;
    private final BlockPos localPos;

    public CreateDynLightSource(AbstractContraptionEntity entity, BlockPos localPos, int luminance) {
        super(entity.level(), luminance);
        this.contraptionEntity = entity;
        this.localPos = localPos.immutable();
        this.syncPositionAndLuminance(true);
    }

    @Override
    protected void syncPositionAndLuminance(boolean force) {
        Level entityLevel = this.contraptionEntity.level();
        if (entityLevel != null) {
            this.level = entityLevel;
        }

        Contraption contraption = this.contraptionEntity.getContraption();
        if (contraption != null && !this.contraptionEntity.isRemoved()) {
            this.position = this.contraptionEntity.toGlobalVector(VecHelper.getCenterOf(this.localPos), 1.0F);
            StructureTemplate.StructureBlockInfo blockInfo = contraption.getBlocks().get(this.localPos);
            this.luminance = blockInfo == null ? 0 : blockInfo.state().getLightEmission();
        } else {
            this.markRemoved();
        }
    }
}