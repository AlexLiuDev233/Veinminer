package me.alexliudev.veinminer;

import me.alexliudev.veinminer.commands.ReloadCommand;
import me.alexliudev.veinminer.events.PlayerBreakBlock;
import org.bukkit.Bukkit;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Veinminer extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(new PlayerBreakBlock(), this);

        ReloadCommand command = new ReloadCommand();
        getCommand("veinminer").setExecutor(command);
        getCommand("veinminer").setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        BlockBreakEvent.getHandlerList().unregister(this);
    }
}
