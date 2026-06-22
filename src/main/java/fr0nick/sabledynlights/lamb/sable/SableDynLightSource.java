package fr0nick.sabledynlights.lamb.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import fr0nick.sabledynlights.lamb.AbstractLambDynamicLightSource;

@SuppressWarnings({"deprecation", "removal"})
public class SableDynLightSource extends AbstractLambDynamicLightSource {
    private Object subLevel;
    private final BlockPos plotPos;

    public SableDynLightSource(Level level, Object subLevel, BlockPos plotPos, int luminance) {
        super(level, luminance);
        this.subLevel = subLevel;
        this.plotPos = plotPos.immutable();
        this.syncPositionAndLuminance(true);
    }

    public void update(Level level, Object subLevel, int luminance) {
        this.level = level;
        this.subLevel = subLevel;
        this.luminance = luminance;
        this.removed = false;
    }

    @Override
    protected void syncPositionAndLuminance(boolean force) {
        if (this.subLevel != null && !SableReflection.isSubLevelRemoved(this.subLevel)) {
            Vec3 plotCenter = Vec3.atCenterOf(this.plotPos);
            this.position = SableReflection.transformPosition(this.subLevel, plotCenter);
        } else {
            this.markRemoved();
        }
    }
}