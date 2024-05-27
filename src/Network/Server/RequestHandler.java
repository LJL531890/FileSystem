package Network.Server;

import DS.Protocol.Exception.RequestHandlerDisabledException;
import DS.Protocol.Token.Token;
import Network.Connection;

/**
 * 抽象类，用于表示处理请求的服务器组件。
 *
 * 服务器处理程序实现“handleRequest”方法，并从其连接或输入通道接收请求。
 *
 * 每种类型的服务器都有自己类型的 RequestHandler，因为它们都以不同的方式处理请求。
 */
public abstract class RequestHandler {

    // member variables
    private Server server;
    private boolean enabled;

    ////////////////////////
    // CLASS CONSTRUUCTOR //
    ////////////////////////

    /**
     * Class constructor.
     * 
     * @param server The Server this request handler is handling requests for.
     */
    public RequestHandler(Server server){
        // initializing
        this.server = server;
        this.enabled = true;
    }

    //////////////////////
    // REQUEST HANDLING //
    //////////////////////

    /**
* 处理新线程上的 give 请求。在新线程上运行句柄请求方法。
     *
     * @param connection 与请求关联的连接。
     * @param请求 正在处理的请求。
     * @throws RequestHandlerDisabledException 如果请求来自客户端，并且请求处理程序被禁用。
     */
    public void handleRequest(Connection connection, Token request) throws Exception{
        // throwing exception if handler is not enabled and request is from client
        if(!this.isEnabled() && this.server.getClientConnections().contains(connection)){
            throw new RequestHandlerDisabledException();
        }

        // 可为请求线程运行
        Runnable runnable = () -> {
            // handling the request
            this.handleRequestAux(connection, request);
        };

        // starting a thread to handle the request
        new Thread(runnable).start();
    }

    /**
     * Handles a given request.
     * 
     * @param request Tokenized request to be handled.
     * @param connection The connection associated with the request.
     */
    public abstract void handleRequestAux(Connection connection, Token request);

    ////////////////////////////
    // ENABLING AND DISABLING //
    ////////////////////////////

    /**
     * Enables the request handler.
     * 
     * The request handler will continue serving requests.
     */
    public void enable(){
        this.enabled = true;
    }

    /**
     * Disables the request handler.
     * 
     * The request handler will finish serving it's current request and
     * serve no further requests until it is enabled again.
     */
    public void disable(){
        this.enabled = false;
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public boolean isEnabled(){
        return this.enabled;
    }
}
