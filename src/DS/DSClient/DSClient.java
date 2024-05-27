package DS.DSClient;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;

import DS.Protocol.Protocol;
import DS.Protocol.Event.Operation.ListCompleteEvent;
import DS.Protocol.Event.Operation.LoadCompleteEvent;
import DS.Protocol.Event.Operation.RemoveCompleteEvent;
import DS.Protocol.Event.Operation.StoreCompleteEvent;
import DS.Protocol.Exception.*;
import DS.Protocol.Token.*;
import DS.Protocol.Token.TokenType.*;
import Network.*;
import Network.Client.Client;
import Network.Protocol.Event.HandeledNetworkEvent;
import Network.Protocol.Event.NetworkEvent;
import Network.Protocol.Exception.*;
import Network.Server.Server.ServerType;
/**
 * 抽象类，用于表示系统内的 DSClient。
 *
 * DSClient 通过连接到控制器来连接到数据存储。
 *
 * DSClient 接收来自关联 DSClientInterface 的输入请求，进入 'handleInputRequest' 方法。
 *
 * 该类本质上是 DSClient 的请求处理程序。
 */
public class DSClient extends Client{

    /**
     * Class Constructor.
     * 
     * @param cPort The port of the Controller.
     * @param timeout The message timeout period.
     * @param networkInterface The interface component for the Client.
     */
    public DSClient(int cPort, int timeout, NetworkInterface networkInterface) {
        // initialising member variables
        super(cPort, timeout, networkInterface);
    }

    ///////////
    // SETUP //
    ///////////

/**
     * 设置 DSClient 以供使用。
     *
     * @throws ClientStartException 如果无法设置客户端。
     */
    public void setup() throws ClientSetupException{
        try{

            // JOIN_CLIENT //

            // sending JOIN_CLIENT message to controller
            this.getServerConnection().sendMessage(Protocol.getJoinClientMessage());

            // waiting for JOIN_ACK
            Token response = RequestTokenizer.getToken(this.getServerConnection().getMessageWithinTimeout(this.getTimeout()));

            // making sure response is JOIN_ACK
            if(!(response instanceof JoinAckToken)){
                // throwing exception
                throw new InvalidMessageException(response.message, this.getServerConnection().getPort());
            }

            // JOIN_CLIENT_HEARTBEAT //

            // sending JOIN_CLIENT_HEARTBEAT message to controller
            this.getServerHeartbeat().getConnection().sendMessage(Protocol.getJoinClientHeartbeatMessage(this.getServerConnection().getLocalPort()));

            // waiting for JOIN_ACK
            response = RequestTokenizer.getToken(this.getServerHeartbeat().getConnection().getMessageWithinTimeout(this.getTimeout()));

            // making sure response is JOIN_ACK
            if(response instanceof JoinAckToken){
                // starting the heartbeat thread
                this.getServerHeartbeat().start();
            }
            else{
                // throwing exception
                throw new InvalidMessageException(response.message, this.getServerConnection().getPort());
            }
        }
        catch(Exception e){
            throw new ClientSetupException(e);
        }
    }

    ////////////////////
    // EVENT HANDLING //
    ////////////////////

    /**
     * 处理已发生的事件。
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
     * Handles an error that has occured for the client
     * 
     * @param error The error that has occured.
     */
    public void handleError(NetworkException error){
        // Connection Termination
        if(error instanceof ConnectionTerminatedException){
            ConnectionTerminatedException exception = (ConnectionTerminatedException) error;

            // Controller Disconnected //

            if(exception.getConnection().getPort() == this.getServerPort()){
                // logging error
                this.getNetworkInterface().logError(new HandeledNetworkException(new ControllerDisconnectException(this.getServerPort(), exception)));
            }

            // Dstore disconnected //
            /*确保只处理次要服务器连接的异常，避免对其他类型的连接异常进行处理。
如果异常是次要服务器连接的异常，需要从次要服务器连接列表中移除该连接，以确保连接管理的一致性和正确性。*/

            else if(this.getSecondaryServerConnections().contains(exception.getConnection())){
                // removiing connection from client
                this.getSecondaryServerConnections().remove(exception.getConnection());

                // logging error
                this.getNetworkInterface().logError(new HandeledNetworkException(new DstoreDisconnectException(exception.getConnection().getPort(), exception)));
            }
        }
        // Non-important error - just need to log
        else{
            // logging error
            this.getNetworkInterface().logError(new HandeledNetworkException(error));
        }
    }

