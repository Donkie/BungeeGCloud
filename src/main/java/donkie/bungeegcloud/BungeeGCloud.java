package donkie.bungeegcloud;

import java.io.IOException;

import com.google.api.services.compute.model.Instance;

import net.md_5.bungee.api.plugin.Plugin;

public class BungeeGCloud extends Plugin {
    private ComputeEngineWrapper compute;

    private static final String PROJECT_ID = "exhale-290316";
    private static final String ZONE_NAME = "europe-west2-c";
    private static final String INSTANCE_NAME = "minecraft-1";
    private static final int SERVER_PORT = 25565;

    @Override
    public void onEnable() {
        // You should not put an enable message in your plugin.
        // BungeeCord already does so
        getLogger().info("BungeeGClud onEnable");

        getProxy().getPluginManager().registerListener(this, new Events(this));

        compute = new ComputeEngineWrapper(getLogger(), PROJECT_ID);
    }

    public boolean isInstanceRunning() throws IOException {
        Instance instance = compute.getInstance(ZONE_NAME, INSTANCE_NAME);
        String status = instance.getStatus();
        return !(status.equals("STOPPING") || status.equals("SUSPENDING") || status.equals("SUSPENDED")
                || status.equals("TERMINATED"));
    }

    public void startInstance() throws IOException {
        compute.startInstance(ZONE_NAME, INSTANCE_NAME).run();
    }

    public String getInstanceIP() throws IOException {
        Instance instance = compute.getInstance(ZONE_NAME, INSTANCE_NAME);
        return ComputeEngineWrapper.getInstanceExternalIP(instance);
    }

    public int getServerPort() {
        return SERVER_PORT;
    }
}
