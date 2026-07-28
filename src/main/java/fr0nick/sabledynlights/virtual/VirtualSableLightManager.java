package fr0nick.sabledynlights.virtual;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges light between the main world and the interior of Sable sub-levels, in both directions.
 * <p>
 * Sable sub-level plots are stored as real chunks that share the main level's own {@code LevelLightEngine} - they
 * just live at unusual "plot grid" chunk coordinates and are rendered through their own {@code SubLevelRenderData}
 * pipeline. Sable's own {@code ViewAreaMixin}/{@code SodiumWorldRendererMixin} redirect vanilla's normal
 * section-dirty notifications to that pipeline whenever a position falls inside a plot - which means the exact
 * same {@link VirtualLightManager#syncExternalPositions} trick already used for the main world (see that class'
 * docs) also correctly lights sub-level interiors, once each candidate world-space light position has been
 * transformed into the target sub-level's local/plot coordinate space via its pose - and, in reverse, lights the
 * main world once a sub-level-local position has been transformed back out via that same pose.
 * <p>
 * Because propagation runs through the real light engine, this respects real occlusion: light held/placed just
 * outside a solid wall will <em>not</em> bleed through it, but will correctly shine in through an open door or
 * window, exactly like a real block light source would.
 * <p>
 * Two independent sources of candidate light feed the same mechanism:
 * <ul>
 *     <li><b>Dynamic</b> (players, mobs, held/dropped glowing items, moving Create contraptions, other sub-levels'
 *     own outward-projected light): re-scanned every tick, from the small list of currently active
 *     LambDynamicLights sources, so moving lights track smoothly. For sources tied to an entity, we first check
 *     whether Sable itself considers that entity "contained" inside some sub-level's plot (via
 *     {@code SableCompanion.getContaining(Entity)}) - if so, its position is already expressed in that sub-level's
 *     own local/plot coordinate space (not a normal world position): it lights that sub-level's interior directly
 *     (no search/transform needed - searching by "nearby normal-world position" would find nothing, since
 *     plot-grid coordinates don't correspond to any real place in the world), <em>and</em> gets bridged back out
 *     to the main world at wherever that sub-level's pose currently places it visually. This matters for Create
 *     contraptions specifically: Create has no awareness of Sable's plot grid, so a contraption assembled while
 *     its anchor is inside a sub-level ends up with its tracked position already in that sub-level's local space
 *     (mirroring how Sable's own {@code EntityRendererMixin} treats "contained" entities' positions) - and
 *     {@code SableSubLevelEventHandler}'s own outward-projection system (which is what normally makes a
 *     sub-level's contents visible from outside) can only scan real placed chunk blocks, so it has no way to see
 *     a contraption's light at all (a contraption's "blocks" only exist as in-memory structure data on the
 *     entity, never as real chunk data) - this covers that gap.</li>
 *     <li><b>Static/ambient</b> (torches, lanterns, lava, glowstone, ... placed in the world): these aren't
 *     tracked by LambDynamicLights at all (it only follows things that move), so instead we sample the real,
 *     already-computed block light value right at each nearby sub-level's exterior boundary. Since vanilla's own
 *     light engine already did the work of computing that value (including falloff and occlusion), we only need
 *     to read it, not search for the source - so this only touches the thin outer shell of each sub-level's
 *     bounding box, not the volume around it. This is throttled since placed blocks don't move.</li>
 * </ul>
 * This class only depends on the lightweight, MIT-licensed "Sable Companion" library (bundled with this mod via
 * Jar-in-Jar), never on Sable itself - so it becomes a complete no-op when Sable isn't installed, without needing
 * any {@code ModList}/reflection guards of our own.
 */
public final class VirtualSableLightManager {
    /**
     * Margin (in addition to a light's own luminance) added to the dynamic-light query bounding box, to avoid
     * missing a sub-level poking just past the theoretical maximum light radius due to rounding at its edges.
     */
    private static final double QUERY_MARGIN = 1.0;

    /** How often (ms) to rescan static/ambient world light at each sub-level's boundary. Static blocks don't move. */
    private static final long STATIC_SCAN_INTERVAL_MILLIS = 500L;

    /** How far (blocks) around the player to look for sub-levels when refreshing the static-light cache. */
    private static final double STATIC_SCAN_ENUMERATION_RADIUS = 192.0;

    /** How far past a sub-level's own bounding box to sample for static/ambient light. */
    private static final int SHELL_MARGIN = 1;

    /** Safety cap on brightness samples per sub-level per static-light rescan, in case of a pathologically large one. */
    private static final int MAX_SHELL_SAMPLES_PER_SUBLEVEL = 4096;

    /** UUIDs of sub-levels we contributed virtual light positions for last tick. */
    private static final Set<UUID> activeOwners = new HashSet<>();

    /**
     * Opaque owner key used with {@link VirtualLightManager#syncExternalPositions} for light bridged from
     * "contained" entities back out to the main world (see {@link #collectDynamicLight}) - distinct from the
     * per-sub-level UUID keys so the two don't collide.
     */
    private static final Object WORLD_BRIDGE_OWNER = new Object();

