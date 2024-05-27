package Network.Server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import Network.*;
import Network.Protocol.Exception.NewServerConnectionException;
import Network.Protocol.Exception.ServerSetupException;
import Network.Protocol.Exception.ServerWaitForConnectionException;
import Network.Protocol.Exception.ServerStartException;

/**
 * 表示 Server 对象的抽象类。
 *
 * 服务器侦听新的连接器并创建新的 Connection 对象来处理连接器的请求。
 */
public abstract class Server implements NetworkProcess{

    // member variables
    private ServerType type;
    private int port;
    private ServerSocket serverSocket;
    private RequestHandler requestHandler;
    private NetworkInterface networkInterface;
    private volatile CopyOnWriteArrayList<Connection> clientConnections;
    private volatile ConcurrentHashMap<Connection, Integer> clientHeartbeatConnections;
    private volatile CopyOnWriteArrayList<Connection> serverConnections;
    private boolean active;

//    /**
//     * Class constructor
//     *
//     * @param type The type of Server.
//     * @param port The port the Server listens on.
//     * @param serverInterface The network interface for the Server.
//     */
    public Server(ServerType type, int port, NetworkInterface networkInterface){
        this.type = type;
        this.port = port;
        this.networkInterface = networkInterface;
        this.clientConnections = new CopyOnWriteArrayList<Connection>();
        this.clientHeartbeatConnections = new ConcurrentHashMap<Connection, Integer>() ;
        this.serverConnections = new CopyOnWriteArrayList<Connection>();
        this.active = true;
    }

    /**
     * Ran to setup the server and start waiting for connections.
     * 
     * @throws ServerStartException If the Server could not be started
     */
    public void start() throws ServerStartException{
        try{
            // running the server's setup method
            this.setup();

            // starting the server
            this.waitForConnection();
        }
        catch(Exception e){
            throw new ServerStartException(this.type, this.port, e);
        }
    }

  /**
     * 在启动服务器之前设置服务器。
     *
     * 由服务器实例实现，用于在服务器启动之前执行设置操作。
     *
     * @throws ServerSetupException 如果无法设置服务器。
     */
    public abstract void setup() throws ServerSetupException;


    /**
     * Makes the server start listening for incoming communication.
     * 
     * @throws ServerWaitForConnectionException If the server was unable to start waiting for
     * connections.
     */
    public void waitForConnection() throws ServerWaitForConnectionException{
        // Starting Listening //
        try{
            this.serverSocket = new ServerSocket(this.port);

            // listening for connections
            while (this.isActive()){
                try{
         /*这段代码的作用是创建一个 ServerSocket 实例来监听指定端口，并通过 accept() 方法等待客户端连接并返回一个 Socket 实例以便进行通信。
        //ServerSocket 类是用于在服务器端监听和接受客户端连接的类。通过创建 ServerSocket 实例并指定端口，服务器可以监听该端口并等待客户端连接请求。
        //Socket 类代表客户端和服务器之间的通信端点。一旦服务器接受了客户端的连接请求，accept() 方法会返回一个 Socket 实例，该实例可以用于与客户端进行通信。
        //accept() 方法是 ServerSocket 类的方法，用于接受客户端的连接请求。当客户端连接到服务器时，accept() 方法会阻塞程序的执行，直到有客户端连接成功，然后返回一个新的 Socket 实例，通过该实例可以与客户端进行通信。*/
                    Socket socket = this.serverSocket.accept();
                    Connection connection = new Connection(this.getNetworkInterface(), socket);

                    // setting up the connection
            /*在这个类中，start 方法执行 setup 和 waitForConnection 方法的原因是为了在启动服务器时先进行必要的设置操作，然后开始等待连接请求。这样可以确保服务器在启动后能够正常运行并接受客户端连接。
            //在 waitForConnection 方法中执行 setUpConnection 方法的原因是当服务器接受到客户端连接请求时，需要建立与客户端的连接。setUpConnection 方法负责设置与连接器的连接，并启动一个新的线程 ServerThread 来处理与客户端的通信。这样可以实现多线程处理多个客户端连接，提高服务器的并发性能。
            //在 setUpConnection 方法中执行 ServerThread serverThread = new ServerThread(this, connection); serverThread.start(); 的目的是为了创建一个新的线程 ServerThread 来处理与客户端的通信。通过启动新线程，可以使服务器能够同时处理多个客户端的请求，而不会阻塞主线程，从而提高服务器的并发处理能力。*/
                    this.setUpConnection(connection);
                }
                catch(Exception e){
                    this.handleError(new NewServerConnectionException(this.type, this.port, e));
                }
            }
        }
        catch(Exception e){
            throw new ServerWaitForConnectionException(this.type, this.port, e);
        }
    }

    /**
     *在服务器和连接器之间建立连接。
     * 
     * @param connection The connection between the connector and the Server.
     */
    public void setUpConnection(Connection connection){
        // 设置与连接器的连接
        ServerThread serverThread = new ServerThread(this, connection);
        serverThread.start();
    }

    /**
     * Closes the server.
     */
    public void close(){
        this.active = false;
    }


    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public ServerType getType(){
        return this.type;
    }
    
    public RequestHandler getRequestHandler(){
        return this.requestHandler;
    }

    public NetworkInterface getNetworkInterface(){
        return this.networkInterface;
    }

    public CopyOnWriteArrayList<Connection> getClientConnections(){
        return this.clientConnections;
    }

    public ConcurrentHashMap<Connection, Integer> getClientHeartbeatConnections(){
        return this.clientHeartbeatConnections;
    }

    public CopyOnWriteArrayList<Connection> getServerConnections(){
        return this.serverConnections;
    }

    public boolean isActive(){
        return this.active;
    }

    public void setRequestHandler(RequestHandler requestHandler){
        this.requestHandler = requestHandler;
    }

    /////////////////
    // SERVER TYPE //
    /////////////////

    /**
     * Enumeration class for the type of Server object that can exist within the 
     * system.
     * 
     * These Server types are specific to the Distributed Storage System.
     */
    public enum ServerType {
        // types
        CONTROLLER("Controller"), // Controller sever
        DSTORE("Dstore"); // Dstore server

        private String serverType;

        private ServerType(String serverType){
            this.serverType = serverType;
        }

        /**
         * Converts the server type method to a string.
         * @return String equivalent of the server type.
         */
        @Override
        public String toString(){
            return this.serverType;
        }

        /**
         * Gathers the server type from the given string.
         * @param text The String form of the server type
         * @return The ServerType object for the server type.
         */
        public static ServerType fromString(String text) {
            for (ServerType type : ServerType.values()) {
                if (type.serverType.equalsIgnoreCase(text)) {
                    return type;
                }
            }
            return null;
        }
    }
}