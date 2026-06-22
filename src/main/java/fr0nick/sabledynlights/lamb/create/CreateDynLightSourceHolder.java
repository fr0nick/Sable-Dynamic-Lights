package fr0nick.sabledynlights.lamb.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CreateDynLightSourceHolder {
    public static final CreateDynLightSourceHolder INSTANCE = new CreateDynLightSourceHolder();
    private final Map<LightSourceKey, CreateDynLightSource> lightSources = new HashMap<>();
    private final ReentrantReadWriteLock lightSourcesLock = new ReentrantReadWriteLock();

    private CreateDynLightSourceHolder() {}

    public CreateDynLightSource create(AbstractContraptionEntity entity, BlockPos blockPos, int luminance) {
        CreateDynLightSource lightSource = new CreateDynLightSource(entity, blockPos, luminance);
        this.lightSourcesLock.writeLock().lock();
        try {
            this.lightSources.put(new LightSourceKey(entity.getId(), blockPos), lightSource);
        } finally {
            this.lightSourcesLock.writeLock().unlock();
        }
        LambDynLightsDelegate.addLightSource(lightSource);
        return lightSource;
    }

    public void removeAll(AbstractContraptionEntity contraptionEntity) {
        int entityId = contraptionEntity.getId();
        this.lightSourcesLock.writeLock().lock();
        try {
            Iterator<Map.Entry<LightSourceKey, CreateDynLightSource>> iterator = this.lightSources.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<LightSourceKey, CreateDynLightSource> entry = iterator.next();
                if (entry.getKey().entityId == entityId) {
                    removeFromLamb(entry.getValue());
                    iterator.remove();
                }
            }
        } finally {
            this.lightSourcesLock.writeLock().unlock();
        }
    }

    public CreateDynLightSource getOrCreate(AbstractContraptionEntity entity, BlockPos blockPos, int luminance) {
        return this.get(entity.getId(), blockPos).orElseGet(() -> this.create(entity, blockPos, luminance));
    }

    public Optional<CreateDynLightSource> get(int entityId, BlockPos blockPos) {
        return this.get(new LightSourceKey(entityId, blockPos));
    }

    public Optional<CreateDynLightSource> get(LightSourceKey key) {
        this.lightSourcesLock.readLock().lock();
        try {
            return Optional.ofNullable(this.lightSources.get(key));
        } finally {
            this.lightSourcesLock.readLock().unlock();
        }
    }

    public void tickAll() {
        this.lightSourcesLock.readLock().lock();
        try {
            for (CreateDynLightSource source : this.lightSources.values()) {
                LambDynLightsDelegate.tickLightSource(source);
            }
        } finally {
            this.lightSourcesLock.readLock().unlock();
        }
    }

    private static void removeFromLamb(CreateDynLightSource lightSource) {
        if (lightSource != null) {
            lightSource.markRemoved();
            LambDynLightsDelegate.removeLightSource(lightSource);
        }
    }

    public static class LightSourceKey {
        private final int entityId;
        private final BlockPos blockPos;

        public LightSourceKey(int entityId, BlockPos blockPos) {
            this.entityId = entityId;
            this.blockPos = blockPos.immutable();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o != null && this.getClass() == o.getClass()) {
                LightSourceKey that = (LightSourceKey) o;
                return this.entityId == that.entityId && Objects.equals(this.blockPos, that.blockPos);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.entityId, this.blockPos.asLong());
        }
    }
}