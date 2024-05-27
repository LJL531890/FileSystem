package Network.Protocol.Exception;

import Network.Connection;

/**
 * 异常表示两个对象之间的连接终止的情况。
 */
public class ConnectionTerminatedException extends NetworkException{
    
    // member variables
    private Connection connection;

    /**
     * Class constructor.
     */
    public ConnectionTerminatedException(Connection connection){
        super("The connection to connector on port : " + connection.getPort() + " was terminated.");
        this.connection = connection;
    }

    /**
     * Class constructor.
     */
    public ConnectionTerminatedException(Connection connection, Exception cause){
        super("The connection to connector on port : " + connection.getPort() + " was terminated.", cause);
        this.connection = connection;
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public Connection getConnection(){
        return this.connection;
    }
}
