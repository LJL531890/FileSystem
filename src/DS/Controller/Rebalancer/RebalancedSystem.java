package DS.Controller.Rebalancer;

import java.util.HashMap;

import DS.Protocol.Token.TokenType.FileToSend;

/**
 * 表示已经重新平衡的系统，它存储了关于重新平衡后系统内文件分布的信息以及重新平衡的具体信息。
 */
public class RebalancedSystem{

    // member variables
    private System system; 
    private HashMap<Integer, RebalanceInformation> rebalanceInformation;

    /**
     * 类构造函数。
     *
     * @param system 系统在重新平衡之后的状态。
     * @param rebalanceInformation 为重新平衡系统而进行的更改。
     */
    public RebalancedSystem(System system, HashMap<Integer, RebalanceInformation> rebalanceInformation){
        // initializing
        this.system = system;
        this.rebalanceInformation = rebalanceInformation;
    }

    ///////////////////////////////////////
    // CONFIGURING REBALANCE INFORMATION //
    ///////////////////////////////////////

//    /**
//     * 将要删除的文件添加到列表中。
//     *
//     * @param fileToRemove 要删除的文件。
//     */
    public void addFileToSend(Integer dstoreSendingFile, FileToSend fileToSend){
        // adding the file to send to the rebalance information
        this.rebalanceInformation.get(dstoreSendingFile).getFilesToSend().add(fileToSend);

        // updating the file distribution
        this.system.updateFromRebalanceInformation(this.rebalanceInformation);
    }

    /**
     * Adds a file to be removed to the list.
     * 
     * @param fileToRemove The file to be removed.
     */
    public void addFileToRemove(Integer dstoreRemovingFile, String fileToRemove){
        // adding the file to send to the rebalance information
        this.rebalanceInformation.get(dstoreRemovingFile).getFilesToRemove().add(fileToRemove);

        // updating the file distribution
        this.system.updateFromRebalanceInformation(this.rebalanceInformation);
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public System getSystem(){
        return this.system;
    }

    public HashMap<Integer, RebalanceInformation> getRebalanceInformation(){
        return this.rebalanceInformation;
    }

    public void setRebalanceInformation(HashMap<Integer, RebalanceInformation> rebalanceInformation){
        this.rebalanceInformation = rebalanceInformation;
    }
}
