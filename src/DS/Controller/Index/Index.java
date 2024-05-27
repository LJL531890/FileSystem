package DS.Controller.Index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import DS.Controller.Controller;
import DS.Controller.Index.State.OperationState;
import DS.Controller.Index.State.RebalanceState;
import DS.Protocol.Exception.*;
import Network.Connection;
import Network.Client.Client.ClientType;
import Network.Protocol.Event.ServerConnectionEvent;
import Network.Protocol.Exception.NetworkException;

/**
 * 管理索引模型的对象。
 *
 * 该索引跟踪当前连接到
 * 控制器，存储在这些 Dstore 及其对应的文件
 *国家。控制器与索引交互以对系统进行更改
 * 当请求来自客户时。
 *
 * 方法同步，属性可变，支持并发访问
 * 当控制者同时处理来自多个客户端的请求时，可能会发生这种情况。
 */
public class Index {

    // member variables
    private Controller controller;
    private volatile CopyOnWriteArrayList<DstoreIndex> dstores;
    private volatile int minDstores;
    private volatile ConcurrentHashMap<Connection, ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>>> loadRecord;

    /**
     * Class constructor.
     * 
     * Creates a new Index instance to be managed.
     * @param controller The Controller instance that manages this Index.
     */
    public Index(Controller controller){
        this.controller = controller;
        this.minDstores = controller.getMinDstores();
        this.dstores = new CopyOnWriteArrayList<DstoreIndex>();
        this.loadRecord = new ConcurrentHashMap<Connection, ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>>>();
    }


    //////////////////////////
    // CONFIGURING DSTORES ///
    //////////////////////////


    /**
     * 将给定的 Dstore 添加到索引中。
     *
     * @param port 要添加的 Dstore 端口（侦听端口）。
     * @param连接 控制器和 Dstore 之间的连接。
     *
     * @throws DstorePortInUseException：如果 Dstore 的端口已被另一个 Dstore 使用
     */
    public synchronized void addDstore(Integer port, Connection connection) throws DstorePortInUseException{
        // ERROR CHECKING //

        // Dstore Port already in use
        if(this.getDstorePorts().contains(port)){
            throw new DstorePortInUseException(port);
        }

        // CHECKS COMPLETE //

        // adding the dstore to the list of dstores
        this.dstores.add(new DstoreIndex(port, connection));

        // logging
        this.controller.handleEvent(new ServerConnectionEvent(ClientType.DSTORE, port));

        // rebalancing 
        try{
            // carrying out rebalance
            //this.controller.getRebalancer().rebalance();
        }
        catch(Exception e){
            // handling failure
            this.controller.handleError(new RebalanceFailureException(e));
        }
    }

//    /**
//     * Removes the given Dstore from the system.
//     *
//     * @param port The port of the Dstore to be removed from the system (listen port).
//     */
    public synchronized void removeDstore(Connection dstore){
        // removing the Dstore from the list of Dstores
        this.dstores.remove(this.getIndexFromConnection(dstore));
    }


    //////////
    // LIST //
    //////////

    /**
     * 返回存储在系统中的所有文件的列表。
     *
     * @return ArrayList 存储在系统中的所有文件。
     * @throws NotEnoughDstoresException 在连接的 Dstores 不够的情况下。
     */
    public HashMap<String, Integer> getFileList() throws Exception{
        // ERROR CHECKING //

        // not enough dstores
        if(!this.hasEnoughDstores()){
            throw new NotEnoughDstoresException();
        }

        // CHECKS COMPLETE //

        // getting map of file names and sizes
        HashMap<String, Integer> files = new HashMap<String, Integer>();
        for(DstoreIndex dstore : this.dstores){
            for(DstoreFile file : dstore.getFiles()){
                files.put(file.getFilename(), file.getFilesize());
            }
        }

        // returning map
        return files;
    }


