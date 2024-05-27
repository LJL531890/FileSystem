package DS.DSClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import DS.Protocol.Exception.*;
import DS.Protocol.Token.RequestTokenizer;
import DS.Protocol.Token.Token;
import DS.Protocol.Token.TokenType.*;
import Network.NetworkInterface;
import Network.Protocol.Event.HandeledNetworkEvent;
import Network.Protocol.Exception.*;

/**
 * ClientInterface 的实现，该接口使用终端作为客户端和用户之间的接口。
 *
 * 创建一个 DSClient 对象，该对象在启动时连接到控制器。
 *
 * 该接口在 stdin 上接受来自用户的请求，并将其传递给要发送到 Controller 的 DSClient 对象。
 *
 * 该接口在 stdout 上记录对请求的响应。
 * 这个类是一个实现了 NetworkInterface 接口的类，用于在终端上作为客户端和用户之间的接口，通过终端接收用户请求并将其传递给 DSClient 对象发送到控制器。以下是这个类的作用以及各个函数的作用：
 * 类的作用：
 * 终端客户端接口：作为 ClientInterface 的实现，通过终端与用户交互，将用户请求传递给 DSClient 对象发送到控制器，并记录响应。
 * 各个函数的作用：
 * 1. waitForInput(): 等待用户在终端中输入请求。通过循环读取用户输入，并处理空输入或异常情况，确保能够正确处理用户请求。
 * 2. handleInput(String input): 处理来自终端的用户输入。根据输入的类型，调用相应的处理函数处理存储、加载、移除和列出文件等请求。
 * 3. handleStoreInput(String filename, int filesize): 处理存储请求。加载文件并将其发送到 DSClient 对象进行存储。
 * 4. handleLoadInput(String filename): 处理加载请求。从 DSClient 对象加载文件内容并保存到本地文件。
 * 5. handleRemoveInput(String filename): 处理移除请求。从 DSClient 对象移除指定文件。
 * 6. handleListInput(): 处理列出文件请求。从 DSClient 对象获取文件列表。
 * 7. handleInvalidInput(String input): 处理无效请求。记录并处理无法识别的用户请求。
 * logMessageSent(Socket connection, String message): 记录发送消息的日志。
 * 9. logMessageReceived(Socket connection, String message): 记录接收消息的日志。
 * 10. logEvent(HandeledNetworkEvent event): 记录事件的日志。
 * 11. logError(HandeledNetworkException error): 记录错误的日志，并根据特定情况处理错误，如控制器断开连接或客户端启动异常。
 * 12. main(String[] args): 主方法，用于从命令行参数实例化一个新的 DSClientTerminal 实例。
 * 可不可以缺少每个函数，给出不可以的理由：
 * waitForInput(): 不能缺少，因为它负责等待用户输入请求，是整个交互过程的起点。
 * handleInput(String input): 不能缺少，因为它根据用户输入的内容分发到不同的处理函数，是请求处理的核心。
 * handleStoreInput(String filename, int filesize): 不能缺少，因为它处理存储请求，是核心功能之一。
 * handleLoadInput(String filename): 不能缺少，因为它处理加载请求，是核心功能之一。
 * handleRemoveInput(String filename): 不能缺少，因为它处理移除请求，是核心功能之一。
 * handleListInput(): 不能缺少，因为它处理列出文件请求，是核心功能之一。
 * handleInvalidInput(String input): 不能缺少，因为它处理无效请求，确保系统能够正确处理异常情况。
 * 日志记录函数和主方法：虽然可以根据具体需求调整日志记录方式，但为了记录和处理网络通信过程中的信息和事件，这些函数是必不可少的。
 */
public class DSClientTerminal extends NetworkInterface{

    // member variables
    public DSClient client;

    /**
     * Class Constructor.
     * 
     * @param cPort The port of the Controller.
     * @param timeout The message timeout period.
     */
    public DSClientTerminal(int cPort, int timeout) {
        // initialising member variables
        this.client = new DSClient(cPort, timeout, this);

        // connecting to network
        //1 Sets up a connection between the Client and the Controller.
        //2 sending JOIN_CLIENT message to controller
        this.startNetworkProcess(this.client);

        // waiting for user input
        this.waitForInput();
    }

    ////////////////////////
    // GETTING USER INPUT //
    ////////////////////////

    /**
     * 等待用户将请求输入到终端中。这个函数 waitForInput 的作用是等待用户在控制台输入请求，并处理这些输入请求。具体步骤如下：
     * 1. 创建一个 BufferedReader 对象来读取控制台输入。
     * 2. 在一个无限循环中等待用户输入。
     * 3. 在每次循环中：
     * 打印提示符 >，等待用户输入。
     * 读取用户输入的请求字符串。
     * 检查用户输入是否为空，如果为空则处理空请求异常。
     * 否则，将用户输入的请求传递给 handleInput 方法进行处理。
     * 4. 如果在等待用户输入的过程中发生任何异常，将异常传递给 client 对象的错误处理方法进行处理。
     * 这个函数的目的是实现一个交互式的控制台界面，让用户能够输入请求并将这些请求传递给相应的处理方法进行处理。
     */
    public void waitForInput(){
        try{
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            // Wait for input 
            while(true){
                // FORMATTING
                System.out.println();
                System.out.print(">");

                // reading in request
                String input = reader.readLine();

                // making sure client request is non-null
                if(input.equals("")){
                    this.client.handleError(new RequestHandlingException("", new NullClientInputRequestException()));
                }
                else{
                    // sending request to controller
                    this.handleInput(input);
                }
            }
        }
        catch(Exception e){
            this.client.handleError(new ClientInputRequestReadException(e));
        }
    }

    ////////////////////
    // INPUT HANDLING //
    ////////////////////

