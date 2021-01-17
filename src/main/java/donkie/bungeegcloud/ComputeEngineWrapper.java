package donkie.bungeegcloud;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.DiskList;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

public class ComputeEngineWrapper {

    /**
     * OS image to use for the VM
     */
    private static final String OS_IMAGE = "https://www.googleapis.com/compute/v1/projects/debian-cloud/global/images/debian-10-buster-v20201216";

    /**
     * Be sure to specify the name of your application. If the application name is
     * {@code null} or blank, the application will log a warning. Suggested format
     * is "MyCompany-ProductName/1.0".
     */
    private static final String APPLICATION_NAME = "Donkie-BungeeGCloud/1.0";

    private final String projectId;

    /** Set the time out limit for operation calls to the Compute Engine API. */
    private static final long OPERATION_TIMEOUT_MILLIS = 60 * 1000;

    /** Global instance of the HTTP transport. */
    private NetHttpTransport httpTransport;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private Compute compute;
    private Logger logger;

    public ComputeEngineWrapper(Logger logger, String projectId) {
        this.projectId = projectId;
        this.logger = logger;

        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            // Authenticate using Google Application Default Credentials.
            GoogleCredentials credential = GoogleCredentials.fromStream(new FileInputStream("exhale-290316-d81df81f2dfd.json"));
            if (credential.createScopedRequired()) {
                List<String> scopes = new ArrayList<>();
                // Set Google Cloud Storage scope to Full Control.
                scopes.add(ComputeScopes.DEVSTORAGE_FULL_CONTROL);
                // Set Google Compute Engine scope to Read-write.
                scopes.add(ComputeScopes.COMPUTE);
                credential = credential.createScoped(scopes);
            }
            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credential);

