package Network.Protocol.Exception;

/**
 * 表示已由网络进程处理的 NetworkException。
 */
public class HandeledNetworkException {

    // member variables
    private NetworkException exception;
    
    /**
     * Class Constructor.
     * 
     * @param exception The NetworkException that has been handeled.
     */
    public HandeledNetworkException(NetworkException exception){
        this.exception = exception;
    }
    /**
     * Converts the exception to a string.
     */
    public String toString(){
        // returning string
        return this.exception.toString();
    }

    /**
     * Gets the exception that has been handeled.
     * 
     * @return The exception that has been handeled.
     */
    public NetworkException getException(){
        return this.exception;
    }
}
