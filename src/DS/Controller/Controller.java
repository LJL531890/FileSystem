package DS.Controller;

import DS.Controller.Index.*;
import DS.Controller.Rebalancer.Rebalancer;
import DS.Protocol.Exception.*;
import Network.NetworkInterface;
import Network.Protocol.Event.HandeledNetworkEvent;
import Network.Protocol.Event.NetworkEvent;
import Network.Protocol.Exception.*;
import Network.Server.*;

/**
 * 数据存储控制器。
 *
 * 连接到 Dstores 并处理来自 DSClients 的请求。
 */
public class Controller extends Server{

    // member variables
    private int port;
    private int minDstores;
    private int timeout;
    private int rebalancePeriod;
    private NetworkInterface networkInterface; 
    private volatile Index index;
    private volatile Rebalancer rebalancer;

//       /**
//* 类构造函数。
//     *
//* @param 端口 控制器应侦听的端口。
//* @param minDstores 要复制文件的数据存储数。
//* @param timeout 通信的超时长度。
//* @param rebalancePeriod 再平衡期。
//* @param networkInterface 与控制器关联的 NetworkInterface。
//     */
    public Controller(int port, int r, int timeout, int rebalancePeriod, NetworkInterface networkInterface){
        // initializing new member variables
        super(ServerType.CONTROLLER, port, networkInterface);
        this.port = port;
        this.minDstores = r;
        this.timeout = timeout;
        this.rebalancePeriod = rebalancePeriod;
        this.networkInterface = networkInterface;
        this.index = new Index(this);
        this.rebalancer = new Rebalancer(this);
        this.setRequestHandler(new ControllerRequestHandler(this));
    }

    ///////////
    // SETUP //
    ///////////
    /**
     * 设置控制器即可使用。
     *
     * 创建记录器。
     *
     * @throws ServerSetupException 如果无法设置控制器。
     */
    public void setup() throws ServerSetupException{
        try{
            // starting rebalance thread
            this.rebalancer.start();
        }
        catch(Exception e){
            throw new ServerSetupException(ServerType.CONTROLLER, e);
        }
    }

    ////////////////////
    // EVENT HANDLING //
    ////////////////////

    /**
     * 处理已发生的事件。
     *
     * @param event 已发生的事件。
     */
    public void handleEvent(NetworkEvent event){
        // handling the event
        // ?? nothing to handle

        // logging the event
        this.getNetworkInterface().logEvent(new HandeledNetworkEvent(event));
    }

    ////////////////////
    // ERROR HANDLING //
    ////////////////////

    /**
     * 处理系统内发生的错误。
     *
     * @param error 已发生的错误。
     */
    public void handleError(NetworkException error){
        // 连接终止
        if(error instanceof ConnectionTerminatedException){
            // 获取连接异常
            ConnectionTerminatedException exception = (ConnectionTerminatedException) error;

            // Dstore 已断开连接

            for(DstoreIndex dstore : this.index.getDstores()){
                if(dstore.getConnection() == exception.getConnection()){
                    // 从索引中删除 dstore
                    this.index.removeDstore(exception.getConnection());

                    // removing the dstore from the server
                    this.getServerConnections().remove(exception.getConnection());

                    // logging the disconnect
                    this.getNetworkInterface().logError(new HandeledNetworkException(new DstoreDisconnectException(dstore.getPort(), exception)));

                    // rebalancing
                    try{
                        this.getRebalancer().rebalance();
                    }
                    catch(NetworkException e){
                        this.handleError(new RebalanceFailureException(e));
                    }

                    return; // nothing else to do
                }
            }

            // 客户端断开连接

            if(this.getClientConnections().contains(exception.getConnection())){
                // removing the client from the server
                this.getClientConnections().remove(exception.getConnection());

                // logging the disconnect
                this.getNetworkInterface().logError(new HandeledNetworkException(new ClientDisconnectException(exception.getConnection().getPort(), exception)));
            }

            // 客户端检测信号断开连接

            else if(this.getClientHeartbeatConnections().containsKey(exception.getConnection())){
                // 获取检测信号端口的客户端
                int clientPort = this.getClientHeartbeatConnections().get(exception.getConnection());

                // 从服务器中删除客户端检测信号
                this.getClientHeartbeatConnections().remove(exception.getConnection());

                // 记录断开连接
                this.getNetworkInterface().logError(new HandeledNetworkException(new ClientHeartbeatDisconnectException(clientPort, exception)));
            }

            // 未知连接器已断开连接

            else{
                // nothing to handle

                // logging the disconnect
                this.getNetworkInterface().logError(new HandeledNetworkException(new UnknownConnectorDisconnectException(exception.getConnection().getPort(), exception)));
            }
        }

        // 不重要的错误 - 只需要记录
        else{
            // logging error
            this.getNetworkInterface().logError(new HandeledNetworkException(error));
        } 
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////


    public int getPort(){
        return this.port;
    }

    public int getMinDstores(){
        return this.minDstores;
    }

    public int getTimeout(){
        return this.timeout;
    }

    public int getRebalancePeriod(){
        return this.rebalancePeriod;
    }

    public Index getIndex(){
        return this.index;
    }

    public Rebalancer getRebalancer(){
        return this.rebalancer;
    }
}