    /** Cached result of the last static/ambient boundary scan, reused every tick until the next throttled refresh. */
    private static Map<UUID, Map<BlockPos, Integer>> staticLightCache = new HashMap<>();
    private static long lastStaticScanMillis = 0L;

    private VirtualSableLightManager() {}

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        // local position -> luminance, per sub-level UUID; combines both light sources below.
        Map<UUID, Map<BlockPos, Integer>> perSubLevel = new HashMap<>();
        // world-space positions bridged back out from "contained" entities (see collectDynamicLight).
        Map<BlockPos, Integer> worldBridgePositions = new HashMap<>();

        collectDynamicLight(level, mc, perSubLevel, worldBridgePositions);
        VirtualLightManager.syncExternalPositions(WORLD_BRIDGE_OWNER, worldBridgePositions);

        refreshStaticCacheIfDue(level, mc);
        for (Map.Entry<UUID, Map<BlockPos, Integer>> entry : staticLightCache.entrySet()) {
            Map<BlockPos, Integer> target = perSubLevel.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
            for (Map.Entry<BlockPos, Integer> e : entry.getValue().entrySet()) {
                target.merge(e.getKey(), e.getValue(), Math::max);
            }
        }

        for (Map.Entry<UUID, Map<BlockPos, Integer>> entry : perSubLevel.entrySet()) {
            VirtualLightManager.syncExternalPositions(entry.getKey(), entry.getValue());
        }

        for (UUID previousOwner : activeOwners) {
            if (!perSubLevel.containsKey(previousOwner)) {
                VirtualLightManager.clearExternalPositions(previousOwner);
            }
        }

