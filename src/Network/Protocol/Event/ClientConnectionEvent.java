package Network.Protocol.Event;

import Network.Server.Server;

/**
 * 表示一个客户端/服务器连接到另一个服务器的事件。
 */
public class ClientConnectionEvent extends NetworkEvent {
    
    /**
     * Class constructor.
     * 
     * @param serverType The type of server the connection was made to.
     * @param port The port of the server that connection was made to.
     */
    public ClientConnectionEvent(Server.ServerType serverType, int port){
        super("Connection made to " + serverType.toString() + " on port : " + port + ".");
    }
}
