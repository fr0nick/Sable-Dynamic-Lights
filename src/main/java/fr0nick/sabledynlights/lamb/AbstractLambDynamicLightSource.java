package fr0nick.sabledynlights.lamb;

import dev.lambdaurora.lambdynlights.DynamicLightSource;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import fr0nick.sabledynlights.util.LightUpdateNotifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Base class for this mod's own "synthetic" dynamic light sources: {@code SableDynLightSource} and
 * {@code CreateDynLightSource}, which project a glowing block found inside a Sable sub-level or Create
 * contraption outward, to wherever that structure currently appears in the world.
 * <p>
 * Implements the (deprecated-but-still-present-for-1.21.1) {@link DynamicLightSource} interface directly - this
 * mod has a required, compile-time dependency on LambDynamicLights, so there's no need to go through reflection
 * here (contrast with the optional Sable integration in {@code SableReflection}).
 * <p>
 * Registration/removal ({@code setDynamicLightEnabled}) and the vanilla-chunk-rebuild bookkeeping
 * ({@code lambdynlights$getTrackedLitChunkPos}/{@code lambdynlights$setTrackedLitChunkPos}, used by
 * {@code getDynamicLightChunksToRebuild}) are intentionally left as the interface's own default implementations:
 * LambDynamicLights' own engine drives those automatically once a source is registered, and reimplementing them
 * here previously caused registration state to silently desync from LambDynamicLights' real internal set (fixed by
 * removing that override).
 */
@SuppressWarnings({"deprecation", "removal"})
public abstract class AbstractLambDynamicLightSource implements DynamicLightSource {
    protected Level level;
    protected Vec3 position;
    private Vec3 previousPosition;
    protected int luminance;
    private int previousLuminance;
    protected boolean removed;
    private long previousUpdateMillis;
    private LongOpenHashSet trackedLitChunkPositions;

    /** Separate from {@link #previousPosition}/{@link #previousLuminance}, which LambDynamicLights' own default
     * {@code getDynamicLightChunksToRebuild} implementation reads/writes via
     * {@code getDynamicLightPrevX/Y/Z}/{@code updateDynamicLightPreviousCoordinates} - keeping our own
     * Flywheel-notification bookkeeping in separate fields avoids the two tracking systems stepping on each other. */
    private Vec3 flywheelNotifiedPosition;
    private int flywheelNotifiedLuminance;

    protected AbstractLambDynamicLightSource(Level level, int luminance) {
        this.position = Vec3.ZERO;
        this.previousPosition = Vec3.ZERO;
        this.flywheelNotifiedPosition = Vec3.ZERO;
        this.trackedLitChunkPositions = new LongOpenHashSet();
        this.level = level;
        this.luminance = luminance;
    }

    protected abstract void syncPositionAndLuminance(boolean force);

    public void markRemoved() {
        this.removed = true;
        this.luminance = 0;
    }

    public double getDynamicLightX() { return this.position.x(); }
    public double getDynamicLightY() { return this.position.y(); }
    public double getDynamicLightZ() { return this.position.z(); }

    public Level getDynamicLightLevel() { return this.level; }

    /**
     * @return the entity this light source is derived from (e.g. a Create contraption entity), or {@code null} if
     * it isn't tied to one. Used by {@code VirtualSableLightManager} to check whether the source's position is
     * already local to a Sable sub-level (see that class' docs) rather than a normal world position.
     */
    public net.minecraft.world.entity.Entity getTrackedEntity() { return null; }

    public void resetDynamicLight() { this.previousLuminance = 0; }

    public int getLuminance() {
        return !this.removed && this.level != null ? Mth.clamp(this.luminance, 0, 15) : 0;
    }

    @Override
    public int getDynamicLightId() {
        return this.hashCode();
    }

    public int getLastDynamicLuminance() { return this.previousLuminance; }
    public void setLastDynamicLuminance(int luminance) { this.previousLuminance = luminance; }
    public void setLuminance(int luminance) { this.luminance = luminance; }
    public void updateDynamicLightPreviousCoordinates() { this.previousPosition = this.position; }

    public double getDynamicLightPrevX() { return this.previousPosition.x(); }
    public double getDynamicLightPrevY() { return this.previousPosition.y(); }
    public double getDynamicLightPrevZ() { return this.previousPosition.z(); }

    public void dynamicLightTick() {
        if (this.level == null || !this.level.isClientSide()) return;

        this.syncPositionAndLuminance(false);
        LambDynLightsDelegate.updateTracking(this);
        this.notifyFlywheelIfChanged();
    }

    /**
     * Notifies Flywheel (Create's renderer) so it can refresh any per-instance cached light values, whenever this
     * source's position or luminance has meaningfully changed since the last notification. Throttled the same way
     * LambDynamicLights throttles its own per-source updates ({@link #shouldUpdateDynamicLight()}), since this
     * only needs to be approximately current, not per-tick-exact.
     */
    private void notifyFlywheelIfChanged() {
        if (!this.shouldUpdateDynamicLight()) return;

        int currentLuminance = this.getLuminance();
        boolean changed = this.removed
                || Math.abs(this.position.x() - this.flywheelNotifiedPosition.x()) > 0.1
                || Math.abs(this.position.y() - this.flywheelNotifiedPosition.y()) > 0.1
                || Math.abs(this.position.z() - this.flywheelNotifiedPosition.z()) > 0.1
                || currentLuminance != this.flywheelNotifiedLuminance;
        if (!changed) return;

        LightUpdateNotifier.notifyLightChanged(BlockPos.containing(this.flywheelNotifiedPosition));
        if (!this.removed) {
            LightUpdateNotifier.notifyLightChanged(BlockPos.containing(this.position));
        }

        this.flywheelNotifiedPosition = this.position;
        this.flywheelNotifiedLuminance = currentLuminance;
    }

    public boolean shouldUpdateDynamicLight() {
        if (this.removed) return true;
        long now = System.currentTimeMillis();
        if (now < this.previousUpdateMillis + 100L) {
            return false;
        }
        this.previousUpdateMillis = now;
        return true;
    }

    public LongSet lambdynlights$getTrackedLitChunkPos() { return this.trackedLitChunkPositions; }

    public void lambdynlights$setTrackedLitChunkPos(LongSet set) {
        if (set instanceof LongOpenHashSet) {
            this.trackedLitChunkPositions = (LongOpenHashSet) set;
        } else {
            this.trackedLitChunkPositions = new LongOpenHashSet(set);
        }
    }
}