    ///////////////////
    // STORING FILES //
    ///////////////////


//   /**
//* 通过将给定文件添加到系统来启动将给定文件添加到系统的过程
//* 索引。
//     *
//* @param file 要添加的文件的名称。
//* @param filesize 要添加的文件的大小（以字节为单位）。
//* @throws NotEnoughDstoresException 如果没有足够的 Dstores 连接到控制器来处理请求。
//* @throws FileAlreadyExists 如果正在存储的文件已存在于索引中。
//     */
    public synchronized ArrayList<Integer> startStoring(String filename, int filesize) throws Exception{
        // ERROR CHECKING //

        // not enough dstores
        if(!this.hasEnoughDstores()){
            throw new NotEnoughDstoresException();
        }

        // file already exists
        if(this.hasFile(filename)){
            throw new FileAlreadyExistsException(filename);
        }

        // ADDING FILE //

        //获取需要存储文件的 dstore 列表。
        ArrayList<Integer> dstoresToStoreOn = this.getDstoresToStoreOn(this.controller.getMinDstores());

        //将存储文件的 dstores 添加到索引中
        for(Integer port : dstoresToStoreOn){
            // 将文件添加到 dstore 状态
            this.getIndexFromPort(port).addFile(filename, filesize);
        }

        // 返回文件需要存储的 dstore 列表
        return dstoresToStoreOn;
    }

    /**
     * 根据从给定 Dstore 接收的STORE_ACK更新索引。
     *
     * @param dstore 接收STORE_ACK的 Dstore 的连接。
     * @param filename STORE_ACK引用的文件名。
     */
    public synchronized void storeAckRecieved(Connection dstore, String filename){
        // updatiing the dstore index
        this.getIndexFromConnection(dstore).updateFileState(filename, OperationState.STORE_ACK_RECIEVED);
    }

    ///////////////////
    // LOADING FILES //
    ///////////////////

    /**
     * 收集应从中加载所提供文件的 Dstore。
     *
     * @param connection 与发送 LOAD 请求的客户端的连接。
     * @param filename 请求的文件的名称。
     * @param isReload Boolean 表示此加载操作是 LOAD 还是 RELOAD。
     * @return 应从中加载文件的 Dstore。
     * @throws NotEnoughDstoresException：如果没有足够的 Dstores 连接到控制器来处理请求。
     */
    public synchronized int getDstoreToLoadFrom(Connection connection, String filename, boolean isReload) throws Exception{

        // ERROR CHECKING //

        // not enough dstores
        if(!this.hasEnoughDstores()){
            throw new NotEnoughDstoresException();
        }

        // file does not exist
        if((!this.hasFile(filename) || !this.fileHasState(filename, OperationState.IDLE))){
            throw new FileDoesNotExistException(filename);
        }

        // GETTING DSTORE //

        // list of all ports
        ArrayList<DstoreIndex> dstores = this.getDstoresStoredOn(filename);

        // load record for the connection
        ConcurrentHashMap<String,CopyOnWriteArrayList<Integer>> fileLoadRecord = this.loadRecord.get(connection);

        // 加载记录为 null（客户端以前从未执行过 LOAD）
        if(fileLoadRecord == null){
            // selecting port to load from
            int selectedPort = dstores.get(0).getPort();

            // creating new file load record
            this.loadRecord.put(connection, new ConcurrentHashMap<String,CopyOnWriteArrayList<Integer>>());

            // adding this file to the load record.
            this.loadRecord.get(connection).put(filename, new CopyOnWriteArrayList<Integer>());

            // adding the port to the fileLoadRecord
            this.loadRecord.get(connection).get(filename).add(selectedPort);

            // returning selected
            return selectedPort;
        }
        // Load record is non-null (Client has done performed a LOAD before)
        else{
            // LOAD 命令
            if(!isReload){
                // 在负载记录中放置/替换映射
                fileLoadRecord.put(filename, new CopyOnWriteArrayList<Integer>());

                // selecting port to load from
                int selectedPort = dstores.get(0).getPort();

                // adding the selected port to the load record
                fileLoadRecord.get(filename).add(selectedPort);

                // returning the selected port
                return selectedPort;
            }
            // RELOAD command
            else{
                // list of attempted ports
                CopyOnWriteArrayList<Integer> attemptedPorts = fileLoadRecord.get(filename);

                // case where attempted is null
                if(attemptedPorts == null){
                    return dstores.get(0).getPort();
                }

                // finding Dstore that has not already been tried
                for(DstoreIndex dstore : dstores){
                    if(!attemptedPorts.contains(dstore.getPort())){
                        // adding the port to the list of attempted ports
                        attemptedPorts.add(dstore.getPort());

                        // returning the port
                        return dstore.getPort();
                    }
                }

                // throwing Exception if no suitable Dstore is found
                throw new NoValidDstoresException();
            }
        }
    }

//    /**
//     * 收集存储在索引中的文件的大小。
//     *
//     * @param filename 正在搜索的文件的名称。
//     * @return 搜索文件的大小（以字节为单位）。
//     * @throws 例外：如果文件未存储在索引中。
//     */
    public synchronized int getFileSize(String filename) throws Exception{
        // file exists
        if(this.hasFile(filename)){
            // gathering a dstore the file is stored on
            int port = this.getDstoresStoredOn(filename).get(0).getPort();

            // returning the size of the file
            return this.getIndexFromPort(port).getFile(filename).getFilesize();
        }
        // file does not exist
        else{
            throw new Exception();
        }
    }


