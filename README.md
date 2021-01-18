# BungeeGCloud
## A Minecraft BungeeCord plugin which automates a Google Cloud Compute Minecraft server start/stop based on usage

If the VM instance is not running and a player connects, the instance will be started and the player is held waiting in the loading screen. When the instance is up, the player is automatically sent to the server.

It also automatically monitors the Minecraft server, and when the last player leaves the VM instance will be stopped again after a short timer.

# Setup
If you don't already have one, create a Google Cloud project and setup billing for it.

## Create Google Cloud Compute instance
1. In the cloud console, go to VM instances. Click Create Instance.
2. Name your instance something, such as `minecraft-1`.
3. Select the region and zone you want. Preferably, the region should be as close as possible to the BungeeCord server to minimize latency.
4. Select the machine you want, you can pick any here depending on your performance needs. The N1 series is pretty good though.
5. Under Boot disk, a regular 10GB standard persistent disk is enough. Image should be regular Debian GNU/Linux 10.
6. At the bottom, expand the "advanced" section and open the Disks tab. Add a new disk. This will be the disk that the Minecraft server is installed on. Name this something like `minecraft-disk-1`. Type can be any persistent disk, pick SSD if you want more speed, it is slightly more expensive though. Size of this disk depends on how large you expect the world to become. A disk can always be enlargened, but cannot be shrunk without deleting it. Click Done.
7. Under Networking tab, add a new Network tag called `minecraft-server`. Under the default networking interface, make sure Ephermal is selected for External IP.
8. Click Create when you're finished setting up the VM.

## Setting up the instance
1. Start the instance if it isn't already started.
2. Press the SSH button once its up to open its terminal.
3. First we need to mount the extra disk that we added. Assuming you named the disk as mentioned above, run:
```
sudo mkdir -p /home/minecraft
sudo mkfs.ext4 -F -E lazy_itable_init=0,lazy_journal_init=0,discard /dev/disk/by-id/google-minecraft-disk-1
sudo mount -o discard,defaults /dev/disk/by-id/google-minecraft-disk-1 /home/minecraft
```
4. Then we need to install some programs necessary to run the server. Run:
```
sudo apt-get update
sudo apt-get install -y default-jre-headless wget screen
```
5. Now we are going to download the Minecraft server. Run:
```
sudo su
cd /home/minecraft
wget https://launcher.mojang.com/v1/objects/link-to-server.jar
java -Xms1G -Xmx3G -jar server.jar nogui
```
6. As always, you have to accept the EULA, so open eula.txt in nano using `nano eula.txt` and replace false with true, save and exit.
7. Start the server again using the java command above and stop it again. Edit server.properties as see fit, but make sure that `enable-query` is turned on, and that online mode is disabled.

## Final Google Cloud setup
### Startup and shutdown scripts
Now we are going to make a startup and shutdown script so the Minecraft server is automatically started when the VM instance is, and safely stopped before the VM instance stops.

1. Click your VM instance in Cloud Console, click Edit. In the Custom metadata section, add a new key called `startup-script` and copy the following there (again, assuming you named the disk as above):
```
#!/bin/bash
mount /dev/disk/by-id/google-minecraft-disk-1 /home/minecraft
cd /home/minecraft
screen -d -m -S mcs java -Xms1G -Xmx3G -jar server.jar nogui
```
2. Add another key called `shutdown-script` and copy the following there:
```
#!/bin/bash
sudo screen -r mcs -X stuff '/stop\n'
```

### Firewall
By default the firewall for VM instances is very restrictive. We want to open the 25565 port towards the BungeeCord host server so it can proxy players there.

1. Open the VPC Firewall page by searching for it at the top.
2. Create a new Firewall rule. Set the following:

* Name: `minecraft-rule`
* Direction of traffic: Ingress
* Action on match: Allow
* Targets: Specified target tags
* Target tags: `minecraft-server`
* Source filter: IP ranges
* Source IP ranges: `<your BungeeCord host>`
* Protocols and ports: Check TCP and UDP and type 25565 in both boxes.
3. Save

### IAM access
You need to create a Service Account for the plugin to have access to control your VM.

1. Open IAM Service Accounts by searching for it at the top of Cloud Console.
2. Create Service Account. Name it something like `Minecraft BungeeGCloud`. Give it Compute Admin role. Click Create.
3. In the IAM Service Accounts main menu again, click the triple dots button on your newly made service account. Click Create Key. Type JSON. The downloaded json file should be placed next to the BungeeCord.jar.
