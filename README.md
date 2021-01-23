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
4. Select the machine you want, you can pick any here depending on your performance needs. Some types will be harder to get hold of (remember you're sharing the resources from a pool), so if you find that the pool is often exhausted it might be worth looking into switching type.
5. Under Boot disk, select what type of storage you want. A 10GB SSD is a good start for a small server. Image should be regular Debian GNU/Linux 10.
6. Under Networking tab, add a new Network tag called `minecraft-server`. Under the default networking interface, make sure Ephermal is selected for External IP.
7. Click Create when you're finished setting up the VM.

## Setting up the instance
1. Start the instance if it isn't already started.
2. Press the SSH button once its up to open its terminal.
3. First we need to install some programs necessary to run the server. Run:
```
sudo apt-get update
sudo apt-get install -y default-jre-headless wget
```
4. Now you need to download the Minecraft server. This is done like any regular unix Minecraft server install. I recommend using https://github.com/GameServerManagers/LinuxGSM .
5. When finished installing the server, make sure `enable-query` is set to true, and `online-mode` set to false in server.properties.
6. In order to easily start and stop the server, we are going to make two bash scripts. The contents of these depends on if you use LGSM or some other method of hosting, but here I assume you use LGSM, installed with a user named minecraft.

`startup.sh`
```
#!/bin/bash
/home/minecraft/mcserver start
```
`shutdown.sh`
```
#!/bin/bash
/home/minecraft/mcserver stop
```

## Final Google Cloud setup
### Startup and shutdown scripts
Now we are going to make a startup and shutdown script so the Minecraft server is automatically started when the VM instance is, and safely stopped before the VM instance stops.

1. Click your VM instance in Cloud Console, click Edit. In the Custom metadata section, add a new key called `startup-script` and copy the following there:
```
#!/bin/bash
sudo -u minecraft bash /home/minecraft/startup.sh
```
2. Add another key called `shutdown-script` and copy the following there:
```
#!/bin/bash
sudo -u minecraft bash /home/minecraft/shutdown.sh
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
3. In the IAM Service Accounts main menu again, click the triple dots button on your newly made service account. Click Create Key. Type JSON. The downloaded json file should be placed in BungeeGCloud's data folder, as explained soon.

## Setting up BungeeCord end
Install BungeeCord/Waterfall or some other derivative on your other host like usual. Add the built BungeeGCloud.jar to the plugins folder. Create a folder inside plugins folder called `BungeeGCloud`. Inside, place the IAM service account json file, named exactly `credentials.json`. Also create a file called `config.yml`. The contents of the config should be as followed:
```
idle_server_stopwait: 180 # How many seconds to wait after last player leaves until the instance is stopped
refresh_players_period: 5 # How often we should refresh the player count from the Minecraft server
compute:
  project_id: minecraft-123456 # Your GCloud project id
  instance_id: minecraft-1 # Your GCloud instance name
minecraft:
  port: 25565 # The Minecraft server port
  default:
    motd: "Welcome to Exhale!" # Default MOTD to show if we haven't seen the Minecraft server up yet
    max_players: 20 # Default max players to show if we haven't seen the Minecraft server up yet
```