    ////////////////////
    // REMOVING FILES //
    ////////////////////


//    /**
//     * 通过更新系统索引开始从系统中删除给定文件的过程。
//     *
//     * @param file 正在删除的文件。
//     * @throws NotEnoughDstoresException 如果没有足够的 Dstores 连接到控制器来处理请求。
//     * @throws FileDoesNotExistException 如果请求的文件未存储在索引中。
//     */
    public synchronized ArrayList<Connection> startRemoving(String filename) throws Exception{

        // ERROR CHECKING //

        // not enough dstores
        if(!this.hasEnoughDstores()){
            throw new NotEnoughDstoresException();
        }

        // file does not exist
        if((!this.hasFile(filename) || !this.fileHasState(filename, OperationState.IDLE))){
            throw new FileDoesNotExistException(filename);
        }

        // getting the list of dstores the file is stored on
        ArrayList<DstoreIndex> dstores = this.getDstoresStoredOn(filename);
        ArrayList<Connection> connections = new ArrayList<Connection>();

        // updating the states of the dstores
        for(DstoreIndex dstore : dstores){
            // updating the dstore state
            dstore.updateFileState(filename, OperationState.REMOVE_IN_PROGRESS);

            // adding the connection to the list，将与存储文件相关的连接添加到一个 ArrayList 中。这是因为在删除文件的过程中，可能需要与存储文件的不同 Dstores 建立连接以执行删除操作。通过将这些连接存储在 connections 列表中，可以在需要时轻松访问这些连接，并在删除文件的过程中与相应的 Dstores 进行通信。
            connections.add(dstore.getConnection());
        }

        // 返回要从中删除文件的存储
        return connections;
    }

    /**
     * 收到REMOVE_ACK后更新索引。
     *
     * @param dstore 接收REMOVE_ACK的 dstore 的连接。
     * @param filename REMOVE_ACK引用的文件的名称。
     */
    public synchronized void removeAckRecieved(Connection dstore, String filename){
        // updating the dstore index
        this.getIndexFromConnection(dstore).updateFileState(filename, OperationState.REMOVE_ACK_RECIEVED);
    }

