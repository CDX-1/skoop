package rip.cdx.skoop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Skoop extends JavaPlugin {

    @Override
    public void onEnable() {
        Bukkit.getLogger().info("Skoop is running!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
