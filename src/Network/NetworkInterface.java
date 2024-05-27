package Network;

import java.net.Socket;

import Network.Protocol.Event.HandeledNetworkEvent;
import Network.Protocol.Exception.HandeledNetworkException;
import Network.Protocol.Exception.NetworkException;
//类的作用：
//定义网络接口结构：提供了一个抽象的网络接口，用于处理网络通信过程中的日志记录、事件处理和错误处理。
//封装网络过程：通过 NetworkProcess 对象封装了网络处理过程，使得网络通信的启动和处理能够统一管理。
//各个函数的作用和为什么要这样实现：
//1. startNetworkProcess(NetworkProcess networkProcess): 启动网络处理过程，将传入的 NetworkProcess 对象与接口关联，并尝试启动网络处理过程。这样实现的目的是统一管理网络处理过程的启动，并在启动失败时进行错误处理。
//2. logMessageSent(Socket connection, String message): 处理发送消息的日志记录。通过抽象方法的形式，允许具体的子类实现特定的日志记录方式，以便根据具体需求记录发送消息的信息。
//3. logMessageReceived(Socket connection, String message): 处理接收消息的日志记录。同样通过抽象方法的形式，允许具体的子类实现特定的日志记录方式，以便根据具体需求记录接收消息的信息。
//4. logEvent(HandeledNetworkEvent event): 处理事件的日志记录。通过抽象方法的形式，允许具体的子类实现特定的事件日志记录方式，以便记录网络事件的发生情况。
//5. logError(HandeledNetworkException error): 处理错误的日志记录。通过抽象方法的形式，允许具体的子类实现特定的错误日志记录方式，以便记录网络错误的发生情况。
//6. getNetworkProcess(): 获取与网络接口关联的网络处理过程对象。这个方法允许外部访问网络处理过程对象，以便进行进一步操作或查询。
//通过这样的实现，这个类提供了一个通用的网络接口结构，允许具体的子类根据需要实现特定的日志记录、事件处理和错误处理方式，从而实现灵活、可定制的网络通信功能。

public abstract class NetworkInterface {

    // member variables
    private NetworkProcess networkProcess;

    /**
     * Starts the network process.
     * 
     * @param networkProcess The network process associated with the interface, and being
     * started.
     */
    public void startNetworkProcess(NetworkProcess networkProcess){
        try{
            this.networkProcess = networkProcess;
            
            // trying to start the server
            networkProcess.start();
        }
        catch(NetworkException e){
            // handling case where the process couldnt be started
            networkProcess.handleError(e);
        }
    }

    /**
     * 处理正在发送的消息的日志记录。
     * 
     * @param connection The socket between the sender and reciever.
     * @param message The message to be logged.
     */
    public abstract void logMessageSent(Socket connection, String message);

    /**
     * Handles the logging of a message being recieved.
     * 
     * @param connection The socket between the sender and reciever.
     * @param message The message to be logged.
     */
    public abstract void logMessageReceived(Socket connection, String message);

    /**
     * Handles the logging of an event.
     * 
     * @param event The event to be logged.
     */
    public abstract void logEvent(HandeledNetworkEvent event);

    /**
     * Handles an error.
     * 
     * @param error The error to be handeled.
     */
    public abstract void logError(HandeledNetworkException error);

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public NetworkProcess getNetworkProcess(){
        return this.networkProcess;
    }
}