    //////////////////////////
    // OPERATION COMPLETION //
    //////////////////////////

//    /**
//     * 等待所有 Dstore 中给定文件的状态与提供的预期状态匹配。将
//     * 仅等待提供的时间。
//     *
//     * @param filename 被跟踪的文件的名称。
//     * @param expectedState 文件的预期状态。//     * @param timeout 跟踪的超时。
//     * @throws OperationTimeoutException 当文件的状态与超时内的预期状态不匹配时。
//     */
    public void waitForFileState(String filename, OperationState expectedState, int timeout) throws Exception{

        // 等待文件具有状态
        
        long timeoutStamp = System.currentTimeMillis() + timeout;

        while(!this.fileHasState(filename, expectedState)){
            if(System.currentTimeMillis() < timeoutStamp){
                Thread.onSpinWait();
            }
            else{
                // timeout occured
                this.handleOperationTimeout(filename, expectedState);

                // throwing exception
                throw new NetworkTimeoutException(filename, expectedState);
            }
        }

        // Operation Complete Within Timeout //

        this.handleOperationComplete(filename, expectedState);
    }

    /**
     * Updates the index to reflect an operation having been completed.
     * 
     * @param filename The name of the file that the operation was completed on.
     * @param stateFileIsIn The state that the file is in now that the operation has completed.
     */
    private synchronized void handleOperationComplete(String filename, OperationState stateFileIsIn){
        
        // STORE 
        if(stateFileIsIn == OperationState.STORE_ACK_RECIEVED){
            // updating file state to the new state
            for(DstoreIndex dstore : this.dstores){
                for(DstoreFile file : dstore.getFiles()){
                    if(file.getFilename().equals(filename)){
                        dstore.updateFileState(filename, OperationState.IDLE);
                    }
                }
            }
        }

        // REMOVE
        else if(stateFileIsIn == OperationState.REMOVE_ACK_RECIEVED){
            // removing the file from the index
            for(DstoreIndex dstore : this.dstores){
                dstore.removeFile(filename);
            }
        }
    }

    /**
     * Handles the case where an operation did not complete wthin the given timeout.
     * 
     * @param filename The filename for which the operation did not complete.
     * @param expectedState The state the file should have been in if the operation had compeleted.
     */
    private synchronized void handleOperationTimeout(String filename, OperationState expectedState){
        // STORE
        if(expectedState == OperationState.STORE_ACK_RECIEVED){
            // removing the file from the index
            for(DstoreIndex dstore : this.getDstoresStoredOn(filename)){
                dstore.removeFile(filename);
            }
        }

        // REMOVE
        else if(expectedState == OperationState.REMOVE_ACK_RECIEVED){
            // removing the file from the index
            for(DstoreIndex dstore : this.dstores){
                dstore.removeFile(filename);
            }
        }
    }


    /////////////////
    // REBALANCING //
    /////////////////


    /**
     * 启动系统重新平衡。
     *
     * 禁用控制器请求处理程序，等待系统变为
     * IDLE 并将索引更新为 REBALANCE_LIST_IN_PROGRESS。
     *
     * @throws NotEnoughDstoresException 如果连接的 Dstores 不够多
     * 对系统进行再平衡操作。
     * @throws RebalanceAlreadyInProgressException 如果已经存在再平衡
     * 进展中的操作
     * @throws NetworkTimeoutException 如果系统在
     * 超时。
     */
    public synchronized void startRebalanceList() throws NetworkException{
        // ERROR CHECKING //

        // not enough dstores
        if(!this.hasEnoughDstores()){
            throw new NotEnoughDstoresException();
        }

        // rebalance already in progress
        if(this.rebalanceInProgress()){
            throw new RebalanceAlreadyInProgressException();
        }

        // CHECKS COMPLETE //

        //禁用控制器请求处理程序,controller等待dstore加入存储系统(参见Rebalance操作)。控制器在至少R个存储库加入系统之前不会处理任何客户端请求。
        this.controller.getRequestHandler().disable();

        // 等待系统处于空闲状态,只有Dstore中所有文件处于操作空闲状态（即不处于store,remove，load），才可以进行Rebalance
        this.controller.getIndex().waitForSystemOperationState(OperationState.IDLE, this.controller.getTimeout());

        // 更新索引中所有 Dstores 的状态
        for(DstoreIndex dstore : this.dstores){
            dstore.setRebalanceState(RebalanceState.REBALANCE_LIST_IN_PROGRESS);
        }
    }

