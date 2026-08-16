package rip.cdx.skoop;

import ch.njol.skript.Skript;
import com.github.shanebeee.skr.Registration;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import rip.cdx.skoop.core.SkoopClassRegistry;

public final class Skoop extends JavaPlugin {

    @Getter
    private final SkoopClassRegistry classRegistry = new SkoopClassRegistry();

    @Getter
    private static Skoop instance;

    @Override
    public void onEnable() {
        instance = this;
        Plugin skript = getServer().getPluginManager().getPlugin("Skript");

        if (skript == null) {
            getLogger().severe("Could not find Skript!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else if (!skript.isEnabled()) {
            getLogger().severe("Skript is not enabled!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else if (!Skript.isAcceptRegistrations()) {
            getLogger().severe("Skript not accepting registrations!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Registration reg = new Registration("skoop", true);
        SkoopLoader.register(reg);
    }

}
