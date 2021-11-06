package donkie.bungeegcloud;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Instances.AggregatedList;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceAggregatedList;
import com.google.api.services.compute.model.InstancesScopedList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Operation.Error;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.DiskList;
import com.google.api.services.compute.model.Snapshot;
import com.google.api.services.compute.model.SnapshotList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

/**
 * Contains methods to easily interact with Google Compute Engine's API
 */
public class ComputeEngineWrapper {

    /** Name of the application, used for the user agent in API requests */
    private static final String APPLICATION_NAME = "Donkie-BungeeGCloud/1.0";

    /** The GCloud project id */
    private final String projectId;

    /** Global instance of the HTTP transport. */
    private NetHttpTransport httpTransport;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /** Cloud Compute service interface */
    private Compute compute;

    /**
     * Initializes the API with the credentialsFile for authorization
     *
     * @param projectId       The project id, such as "test-123456"
     * @param credentialsFile File handle to the json file containing credentials to
     *                        a Google Cloud service account
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public ComputeEngineWrapper(String projectId, File credentialsFile) throws GeneralSecurityException, IOException {
        this.projectId = projectId;

        httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Authenticate using Google Application Default Credentials.
        GoogleCredentials credential = GoogleCredentials.fromStream(new FileInputStream(credentialsFile));
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
    }

    /**
     * Deletes the snapshot
     * @param snapshotName
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException
     */
    public void deleteSnapshot(String snapshotName) throws IOException, InterruptedException, ServiceException {
        Compute.Snapshots.Delete deleter = compute.snapshots().delete(projectId, snapshotName);
        Operation op = deleter.execute();
        blockUntilComplete(op, 5 * 60 * 1000L);
    }

    /**
     * Returns the corresponding snapshot name for a disk name
     * @param diskName
     * @return
     */
    public String getSnapshotName(String diskName) {
        return "bungeegcloud-" + diskName;
    }

    /**
     * Creates a new snapshot based on the disk
     * @param zoneName
     * @param diskName
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException
     */
    public void makeSnapshot(String zoneName, String diskName)
            throws IOException, InterruptedException, ServiceException {
        Snapshot snapshotRequest = new Snapshot();
        snapshotRequest.setName(getSnapshotName(diskName));

        Compute.Disks.CreateSnapshot request = compute.disks().createSnapshot(projectId, zoneName, diskName,
                snapshotRequest);
        Operation op = request.execute();

        blockUntilComplete(op, 5 * 60 * 1000L);
    }

    /**
     * Returns wether the disk exists or not
     * @param zoneName
     * @param diskName
     * @return
     * @throws IOException
     */
    public boolean diskExists(String zoneName, String diskName) throws IOException {
        Compute.Disks.List lister = compute.disks().list(projectId, zoneName);
        DiskList response;
        do {
            response = lister.execute();
            if (response.getItems() == null) {
                continue;
            }
            for (Disk disk : response.getItems()) {
                if (disk.getName().equals(diskName))
                    return true;
            }
            lister.setPageToken(response.getNextPageToken());
        } while (response.getNextPageToken() != null);
        return false;
    }

    /**
     * Returns whether the snapshot exists or not
     * @param snapshotName
     * @return
     * @throws IOException
     */
    public boolean snapshotExists(String snapshotName) throws IOException {
        Compute.Snapshots.List lister = compute.snapshots().list(projectId);
        SnapshotList response;
        do {
            response = lister.execute();
            if (response.getItems() == null) {
                continue;
            }
            for (Snapshot snapshot : response.getItems()) {
                if (snapshot.getName().equals(snapshotName))
                    return true;
            }
            lister.setPageToken(response.getNextPageToken());
        } while (response.getNextPageToken() != null);
        return false;
    }

    /**
     * Deletes a disk
     * @param zoneName
     * @param diskName
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException
     */
    public void deleteDisk(String zoneName, String diskName)
            throws IOException, InterruptedException, ServiceException {
        Compute.Disks.Delete deleter = compute.disks().delete(projectId, zoneName, diskName);
        Operation op = deleter.execute();
        blockUntilComplete(op, 5 * 60 * 1000L);
    }

    /**
     * Creates a new disk (SSD) based on a snapshot
     * @param zoneName
     * @param diskName
     * @param snapshotName
     * @param diskSizeGb
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException
     */
    public void makeDiskFromSnapshot(String zoneName, String diskName, String snapshotName, long diskSizeGb)
            throws IOException, InterruptedException, ServiceException {
        Disk diskRequest = new Disk();
        diskRequest.setSourceSnapshot("global/snapshots/" + snapshotName);
        diskRequest.setSizeGb(diskSizeGb);
        diskRequest.setName(diskName);
        diskRequest.setType("projects/" + projectId + "/zones/" + zoneName + "/diskTypes/pd-ssd"); // TODO: add config
                                                                                                   // to let you pick
                                                                                                   // the disk type here

        Compute.Disks.Insert inserter = compute.disks().insert(projectId, zoneName, diskRequest);
        Operation op = inserter.execute();
        blockUntilComplete(op, 5 * 60 * 1000L);
    }

