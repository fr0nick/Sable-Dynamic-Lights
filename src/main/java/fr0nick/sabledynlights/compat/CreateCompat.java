package fr0nick.sabledynlights.compat;

import net.neoforged.fml.ModList;

/**
 * The single gate guarding every Create-typed code path in this mod.
 * <p>
 * Create is an <em>optional</em> dependency (see {@code neoforge.mods.toml}): contraption lighting works when it
 * is installed, and the mod loads normally when it isn't. That is possible because no class holding a
 * compile-time reference to a Create type is ever <em>reached</em> unless {@link #LOADED} is true - the JVM
 * resolves the owner of a static call lazily, at the first execution of that call site, so a branch that never
 * runs never triggers the class load that would fail with Create absent.
 * <p>
 * The practical rule: anything in {@code fr0nick.sabledynlights.lamb.create} may only be touched from inside an
 * {@code if (CreateCompat.LOADED)} branch, and the class containing that branch must itself stay free of Create
 * types (including in method signatures, which the bytecode verifier <em>does</em> eagerly resolve).
 */
public final class CreateCompat {
    /** Whether Create is present in this instance. Resolved once, on first access to this class. */
    public static final boolean LOADED = ModList.get().isLoaded("create");

    private CreateCompat() {}
}
