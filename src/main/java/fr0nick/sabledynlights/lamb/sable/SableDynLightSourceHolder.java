package fr0nick.sabledynlights.lamb.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SableDynLightSourceHolder {
    public static final SableDynLightSourceHolder INSTANCE = new SableDynLightSourceHolder();
    private final Map<LightSourceKey, SableDynLightSource> lightSources = new HashMap<>();
    private final ReentrantReadWriteLock lightSourcesLock = new ReentrantReadWriteLock();

    private SableDynLightSourceHolder() {}

    public void syncSubLevel(Level level, Object subLevel, UUID subLevelId, Iterable<SableLightCandidate> candidates) {
        Set<LightSourceKey> seen = new HashSet<>();
        this.lightSourcesLock.writeLock().lock();
        try {
            for (SableLightCandidate candidate : candidates) {
                LightSourceKey key = new LightSourceKey(subLevelId, candidate.plotPos());
                seen.add(key);
                SableDynLightSource existing = this.lightSources.get(key);
                if (existing == null) {
                    existing = new SableDynLightSource(level, subLevel, candidate.plotPos(), candidate.luminance());
                    this.lightSources.put(key, existing);
                    LambDynLightsDelegate.addLightSource(existing);
                } else {
                    existing.update(level, subLevel, candidate.luminance());
                }
            }

            Iterator<Map.Entry<LightSourceKey, SableDynLightSource>> iterator = this.lightSources.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<LightSourceKey, SableDynLightSource> entry = iterator.next();
                LightSourceKey key = entry.getKey();
                if (key.subLevelId.equals(subLevelId) && !seen.contains(key)) {
                    removeFromLamb(entry.getValue());
                    iterator.remove();
                }
            }
        } finally {
            this.lightSourcesLock.writeLock().unlock();
        }
    }

    public void removeMissingSubLevels(Set<UUID> loadedSubLevels) {
        this.lightSourcesLock.writeLock().lock();
        try {
            Iterator<Map.Entry<LightSourceKey, SableDynLightSource>> iterator = this.lightSources.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<LightSourceKey, SableDynLightSource> entry = iterator.next();
                if (!loadedSubLevels.contains(entry.getKey().subLevelId)) {
                    removeFromLamb(entry.getValue());
                    iterator.remove();
                }
            }
        } finally {
            this.lightSourcesLock.writeLock().unlock();
        }
    }

    public void clear() {
        this.lightSourcesLock.writeLock().lock();
        try {
            for (SableDynLightSource source : this.lightSources.values()) {
                removeFromLamb(source);
            }
            this.lightSources.clear();
        } finally {
            this.lightSourcesLock.writeLock().unlock();
        }
    }

    public void tickAll() {
        this.lightSourcesLock.readLock().lock();
        try {
            for (SableDynLightSource source : this.lightSources.values()) {
                LambDynLightsDelegate.tickLightSource(source);
            }
        } finally {
            this.lightSourcesLock.readLock().unlock();
        }
    }

    private static void removeFromLamb(SableDynLightSource lightSource) {
        if (lightSource != null) {
            lightSource.markRemoved();
            LambDynLightsDelegate.removeLightSource(lightSource);
        }
    }

    private static final class LightSourceKey {
        private final UUID subLevelId;
        private final BlockPos plotPos;

        private LightSourceKey(UUID subLevelId, BlockPos plotPos) {
            this.subLevelId = subLevelId;
            this.plotPos = plotPos.immutable();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o != null && this.getClass() == o.getClass()) {
                LightSourceKey that = (LightSourceKey) o;
                return Objects.equals(this.subLevelId, that.subLevelId) && Objects.equals(this.plotPos, that.plotPos);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.subLevelId, this.plotPos.asLong());
        }
    }
}