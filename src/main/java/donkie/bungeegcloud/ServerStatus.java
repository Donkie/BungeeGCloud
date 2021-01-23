package donkie.bungeegcloud;

import net.md_5.bungee.api.ServerPing.PlayerInfo;
import net.md_5.bungee.api.ServerPing.Players;
import query.QueryResponse;

/**
 * Represents the status of a Minecraft server
 */
public class ServerStatus {
    private boolean isOnline;
    private int numPlayers;
    private int maxPlayers;
    private String motd;

    public ServerStatus(int maxPlayers, String motd){
        isOnline = false;
        numPlayers = 0;
        this.maxPlayers = maxPlayers;
        this.motd = motd;
    }

    public ServerStatus(QueryResponse resp){
        isOnline = true;
        numPlayers = resp.getOnlinePlayers();
        maxPlayers = resp.getMaxPlayers();
        motd = resp.getMOTD();
    }

    public ServerStatus(){
        isOnline = false;
        numPlayers = 0;
        maxPlayers = 1;
        motd = "";
    }

    public void update(QueryResponse resp){
        isOnline = true;
        numPlayers = resp.getOnlinePlayers();
        maxPlayers = resp.getMaxPlayers();
        motd = resp.getMOTD();
    }

    public Players getPlayersForPing(){
        return new Players(getMaxPlayers(), getOnlinePlayers(), new PlayerInfo[0]);
    }

    public boolean isOnline(){
        return isOnline;
    }

    public void setOffline(){
        isOnline = false;
        numPlayers = 0;
    }

    public void setOnlinePlayers(int players){
        numPlayers = players;
    }

    public int getOnlinePlayers(){
        return numPlayers;
    }

    public int getMaxPlayers(){
        return maxPlayers;
    }

    public String getMOTD(){
        return motd;
    }
}
