package DS.Controller.Rebalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import DS.Controller.Controller;
import DS.Controller.Index.DstoreIndex;
import DS.Controller.Index.State.RebalanceState;
import DS.Protocol.Exception.NetworkTimeoutException;
import DS.Protocol.Exception.NotEnoughDstoresException;
import DS.Protocol.Exception.RebalanceAlreadyInProgressException;
import DS.Protocol.Protocol;
import DS.Protocol.Event.Rebalance.RebalanceCompleteEvent;
import DS.Protocol.Event.Rebalance.RebalanceFileListGatheredEvent;
import DS.Protocol.Event.Rebalance.RebalanceNotRequiredEvent;
import DS.Protocol.Event.Rebalance.RebalanceStartedEvent;
import DS.Protocol.Exception.RebalanceFailureException;
import DS.Protocol.Token.TokenType.FileToSend;
import Network.Protocol.Exception.MessageSendException;
import Network.Protocol.Exception.NetworkException;

/**
 * 处理文件系统的重新平衡。负责执行重平衡操作的类。
 */
public class Rebalancer extends Thread{
    
    // member variables
    private Controller controller;
    /**
     * 类构造函数。
     *
     * @param控制器 与此 Rebalancer 关联的控制器。
     */
    public Rebalancer(Controller controller){
        // initializing
        this.controller = controller;
    }

    /**
     * 线程启动时的方法运行。在线程启动时调用，调用 waitForRebalance 方法等待重新平衡。
     */
    public void run(){
        // waiting for rebalance
        this.waitForRebalance();
    }

    ///////////////////////////
    // WAITING FOR REBALANCE //
    ///////////////////////////

    /**
     * 持续等待“再平衡期”过去，然后再重新平衡系统。持续等待重新平衡周期结束，然后执行重新平衡。
     */
    private void waitForRebalance(){
        while(controller.isActive()){
            try{
                // 等待重新平衡期的时间量
                Thread.sleep(this.controller.getRebalancePeriod());

                // 再平衡系统
                this.rebalance();
            }
            catch(Exception e){
                // handling failure through controller
                this.controller.handleError(new RebalanceFailureException(e));
            }
        }
    }

    ///////////////////////////
    // MAIN REBALANCE METHOD //
    ///////////////////////////

    /**
     * 重新平衡系统。
     *
     * @throws NotEnoughDstoresException 如果没有足够的 Dstores 连接到系统来执行重新平衡操作。
     * @throws RebalanceAlreadyInProgressException 如果 progess 中已经有重新平衡操作。
     * @throws NetworkTimeoutException 如果系统在超时内未变为空闲状态。
     * @throws MessageSendException：如果无法通过连接通道发送消息
     */
    public void rebalance() throws NetworkException{

        // 重新平衡开始的事件
        this.controller.handleEvent(new RebalanceStartedEvent());

        // 收集文件列表

        //开始重新平衡列表，* 禁用控制器请求处理程序，等待系统变为 IDLE 并将索引更新为 REBALANCE_LIST_IN_PROGRESS。
        this.controller.getIndex().startRebalanceList();

        // 向 dstores 发送 LIST 请求
        for(DstoreIndex dstore : this.controller.getIndex().getDstores()){
            dstore.getConnection().sendMessage(Protocol.getListMessage());
        }

        // 等待所有 Dstores 响应
        this.controller.getIndex().waitForRebalanceState(RebalanceState.REBALANCE_LIST_RECIEVED, this.controller.getTimeout());
    
        // 创建系统实例
        System system = new System(this.controller.getMinDstores(), this.controller.getIndex().getFileDistribution());

        // 收集的文件列表的事件
        this.controller.handleEvent(new RebalanceFileListGatheredEvent());

        // 检查系统是否平衡

        if(!system.isBalanced()){

            // REBALANCING //

            // 开始搬家过程， * 开始系统重新平衡的移动阶段。更新索引
            //     * REBALANCE_MOVE_IN_PROGRESS。
            this.controller.getIndex().startRebalanceMove();

            //计算调整
            RebalancedSystem rebalancedSystem = Rebalancer.getRebalancedSystem(system);

            // 发送再平衡消息
            for(Integer dstore : rebalancedSystem.getRebalanceInformation().keySet()){
                // 形成信息
                String rebalanceMessage = rebalancedSystem.getRebalanceInformation().get(dstore).getRebalanceMessage();

                // 发送消息
                this.controller.getIndex().getIndexFromPort(dstore).getConnection().sendMessage(rebalanceMessage);
            }

            // 等待重新平衡完成响应
            this.controller.getIndex().waitForRebalanceState(RebalanceState.REBALANCE_COMPLETE_RECIEVED, this.controller.getTimeout());

            // REBALANCE COMPLETE

            // 更新索引
            this.controller.getIndex().setFileDistribution(rebalancedSystem.getSystem().getFileDistribution());

            //创建事件以显示重新平衡成功
            this.controller.handleEvent(new RebalanceCompleteEvent());
        }
        else{
            //不需要重新平衡的事件
            this.controller.handleEvent(new RebalanceNotRequiredEvent());
        }
    }

