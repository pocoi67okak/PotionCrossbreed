package com.potioncraft.potioncrossbreed;

import org.bukkit.plugin.java.JavaPlugin;

public class PotionCrossbreed extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
        getLogger().info("PotionCrossbreed загружен! Скрещивайте зелья на наковальне.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PotionCrossbreed выключен.");
    }
}
