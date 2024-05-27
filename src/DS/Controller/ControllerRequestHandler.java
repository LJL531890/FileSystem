package DS.Controller;

import java.util.ArrayList;
import java.util.HashMap;

import DS.Controller.Index.State.OperationState;
import DS.Protocol.Protocol;
import DS.Protocol.Event.Operation.ListCompleteEvent;
import DS.Protocol.Event.Operation.LoadCompleteEvent;
import DS.Protocol.Event.Operation.RemoveCompleteEvent;
import DS.Protocol.Event.Operation.StoreCompleteEvent;
import DS.Protocol.Exception.*;
import DS.Protocol.Token.*;
import DS.Protocol.Token.TokenType.*;
import Network.*;
import Network.Client.Client.ClientType;
import Network.Protocol.Event.ServerConnectionEvent;
import Network.Protocol.Exception.*;
import Network.Server.RequestHandler;

/**
 * 处理 DSClient 发送到 Controller 的请求。
 */
public class ControllerRequestHandler extends RequestHandler{

    // member variables
    private Controller controller;

    /**
     * Class constructor.
     * 
     * @param controller The Controller associated with the request handler.
     */
    public ControllerRequestHandler(Controller controller){
        // initializing在这种情况下，如果构造函数没有显式调用父类的构造函数`super(controller)`，Java会隐式调用父类的无参构造函数。如果父类没有无参构造函数，编译器会报错。
        //
        //因此，如果父类没有无参构造函数，就需要显式调用父类的构造函数来传递参数。如果父类有无参构造函数，那么在这种情况下只留下`this.controller = controller;
        super(controller);
        this.controller = controller;
    }

    //////////
    // MAIN //
    //////////

    /**
     * 处理给定的请求。
     * 
     *@param connection 与请求关联的连接。
     * @param request 要处理的令牌化请求。
     */
    public void handleRequestAux(Connection connection, Token request){
        // handling request
        try{
            // JOIN_DSTORE
            if(request instanceof JoinDstoreToken){
                JoinDstoreToken joinToken = (JoinDstoreToken) request;
                this.handleJoinDstoreRequest(connection, joinToken.port);
            }

            // JOIN_CLIENT
            else if(request instanceof JoinClientToken){
                this.handleJoinClientRequest(connection, request);
            }

            // JOIN_CLIENT_HEARTBEAT
            else if(request instanceof JoinClientHeartbeatToken){
                JoinClientHeartbeatToken joinToken = (JoinClientHeartbeatToken) request;
                this.handleJoinClientHeartbeatRequest(connection, joinToken);
            }

            // STORE
            else if(request instanceof StoreToken){
                StoreToken storeToken = (StoreToken) request;
                this.handleStoreRequest(connection, storeToken.filename, storeToken.filesize);
            }

            // STORE_ACK
            else if(request instanceof StoreAckToken){
                StoreAckToken storeAckToken = (StoreAckToken) request;
                this.handleStoreAckRequest(connection, storeAckToken.filename); 
            }

            // LOAD
            else if(request instanceof LoadToken){
                LoadToken loadToken = (LoadToken) request;
                this.handleLoadRequest(connection, loadToken.filename, false);
            }
            
            // RELOAD
            else if(request instanceof ReloadToken){
                ReloadToken reloadToken = (ReloadToken) request;
                this.handleLoadRequest(connection, reloadToken.filename, true);
            }

            // REMOVE
            else if(request instanceof RemoveToken){
                RemoveToken removeToken = (RemoveToken) request;
                this.handleRemoveRequest(connection, removeToken.filename);
            }

            // REMOVE_ACK
            else if(request instanceof RemoveAckToken){
                RemoveAckToken removeAckToken = (RemoveAckToken) request;
                this.handleRemoveAckRequest(connection, removeAckToken.filename);
            }

            // ERROR_FILE_DOES_NOT_EXIST
            else if(request instanceof ErrorFileDoesNotExistFilenameToken){
                // nothing to do ...
            }

            // LIST
            else if(request instanceof ListToken){
                this.handleListRequest(connection);
            }

            // LIST OF FILES (rebalancing)
            else if(request instanceof ListFilesToken){
                ListFilesToken listFilesToken = (ListFilesToken) request;
                this.handleListFilesRequest(connection, listFilesToken.files);
            }

            // REBALANCE COMPLETE
            else if(request instanceof RebalanceCompleteToken){
                this.handleRebalanceCompleteRequest(connection);
            }

            // Invalid Request
            else{
                this.handleInvalidRequest(connection, request);
            }
        }
        catch(Exception e){
            // loggiing error
            this.controller.handleError(new RequestHandlingException(request.message, e));

            // Handling Specific Cases //

            try{
                // Dstore port already in use
                if(e instanceof DstorePortInUseException){
                    // sending error message to Dstore
                    connection.sendMessage(Protocol.getErrorDstorePortInUseMessage());
                }
                // Not enough Dstores
                if(e instanceof NotEnoughDstoresException){
                    // sending error message to client
                    connection.sendMessage(Protocol.getErrorNotEnoughDstoresMessage());
                }
                // File already exists
                else if(e instanceof FileAlreadyExistsException){
                    // sending error message to client
                    connection.sendMessage(Protocol.getErrorFileAlreadyExistsMessage());
                }
                // File does not exist
                else if(e instanceof FileDoesNotExistException){
                    // sending error message to client
                    connection.sendMessage(Protocol.getErrorFileDoesNotExistMessage());
                }
                // No valid Dstores
                else if(e instanceof NoValidDstoresException){
                    // sending error message to client
                    connection.sendMessage(Protocol.getErrorLoadMessage());
                }
            }
            catch(MessageSendException ex){
                this.controller.handleError(ex);
            }
        }
    }