    /////////////////////////////
    // CALCULATING ADJUSTMENTS //
    /////////////////////////////

/**
     * 为不平衡系统创建一个 RebalancedSystem 对象。
     *
     * @param system 正在重新平衡的不平衡系统。
     * @return 一个 RebalancedSystem 对象，其中包含 rebalanced
     * 文件分发和重新平衡信息。
     */
    private static RebalancedSystem getRebalancedSystem(System system){
        // 创建再平衡信息对象
        HashMap<Integer, RebalanceInformation> rebalanceInformation = new HashMap<Integer, RebalanceInformation>(); 

        // 将每个连接的映射添加到 Rebalance Information 对象中
        for(Integer dstore : system.getDstores()){
            rebalanceInformation.put(dstore, new RebalanceInformation());
        }

        // 创建重新平衡的系统对象
        RebalancedSystem rebalancedSystem = new RebalancedSystem(system, rebalanceInformation);

        //文件 OT 存储的 R 次

        if(!system.filesStoredMinTimes()){
            // 重新平衡系统，用于存储不均匀的文件
            rebalancedSystem = Rebalancer.rebalanceForNotStoredMinTimes(rebalancedSystem);
        }

        // 文件存储不均匀

        if(!system.filesStoredEvenly()){
            // rebalancing system for files not stored evenly
            rebalancedSystem = Rebalancer.rebalanceForNotStoredEvenly(rebalancedSystem);
        }

        return rebalancedSystem;
    }

/**
     * 在一个或多个文件未存储 R 次的情况下重新平衡给定系统。
     *
     * @param rebalancedSystem 由于 not
     * 所有文件都存储了 R 次。
     * @return 已更新的 RebalancedSystem 对象，以便存储所有文件
     * R 次。
     */
    private static RebalancedSystem rebalanceForNotStoredMinTimes(RebalancedSystem rebalancedSystem){
        // 计算再平衡信息

        // 收集未存储R 次的文件列表
        HashMap<String, Integer> filesNotStoredMinTimes = rebalancedSystem.getSystem().getFilesNotStoredMinTimes();

        // 循环访问未存储R 次的文件
        for(String file : filesNotStoredMinTimes.keySet()){
            int neededDstores = rebalancedSystem.getSystem().getMinDstores() - filesNotStoredMinTimes.get(file);

            //收集 dstore 以存储文件
            ArrayList<Integer> dstoresToStoreOn = Rebalancer.getDstoresToSendTo(rebalancedSystem.getSystem(), file,  neededDstores);

            // 收集 dstore 以将文件发送给其他人
            Integer dstoreToSendFrom = rebalancedSystem.getSystem().getDstoreThatHasFile(file);

            // 创建文件以发送对象
            FileToSend fileToSend = new FileToSend(file, rebalancedSystem.getSystem().getFileSize(file), dstoresToStoreOn);

            // 添加 FileToSend 对象以重新平衡信息
            rebalancedSystem.addFileToSend(dstoreToSendFrom, fileToSend);
        }

        // RETURNING RESULT

        return rebalancedSystem;
    }