        activeOwners.clear();
        activeOwners.addAll(perSubLevel.keySet());
    }

    public static void clearAll() {
        if (!activeOwners.isEmpty()) {
            for (UUID owner : activeOwners) {
                VirtualLightManager.clearExternalPositions(owner);
            }
            activeOwners.clear();
        }
        VirtualLightManager.clearExternalPositions(WORLD_BRIDGE_OWNER);
        staticLightCache = new HashMap<>();
        lastStaticScanMillis = 0L;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Dynamic light: players, mobs, held/dropped items, contraptions, other sub-levels' outward light. Cheap
    // (bounded by the small number of currently active dynamic lights), so this runs fresh every tick.
    // ---------------------------------------------------------------------------------------------------------

    private static void collectDynamicLight(ClientLevel level, Minecraft mc, Map<UUID, Map<BlockPos, Integer>> perSubLevel,
                                             Map<BlockPos, Integer> worldBridgePositions) {
        List<VirtualLightManager.LightCandidate> candidates = VirtualLightManager.collectActiveLightCandidates(mc);
        if (candidates.isEmpty()) return;

        for (VirtualLightManager.LightCandidate candidate : candidates) {
            BlockPos pos = candidate.pos();
            int luminance = candidate.luminance();

            // If this light is tied to an entity that Sable itself considers "contained" inside a sub-level's
            // plot (e.g. a Create contraption assembled while inside one - Create has no awareness of Sable's
            // plot grid, so such a contraption's tracked position ends up expressed in that sub-level's own
            // local/plot coordinate space, not a normal world position, exactly like the entity's own position
            // per Sable's EntityRendererMixin), the position is *already* local to that specific sub-level: no
            // coordinate search or transform needed, and searching by "nearby normal-world position" would find
            // nothing (or something unrelated), since plot-grid coordinates don't correspond to any real
            // location in the world. Route it straight there instead.
            SubLevelAccess containingSubLevel = null;
            if (candidate.entity() != null) {
                try {
                    containingSubLevel = SableCompanion.INSTANCE.getContaining(candidate.entity());
                } catch (Throwable ignored) {}
            }

            if (containingSubLevel != null) {
                perSubLevel.computeIfAbsent(containingSubLevel.getUniqueId(), k -> new HashMap<>())
                        .merge(pos, luminance, Math::max);

                // Also bridge this light back OUT to the main world, at wherever the sub-level's pose places it
                // visually - mirroring what SableSubLevelEventHandler's outward-projection already does for real
                // placed blocks inside a sub-level. That system can only scan real chunk block data though, so it
                // never sees a Create contraption's light (the contraption's "blocks" only exist as in-memory
                // structure data on the entity, not as real placed blocks) - this covers that gap, and also makes
                // e.g. a player's held torch visible from outside a sub-level's window while they're inside it.
                try {
                    Vec3 worldVec = containingSubLevel.logicalPose().transformPosition(Vec3.atCenterOf(pos));
                    worldBridgePositions.merge(BlockPos.containing(worldVec), luminance, Math::max);
                } catch (Throwable ignored) {}

                continue;
            }

            BoundingBox3d queryBox = new BoundingBox3d(pos).expand(luminance + QUERY_MARGIN);

            Iterable<? extends SubLevelAccess> subLevels;
            try {
                subLevels = SableCompanion.INSTANCE.getAllIntersecting(level, queryBox);
            } catch (Throwable ignored) {
                continue;
            }
            if (subLevels == null) continue;

            for (SubLevelAccess subLevel : subLevels) {
                BlockPos localPos;
                try {
                    Vec3 localVec = subLevel.logicalPose().transformPositionInverse(Vec3.atCenterOf(pos));
                    localPos = BlockPos.containing(localVec);
                } catch (Throwable ignored) {
                    continue;
                }
                perSubLevel.computeIfAbsent(subLevel.getUniqueId(), k -> new HashMap<>()).merge(localPos, luminance, Math::max);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Static/ambient light: sampled from the real, already-computed block light at each nearby sub-level's
    // exterior boundary shell (not the volume around it), throttled since placed blocks don't move on their own.
    // ---------------------------------------------------------------------------------------------------------

    private static void refreshStaticCacheIfDue(ClientLevel level, Minecraft mc) {
        long now = System.currentTimeMillis();
        if (lastStaticScanMillis != 0L && now < lastStaticScanMillis + STATIC_SCAN_INTERVAL_MILLIS) return;
        lastStaticScanMillis = now;

        if (mc.player == null) {
            staticLightCache = new HashMap<>();
            return;
        }

        BoundingBox3d enumerationBox = new BoundingBox3d(mc.player.blockPosition()).expand(STATIC_SCAN_ENUMERATION_RADIUS);

        Iterable<? extends SubLevelAccess> nearbySubLevels;
        try {
            nearbySubLevels = SableCompanion.INSTANCE.getAllIntersecting(level, enumerationBox);
        } catch (Throwable ignored) {
            return;
        }
        if (nearbySubLevels == null) return;

        Map<UUID, Map<BlockPos, Integer>> newCache = new HashMap<>();
        for (SubLevelAccess subLevel : nearbySubLevels) {
            Map<BlockPos, Integer> found = scanExteriorShell(level, subLevel);
            if (!found.isEmpty()) {
                newCache.put(subLevel.getUniqueId(), found);
            }
        }
        staticLightCache = newCache;
    }

    private static Map<BlockPos, Integer> scanExteriorShell(ClientLevel level, SubLevelAccess subLevel) {
        Map<BlockPos, Integer> found = new HashMap<>();

        BoundingBox3dc box;
        Pose3dc pose;
        try {
            box = subLevel.boundingBox();
            pose = subLevel.logicalPose();
        } catch (Throwable ignored) {
            return found;
        }
        if (box == null || pose == null) return found;

        int minX = (int) Math.floor(box.minX()) - SHELL_MARGIN;
        int minY = (int) Math.floor(box.minY()) - SHELL_MARGIN;
        int minZ = (int) Math.floor(box.minZ()) - SHELL_MARGIN;
        int maxX = (int) Math.floor(box.maxX()) + SHELL_MARGIN;
        int maxY = (int) Math.floor(box.maxY()) + SHELL_MARGIN;
        int maxZ = (int) Math.floor(box.maxZ()) + SHELL_MARGIN;

        int[] budget = {MAX_SHELL_SAMPLES_PER_SUBLEVEL};

        // Only the 6 outer faces of the (margin-expanded) box are sampled - not the volume inside it - since the
        // real light engine has already done the work of computing correct falloff/occlusion up to that boundary.
        scanFace(level, pose, found, budget, minX, minX, minY, maxY, minZ, maxZ);
        scanFace(level, pose, found, budget, maxX, maxX, minY, maxY, minZ, maxZ);
        scanFace(level, pose, found, budget, minX, maxX, minY, minY, minZ, maxZ);
        scanFace(level, pose, found, budget, minX, maxX, maxY, maxY, minZ, maxZ);
        scanFace(level, pose, found, budget, minX, maxX, minY, maxY, minZ, minZ);
        scanFace(level, pose, found, budget, minX, maxX, minY, maxY, maxZ, maxZ);

        return found;
    }

    private static void scanFace(ClientLevel level, Pose3dc pose, Map<BlockPos, Integer> found, int[] budget,
                                  int x0, int x1, int y0, int y1, int z0, int z1) {
        if (budget[0] <= 0) return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos columnCheck = new BlockPos.MutableBlockPos();

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                // Cheap, non-loading check: skip columns that aren't actually loaded rather than forcing them in.
                if (!level.hasChunkAt(columnCheck.set(x, y0, z))) continue;

                for (int y = y0; y <= y1; y++) {
                    if (budget[0]-- <= 0) return;

                    cursor.set(x, y, z);
                    int brightness = level.getBrightness(LightLayer.BLOCK, cursor);
                    if (brightness > 0) {
                        try {
                            Vec3 localVec = pose.transformPositionInverse(Vec3.atCenterOf(cursor));
                            found.merge(BlockPos.containing(localVec), brightness, Math::max);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }
}
