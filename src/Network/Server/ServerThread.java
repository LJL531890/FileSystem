package Network.Server;

import DS.Protocol.Exception.RequestHandlerDisabledException;
import DS.Protocol.Token.RequestTokenizer;
import DS.Protocol.Token.Token;
import Network.Connection;
import Network.Protocol.Exception.ConnectionTerminatedException;

/**
 * 表示服务器与连接对象之间的连接。
 *
 * 是一个线程，以便可以在新线程上处理来自服务器的请求，这允许一个服务器为多个连接对象提供服务。
 *
 * 当线程运行时，连接会等待请求，然后将此请求传递到底层服务器的请求处理程序。
 */
public class ServerThread extends Thread {
    
    // member variables
    private Server server;
    private Connection connection;
    private boolean isActive;

    /**
     * Class constructor.
     * 
     * @param server 连接中涉及的 Server 对象。
     * @param connection The conection between the Server and the connector.
     */
    public ServerThread(Server server, Connection connection){
        this.server = server;
        this.connection = connection;
        this.isActive = true;
    }

    /**
     * Method run when thread started.
     */
    public void run(){
        // listening for future requests
        this.waitForRequest();
    }

    /**
     * Waits for an incoming request.
     */
    public void waitForRequest(){
        try{
            while(this.connection.isOpen()){
                // getting request
                String request = this.connection.getMessage();

                // tokenizing request
                Token requestToken = RequestTokenizer.getToken(request);

                // handling request (need loop as the request handler could be disabled)
                while(true){
                    try{
                        // trying to handle
                        this.server.getRequestHandler().handleRequest(this.connection, requestToken);

                        // breaking out of loop if successful
                        break;
                    }
                    catch(RequestHandlerDisabledException e){
                        // request handler not enabled - wait and try again
                        Thread.onSpinWait();
                    }
                }
                
            }
        }
        catch(Exception e){
            // error getting request = need to terminate connection
            this.server.handleError(new ConnectionTerminatedException(this.connection, e));
        }
    }

    /**
     * Called to stop the connection for looking for futher requests.
     * 
     * i.e., It ends the connection.
     */
    public void close(){
        this.isActive = false;
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////
    
    public Connection getConnection(){
        return this.connection;
    }

    public boolean isActive(){
        return this.isActive;
    }
}