    //////////////////////
    // REQUEST HANDLING //
    //////////////////////

    ///////////
    // STORE //
    ///////////

//    /**
//     * Handles a STORE request.
//     *
//     * @param file The file object to be stored.
//     * @param filesize The size of the file being stored.
//     *
//     * @throws MessageSendException If a message couldn't be sent through the connection.
//     * @throws MessageReceievedException If a message could not be receieved through the connection.
//     * @throws NotEnoughDstoresException If there are not enough Dstores connected to the controller to handle
//     * the request.
//     * @throws FileAlreadyExistsException If there is already a file with this name stored in the Dstore.
//     * @throws InvalidMessageException If a message of the wrong form is receieved during the communication.
//     */
    public void storeFile(File file, int filesize) throws Exception{
        // sending the store message to the controller
        this.getServerConnection().sendMessage(Protocol.getStoreMessage(file.getName(), filesize));

        // 收集响应
        Token response = RequestTokenizer.getToken(this.getServerConnection().getMessageWithinTimeout(this.getTimeout()));
        
        // STORE_TO
        if(response instanceof StoreToToken){
            // gathering the token
            StoreToToken storeToToken = (StoreToToken) response;
            
            // sending file to each dstore
            for(int dstore : storeToToken.ports){
                this.sendFileToDstore(file, filesize, dstore);
            }

            // waiting for response from Controller
            response = RequestTokenizer.getToken(this.getServerConnection().getMessageWithinTimeout(this.getTimeout()));

            // STORE_COMPLETE
            if(response instanceof StoreCompleteToken){
                // logging operation complete
                this.handleEvent(new StoreCompleteEvent(file.getName(), filesize));
            }

            // Invalid Response
            else{
                throw new InvalidMessageException(response.message, this.getServerPort());
            }
        }

        // ERROR_NOT_ENOUGH_DSTORES
        else if(response instanceof ErrorNotEnoughDStoresToken){
            throw new NotEnoughDstoresException();
        }

        // ERROR_FILE_ALREADY_EXISTS
        else if(response instanceof ErrorFileAlreadyExistsToken){
            throw new FileAlreadyExistsException(file.getName());
        }

        // Invalid Response
        else{
            throw new InvalidMessageException(response.message, this.getServerPort());
        }
    }
//
//  /**
//* 将具有给定名称的文件发送到侦听提供的端口的 Dstore。
//     *
//* @param文件：要发送到Dstore的文件
//* @param dstore 文件要发送到的 Dstore。
//     *
//* @throws MessageSendException：如果无法通过连接发送消息。
//* @throws MessageReceievedException 如果无法通过连接接收消息。
//* @throws InvalidMessageException 如果在通信过程中收到错误形式的消息。
//     */
    private void sendFileToDstore(File file, int filesize, int dstore) throws Exception{
        // loading the file
        FileInputStream fileInput = new FileInputStream(file);

        // setting up the connection
        Connection connection = new Connection(this.getNetworkInterface(), dstore, ServerType.DSTORE);

        // adding connection to client
        this.getSecondaryServerConnections().add(connection);

        // sending client join message
        connection.sendMessage(Protocol.getJoinClientMessage());

        try{
            // waiting for acknowledgement
            Token response = RequestTokenizer.getToken(connection.getMessageWithinTimeout(this.getTimeout()));

            // making sure response is JOIN_ACK
            if(response instanceof JoinAckToken){
                // sending store message
                connection.sendMessage(Protocol.getStoreMessage(file.getName(), filesize));

                // waiting for acknowledgement
                response = RequestTokenizer.getToken(connection.getMessageWithinTimeout(this.getTimeout()));

                // making sure acknowledgement was receieved
                if(response instanceof AckToken){
                    // sending the file to the dstore
                    byte[] fileContent = fileInput.readNBytes(filesize);
                    connection.sendBytes(fileContent);

                    // closing streams
                    connection.close();
                    fileInput.close();
                }
                // invalid response received
                else{
                    // closing streams
                    connection.close();
                    fileInput.close();

                    // throwing exception
                    throw new InvalidMessageException(response.message, connection.getPort());
                }
            }
            // invalid response received
            else{
                // closing streams
                connection.close();
                fileInput.close();

                // throwing exception
                throw new InvalidMessageException(response.message, connection.getPort());
            }
        }
        catch(Exception e){
            // closing connection
            connection.close();

            // throwing exception
            throw e;
        }
    }

