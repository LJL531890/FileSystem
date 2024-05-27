package DS.Controller.Index;

/**
 * 包含不同类型状态的枚举类
 * 可以存在于索引中的对象中。
 */
public class State{

    /**
     * 表示文件相对于可以对文件执行的操作的状态。
     */
    public enum OperationState{
        // states
        STORE_IN_PROGRESS("Store In Progress"),  //存储进行中
        STORE_ACK_RECIEVED("Store Acknowledgement Recieved"),//Store（存储） 收到确认
        STORE_COMPLETE("Store Complete"),
        REMOVE_IN_PROGRESS("Remove In Progress"),
        REMOVE_ACK_RECIEVED("Remove Acknowledgement Recieved"),
        REMOVE_COMPLETE("Remove Complete"),
        IDLE("Idle");

        private String state;

        private OperationState(String state){
            this.state = state;
        }
    
        /**
         * Converts the state method to a string.
         * @return String equivalent of the state.
         */
        @Override
        public String toString(){
            return this.state;
        }
    }

    /**
     * 表示系统在再平衡操作方面的状态
     */
    public enum RebalanceState{
        //states
        REBALANCE_LIST_IN_PROGRESS("Rebalance List In Progess"),//Progess 中的再平衡列表
        REBALANCE_LIST_RECIEVED("Rebalance List Recieved"),//已收到的再平衡清单
        REBALANCE_MOVE_IN_PROGRESS("Rebalance Move In Progress"),//再平衡行动进行中
        REBALANCE_COMPLETE_RECIEVED("Rebalance Complete Recieved"),//再平衡完成 已收到
        IDLE("Idle");//空闲状态

        private String state;

        private RebalanceState(String state){
            this.state = state;
        }
    
        /**
         * Converts the state method to a string.
         * @return String equivalent of the state.
         */
        @Override
        public String toString(){
            return this.state;
        }
    }
}