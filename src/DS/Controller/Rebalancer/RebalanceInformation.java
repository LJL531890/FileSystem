package DS.Controller.Rebalancer;

import java.util.ArrayList;

import DS.Protocol.Protocol;
import DS.Protocol.Token.TokenType.FileToSend;

/**
 * 存储在重新平衡期间发送/删除的文件。用于存储在重新平衡期间需要发送或删除的文件信息。
 */
public class RebalanceInformation{

    // member variables
    private ArrayList<FileToSend> filesToSend;
    private ArrayList<String> filesToRemove;

    /**
     * Class constructor.
     * 
     */
    public RebalanceInformation(){
        // initializing
        this.filesToSend = new ArrayList<FileToSend>();;
        this.filesToRemove = new ArrayList<String>();
    }

    ////////////////////
    // HELPER METHODS //
    ////////////////////

    /**
     * 返回此再平衡信息的再平衡消息。
     *
     * @return 此再平衡信息的再平衡消息。
     */
    public String getRebalanceMessage(){
        return Protocol.getRebalanceMessage(this.getFilesToSend(), this.getFilesToRemove());
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public ArrayList<FileToSend> getFilesToSend(){
        return this.filesToSend;
    }

    public ArrayList<String> getFilesToRemove(){
        return this.filesToRemove;
    }
}