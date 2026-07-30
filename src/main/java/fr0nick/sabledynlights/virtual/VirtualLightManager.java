package fr0nick.sabledynlights.virtual;

import dev.lambdaurora.lambdynlights.DynamicLightSource;
import fr0nick.sabledynlights.lamb.AbstractLambDynamicLightSource;
import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks "virtual" light-emitting positions inside the shared client {@link LevelLightEngine}.
 * <p>
 * A virtual position doesn't hold a real light-emitting block. Instead:
 * <ul>
 *     <li>{@code BlockLightEngineEmissionMixin} reports an emission value for it whenever the vanilla block light
 *     engine's BFS propagation asks for the emission at that position, and</li>
 *     <li>{@link LevelLightEngine#checkBlock(BlockPos)} is used to make the engine (re)compute real propagation
 *     from/through that position, exactly as if a real light-emitting block had been placed/removed there.</li>
 * </ul>
 * Because this goes through the real light engine, falloff and occlusion (walls, floors, etc.) are handled
 * correctly "for free" by vanilla's own BFS, and the resulting light values get baked into chunk meshes normally
 * the next time the affected section rebuilds.
 * <p>
 * Sable sub-level plots share the exact same {@link LevelLightEngine} instance as the main level (their chunks
 * just live at unusual "plot grid" coordinates), so this same mechanism transparently lights the interior of
 * sub-levels too &mdash; {@link VirtualSableLightManager} feeds it positions that have already been transformed
 * into that coordinate space via each sub-level's pose.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class VirtualLightManager {
    /** Positions contributed directly by nearby world-space dynamic lights (main world / Create case). */
    private static final Map<BlockPos, Integer> worldPositions = new HashMap<>();

    /**
     * Positions contributed by other callers (currently: sub-level local-space transforms from
     * {@link VirtualSableLightManager}), grouped by an opaque owner key so each contributor's positions can be
     * diffed and cleared independently without stepping on one another.
     */
    private static final Map<Object, Map<BlockPos, Integer>> externalPositions = new HashMap<>();

    private VirtualLightManager() {}

    /**
     * @return the virtual emission that should apply at {@code pos} (0-15), or 0 if none is active there.
     */
    public static int getVirtualEmission(BlockPos pos) {
        if (pos == null) return 0;
        int max = worldPositions.getOrDefault(pos, 0);
        if (!externalPositions.isEmpty()) {
            for (Map<BlockPos, Integer> group : externalPositions.values()) {
                Integer v = group.get(pos);
                if (v != null && v > max) max = v;
            }
        }
        return max;
    }

    /**
     * Self-contained tick: collects active lights itself. Used by call sites that don't already have a candidate
     * list handy (e.g. when a contraption entity joins the world outside the main tick loop).
     */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        onClientTick(collectActiveLightCandidates(mc));
    }

    /**
     * Same as {@link #onClientTick()}, but reuses a candidate list already collected by the caller (see
     * {@code LambModEventHandler}, which collects it once per tick and shares it with
     * {@link VirtualSableLightManager} too) instead of re-scanning LambDynamicLights' active sources again.
     */
    public static void onClientTick(List<LightCandidate> candidates) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        LevelLightEngine worldLightEngine = level.getLightEngine();
        if (worldLightEngine == null) return;

        Map<BlockPos, Integer> current = new HashMap<>();
        for (LightCandidate candidate : candidates) {
            current.merge(candidate.pos(), candidate.luminance(), Math::max);
        }
        applyDiff(worldPositions, current, worldLightEngine);

        worldPositions.clear();
        worldPositions.putAll(current);
    }

    /**
     * Called by other contributors (currently just {@link VirtualSableLightManager}) to publish the set of
     * positions - in the shared light-engine coordinate space - that should currently act as virtual light
     * sources on their behalf. Diffed internally against what that same owner published last time.
     */
    public static void syncExternalPositions(Object owner, Map<BlockPos, Integer> positions) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        LevelLightEngine lightEngine = level.getLightEngine();
        if (lightEngine == null) return;

        Map<BlockPos, Integer> previous = externalPositions.computeIfAbsent(owner, k -> new HashMap<>());
        applyDiff(previous, positions, lightEngine);

        if (positions.isEmpty()) {
            externalPositions.remove(owner);
        } else {
            previous.clear();
            previous.putAll(positions);
        }
    }

    /**
     * Removes every position previously contributed by {@code owner} (e.g. when a sub-level unloads), letting
     * the light engine relax back down at those positions.
     */
    public static void clearExternalPositions(Object owner) {
        Map<BlockPos, Integer> previous = externalPositions.remove(owner);
        if (previous == null || previous.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LevelLightEngine lightEngine = level != null ? level.getLightEngine() : null;
        if (lightEngine == null) return;

        for (BlockPos pos : previous.keySet()) {
            try { lightEngine.checkBlock(pos); } catch (Throwable ignored) {}
        }
    }

    public static void clearAll() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LevelLightEngine lightEngine = level != null ? level.getLightEngine() : null;

        if (lightEngine != null) {
            for (BlockPos pos : worldPositions.keySet()) {
                try { lightEngine.checkBlock(pos); } catch (Throwable ignored) {}
            }
            for (Map<BlockPos, Integer> group : externalPositions.values()) {
                for (BlockPos pos : group.keySet()) {
                    try { lightEngine.checkBlock(pos); } catch (Throwable ignored) {}
                }
            }
        }

        worldPositions.clear();
        externalPositions.clear();
    }

    /**
     * Calls {@link LevelLightEngine#checkBlock} for every position whose presence or luminance differs between
     * {@code previous} and {@code current}. Does not itself mutate {@code previous}; callers commit the new
     * state afterward.
     */
    private static void applyDiff(Map<BlockPos, Integer> previous, Map<BlockPos, Integer> current, LevelLightEngine lightEngine) {
        for (Map.Entry<BlockPos, Integer> entry : current.entrySet()) {
            Integer old = previous.get(entry.getKey());
            if (!entry.getValue().equals(old)) {
                try { lightEngine.checkBlock(entry.getKey()); } catch (Throwable ignored) {}
            }
        }
        for (BlockPos pos : previous.keySet()) {
            if (!current.containsKey(pos)) {
                try { lightEngine.checkBlock(pos); } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * A single active dynamic light candidate: its current world-space position, luminance, and (if known) the
     * entity it's tied to. The entity is used by {@link VirtualSableLightManager} to check whether the position is
     * already local to a Sable sub-level's plot (as opposed to a normal world position) - see that class' docs.
     */
    public record LightCandidate(BlockPos pos, int luminance, Entity entity) {}

    /**
     * Collects every currently active dynamic light: LambDynamicLights sources (including this mod's own
     * outward-projected Create/Sable lights - a moving contraption or another sub-level's light is a perfectly
     * legitimate light source for a nearby sub-level too) plus the local player's held-item light. Shared between
     * the main-world tracker below and {@link VirtualSableLightManager}.
     */
    public static List<LightCandidate> collectActiveLightCandidates(Minecraft mc) {
        List<LightCandidate> current = new ArrayList<>();

        if (LambDynLightsDelegate.isAvailable()) {
            Collection<?> sources = LambDynLightsDelegate.getActiveSources();
            if (sources != null && !sources.isEmpty()) {
                for (Object obj : sources) {
                    if (obj instanceof DynamicLightSource source && source.isDynamicLightEnabled() && source.getLuminance() > 0) {
                        BlockPos pos = BlockPos.containing(source.getDynamicLightX(), source.getDynamicLightY(), source.getDynamicLightZ());
                        current.add(new LightCandidate(pos, source.getLuminance(), resolveEntity(obj)));
                    }
                }
            }
        }

        if (mc.player != null) {
            int pLight = Math.max(getLightEmission(mc.player.getMainHandItem()), getLightEmission(mc.player.getOffhandItem()));
            if (pLight > 0) {
                current.add(new LightCandidate(mc.player.blockPosition().above(), pLight, mc.player));
            }
        }

        return current;
    }

    /**
     * @return the entity backing this light source, if any: LambDynamicLights represents players/mobs/item
     * entities by mixing its behavior directly into the {@link Entity} itself (so the source object IS the
     * entity), while this mod's own {@link AbstractLambDynamicLightSource}s are separate objects that expose their
     * backing entity (if any) via {@link AbstractLambDynamicLightSource#getTrackedEntity()}.
     */
    private static Entity resolveEntity(Object source) {
        if (source instanceof Entity entity) return entity;
        if (source instanceof AbstractLambDynamicLightSource abstractSource) return abstractSource.getTrackedEntity();
        return null;
    }

    private static int getLightEmission(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getLightEmission();
        }
        return 0;
    }
}