    //////////
    // LOAD //
    //////////

//    /**
//     * 处理 LOAD 请求。
//     *
//     * @param filename 要删除的文件的名称。
//     * @param isReload 确定此 LOAD 操作是否为 Reload。
//     *
//     * @return 加载文件数据的字节数组。
//     *
//     * @throws MessageSendException 如果无法通过连接发送消息。
//     * @throws MessageReceievedException 如果无法通过连接接收消息。
//     * @throws NotEnoughDstoresException：如果没有足够的 Dstores 连接到控制器来处理请求。
//     * @throws FileDoesNotExist 如果系统中没有具有所提供文件名的文件。
//     * @throws InvalidMessageException 如果在通信过程中收到错误形式的消息。
//     */
    public byte[] loadFile(String filename, boolean isReload) throws Exception{
        // 收集协议命令
        String message = "";
        if(!isReload){
            message = Protocol.getLoadMessage(filename);
        }
        else{
            message = Protocol.getReloadMessage(filename);
        }

        // sending LOAD message to controller
        this.getServerConnection().sendMessage(message);

        // gathering response
        Token response = RequestTokenizer.getToken(this.getServerConnection().getMessageWithinTimeout(this.getTimeout()));
        
        // LOAD_FROM
        if(response instanceof LoadFromToken){
            // gathering the token
            LoadFromToken loadFromToken = (LoadFromToken) response;

            // LOADING FILE
            try{
                // loading file from Dstore
                byte[] fileContent = this.loadFileFromDstore(loadFromToken.port, filename, loadFromToken.filesize);

                // logging operation complete
                this.handleEvent(new LoadCompleteEvent(filename));

                // returning the file content
                return fileContent;
            }
            // unable to load file content
            catch(Exception e){
                // Logging error
                this.handleError(new FileLoadException(filename, loadFromToken.port, e));

                // reloading if data could not be gathered
                return this.loadFile(filename, true);
            }
        }

        // ERROR_NOT_ENOUGH_DSTORES
        else if(response instanceof ErrorNotEnoughDStoresToken){
            throw new NotEnoughDstoresException();
        }

        // ERROR_FILE_DOES_NOT_EXIST
        else if(response instanceof ErrorFileDoesNotExistToken){
            throw new FileDoesNotExistException(filename);
        }

        // ERROR_LOAD
        else if(response instanceof ErrorLoadToken){
            throw new NoValidDstoresException();
        }

        // Invalid Response
        else{
            throw new InvalidMessageException(response.message, this.getServerPort());
        }
    }

//    /**
//     * 从提供的 Dstore 加载给定文件。
//     *
//     * @param port 从中加载文件的端口。
//     * @param filename 从 Dstore 加载的文件的名称。
//     * @param filesize 正在加载的文件的大小。
//     * @return 从 Dstore 加载的文件作为字节数组。
//     *
//     * @throws MessageSendException 如果无法通过连接发送消息。
//     * @throws MessageReceievedException 如果无法通过连接接收消息。
//     */
    private byte[] loadFileFromDstore(int port, String filename, int filesize) throws Exception{
        // setting up the connection
        Connection connection = new Connection(this.getNetworkInterface(), port, ServerType.DSTORE);
        
        // adding connection to client
        this.getSecondaryServerConnections().add(connection);

        // sending JOIN_CLIENT message
        connection.sendMessage(Protocol.getJoinClientMessage());

        try{
            // waiting for acknowledgement
            Token response = RequestTokenizer.getToken(connection.getMessageWithinTimeout(this.getTimeout()));

            // making sure response is JOIN_ACK
            if(response instanceof JoinAckToken){
                 // sending LOAD_DATA message
                connection.sendMessage(Protocol.getLoadDataMessage(filename));

                // reading file data
                byte[] fileContent = connection.getNBytesWithinTimeout(filesize, this.getTimeout());

                // closing connection
                connection.close();

                return fileContent;
            }
            // invalid response received
            else{
                // closing streams
                connection.close();

                // throwing exception
                throw new InvalidMessageException(response.message, connection.getPort());
            }
        }
        catch(Exception e){
            // closing connection
            connection.close();

            // throwing exception
            throw e;
        }
    }