    /**
     * 处理来自客户端通过终端的输入
     * 
     * @param input The input to be handeled.
     */
    public void handleInput(String input){
        try{
            Token requestToken = RequestTokenizer.getToken(input);

            // STORE //
            if(requestToken instanceof StoreToken){
                StoreToken storeToken = (StoreToken) requestToken;
                this.handleStoreInput(storeToken.filename, storeToken.filesize);
            }

            // LOAD //
            else if(requestToken instanceof LoadToken){
                LoadToken loadToken = (LoadToken) requestToken;
                this.handleLoadInput(loadToken.filename);
            }

            // REMOVE //
            else if(requestToken instanceof RemoveToken){
                RemoveToken removeToken = (RemoveToken) requestToken;
                this.handleRemoveInput(removeToken.filename);
            }

            // LIST //
            else if(requestToken instanceof ListToken){
                this.handleListInput();
            }

            // Invalid Request
            else{
                this.handleInvalidInput(input);
            }
        }
        catch(Exception e){
            this.client.handleError(new RequestHandlingException(input, e));
        }
    }

    ///////////
    // STORE //
    ///////////

    /**
     * Handles the input of a STORE request into the terminal.
     * 
     * @param filename The name of the file to be stored.
     * @param filesize The size of the file to be stored (in bytes).
     * 
     * @throws Exception If the request could not be handeled.
     */
    public void handleStoreInput(String filename, int filesize) throws Exception{
        // loading the file
        File file = new File(filename);

        // send the file to the DSClient
        this.client.storeFile(file, filesize);
    }

    //////////
    // LOAD //
    //////////

    /**
     * Handles the input of a LOAD request into the terminal.
     * 
     * @param filename The name of the file to be loaded.
     * 
     * @throws Exception If the request could not be handeled.
     */
    public void handleLoadInput(String filename) throws Exception{
        // gathering the file content
        byte[] fileContent = this.client.loadFile(filename, false);

        // storing the file
        File file = new File(filename);
        FileOutputStream fileOutput = new FileOutputStream(file);
        fileOutput.write(fileContent);
        fileOutput.flush();
        fileOutput.close();
    }

    ////////////
    // REMOVE //
    ////////////
    
    /**
     * Handles the input of a REMOVE request into the terminal.
     * 
     * @param filename The name of the file to be removed.
     * 
     * @throws Exception If the request could not be handeled.
     */
    public void handleRemoveInput(String filename) throws Exception{
        // removes the file from the system
        this.client.removeFile(filename);
    }

    //////////
    // LIST //
    //////////

    /**
     * Handles the input of a LIST request into the terminal.
     * 
     * @throws Exception If the request could not be handeled.
     */
    public void handleListInput() throws Exception{
        // gathering the list of files
        HashMap<String, Integer> files = this.client.getFileList();

        // nothing to do with the list ...
    }

    /////////////
    // INVALID //
    /////////////

    /**
     * Handles the input of an invalid request into the terminal.
     * 
     * @param input The input into the terminal.
     * 
     * @throws Exception If the request could not be handeled.
     */
    public void handleInvalidInput(String input) throws Exception{
        // handling the error
        this.client.handleError(new RequestHandlingException(input, new InvalidClientRequestException(input)));
    }


    /////////////
    // LOGGING //
    /////////////

   /**
     * 处理正在发送的消息的日志记录。
     *
     * @param connection：发送方和接收方之间的套接字。
     * @param message 要记录的消息。
    *在这段代码中，connection.getLocalPort() 和 connection.getPort() 分别表示以下内容：
    * connection.getLocalPort(): 返回本地端口号，即当前主机上的连接使用的端口号。
    * connection.getPort(): 返回远程端口号，即连接对端主机的端口号。
    * 区别在于：
    * getLocalPort() 返回的是本地主机上的端口号，用于标识当前主机上的连接。
    * getPort() 返回的是远程主机的端口号，用于标识连接对端主机的端口。
    * 通过打印这两个端口号，可以在日志中记录每条消息发送的连接信息，包括本地端口和远程端口，以便跟踪和调试网络通信。
     */
    public void logMessageSent(Socket connection, String message){
        System.out.println("[" + connection.getLocalPort() + " -> " + connection.getPort() + "] " + message);
    }

    /**
     * Handles the logging of a message being recieved.
     * 
     * @param connection The socket between the sender and reciever.
     * @param message The message to be logged.
     */
    public void logMessageReceived(Socket connection, String message){
        System.out.println("[" + connection.getLocalPort() + " <- " + connection.getPort() + "] " + message);
    }

    /**
     * Handles the logging of an event.
     * 
     * @param event The event to be logged.
     */
    public void logEvent(HandeledNetworkEvent event){
        System.out.println(event.toString());
    }

    /**
     * Handles the logging of an error.
     * 
     * @param error The error to be logged.
     */
    public void logError(HandeledNetworkException error){
        // logging error to terminal
        System.out.println(error.toString());

        // HANDLING SPECIFIC CASES //

        // Controller disconnected
        if(error.getException() instanceof ControllerDisconnectException){
            System.exit(0);
        }
        // Client Start Exception
        else if(error.getException() instanceof ClientStartException){
            System.exit(0);
        }
    }


    /////////////////
    // MAIN METHOD //
    /////////////////

    
    /**
     * Main method - instantiates a new Client instance using the command line parammeters.
     * @param args Parameters for the new Client.
     */
    public static void main(String[] args){
        try{
            // gathering parameters
            int cPort = Integer.parseInt(args[0]);
            int timeout = Integer.parseInt(args[1]);

            // Creating new Client instance
            new DSClientTerminal(cPort, timeout);
        }
        catch(Exception e){
            System.out.println("Unable to create Client.");
        }
    }
}