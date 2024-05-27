package Network.Protocol.Event;

import Network.Client.Client;

/**
 * 表示服务器从客户端/服务器接收新连接的事件。
 */
public class ServerConnectionEvent extends NetworkEvent{

    /**
     * Class constructor.
     * 
     * @param clientType The type of Client the connection has come from.
     * @param port The port associated with the client.
     */
    public ServerConnectionEvent(Client.ClientType clientType, int port){
        super("New " + clientType.toString() + " connected on port : " + port + ".");
    }
}
