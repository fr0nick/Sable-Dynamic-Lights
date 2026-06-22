package fr0nick.sabledynlights.lamb;

import dev.lambdaurora.lambdynlights.DynamicLightSource;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings({"deprecation", "removal"})
public abstract class AbstractLambDynamicLightSource implements DynamicLightSource {
    protected Level level;
    protected Vec3 position;
    private Vec3 previousPosition;
    protected int luminance;
    private int previousLuminance;
    protected boolean removed;
    private boolean enabled = true;
    private long previousUpdateMillis;
    private LongOpenHashSet trackedLitChunkPositions;

    protected AbstractLambDynamicLightSource(Level level, int luminance) {
        this.position = Vec3.ZERO;
        this.previousPosition = Vec3.ZERO;
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
    
    public Level getDynamicLightWorld() { return this.level; }
    public Level getDynamicLightLevel() { return this.level; }

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
        if (this.level != null && this.level.isClientSide()) {
            this.syncPositionAndLuminance(false);
            LambDynLightsDelegate.updateTracking(this);
        }
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

    public boolean lambdynlights$updateDynamicLight(LevelRenderer renderer) {
        if (!this.shouldUpdateDynamicLight()) return false;
        
        this.syncPositionAndLuminance(false);
        int currentLuminance = this.getLuminance();
        boolean changed = this.removed || 
            Math.abs(this.position.x() - this.previousPosition.x()) > 0.1 || 
            Math.abs(this.position.y() - this.previousPosition.y()) > 0.1 || 
            Math.abs(this.position.z() - this.previousPosition.z()) > 0.1 || 
            currentLuminance != this.previousLuminance;
            
        if (!changed) return false;
        
        this.updateDynamicLightPreviousCoordinates();
        this.previousLuminance = currentLuminance;
        LongOpenHashSet newTrackedChunks = new LongOpenHashSet();
        
        if (currentLuminance > 0 && !this.removed) {
            this.trackLitChunks(renderer, newTrackedChunks);
        }

        this.lambdynlights$scheduleTrackedChunksRebuild(renderer);
        this.trackedLitChunkPositions = newTrackedChunks;
        return true;
    }

    public void lambdynlights$scheduleTrackedChunksRebuild(LevelRenderer renderer) {
        if (Minecraft.getInstance().level == this.level) {
            LongIterator var2 = this.trackedLitChunkPositions.iterator();
            while (var2.hasNext()) {
                long packedPos = var2.nextLong();
                LambDynLightsDelegate.scheduleChunkRebuild(renderer, packedPos);
            }
        }
    }

    public void setDynamicLightEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDynamicLightEnabled() { return this.enabled; }

    public LongSet lambdynlights$getTrackedLitChunkPos() { return this.trackedLitChunkPositions; }

    public void lambdynlights$setTrackedLitChunkPos(LongSet set) {
        if (set instanceof LongOpenHashSet) {
            this.trackedLitChunkPositions = (LongOpenHashSet) set;
        } else {
            this.trackedLitChunkPositions = new LongOpenHashSet(set);
        }
    }

    private void trackLitChunks(LevelRenderer renderer, LongOpenHashSet newTrackedChunks) {
        int blockX = Mth.floor(this.position.x());
        int blockY = Mth.floor(this.position.y());
        int blockZ = Mth.floor(this.position.z());
        BlockPos.MutableBlockPos chunkPos = new BlockPos.MutableBlockPos(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockY), SectionPos.blockToSectionCoord(blockZ));
        
        LambDynLightsDelegate.updateTrackedChunks(chunkPos, this.trackedLitChunkPositions, newTrackedChunks, renderer);
        Direction directionX = (blockX & 15) >= 8 ? Direction.EAST : Direction.WEST;
        Direction directionY = (blockY & 15) >= 8 ? Direction.UP : Direction.DOWN;
        Direction directionZ = (blockZ & 15) >= 8 ? Direction.SOUTH : Direction.NORTH;

        for (int i = 0; i < 7; ++i) {
            if (i % 4 == 0) chunkPos.move(directionX);
            else if (i % 4 == 1) chunkPos.move(directionZ);
            else if (i % 4 == 2) chunkPos.move(directionX.getOpposite());
            else {
                chunkPos.move(directionZ.getOpposite());
                chunkPos.move(directionY);
            }

            LambDynLightsDelegate.updateTrackedChunks(chunkPos, this.trackedLitChunkPositions, newTrackedChunks, renderer);
        }
    }
}