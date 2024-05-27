package Network;

import Network.Protocol.Event.NetworkEvent;
import Network.Protocol.Exception.NetworkException;

/**
 * Reprsents 在网络上运行的进程（例如，客户端、服务器）。
 */
public interface NetworkProcess {
    
//    /**
//* 启动网络进程。
//     *
//* @throws NetworkStartException 如果无法启动网络进程。
//     */
    public abstract void start() throws NetworkException;

    /**
     * 设置网络进程。
     * 
     * @throws NetworkException 如果无法设置网络进程。
     */
    public abstract void setup() throws NetworkException;


    /**
     * Handles an event.
     * 
     * @param event The NetworkEvent that has occured.
     */
    public abstract void handleEvent(NetworkEvent event);

    /**
     * Handles an error.
     * 
     * @param error The NetworkException that has occured.
     */
    public abstract void handleError(NetworkException error);
}
