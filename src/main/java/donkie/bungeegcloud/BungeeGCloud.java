package donkie.bungeegcloud;

import net.md_5.bungee.api.plugin.Plugin;

public class BungeeGCloud extends Plugin {
    @Override
    public void onEnable() {
        // You should not put an enable message in your plugin.
        // BungeeCord already does so
        getLogger().info("BungeeGClud onEnable");

        getProxy().getPluginManager().registerListener(this, new Events(this));
    }

}