    ////////////
    // REMOVE //
    ////////////

///**
//     * 处理 REMOVE 请求。
//     *
//     * @param filename 要删除的文件的名称。
//     *
//     * @throws MessageSendException 如果无法通过连接发送消息。
//     * @throws MessageReceievedException 如果无法通过连接接收消息。
//     * @throws NotEnoughDstoresException：如果没有足够的 Dstores 连接到控制器来处理请求。
//     * @throws FileDoesNotExist 如果系统中没有具有所提供文件名的文件。
//     * @throws InvalidMessageException 如果在通信过程中收到错误形式的消息。
//     */
    public void removeFile(String filename) throws Exception{
        // sending remove to controller
        this.getServerConnection().sendMessage(Protocol.getRemoveMessage(filename));

        // gathering response
        Token response = RequestTokenizer.getToken(this.getServerConnection().getMessageWithinTimeout(this.getTimeout()));

        // REMOVE
        if(response instanceof RemoveCompleteToken){
            // logging operation complete
            this.handleEvent(new RemoveCompleteEvent(filename));
        }

        // ERROR_NOT_ENOUGH_DSTORES
        else if(response instanceof ErrorNotEnoughDStoresToken){
            throw new NotEnoughDstoresException();
        }

        // ERROR_FILE_DOES_NOT_EXIST
        else if(response instanceof ErrorFileDoesNotExistToken){
            throw new FileDoesNotExistException(filename);
        }

        // Invalid Response
        else{
            throw new InvalidMessageException(response.message, this.getServerPort());
        }
    }

    //////////
    // LIST //
    //////////

//    /**
//     * Handles a LIST request.
//     *
//     * @throws MessageSendException If a message couldn't be sent through the connection.
//     * @throws MessageReceievedException If a message could not be receieved through the connection.
//     */
    public HashMap<String, Integer> getFileList() throws Exception{
        // sending message to Controller
        this.getServerConnection().sendMessage(Protocol.getListMessage());

        // gathering response
        Token response = RequestTokenizer.getToken(this.getServerConnection().getMessageWithinTimeout(this.getTimeout()));

        // LIST file1 file2 ...
        if(response instanceof ListFilesToken){
            // getting the file list token
            ListFilesToken listFilesToken = (ListFilesToken) response;

            // logging operation complete
            this.handleEvent(new ListCompleteEvent());

            // returning the list of files
            return listFilesToken.files;
        }

        // ERROR_NOT_ENOUGH_DSTORES
        else if(response instanceof ErrorNotEnoughDStoresToken){
            throw new NotEnoughDstoresException();
        }

        // Invalid response
        else{
            throw new InvalidMessageException(response.message, this.getServerPort());
        }
    }
}