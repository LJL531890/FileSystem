package DS.Controller.Rebalancer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import DS.Protocol.Token.TokenType.FileToSend;

/**
 * 存储分布式文件存储系统的内容。
 * 存储分布式文件存储系统的内容。
 *
 * 分布式文件存储系统的内容包括每个文件必须复制的最小 Dstore 数以及跨 Dstore 的文件映射（文件分发）。
 */
public class System{

    // member variables
    private int minDstores;
    private HashMap<Integer, HashMap<String, Integer>> fileDistribution;

    /**
     * 类构造函数。
     *
     * @param minDstores 每个文件必须复制的最小 Dstore 数。
     * @param fileDistribution 系统内各存储之间的文件分布。
     */
    public System(int minDstores, HashMap<Integer, HashMap<String, Integer>> fileDistribution){
        // initializing
        this.minDstores = minDstores;
        this.fileDistribution = fileDistribution;
    }

    ///////////////////////////////////
    // CONFIGURING FILE DISTRIBUTION //
    ///////////////////////////////////

//    /**
//     * 将提供的文件添加到文件分发中提供的 Dstore。
//     *
//     * @param dstore 要将文件添加到的 Dstore。
//     * @param 文件 要添加到 Dstore 的文件。
//     */
    public void addFileToDstore(Integer dstore, String file, int filesize){
        // adding the file to the dstore's file list
        this.fileDistribution.get(dstore).put(file, filesize);
    }

//    /**
//     * 从文件分发中提供的 Dstore 中删除提供的文件。
//     *
//     * @param dstore 要从中删除文件的 Dstore。
//     * @param 文件 要从 Dstore 中删除的文件。
//     */
    public void removeFileFromDstore(Integer dstore, String file){
        // removing the file from the dstore's file list
        this.fileDistribution.get(dstore).remove(file);
    }

    /**
     * 更新提供的系统状态，以反映在提供的再平衡信息中所做的更改。
     *
     * @param rebalanceInformation 对系统状态所做的更改的描述。
     */
    public void updateFromRebalanceInformation(HashMap<Integer, RebalanceInformation> rebalanceInformation){
        // 处理每个 dstore 的再平衡信息
        for(Integer dstore : rebalanceInformation.keySet()){

            // 处理发送的文件

            for(FileToSend fileToSend : rebalanceInformation.get(dstore).getFilesToSend()){
                for(int dstorePort : fileToSend.dStores){
                    this.addFileToDstore(dstorePort, fileToSend.filename, fileToSend.filesize);
                }
            }

            // DEALING WITH FILES REMOVED //
            
            for(String fileToRemove : rebalanceInformation.get(dstore).getFilesToRemove()){
                // removing the file from the file distribution
                this.removeFileFromDstore(dstore, fileToRemove);
            }
        }
    }

    //////////////////////////
    // CHECKING IF BALANCED //
    //////////////////////////

    /**
     * 确定系统是否平衡。
     *
     如果所有文件都以最少的复制次数复制，并且文件在 Dstore 之间均匀存储，则系统是平衡的。
     *
     * 如果系统是平衡的，则@return True，如果不是，则为 false。
     */
    public boolean isBalanced(){
        return (this.filesStoredMinTimes() && this.filesStoredEvenly());
    }

    /**
     * 确定系统内所有文件是否被复制到最少的次数。
     *
     * @return 如果所有文件都复制了最少次数，则为 True，
     * 如果不是，则为 false。
     */
    public boolean filesStoredMinTimes(){
        // 如果文件存储了 R 次，则没有未存储 R 次的文件（呃......
        return (this.getFilesNotStoredMinTimes().size() == 0);
    }

