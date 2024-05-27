package Network.Protocol.Event;

/**
 *表示系统内操作完成的事件.
 */
public abstract class OperationCompleteEvent extends NetworkEvent{

    /**
     * Class constroctor.
     * 
     * @param message The String message associated with the event.
     */
    public OperationCompleteEvent(String message){
        super(message);
    }
}
