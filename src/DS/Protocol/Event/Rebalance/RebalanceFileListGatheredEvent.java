package DS.Protocol.Event.Rebalance;

import Network.Protocol.Event.NetworkEvent;
/**
 * 从 Dstores 收集文件列表的情况的事件
 * 在重新平衡期间。
 */
public class RebalanceFileListGatheredEvent extends NetworkEvent{

    /**
     * Class constructor.
     * 
     */
    public RebalanceFileListGatheredEvent(){
        super("File list gathered from all Dstores for Rebalance.");
    }
}
