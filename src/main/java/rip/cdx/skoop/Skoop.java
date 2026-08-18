package rip.cdx.skoop;

import ch.njol.skript.Skript;
import com.github.shanebeee.skr.Registration;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import rip.cdx.skoop.core.SkoopClassRegistry;

public final class Skoop extends JavaPlugin {

    @Getter
    private static Skoop instance;

    @Getter
    private final SkoopClassRegistry classRegistry = new SkoopClassRegistry();

    @Override
    public void onEnable() {
        instance = this;

        if (!canHookIntoSkript()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SkoopLoader.register(new Registration("skoop", true));
    }

    /**
     * Deliberately leaves the instance and the class registry standing.
     * <p>
     * Skript writes its variables to disk <em>after</em> plugins are disabled, and serializing a
     * Skoop object has to resolve its class — and walk its superclasses — to know which fields to
     * write. Tearing that down here would either fail the save outright or, worse, quietly write
     * the object back without its inherited fields. Nothing outlives the JVM either way: the
     * registry is per-instance, and a re-enable rebuilds it from the scripts that load.
     */
    @Override
    public void onDisable() {
    }

    private boolean canHookIntoSkript() {
        Plugin skript = getServer().getPluginManager().getPlugin("Skript");

        if (skript == null) {
            getLogger().severe("Could not find Skript!");
            return false;
        }

        if (!skript.isEnabled()) {
            getLogger().severe("Skript is not enabled!");
            return false;
        }

        if (!Skript.isAcceptRegistrations()) {
            getLogger().severe("Skript is no longer accepting registrations!");
            return false;
        }

        return true;
    }
}