    /**
     * 确定系统中的文件是否均匀地存储在 Dstore 之间。
     *
     * 如果每个 Dstore 存储在 Floor（R * F / N） 和天花板（R * F / N）文件，其中R是复制因子，F是数字
     文件数，N 是 Dstore 的数量（即，每个 Dstore 存储的文件数量文件数量平均值）。
     *
     * 如果文件存储均匀，则@return true，如果不是，则为 false。
     */
    public boolean filesStoredEvenly(){
        // calculating min and max values
        double r = this.minDstores;
        double f = this.getNumberOfFiles();
        double n = this.getNumberOfDstores();
        double averageFiles = r * f / n;
        double minFiles = Math.floor(averageFiles);
        double maxFiles = Math.ceil(averageFiles);

        // 遍历 dstore 并检查它们存储的文件数
        for(int dstoreCount : this.getDstoreFileCount().values()){
            if(dstoreCount < minFiles || dstoreCount > maxFiles){
                return false;
            }
        }

        // not returned false - file spread is good - returning true
        return true;
    }

    ///////////////////////////
    // DSTORE HELPER METHODS //
    ///////////////////////////

    /**
     * Returns a list of all Dstores in the file distribution.
     * 
     * @return A list of all Dstores in the file distribution as a list
     * of port numbers.
     */
    public ArrayList<Integer> getDstores(){
        return new ArrayList<Integer>(this.fileDistribution.keySet());
    }

    /**
     * Returns the number of Dstores within the System.
     * 
     * @return The number of Dstores within the System.
     */
    public int getNumberOfDstores(){
        return this.fileDistribution.size();
    }

//    /**
//     * 返回存储所提供文件的 Dstore。
//     *
//     * @param filename 正在搜索 Dstore 的文件的名称。
//     * @return 存储所提供文件的 Dstore，如果未找到匹配的 Dstore，则为 null。
//     */
    public Integer getDstoreThatHasFile(String file){
        // finding dstore that stores the file
        for(Integer dstore : this.getDstores()){
            if(this.getFilesOnDstore(dstore).keySet().contains(file)){
                return dstore;
            }
        }

        // no dstore found - returning null
        return null;
    }

    /**
     * Returs a mapping of Dstores to the number of files they store.
     * 
     * @return A mapping of Dstores to the number of files they store.
     */
    public HashMap<Integer, Integer> getDstoreFileCount(){
        // 映射以存储每个 Dstore 的计数
        HashMap<Integer, Integer> dstoreCount = new HashMap<Integer, Integer>();

        // populating the dstore count
        for(Integer dstore : this.fileDistribution.keySet()){
            dstoreCount.put(dstore, this.fileDistribution.get(dstore).keySet().size());
        }

        // returning the dstore count
        return dstoreCount;
    }

