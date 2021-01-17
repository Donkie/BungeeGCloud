package donkie.bungeegcloud;

import java.net.InetSocketAddress;

public class IPPort {
    private String ip;
    private int port;

    public IPPort(String ip, int port){
        this.ip = ip;
        this.port = port;
    }

    public String getIp(){
        return this.ip;
    }

    public int getPort(){
        return this.port;
    }

    public InetSocketAddress toAddress(){
        return new InetSocketAddress(ip, port);
    }
}