            // Create Compute Engine object for listing instances.
            compute = new Compute.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                    .setApplicationName(APPLICATION_NAME).build();
/*
            // List out instances, looking for the one created by this sample app.
            boolean foundOurInstance = printInstances(compute);

            Operation op;
            if (foundOurInstance) {
                op = deleteInstance(compute, SAMPLE_INSTANCE_NAME);
            } else {
                op = startInstance(compute, SAMPLE_INSTANCE_NAME);
            }

            // Call Compute Engine API operation and poll for operation completion status
            System.out.println("Waiting for operation completion...");
            Operation.Error error = blockUntilComplete(compute, op, OPERATION_TIMEOUT_MILLIS);
            if (error == null) {
                System.out.println("Success!");
            } else {
                System.out.println(error.toPrettyString());
            }*/
        } catch (IOException e) {
            logger.warning(e.getMessage());
        } catch (Exception t) {
            logger.warning(t.toString());
        }
    }

    /**
     * Get all machine instances
     *
     * @param zoneName The zone to look in
     * @return The list of machine instances
     */
    public List<Instance> getInstances(String zoneName) throws IOException {
        Compute.Instances.List instances = compute.instances().list(projectId, zoneName);
        InstanceList list = instances.execute();
        if (list.getItems() == null) {
            return new ArrayList<>(0);
        } else {
            return list.getItems();
        }
    }

    public Instance getInstanceByName(String zoneName, String instanceName) throws IOException {
        for (Instance instance : getInstances(zoneName)) {
            if (instance.getName().equals(instanceName)) {
                return instance;
            }
        }
        throw new FileNotFoundException(String.format("Found no instance named %q in zone %q", instanceName, zoneName));
    }

    public Disk getDiskByName(String zoneName, String diskName) throws IOException {
        Compute.Disks.List disks = compute.disks().list(projectId, zoneName);
        DiskList list = disks.execute();
        if (list.getItems() != null) {
            for (Disk disk : list.getItems()) {
                if (disk.getName().equals(diskName)) {
                    return disk;
                }
            }
        }
        throw new FileNotFoundException(String.format("Found no disk named %q in zone %q", diskName, zoneName));
    }

    public Instance getInstance(String zoneName, String instanceName) throws IOException {
        Compute.Instances.Get getter = compute.instances().get(projectId, zoneName, instanceName);
        return getter.execute();
    }

    public static String getInstanceExternalIP(Instance instance) throws IOException {
        for (NetworkInterface nif : instance.getNetworkInterfaces()) {
            for (AccessConfig conf : nif.getAccessConfigs()) {
                if (conf.getType().equals("ONE_TO_ONE_NAT") && conf.getName().equals("External NAT")) {
                    return conf.getNatIP();
                }
            }
        }
        throw new FileNotFoundException("No external IP found for this instance");
    }

    public Runnable startInstance(String zoneName, String instanceName) throws IOException {
        Compute.Instances.Start starter = compute.instances().start(projectId, zoneName, instanceName);
        return () -> {
            try {
                Operation op = starter.execute();
                blockUntilComplete(op, 5 * 60 * 1000L);
            } catch (IOException e1) {
                logger.warning(e1.getMessage());
            } catch (InterruptedException e2) {
                logger.warning(e2.getMessage());
            }
        };
    }

    /*
     * public Operation createInstance(String instanceName, String machineType,
     * String zoneName, Disk gameDisk) throws IOException {
     * logger.info("Starting new instance");
     *
     * // Create VM Instance object with the required properties. Instance instance
     * = new Instance(); instance.setName(instanceName);
     * instance.setMachineType(String.format(
     * "https://www.googleapis.com/compute/v1/projects/%s/zones/%s/machineTypes/%s",
     * projectId, zoneName, machineType));
     *
     * // Add Network Interface to be used by VM Instance. NetworkInterface ifc =
     * new NetworkInterface(); ifc.setNetwork(String.format(
     * "https://www.googleapis.com/compute/v1/projects/%s/global/networks/default",
     * projectId)); AccessConfig config = new AccessConfig();
     * config.setType("ONE_TO_ONE_NAT"); config.setName("External NAT");
     * ifc.setAccessConfigs(Collections.singletonList(config));
     * instance.setNetworkInterfaces(Collections.singletonList(ifc));
     *
     * // Add attached Persistent Disk to be used by VM Instance. AttachedDisk disk
     * = new AttachedDisk(); disk.setBoot(true); disk.setAutoDelete(true);
     * disk.setType("PERSISTENT"); AttachedDiskInitializeParams params = new
     * AttachedDiskInitializeParams(); params.setDiskName(instanceName);
     * params.setSourceImage(OS_IMAGE); params.setDiskType(String.format(
     * "https://www.googleapis.com/compute/v1/projects/%s/zones/%s/diskTypes/pd-standard",
     * projectId, zoneName)); disk.setInitializeParams(params);
     *
     * List<Disk> disks = new ArrayList<>(); disks.add(disk); disks.add(gameDisk);
     * instance.setDisks(disks);
     *
     * // Initialize the service account to be used by the VM Instance and set the
     * API // access scopes. ServiceAccount account = new ServiceAccount();
     * account.setEmail("default"); List<String> scopes = new ArrayList<>();
     * scopes.add("https://www.googleapis.com/auth/devstorage.full_control");
     * scopes.add("https://www.googleapis.com/auth/compute");
     * account.setScopes(scopes);
     * instance.setServiceAccounts(Collections.singletonList(account));
     *
     * // Optional - Add a startup script to be used by the VM Instance. Metadata
     * meta = new Metadata(); Metadata.Items item = new Metadata.Items();
     * item.setKey("startup-script-url"); // If you put a script called
     * "vm-startup.sh" in this Google Cloud Storage // bucket, it will execute on VM
     * startup. This assumes you've created a // bucket named the same as your
     * PROJECT_ID. // For info on creating buckets see: //
     * https://cloud.google.com/storage/docs/cloud-console#_creatingbuckets
     * item.setValue(String.format("gs://%s/vm-startup.sh", PROJECT_ID));
     * meta.setItems(Collections.singletonList(item)); instance.setMetadata(meta);
     *
     * System.out.println(instance.toPrettyString()); Compute.Instances.Insert
     * insert = compute.instances().insert(PROJECT_ID, ZONE_NAME, instance); return
     * insert.execute(); }
     *
     * private static Operation deleteInstance(Compute compute, String instanceName)
     * throws Exception { System.out.println("================== Deleting Instance "
     * + instanceName + " =================="); Compute.Instances.Delete delete =
     * compute.instances().delete(PROJECT_ID, ZONE_NAME, instanceName); return
     * delete.execute(); }
     */
    /**
     * Wait until {@code operation} is completed.
     *
     * @param compute   the {@code Compute} object
     * @param operation the operation returned by the original request
     * @param timeout   the timeout, in millis
     * @return the error, if any, else {@code null} if there was no error
     * @throws InterruptedException if we timed out waiting for the operation to
     *                              complete
     * @throws IOException          if we had trouble connecting
     */
    public Operation.Error blockUntilComplete(Operation operation, long timeout)
            throws InterruptedException, IOException {
        long start = System.currentTimeMillis();
        final long pollInterval = 5 * 1000L;
        String zone = operation.getZone(); // null for global/regional operations
        if (zone != null) {
            String[] bits = zone.split("/");
            zone = bits[bits.length - 1];
        }
        String status = operation.getStatus();
        String opId = operation.getName();
        while (operation != null && !status.equals("DONE")) {
            Thread.sleep(pollInterval);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= timeout) {
                throw new InterruptedException("Timed out waiting for operation to complete");
            }
            System.out.println("waiting...");
            if (zone != null) {
                Compute.ZoneOperations.Get get = compute.zoneOperations().get(projectId, zone, opId);
                operation = get.execute();
            } else {
                Compute.GlobalOperations.Get get = compute.globalOperations().get(projectId, opId);
                operation = get.execute();
            }
            if (operation != null) {
                status = operation.getStatus();
            }
        }
        return operation == null ? null : operation.getError();
    }
}