    /**
     * Rebalances the given system in the case where files are not stored evenly across
     * the Dstores.
     *
     * @param rebalancedSystem The RebalancedSystem object that is being rebalanced due to
     * files not being stored evenly across Dstores.
     * @return 已更新的 RebalancedSystem 对象，以便文件在 dstore 之间均匀存储。
     */
    private static RebalancedSystem rebalanceForNotStoredEvenly(RebalancedSystem rebalancedSystem){
       //计算最小值和最大值
        double r = rebalancedSystem.getSystem().getMinDstores();
        double f = rebalancedSystem.getSystem().getNumberOfFiles();
        double n = rebalancedSystem.getSystem().getNumberOfDstores();
        double averageFiles = r * f / n;
        double minFiles = Math.floor(averageFiles);
        double maxFiles = Math.ceil(averageFiles);

        // iterating over dstores
        for(Integer dstore : rebalancedSystem.getSystem().getDstores()){
            
            //处理文件太少

            while(rebalancedSystem.getSystem().getFilesOnDstore(dstore).size() < minFiles){

                // 查找可能被盗的文件

                FileOnDstore fileOnDstoreToSteal = Rebalancer.getFileToSteal(rebalancedSystem.getSystem(), dstore);

                // CREATING FILE TO SEND OBJECT //

                FileToSend fileToSend = new FileToSend(fileOnDstoreToSteal.getFilename(), fileOnDstoreToSteal.getFileSize(), new ArrayList<Integer>(List.of(dstore)));

                // UPDATING REBALANCE INFORMATION //

                //文件被盗的 dstore 必须发送文件
                rebalancedSystem.addFileToSend(fileOnDstoreToSteal.getDstore(), fileToSend);

//                // 文件被盗的 dstore 必须删除该文件
//                rebalancedSystem.addFileToRemove(fileOnDstoreToSteal.getDstore(), fileToSend.filename);
            }

            // 处理过多文件 //

            while(rebalancedSystem.getSystem().getFilesOnDstore(dstore).size() > maxFiles){

                // FINDING FILE THAT CAN BE SENT //

                FileOnDstore fileOnDstoreToSend = Rebalancer.getFileToSend(rebalancedSystem.getSystem(), dstore);

                // CREATING FILE TO SEND OBJECT //

                FileToSend fileToSend = new FileToSend(fileOnDstoreToSend.getFilename(), fileOnDstoreToSend.getFileSize(), new ArrayList<Integer>(List.of(fileOnDstoreToSend.getDstore())));

                // UPDATING REBALANCE INFORMATION //

//                // the dstore the file is being stolen from must send the file
//                rebalancedSystem.addFileToSend(dstore, fileToSend);

                // the dstore the file is being stolen from must remove the file
                rebalancedSystem.addFileToRemove(dstore, fileToSend.filename);
            }
        }

        // RETURNING RESULT //

        return rebalancedSystem;
    }

