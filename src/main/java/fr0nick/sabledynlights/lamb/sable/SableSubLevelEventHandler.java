package fr0nick.sabledynlights.lamb.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import fr0nick.sabledynlights.SableDynLightsMod;
import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SableSubLevelEventHandler {
    private static long lastScanMillis;
    private static boolean lastEnabled = true;
    private static Level lastLevel;

    private SableSubLevelEventHandler() {}

    public static void onTick(Level level) {
        if (level.isClientSide()) {
            boolean enabled = LambDynLightsDelegate.isAvailable() && SableReflection.isSableLoaded();
            if (!enabled) {
                if (lastEnabled) {
                    SableDynLightSourceHolder.INSTANCE.clear();
                }
                lastEnabled = false;
            } else {
                if (!lastEnabled) {
                    lastScanMillis = 0L;
                    lastEnabled = true;
                }

                if (lastLevel != level) {
                    SableDynLightSourceHolder.INSTANCE.clear();
                    lastLevel = level;
                    lastScanMillis = 0L;
                }

                long now = System.currentTimeMillis();
                int interval = 500; 
                if (lastScanMillis != 0L && now < lastScanMillis + interval) {
                    SableDynLightSourceHolder.INSTANCE.tickAll();
                } else {
                    lastScanMillis = now;
                    Optional<Object> container = SableReflection.getContainer(level);
                    if (container.isEmpty()) {
                        SableDynLightSourceHolder.INSTANCE.clear();
                    } else {
                        Set<UUID> loadedSubLevels = new HashSet<>();

                        for (Object subLevel : SableReflection.getAllSubLevels(container.get())) {
                            if (subLevel != null && !SableReflection.isSubLevelRemoved(subLevel)) {
                                UUID subLevelId = SableReflection.getSubLevelId(subLevel);
                                if (subLevelId != null) {
                                    loadedSubLevels.add(subLevelId);
                                    List<SableLightCandidate> candidates = scanLightSources(subLevel);
                                    SableDynLightSourceHolder.INSTANCE.syncSubLevel(level, subLevel, subLevelId, candidates);
                                }
                            }
                        }

                        SableDynLightSourceHolder.INSTANCE.removeMissingSubLevels(loadedSubLevels);
                        SableDynLightSourceHolder.INSTANCE.tickAll();
                    }
                }
            }
        }
    }

    private static List<SableLightCandidate> scanLightSources(Object subLevel) {
        int maxSources = 2048; 
        int lowerLimit = 1; 
        List<SableLightCandidate> candidates = new ArrayList<>();

        for (Object holder : SableReflection.getLoadedChunkHolders(subLevel)) {
            LevelChunk chunk = SableReflection.getChunk(holder);
            if (chunk != null) {
                scanChunk(chunk, candidates, lowerLimit, maxSources);
                if (candidates.size() >= maxSources) {
                    break;
                }
            }
        }

        return candidates;
    }

    private static void scanChunk(LevelChunk chunk, List<SableLightCandidate> candidates, int lowerLimit, int maxSources) {
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int sectionIndex = 0; sectionIndex < sections.length; ++sectionIndex) {
            LevelChunkSection section = sections[sectionIndex];
            if (section != null && !section.hasOnlyAir()) {
                int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;

                for (int x = 0; x < 16; ++x) {
                    for (int y = 0; y < 16; ++y) {
                        for (int z = 0; z < 16; ++z) {
                            BlockState state = section.getBlockState(x, y, z);
                            int luminance = state.getLightEmission();
                            if (luminance >= lowerLimit) {
                                pos.set(chunkPos.getMinBlockX() + x, sectionMinY + y, chunkPos.getMinBlockZ() + z);
                                candidates.add(new SableLightCandidate(pos.immutable(), luminance));
                                if (candidates.size() >= maxSources) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}