    /**
     * Returns the internal device name of the disk attached to the instance
     * @param zoneName
     * @param instanceName
     * @param diskName
     * @return
     * @throws IOException
     */
    public String getDeviceNameOfDisk(String zoneName, String instanceName, String diskName) throws IOException{
        String partialURL = "projects/" + projectId + "/zones/" + zoneName + "/disks/" + diskName;
        Instance instance = getInstance(zoneName, instanceName);
        List<AttachedDisk> disks = instance.getDisks();
        if(disks == null || disks.isEmpty())
            throw new IOException("No disks are attached to instance " + instanceName);
        for (AttachedDisk attachedDisk : disks) {
            if(attachedDisk.getSource().endsWith(partialURL))
                return attachedDisk.getDeviceName();
        }
        throw new IOException("No disk named " + diskName + " was found attached to instance " + instanceName);
    }

    /**
     * Detaches the disk from the instance
     * @param zoneName
     * @param instanceName
     * @param diskName
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException
     */
    public void detachDiskFromInstance(String zoneName, String instanceName, String diskName)
            throws IOException, InterruptedException, ServiceException {
        String deviceName = getDeviceNameOfDisk(zoneName, instanceName, diskName);
        Compute.Instances.DetachDisk detacher = compute.instances().detachDisk(projectId, zoneName, instanceName, deviceName);
        Operation op = detacher.execute();
        blockUntilComplete(op, 5 * 60 * 1000L);
    }

    /**
     * Attaches the disk as a boot disk to the instance
     * @param zoneName
     * @param instanceName
     * @param diskName
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException
     */
    public void attachDiskToInstance(String zoneName, String instanceName, String diskName)
            throws IOException, InterruptedException, ServiceException {
        AttachedDisk diskRequest = new AttachedDisk();
        diskRequest.setAutoDelete(false);
        diskRequest.setBoot(true);
        diskRequest.setSource("projects/" + projectId + "/zones/" + zoneName + "/disks/" + diskName);

        Compute.Instances.AttachDisk attacher = compute.instances().attachDisk(projectId, zoneName, instanceName,
                diskRequest);
        Operation op = attacher.execute();
        blockUntilComplete(op, 5 * 60 * 1000L);
    }

    /**
     * Returns wether the instance has any disk attached or not
     * @param zoneName
     * @param instanceName
     * @return Instance has a disk attached
     * @throws IOException
     */
    public boolean instanceHasDisk(String zoneName, String instanceName) throws IOException{
        Instance instance = getInstance(zoneName, instanceName);
        List<AttachedDisk> disks = instance.getDisks();
        return disks != null && !disks.isEmpty();
    }

    /**
     * The opposite of wakeInstanceDisk, creates a new snapshot from the disk, detaches the disk from the VM instance and then deletes the disk.
     * @param zoneName
     * @param instanceName
     * @param diskName
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException
     */
    public void sleepInstanceDisk(String zoneName, String instanceName, String diskName)
            throws IOException, InterruptedException, ServiceException {

        String snapshotName = getSnapshotName(diskName);
        if(snapshotExists(snapshotName)){
            deleteSnapshot(snapshotName);
        }

        makeSnapshot(zoneName, diskName);

        detachDiskFromInstance(zoneName, instanceName, diskName);

        deleteDisk(zoneName, diskName);
    }

    /**
     * The opposite of sleepInstanceDisk, creates a new disk (SSD) from an existing snapshot and then attaches it to the VM instance.
     * Snapshot name needs to be "bungeegcloud-<diskname>"
     * Assumes the snapshot exists, will error otherwise
     * @param zoneName
     * @param instanceName
     * @param diskName
     * @param diskSizeGb The desired size of the disk
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException
     */
    public void wakeInstanceDisk(String zoneName, String instanceName, String diskName, long diskSizeGb)
            throws IOException, InterruptedException, ServiceException {

        if(!diskExists(zoneName, diskName)){
            makeDiskFromSnapshot(zoneName, diskName, getSnapshotName(diskName), diskSizeGb);
        }

        if(!instanceHasDisk(zoneName, instanceName)){
            attachDiskToInstance(zoneName, instanceName, diskName);
        }
    }

