package DS.Dstore;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import DS.Protocol.Protocol;
import DS.Protocol.Exception.*;
import DS.Protocol.Token.RequestTokenizer;
import DS.Protocol.Token.Token;
import DS.Protocol.Token.TokenType.ErrorDstorePortInUseToken;
import DS.Protocol.Token.TokenType.JoinAckToken;
import Network.*;
import Network.Protocol.Event.HandeledNetworkEvent;
import Network.Protocol.Event.NetworkEvent;
import Network.Protocol.Exception.ClientDisconnectException;
import Network.Protocol.Exception.ConnectToServerException;
import Network.Protocol.Exception.ConnectionTerminatedException;
import Network.Protocol.Exception.HandeledNetworkException;
import Network.Protocol.Exception.NetworkException;
import Network.Protocol.Exception.ServerSetupException;
import Network.Protocol.Exception.UnknownConnectorDisconnectException;
import Network.Server.Server;
import Network.Server.ServerThread;

/**
  * 系统内的单个数据存储单元。
  *
  * 连接到 Controller 以加入数据存储和服务器来自 DSClients 和 Controller 的请求。
  */
public class Dstore extends Server{

    // member variables
    private int port;
    private int cPort;
    private int timeout;
    private String folderPath;
    private File fileStore;
    private ServerThread controllerThread;
    private NetworkInterface networkInterface;
//
//    /**
//* 类构造函数。
//     *
//* @param port DStore 将侦听的端口。
//* @param cPort DStore 将连接到的控制器所在的端口。
//* @param timeout DStore 的超时周期。
//* @param fileFolder DStore 将存储文件的文件夹。
//* @param networkInterface Dstore 的网络接口。
//     */
    public Dstore(int port, int cPort, int timeout, String folderPath, NetworkInterface networkInterface){
        // initializing member variables
        super(ServerType.DSTORE, port, networkInterface);
        this.port = port;
        this.cPort = cPort;
        this.timeout = timeout;
        this.folderPath = folderPath;
        this.networkInterface = networkInterface;
        this.setRequestHandler(new DstoreRequestHandler(this));
    }

    ///////////
    // SETUP //
    ///////////
    
/**
     * 设置 Dstore 以供使用。
     *
     * 创建记录器，连接到控制器并创建文件存储。
     *
     * @throws ServerSetupException 如果无法设置 Dstore。
     */
    public void setup() throws ServerSetupException{
        try{
            // connecting to controller
            this.connectToController();

            // setting up file storage folder
            this.setupFileStore(this.folderPath);
        }
        catch(Exception e){
            throw new ServerSetupException(ServerType.DSTORE, e);
        }
    }

/**
     * 在 DStore 和控制器之间建立连接。
     *
     * @throws ConnectToServerException 如果 Dstore 无法连接到 Controller。
     */
    public void connectToController() throws ConnectToServerException{
        try{
            // creating communicatoin channel
            Connection connection = new Connection(this.getNetworkInterface(), this.cPort, ServerType.CONTROLLER);
            this.controllerThread = new ServerThread(this, connection);

            // sending JOIN message to Controller
            this.controllerThread.getConnection().sendMessage(Protocol.getJoinDstoreMessage(port));

            // handling response from Controller

            Token response = RequestTokenizer.getToken(this.controllerThread.getConnection().getMessageWithinTimeout(this.timeout));

            if(response instanceof JoinAckToken){
                // Join Successful

                // starting the connection thread
                this.controllerThread.start();

            }
            else if(response instanceof ErrorDstorePortInUseToken){
                // Join not successful
                throw new DstorePortInUseException(this.port);
            }
        }
        catch(Exception e){
            throw new ConnectToServerException(ServerType.CONTROLLER, this.cPort, e);
        }
    }

/**
     * 通过创建目录确保 DStores 文件存储已准备好使用
     * 如果尚不存在。
     *
     * @param folderPath 文件存储目录。
     */
    public void setupFileStore(String folderPath){
        // creating file object
        this.fileStore = new File(folderPath);

        // creating the file if it doesnt exist
        if(!this.fileStore.exists()){
            this.fileStore.mkdir();
        }
    }

    ////////////////////
    // EVENT HANDLING //
    ////////////////////

    /**
     * Handles an event that has occured.
     * 
     * @param event The event that has occured.
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
     * Handles an error that occured within the system.
     * 
     * @param error The error that has occured.
     */
    public void handleError(NetworkException error){
        // Connection Termination
        if(error instanceof ConnectionTerminatedException){
            ConnectionTerminatedException exception = (ConnectionTerminatedException) error;

            // Controller Disconnected
            if(exception.getConnection().getPort() == this.cPort){
                // logging disconnect
                this.getNetworkInterface().logError(new HandeledNetworkException(new ControllerDisconnectException(exception.getConnection().getPort(), exception)));
            }

            // Client Disconnected
            else if(this.getClientConnections().contains(exception.getConnection())){
                // logging disconnect
                this.getNetworkInterface().logError(new HandeledNetworkException(new ClientDisconnectException(exception.getConnection().getPort(), exception)));
            }

            // Dstore Disconnected
            else if(this.getServerConnections().contains(exception.getConnection())){
                // logging disconnect
                this.getNetworkInterface().logError(new HandeledNetworkException(new DstoreDisconnectException(exception.getConnection().getPort(), exception)));
            }

            // Unknown Connector Disconnected
            else{
                // logging disconnect
                this.getNetworkInterface().logError(new HandeledNetworkException(new UnknownConnectorDisconnectException(exception.getConnection().getPort(), exception)));
            }
        }
        // Non-important error - just need to log
        else{
            // logging error
            this.getNetworkInterface().logError(new HandeledNetworkException(error));
        }
    }

    ////////////////////
    // HELPER METHODS //
    ////////////////////

    /**
     * Returns a list of files stored in the Dstore as a mapping of
     * filenames to filesizes.
     * 
     * @return A mapping of filenames to filesizes.
     */
    public HashMap<String, Integer> getFiles(){
        // gathering list of files
        File[] fileList = this.getFileStore().listFiles();

        // creating hashmap of files
        HashMap<String, Integer> files = new HashMap<String, Integer>();
        for(File file : fileList){
            files.put(file.getName(), (int) file.length());
        }

        // returning map of files
        return files;
    }


    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public int getPort(){
        return this.port;
    }

    public int getCPort(){
        return this.cPort;
    }

    public int getTimeout(){
        return this.timeout;
    }

    public String getFolderPath(){
        return this.folderPath;
    }

    public File getFileStore(){
        return this.fileStore;
    }

    public ServerThread getControllerThread(){
        return this.controllerThread;
    }
}