    /**
     * 在系统期间从 Dstore 收到文件 LIST 后更新索引
     *平衡。
     *
     * @param dstore 接收 LIST 的 dstore 的连接。
     * @param files 映射到其文件大小的文件名列表（文件
     * 存储在此 Dstore 上）。
     */
    public synchronized void rebalanceListRecieved(Connection dstore, HashMap<String, Integer> files){
        // updating the dstore index state
        this.getIndexFromConnection(dstore).setRebalanceState(RebalanceState.REBALANCE_LIST_RECIEVED);

        // updating the DstoreIndex for this Dstore
        this.getIndexFromConnection(dstore).setFiles(files);
    }

    /**
     * 开始系统重新平衡的移动阶段。更新索引
     * REBALANCE_MOVE_IN_PROGRESS。
     */
    public synchronized void startRebalanceMove(){
        // updating index
        for(DstoreIndex dstore : this.dstores){
            dstore.setRebalanceState(RebalanceState.REBALANCE_MOVE_IN_PROGRESS);
        }
    }

    /**
     * 在从 Dstore 收到REBALANCE_COMPLETE消息后更新索引。
     *
     * @param dstore 接收消息的 Dstore Conection。
     */
    public synchronized void rebalanceCompleteReceived(Connection dstore){
        // updating the dstore index state
        this.getIndexFromConnection(dstore).setRebalanceState(RebalanceState.REBALANCE_COMPLETE_RECIEVED);
    }

    /**
     * 等待系统中的所有 Dstores 具有提供的重新平衡状态。
     *
     * @param rebalanceState 预期的重新平衡状态。
     * @param timeout 等待系统具有预期重新平衡状态的超时。
     * @throws NetworkTimeoutException：如果未达到预期的重新平衡状态，则抛出
     * 在超时范围内。
     */
    public void waitForRebalanceState(RebalanceState rebalanceState, int timeout) throws NetworkTimeoutException{
        
        long timeoutStamp = System.currentTimeMillis() + timeout;

        //等到收到 REBALANCE 状态列表
        while(!this.systemHasRebalanceState(rebalanceState)){
            if(System.currentTimeMillis() < timeoutStamp){
                Thread.onSpinWait();
            }
            else{
                // timeout occured
                this.handleRebalanceTimeout(rebalanceState);

                // throwing exception
                throw new NetworkTimeoutException(rebalanceState);
            }
        }

        // 在超时内完成重新平衡阶段

        this.handleRebalanceComplete();
    }

    /**
     * 处理系统重新平衡的完成。
     */
    private synchronized void handleRebalanceComplete(){
        // enabling controller request handler
        this.controller.getRequestHandler().enable();

        // resetting the state of the index
        for(DstoreIndex dstore : this.dstores){
            dstore.setRebalanceState(RebalanceState.IDLE);
        }
    }

    /**
     * 处理系统再平衡阶段未实现的情况
     * 在超时内完成。
     *
     * @param expectedRebalancetate 未达到的再平衡状态
     * 在超时范围内。
     */
    private void handleRebalanceTimeout(RebalanceState expectedRebalancetate){
        // enabling controller request handler
        this.controller.getRequestHandler().enable();

        // resetting the state of the index
        for(DstoreIndex dstore : this.dstores){
            dstore.setRebalanceState(RebalanceState.IDLE);
        }
    }


    ////////////////////
    // HELPER METHODS //
    ////////////////////


    /**
     * 确定索引是否有足够的 Dstores 连接到它。
     *
     * 如果有足够的 Dstores，则@return True，否则为 false。
     */
    private synchronized boolean hasEnoughDstores(){
        if(this.dstores.size() < this.minDstores){
            // not enough dstores
            return false;
        }
        else{
            // enough dstores
            return true;
        }
    }

