package donkie.bungeegcloud;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.api.services.compute.model.Instance;

import net.md_5.bungee.config.Configuration;

/**
 * Represents a Google Cloud Compute instance
 */
public class ComputeInstance implements Machine {

    /** The instance name */
    private String name;

    /** The instance zone */
    private String zone;

    /** The boot disk associated with the instance */
    private String diskName;

    /** The size of the boot disk associated with the instance */
    private long diskSizeGb;

    /** The external IP address of the instance */
    private String ip = null;

    /** Whether the instance is running or not */
    private boolean running = false;

    /** The compute instance used to interact with the Cloud Compute API */
    private ComputeEngineWrapper compute;

    /**
     * Synchronously grabs required configs from the Configuration object, initializes the Cloud Compute service API and fetches the zone.
     * @param computeConfig The configuration section or file containing Cloud Compute related configs
     * @param dataFolder The data folder of the plugin
     * @throws IOException Thrown if the Google Cloud credentials file is missing, if there was any issue initializing the Cloud Compute service API or if the zone couldn't be fetched.
     * @throws GeneralSecurityException
     */
    public ComputeInstance(Configuration computeConfig, File dataFolder)
            throws IOException, GeneralSecurityException {
        String projectId = computeConfig.getString("project_id");
        this.name = computeConfig.getString("instance_id");
        this.diskName = computeConfig.getString("disk_name");
        this.diskSizeGb = computeConfig.getLong("disk_size_gb");
        this.compute = new ComputeEngineWrapper(projectId, new File(dataFolder, "credentials.json"));

        fetchZone();
    }

    /**
     * Synchronously fetches the Cloud Compute service API Instance connected to this object.
     * @return The Instance object
     * @throws IOException
     */
    private Instance fetchInstance() throws IOException {
        return compute.getInstance(zone, name);
    }

    /**
     * Synchronously fetches the zone id of this instance
     * @throws IOException
     */
    private void fetchZone() throws IOException {
        zone = compute.getInstanceZone(name);
    }

    /**
     * Synchronously fetches the running status and IP of this instance
     * @throws IOException
     */
    @Override
    public void updateRunningStatus() throws IOException {
        Instance instance = fetchInstance();
        running = ComputeEngineWrapper.isRunning(instance);
        if (running && ip == null) {
            // This can happen if the instance is started by something else, that means we need to fetch the ip now
            ip = ComputeEngineWrapper.getExternalIP(instance);
        }
        else if (!running) {
            ip = null;
        }
    }

    /**
     * Returns whether the instance is running or not
     * @return Is running
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the name of the instance
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the zone id of the instance
     * @return
     */
    public String getZone() {
        return zone;
    }

    /**
     * Returns the external IP of the instance
     * @throws NotOnlineException Thrown if the server is not running, or if we haven't fetched any IP yet
     */
    @Override
    public String getIp() throws NotOnlineException {
        if (ip == null || !running) {
            throw new NotOnlineException();
        }
        return ip;
    }

    /**
     * Synchronously starts the instance. Updates the running status and external IP once its started.
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException Thrown by the API if there was any issue starting the instance
     * @throws MachinePoolExhaustedException Thrown if the instance couldn't be started cause there isn't enough resources in the pool
     */
    @Override
    public void start() throws IOException, InterruptedException, ServiceException, MachinePoolExhaustedException {
        compute.wakeInstanceDisk(zone, name, diskName, diskSizeGb);
        compute.startInstance(zone, name);
        ip = ComputeEngineWrapper.getExternalIP(fetchInstance());
        running = true;
    }

    /**
     * Synchronously stops the instance.
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException Thrown by the API if there was any issue stopping the instance
     */
    @Override
    public void stop() throws IOException, InterruptedException, ServiceException {
        running = false;
        ip = null;
        compute.stopInstance(zone, name);
        compute.sleepInstanceDisk(zone, name, diskName);
    }
}