    /////////////////
    // JOIN_DSTORE //
    /////////////////

/**
     * 处理JOIN_DSTORE请求。
     *
     * @param connection 与请求关联的连接。
     * @param dstorePort 加入系统的 Dstore 的端口号。
     * @throws DstorePortInUseException 如果 Dstore 尝试加入的端口已在使用中。
     */
    public void handleJoinDstoreRequest(Connection connection, int dstorePort) throws Exception{
        // addding the Dstore to the index
        this.controller.getIndex().addDstore(dstorePort, connection);

        // 服务器在这种情况下扮演着管理和协调不同组件之间通信和操作的角色。通过服务器，可以实现以下功能：
        //- 管理和跟踪连接：服务器可以维护连接列表，记录哪些Dstore已连接到系统。
        //- 发送和接收消息：服务器可以处理来自不同组件的请求，并向它们发送响应消息。
        //- 协调系统操作：服务器可以协调系统中不同组件的操作，如重新平衡系统、处理请求等。
        //
        //因此，服务器在这种情况下是必要的，以确保系统中的各个组件能够协同工作，实现系统的正常运行和功能。
        this.controller.getServerConnections().add(connection);

        // sending JOIN_ACK to Dstore
        connection.sendMessage(Protocol.getJoinAckMessage());

        // rebalancing system
        this.controller.getRebalancer().rebalance();
        //```plaintext
        //在这段代码中，`this.controller.getServerConnections().add(connection)`的作用是将新加入的Dstore的连接添加到服务器连接列表中，以便跟踪所有连接到服务器的Dstore。`connection.sendMessage(Protocol.getJoinAckMessage())`用于向新加入的Dstore发送JOIN_ACK消息，以确认其成功加入系统。`this.controller.getRebalancer().rebalance()`用于在新Dstore加入后重新平衡系统。
        //
        //如果只保留`this.controller.getIndex().addDstore(dstorePort, connection)`，而不包含上述操作，理论上也可以。但是，根据系统的需求和设计，可能需要执行其他操作，如维护连接列表、发送确认消息和重新平衡系统。因此，根据具体情况，可以根据需求来决定是否保留这些额外的操作。
        //```
    }