    ////////////////////
    // HELPER METHODS //
    ////////////////////

/**
     * 收集指定数量的 Dstore，以便可以发送所提供的文件。文件
     * 可以发送到任何尚未存储的 Dstore。
     *
     * @param系统：系统正在重新平衡。
     * @param filename 发送到其他 Dstore 的文件的名称。
     * @param neededDstores 文件必须发送到的 Dstores 数量。
     * @return 文件可以发送到的存储列表。
     */
    public static ArrayList<Integer> getDstoresToSendTo(System system, String filename, int neededDstores){
        // 获取 dstore 的排序列表
        ArrayList<Integer> sortedDstores = system.getDstoresSortedByFiles();

        //形成文件可以发送到的 dstore 列表
        ArrayList<Integer> dstoresToSendTo = new ArrayList<Integer>();
        for(Integer dstore : sortedDstores){
            // 仅选择尚未存储文件的存储
            if(!system.getFilesOnDstore(dstore).keySet().contains(filename)){
                // 将 DSTOE 添加到列表中
                dstoresToSendTo.add(dstore);

                // 如果找到所需的数量，则从循环中中断。
                if(dstoresToSendTo.size() == neededDstores){
                    break;
                }
            }
        }

        // 返回 dstore 列表
        return dstoresToSendTo;
    }

/**
     * 在提供的系统中查找可能被盗的 Dstore 和文件
     * 由提供的 Dstore。如果文件尚未丢失，则文件可能会被盗
     * 包含在窃取文件的 Dstore 上。
     *
     * @param系统 Dstore 窃取文件的系统包含在其中。
     * @param dstoreStealing Dstore窃取文件。
     * @return 一个 FileOnDstore 对象，表示 Dstore 上可被提供的 Dstore 窃取的文件。
     */
    public static FileOnDstore getFileToSteal(System system, Integer dstoreStealing){
        // 根据文件对 dstore 进行排序
        ArrayList<Integer> sortedDstores = system.getDstoresSortedByFiles();

        // reversing the list - most-to-fewest
        Collections.reverse(sortedDstores);

        // dstore 窃取文件列表
        ArrayList<String> filesOnDstoreStealing = new ArrayList<String>(system.getFilesOnDstore(dstoreStealing).keySet());

        //查找可能被盗的文件（最高级别的 dstore 上尚未在 dstore 窃取中的第一个文件）
        for(Integer dstoreToStealFrom : sortedDstores){
            // 只选择不是偷窃的 dstore
            if(dstoreToStealFrom != dstoreStealing){
                // iterating over this dstore's files
                for(String fileToSteal : system.getFilesOnDstore(dstoreToStealFrom).keySet()){
                    // 查找不在  窃取dstore 上的文件
                    if(!filesOnDstoreStealing.contains(fileToSteal)){
                        // returning the FileOnDstore
                        return new FileOnDstore(dstoreToStealFrom, fileToSteal, system.getFileSize(fileToSteal));
                    }
                }
            }
        }

        // no suitable file found - returning null
        return null;
    }

/**
//     * 在系统内查找 Dstore，并在提供的 Dstore 中查找可发送的文件
//     * 到这个 Dstore。如果文件尚未包含在它要发送到的 Dstore。
//     *
//     * @param系统 发送文件的 Dstore 包含在其中。
//     * @param dstore发送 dstore 发送文件。
//     * @return 一个 FileOnDstore 对象，该对象表示 Dstore 上的一个文件，该文件可以发送到系统内的另一个 Dstore。
//     */
    public static FileOnDstore getFileToSend(System system, Integer dstoreSending){
        // 根据文件对 dstore 进行排序
        ArrayList<Integer> sortedDstores = system.getDstoresSortedByFiles();

        // dstore 发送中的文件列表
        ArrayList<String> filesOnDstoreSending = new ArrayList<String>(system.getFilesOnDstore(dstoreSending).keySet());

        // 查找可能被盗的文件（最高级别的 dstore 上尚未在 dstore 窃取中的第一个文件）
        for(Integer dstoreToSendTo : sortedDstores){
            // 只选择不是偷窃的 dstore
            if(dstoreToSendTo != dstoreSending){
                // 要发送到的 dstore 上的文件列表
                ArrayList<String> filesOnDstoreToSendTo = new ArrayList<String>(system.getFilesOnDstore(dstoreToSendTo).keySet());

                // seeinng if dstore sending has a file that can be send to this dstore
                for(String fileToSend : filesOnDstoreSending){
                    if(!filesOnDstoreToSendTo.contains(fileToSend)){
                        // 找到合适的文件 - 返回 dstore 对象上的文件
                        return new FileOnDstore(dstoreToSendTo, fileToSend, system.getFileSize(fileToSend));
                    }
                }
            }
        }

        // no suitable file found - returning null
        return null;
    }
}