    /**
     * 确定给定文件是否存储在系统上。
     *
     * @param filename 正在检查的文件。
     * 如果文件在系统上，则@return True，否则为 false
     */
    private synchronized boolean hasFile(String filename){
        for(DstoreIndex dstore : this.dstores){
            if(dstore.hasFile(filename)){
                // file found
                return true;
            }
        }

        // file not found.
        return false;
    }

    /**
     * 确定给定文件是否在所有 Dstore 中具有给定状态
     * 它被存储在。
     *
     * @param filename 正在检查的文件。
     * @param state 文件的状态。
     * 如果文件的状态是提供的状态，则@return True，如果不是，则为 false。
     */
    public synchronized boolean fileHasState(String filename, OperationState state){
        for(DstoreIndex dstore : this.getDstoresStoredOn(filename)){
            for(DstoreFile file : dstore.getFiles()){
                if(file.getFilename().equals(filename) && file.getState() != state){
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 获取与提供的 Dstore 服务器端口关联的 DstoreIndex 对象。
     *
     * @param port Dstore 正在侦听的端口。
     * @return 与提供的端口关联的 DstoreIndex 对象，如果有，则为 null
     * 不匹配。
     */
    public synchronized DstoreIndex getIndexFromPort(int port){
        // findiing the matching DstoreIndex
        for(DstoreIndex dstore : this.dstores){
            if(dstore.getPort() == port){
                return dstore;
            }
        }

        // returning null if no match found
        return null;
    }

    /**
     * 获取与提供的 Connection 对象同化的 DstoreIndex 对象。
     *
     * @param connection Dstore 和 Controller 之间的连接对象。
     * @return 与提供的 Connectoin 对象关联的 DstorerIndex 对象，null
     * 如果没有匹配。
     */
    public synchronized DstoreIndex getIndexFromConnection(Connection connection){
        // finding the matching DstoreIndex
        for(DstoreIndex dstore : this.dstores){
            if(dstore.getConnection().getPort() == connection.getPort()){
                return dstore;
            }
        }

        // returning null if no match found
        return null;
    }

    /**
     * 获取可以存储文件的 Dstore 列表。仅返回 Dstores
     * 存储文件后将保持平衡。
     *
     * @param numberOfDstores 要存储的 Dstoes 数。
     * @return 可以存储新文件的 Dstore 端口列表。
     */
    public synchronized ArrayList<Integer> getDstoresToStoreOn(int numberOfDstores){
        // sorting the dstores based on the number of files they contain
        Collections.sort(this.dstores);

        ArrayList<Integer> ports = new ArrayList<Integer>();

        // picking the first r dstores to store on
        for(int i =0; i < numberOfDstores; i++){
            ports.add(this.dstores.get(i).getPort());
        }

        // returning the list of dstores
        return ports;
    }

    /**
     * 收集存储所提供文件的索引列表。
     *
     * @param filename 正在搜索的文件的名称。
     * @return 存储文件的 DstoreIndex 列表。
     */
    public synchronized ArrayList<DstoreIndex> getDstoresStoredOn(String filename){
        ArrayList<DstoreIndex> indexes = new ArrayList<DstoreIndex>();
        
        // 遍历所有 dstore 并查看它们是否包含该文件
        for(DstoreIndex dstore : this.dstores){
            if(dstore.hasFile(filename)){
                indexes.add(dstore);
            }
        }

        // file not stored on the system.
        return indexes;
    }

    /**
     * 收集系统的文件分发。文件分发是 Dstores 到存储在它们上的文件的映射。
     *
     * 请注意，此方法不考虑存储在 Dstores 上的文件的状态。
     *
     * @return Dstore 到存储在其上的文件的映射。
     */
    public HashMap<Integer, HashMap<String, Integer>> getFileDistribution(){
        // 创建对象以保存文件分发
        HashMap<Integer, HashMap<String,Integer>> fileDistribution = new HashMap<Integer, HashMap<String,Integer>>();
        
        // 遍历 dstore 和每个 dstore 文件到对象
        for(DstoreIndex dstore : this.dstores){
            HashMap<String, Integer> files = new HashMap<String, Integer>();

            for(DstoreFile file : dstore.getFiles()){
                files.put(file.getFilename(), file.getFilesize());
            }

            fileDistribution.put(dstore.getPort(), files);
        }

        // returning the file distribution
        return fileDistribution;
    }

    /**
     * 将提供的文件分布设置到索引中。
     *
     * @param fileDistribution 要设置到索引中的文件分布。
     */
    public void setFileDistribution(HashMap<Integer, HashMap<String, Integer>> fileDistribution){
        // iterating through file distribution
        for(Integer dstore : fileDistribution.keySet()){
            // setting the file list into the index
            this.getIndexFromPort(dstore).setFiles(fileDistribution.get(dstore));
        }
    }

//    /**
//     * 等待系统具有预期的操作状态。
//     * 当所有 dstore 上的所有文件都具有相同的状态时，系统具有特定状态。
//     *
//     * @param timeout 等待系统达到预期状态的时间长度。
//     * @throws NetworkTimeout 如果系统在超时内未达到预期状态。
//     */
    public synchronized void waitForSystemOperationState(OperationState expectedState, int timeout) throws NetworkTimeoutException{
        
        long timeoutStamp = System.currentTimeMillis() + timeout;

        // 系统未空闲时循环
        while(!this.systemHasOperationState(expectedState)){
            if(System.currentTimeMillis() < timeoutStamp){
                // no timeout yet - need to wait
                Thread.onSpinWait();
            }
            else{
                // throwing exception
                throw new NetworkTimeoutException(OperationState.IDLE);
            }
        }

        // System Is Idle Within Timeout //
    }

    /**
     * 确定系统是否具有特定的操作状态。
     *
     * @param expectedState 系统的预期状态。
     * 如果系统处于空闲状态，则@return true，如果不是，则为 false。
     */
    private synchronized boolean systemHasOperationState(OperationState expectedState){
        for(DstoreIndex dstore : this.dstores){
            for(DstoreFile file : dstore.getFiles()){
                if(file.getState() != expectedState){
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 确定系统的再平衡状态是否与给定状态相同。
     * 系统的再平衡状态是系统中所有 Dstore 的再平衡状态。
     *
     * @param expectedState 系统预期的 RebalanceState。
     * 如果系统具有预期状态，则@return True，如果没有，则为 false。
     */
    private synchronized boolean systemHasRebalanceState(RebalanceState expectedState){
        for(DstoreIndex dstore : this.dstores){
            if(dstore.getRebalanceState() != expectedState){
                return false;
            }
        }

        return true;
    }

    /**
     * 通过查看来确定系统当前是否正在重新平衡
     * 在每个 Dstore 的重新平衡状态下。
     *
     * 如果系统当前正在重新平衡，则@return True，否则为 false。
     */
    private synchronized boolean rebalanceInProgress(){
        for(DstoreIndex dstore : this.dstores){
            if(dstore.getRebalanceState() != RebalanceState.IDLE){
                return true;
            }
        }

        return false;
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public CopyOnWriteArrayList<DstoreIndex> getDstores(){
        return this.dstores;
    }

    /**
     * 返回系统上所有 Dstores 的端口列表。
     *
     * @return 系统上所有 Dstore 的端口列表。
     */
    public ArrayList<Integer> getDstorePorts(){
        ArrayList<Integer> ports = new ArrayList<Integer>();

        for(DstoreIndex dstore : this.dstores){
            ports.add(dstore.getPort());
        }

        return ports;
    }

    /**
     * Returns a list of all files stored in the system.
     * 
     * @return ArrayList of all files stored in the system.
     */
    public ArrayList<String> getFiles(){
        // getting list of all files
        ArrayList<String> allFiles = new ArrayList<String>();
        for(DstoreIndex dstore : this.dstores){
            for(DstoreFile file: dstore.getFiles()){
                allFiles.add(file.getFilename());
            }
        }

        // removing duplicates and returning
        return new ArrayList<String>(new HashSet<String>(allFiles));
    }
}