    /////////////////
    // JOIN_CLIENT //
    /////////////////

/**
     * 处理JOIN_CLIENT请求。
     *
     * @param connection：请求来自的连接。
     * @param request 请求令牌。
     */
    public void handleJoinClientRequest(Connection connection, Token request) throws Exception{
        // adding the client to the server
        this.controller.getClientConnections().add(connection);

        // logging
        this.controller.handleEvent(new ServerConnectionEvent(ClientType.CLIENT, connection.getPort()));

        // sending JOIN_ACK to Client
        connection.sendMessage(Protocol.getJoinAckMessage());
    }

    ///////////////////////////
    // JOIN_CLIENT_HEARTBEAT //
    ///////////////////////////

  /**
     * 处理JOIN_CLIENT_HEARTBEAT请求。
     *
     * @param connection：请求来自的连接。
     * @param joinToken 请求令牌。
     */
    public void handleJoinClientHeartbeatRequest(Connection connection, JoinClientHeartbeatToken joinToken) throws Exception{
        // adding the client to the server
        this.controller.getClientHeartbeatConnections().put(connection, joinToken.port);

        // sending JOIN_ACK to Client
        connection.sendMessage(Protocol.getJoinAckMessage());
    }

    ///////////
    // STORE //
    ///////////

///**
//     * 处理在系统中存储文件的请求。
//     *
//     * @param connection 与请求关联的连接。
//     * @param filename 正在存储的文件的名称。
//     * @param filesize 正在存储的文件的大小。
//     * @throws NotEnoughDstoresException：如果没有足够的 Dstores 连接到 Controller 来处理请求。
//     * @throws FileAlreadyExistsException 如果索引中已有此名称下的文件。
//     * @throws MessageSendException 如果无法通过连接发送消息。
//     * @throws OperationTimeoutException：如果存储操作未在控制器超时期限内完成。
//     */
    public void handleStoreRequest(Connection connection, String filename, int filesize) throws Exception{
        // starting to store the file
        ArrayList<Integer> dstores = this.controller.getIndex().startStoring(filename, filesize);

        // sending the message to the client
        connection.sendMessage(Protocol.getStoreToMessage(dstores));

        // waiting for the store to be complete
        this.controller.getIndex().waitForFileState(filename, OperationState.STORE_ACK_RECIEVED, this.controller.getTimeout());

        // store complete, sending STORE_COMPLETE message to Client
        connection.sendMessage(Protocol.getStoreCompleteMessage());

        // logging
        this.controller.handleEvent(new StoreCompleteEvent(filename, filesize));
    }

/**
     * 处理STORE_ACK令牌。
     *
     * @param connection 接收STORE_ACK的连接。
     * @param filename 与STORE_ACK关联的文件名。
     */
    private void handleStoreAckRequest(Connection connection, String filename){
        this.controller.getIndex().storeAckRecieved(connection, filename);
        //`handleStoreRequest`函数涉及到存储文件的完整过程，包括启动存储、向客户端发送消息、等待存储完成、发送存储完成消息以及记录存储完成事件。这些步骤涉及到文件的实际存储过程、与客户端的通信以及系统状态的更新，因此需要多个步骤来完成整个存储过程。
        //
        //相比之下，`handleStoreAckRequest`函数只涉及处理接收到的`STORE_ACK`令牌，即确认文件存储的消息。这个操作相对简单，只需要将接收到的`STORE_ACK，
    }

    //////////
    // LOAD //
    //////////

//    /**
//* 处理 LOAD 请求。
//     *
//* @param connection 与请求关联的连接。
//* @param filename 正在加载的文件的名称。
//* @throws NotEnoughDstoresException：如果没有足够的 Dstores 连接到控制器来处理请求。
//* @throws FileDoesNotExist：如果索引中没有具有此名称的文件。
//* @throws NoValidDstoresException：如果没有剩余的有效 Dstores 可以加载文件（用尽所有可能的 Dstore）。
//* @throws MessageSendException 如果无法通过连接发送消息。
//     */
    private void handleLoadRequest(Connection connection, String filename, boolean isReload) throws Exception{
        // 获取要存储的 dstore
        int dstoreToLoadFrom = this.controller.getIndex().getDstoreToLoadFrom(connection, filename, isReload);

        // getting the file size
        int filesize = this.controller.getIndex().getFileSize(filename);

        // sending LOAD_FROM to the Client
        connection.sendMessage(Protocol.getLoadFromMessage(dstoreToLoadFrom, filesize));

        // logging
        this.controller.handleEvent(new LoadCompleteEvent(filename));
    }