    /**
     * Synchronously retrieves an Instance object from zone and name using the
     * service API
     *
     * @param zoneName     The zone name
     * @param instanceName The instance name
     * @return The Instance object
     * @throws IOException
     */
    public Instance getInstance(String zoneName, String instanceName) throws IOException {
        Compute.Instances.Get getter = compute.instances().get(projectId, zoneName, instanceName);
        return getter.execute();
    }

    /**
     * Synchronously retrieves the zone id of the specified instance.
     *
     * @param instanceName The instance name
     * @return The zone id
     * @throws IOException
     * @throws FileNotFoundException Thrown if the specified instance wasn't found
     *                               in the project.
     */
    public String getInstanceZone(String instanceName) throws IOException {
        AggregatedList lister = compute.instances().aggregatedList(projectId);
        InstanceAggregatedList list = lister.execute();
        for (Map.Entry<String, InstancesScopedList> entry : list.getItems().entrySet()) {
            List<Instance> instances = entry.getValue().getInstances();
            if (instances != null) {
                for (Instance instance : instances) {
                    if (instance.getName().equals(instanceName)) {
                        return entry.getKey().substring(6);
                    }
                }
            }
        }
        throw new FileNotFoundException(
                String.format("Couldn't find any instance named \"%s\" in the project!", instanceName));
    }

    /**
     * Synchronously starts the instance
     *
     * @param zoneName     The zone the instance resides in
     * @param instanceName The instance name
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException              Thrown by the API if there was any
     *                                       issue starting the instance
     * @throws MachinePoolExhaustedException Thrown if the instance couldn't be
     *                                       started cause there isn't enough
     *                                       resources in the pool
     */
    public void startInstance(String zoneName, String instanceName)
            throws IOException, InterruptedException, ServiceException, MachinePoolExhaustedException {
        Compute.Instances.Start starter = compute.instances().start(projectId, zoneName, instanceName);
        Operation op = starter.execute();

        try {
            blockUntilComplete(op, 5 * 60 * 1000L);
        } catch (ServiceException e) {
            if (e.getCode().equals("ZONE_RESOURCE_POOL_EXHAUSTED")) {
                throw MachinePoolExhaustedException.FromComputeMessage(e.getMessage());
            } else {
                throw e;
            }
        }
    }

    /**
     * Synchronously stops the instance
     *
     * @param zoneName     The zone the instance resides in
     * @param instanceName The instance name
     * @throws IOException
     * @throws InterruptedException
     * @throws ServiceException     Thrown by the API if there was any issue
     *                              stopping the instance
     */
    public void stopInstance(String zoneName, String instanceName)
            throws IOException, InterruptedException, ServiceException {
        Compute.Instances.Stop stopper = compute.instances().stop(projectId, zoneName, instanceName);
        Operation op = stopper.execute();
        blockUntilComplete(op, 5 * 60 * 1000L);
    }

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
     * @throws ServiceException
     */
    private void blockUntilComplete(Operation operation, long timeout)
            throws InterruptedException, IOException, ServiceException {
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
        if (operation != null && operation.getError() != null) {
            throwOperationException(operation.getError());
        }
    }

    /**
     * Converts an operation error to a thrown exception
     *
     * @param err
     * @throws ServiceException
     */
    private void throwOperationException(Error err) throws ServiceException {
        if (err.getErrors().size() == 0) {
            throw new ServiceException();
        }

        Error.Errors error = err.getErrors().get(0);
        throw new ServiceException(String.format("[%s] %s", error.getCode(), error.getMessage()), error.getCode(),
                error.getMessage());
    }

    /**
     * Returns whether the Instance is running or not
     *
     * @param instance The instance
     * @return Is running
     */
    public static boolean isRunning(Instance instance) {
        return isRunningStatus(instance.getStatus());
    }

    /**
     * Returns whether the supplied Cloud Compute String instance status indicates a
     * running or not running status
     *
     * @param status The String status, such as "STOPPING"
     * @return If it is a status indicating running or not
     */
    private static boolean isRunningStatus(String status) {
        return !(status.equals("STOPPING") || status.equals("SUSPENDING") || status.equals("SUSPENDED")
                || status.equals("TERMINATED"));
    }

    /**
     * Gets the external IP of the Instance object
     *
     * @param instance The Instance object
     * @return The external IP
     * @throws IOException
     * @throws FileNotFoundException Thrown if no external IP was found for the
     *                               Instance
     */
    public static String getExternalIP(Instance instance) throws IOException {
        for (NetworkInterface nif : instance.getNetworkInterfaces()) {
            for (AccessConfig conf : nif.getAccessConfigs()) {
                if (conf.getType().equals("ONE_TO_ONE_NAT") && conf.getName().equals("External NAT")) {
                    return conf.getNatIP();
                }
            }
        }
        throw new FileNotFoundException("No external IP found for this instance");
    }
}