    /**
     * 返回系统中所有 Dstore 的列表，按它们包含的文件数按升序排序。
     *
     * @return 系统中所有 Dstore 的列表，根据它们包含的文件数量进行排序。
     */
    public ArrayList<Integer> getDstoresSortedByFiles(){
        // 创建用于排序的映射（无法对对象 -> 映射的映射进行排序，但可以对对象 -> 列表的映射进行排序）
        HashMap<Integer, ArrayList<String>> dstoresList = new HashMap<Integer, ArrayList<String>>();
        for(Integer dstore : this.fileDistribution.keySet()){
            dstoresList.put(dstore, new ArrayList<String>(this.fileDistribution.get(dstore).keySet()));
        }

        //按存储的文件顺序获取 dstore 列表
        List<Integer> sortedDstoresList = dstoresList.entrySet().stream()
            .collect(
                    Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().size()
                        ) //获取 key=original 键和值列表大小的映射
            )
            .entrySet()
            .stream() // sort map by value - list size
            .sorted(Map.Entry.<Integer, Integer>comparingByValue())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        // returning the sorted list of dstores
        return new ArrayList<Integer>(sortedDstoresList);
    }

    /////////////////////////
    // FILE HELPER METHODS //
    /////////////////////////

    /**
     * 返回系统中所有文件的列表，作为其文件名到文件大小的映射。
     *
     * @return 系统中所有文件的文件名到文件大小的映射。
     */
    public HashMap<String, Integer> getFiles(){
        // hashmap to hold the list of files
        HashMap<String, Integer> files = new HashMap<String, Integer>();

        // iterating over dstores and adding each ones files to the map
        for(HashMap<String, Integer> dstoreFiles : this.fileDistribution.values()){
            files.putAll(dstoreFiles);
        }

        // returning the created file list
        return files;
    }

    /**
     * Returns a list of all files stored in the System.
     * 
     * @return All files stored in the system as a list of filenames.
     */
    public ArrayList<String> getFileNames(){
        return new ArrayList<String>(this.getFiles().keySet());
    }

    /**
     * Returns the number of files stored in the System.
     * 
     * @return The number of file stored in the System.
     */
    public int getNumberOfFiles(){
        // list to hold all files
        ArrayList<String> files = new ArrayList<String>();

        // iterating through dstordes and adding files to list
        for(Integer dstore : this.fileDistribution.keySet()){
            for(String file : this.fileDistribution.get(dstore).keySet()){
                // only adding file if it's not yet been added
                if(!files.contains(file)){
                    files.add(file);
                }
            }
        }

        // returning number of files
        return files.size();
    }

    /**
     * 返回存储在提供的 Dstore 上的文件列表。
     *
     * @param dstore 正在收集文件列表的 Dstore。
     * @return 存储在提供的 Dstore 上的文件列表，作为文件名到文件大小的映射。
     */
    public HashMap<String, Integer> getFilesOnDstore(Integer dstore){
        return this.fileDistribution.get(dstore);
    }

    /**
     * 返回所提供文件的文件大小。
     *
     * 如果系统中没有文件记录，则返回 -1。
     *
     * @return 所提供文件的文件大小，如果在文件分发中找不到该文件，则为 -1。
     */
    public int getFileSize(String file){
        // setting initial value for the filesize
        int filesize = -1;

        // iterating through the file distributio to find the file
        for(Integer dstore : this.fileDistribution.keySet()){
            for(String f : this.fileDistribution.get(dstore).keySet()){
                if(f.equals(file)){
                    filesize = this.fileDistribution.get(dstore).get(f);
                }
            }
        }

        // returning the filesize
        return filesize;
    }

    /**
     * 返回文件与复制文件的 Dstore 数的映射。
     *
     * @return 映射到文件复制的 Dstore 数的系统中的文件列表。
     */
    public HashMap<String, Integer> getFileCount(){
        //map 保存所有映射
        HashMap<String, Integer> fileCount = new HashMap<String, Integer>();

        // 遍历 dstordes 并将文件添加到列表
        for(Integer dstore : this.fileDistribution.keySet()){
            for(String file : this.fileDistribution.get(dstore).keySet()){
                // 尚未添加的文件 - 计数为 1
                if(!fileCount.keySet().contains(file)){
                    fileCount.put(file, 1);
                }
                // file already added - increment count by 1
                else{
                    fileCount.put(file, fileCount.get(file) + 1);
                }
            }
        }

        // returning number of files
        return fileCount;
    }

    /**
     * 重新列出未复制超过最小数量的文件
     作为文件名与存储次数的映射。
     *
     * @return 对于所有未存储最小次数的文件，文件名与其存储次数的映射。
     */
    public HashMap<String, Integer> getFilesNotStoredMinTimes(){
        // 得到将文件映射到存储的次数
        HashMap<String, Integer> allFilesCount = this.getFileCount();

        // 为未存储 R 次的文件创建映射
        HashMap<String, Integer> filesNotStoredMinTimes = new HashMap<String, Integer>();

        // 循环访问文件并将未存储的文件添加到列表中 R 次
        for(String file : allFilesCount.keySet()){
            if(allFilesCount.get(file) < this.minDstores){
                filesNotStoredMinTimes.put(file, allFilesCount.get(file));
            }
        }

        // returning the list of files that are not stored r times
        return filesNotStoredMinTimes;
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public int getMinDstores(){
        return this.minDstores;
    }

    public HashMap<Integer, HashMap<String, Integer>> getFileDistribution(){
        return this.fileDistribution;
    }

    public String toString(){
        return this.fileDistribution.toString();
    }
}