    ////////////
    // REMOVE //
    ////////////


///**
//     * 处理 REMOVE 请求。
//     *
//     * @param connection 与请求关联的连接。
//     * @param filename 要删除的文件的名称。
//     * @throws NotEnoughDstoresException：如果没有足够的 Dstores 连接到 Controller 来处理请求。
//     * @throws FileDoesNotExist 如果索引中没有具有此名称的文件。
//     * @throws MessageSendException 如果无法通过连接发送消息。
//     * @throws OperationTimeoutException：如果删除操作未在控制器超时期限内完成。
//     */
    private void handleRemoveRequest(Connection connection, String filename) throws Exception{
        // starting to remove the file
        ArrayList<Connection> dstores = this.controller.getIndex().startRemoving(filename);

        // looping through Dstores
        for(Connection dstore : dstores){
            // sending REMOVE message to the Dstore
            dstore.sendMessage(Protocol.getRemoveMessage(filename));
        }

        // waiting for the REMOVE to be complete
        this.controller.getIndex().waitForFileState(filename, OperationState.REMOVE_ACK_RECIEVED, this.controller.getTimeout());

        // store complete, sending REMOVE_COMPLETEE message to Client
        connection.sendMessage(Protocol.getRemoveCompleteMessage());

        // logging
        this.controller.handleEvent(new RemoveCompleteEvent(filename));
    }

    ////////////////
    // REMOVE ACK //
    ////////////////

    /**
     * 处理REMOVE_ACK请求。
     *
     * @param connection 与请求关联的连接。
     * @param filename 与请求关联的文件的名称。
     */
    private void handleRemoveAckRequest(Connection connection, String filename){
        this.controller.getIndex().removeAckRecieved(connection, filename);
    }

    //////////
    // LIST //
    //////////

    /**
     * 处理 LIST 请求。
     *
     * @param connection 与请求关联的连接。
     * @throws MessageSendException 如果无法通过连接发送消息。
     * @throws NotEnoughDstoresException 如果连接的 Dstores 数量不足，则无法处理请求。
     */
    private void handleListRequest(Connection connection) throws Exception{
        // sending message to client
        connection.sendMessage(Protocol.getListOfFilesMessage(this.controller.getIndex().getFileList()));

        // logging
        this.controller.handleEvent(new ListCompleteEvent());
    }

    ///////////////////
    // LIST OF FILES //
    ///////////////////

 /**
     * 处理从 Dstore 接收文件列表（重新平衡）。
     *
     * @param connection 与消息关联的连接。
     * @param files 消息中提供的文件列表。
     */
    private void handleListFilesRequest(Connection connection, HashMap<String, Integer> files){
        this.controller.getIndex().rebalanceListRecieved(connection, files);
    }

///**
//     * 处理来自 DSTORE 的REBALANCE_COMPLETE消息的接收。
//     *
//     * @param connection 从中接收消息的连接。
//     * @param files 邮件中接收的文件列表。
//     */
    private void handleRebalanceCompleteRequest(Connection connection){
        this.controller.getIndex().rebalanceCompleteReceived(connection);
    }

    /////////////
    // INVALID //
    /////////////

    /*    *//**
     * 处理无效请求。
     *
     * @param connection 与请求关联的连接。
     * @param请求：请求的标记化形式。
     * @throws InvalidMessageException 总是在请求被“handeled”后抛出。
     */
    public void handleInvalidRequest(Connection connection, Token request) throws Exception{
        // Nothing to do...

        // throwing exception
        throw new InvalidMessageException(request.message, connection.getPort());
    }
}