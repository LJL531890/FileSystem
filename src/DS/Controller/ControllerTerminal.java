package DS.Controller; 

import java.net.Socket;

import Network.NetworkInterface;
import Network.Protocol.Event.HandeledNetworkEvent;
import Network.Protocol.Exception.*;
/**
 * ServerInterface 的实现，通过终端为数据存储控制器提供接口。
 * 消息通过 stdout 登录到终端。
 */
public class ControllerTerminal extends NetworkInterface{

    // member variables
    Controller controller;

    /**
     * Class constructor.
     * 
     * @param port The port the controller should listen on.
     * @param r The number of data stores to replicate files across.
     * @param timeout The timeout length for communication.
     * @param rebalancePeriod The rebalance period.
     */
    public ControllerTerminal(int port, int r, int timeout, int rebalancePeriod){
        this.controller = new Controller(port, r, timeout, rebalancePeriod, this);

        // starting Controller
        //1 开启rebalancer线程
        //2 等待Client连接
        this.startNetworkProcess(this.controller); // 在单独的线程上启动它
    }

    /////////////
    // LOGGING //
    /////////////

/**
     * 处理正在发送的消息的日志记录。
     *
     * @connection：发送方和接收方之间的套接字。
     * @param message 要记录的消息。
     */
    public void logMessageSent(Socket connection, String message){
        System.out.println("[" + connection.getLocalPort() + " -> " + connection.getPort() + "] " + message);
    }

/**
     * 处理正在接收的消息的记录。
     *
     * @param连接：发送方和接收方之间的套接字。
     * @param message 要记录的消息。
     */
    public void logMessageReceived(Socket connection, String message){
        System.out.println("[" + connection.getLocalPort() + " <- " + connection.getPort() + "] " + message);
    }
/**
     * 处理事件的日志记录。
     *
     * @param event 要记录的事件。
     */
    public void logEvent(HandeledNetworkEvent event){
        System.out.println(event.toString());
    }

  /**
     * 处理错误的日志记录。
     *
     * @param error 要记录的错误。
     */
    public void logError(HandeledNetworkException error){
        // logging error to terminal
        System.out.println(error.toString());

        // HANDLING ERROR //

        // Server Start Exception
        if(error.getException() instanceof ServerStartException){
            // closing the system
            System.exit(0);
        }
    }
    

    /////////////////
    // MAIN METHOD //
    /////////////////


    /**
     * Main 方法 - 使用命令行参数实例化新的 Controller 实例。
     *
     * @param args 新控制器的参数。
     */
    public static void main(String[] args){
        try{
            // gathering parameters
            int cPort = Integer.parseInt(args[0]);
            int r = Integer.parseInt(args[1]);
            int timeout = Integer.parseInt(args[2]);
            int rebalancePeriod = Integer.parseInt(args[3]);

            // Creating new DStore instance
            ControllerTerminal controller = new ControllerTerminal(cPort, r, timeout, rebalancePeriod);
        }
        catch(Exception e){
            System.out.println("Unable to create Controller.");
        }
    }
}