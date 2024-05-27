package Network;

import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import Network.Protocol.Event.ClientConnectionEvent;
import Network.Protocol.Exception.*;
import Network.Server.Server.ServerType;

/**
 * 套接字和日志记录的便利类。
 *
 * 在实例化时为套接字创建新的套接字以及输入和输出流。
 *
 * 具有允许发送消息和接收消息的方法，并且还处理将这些消息记录到给定的 NetworkInterface。
 */
public class Connection{
    
    // member variables
    private NetworkInterface networkInterface;
    //用于建立网络连接。它被用来获取输入流和输出流，以便通过网络接收和发送数据。
    private Socket socket;
    private PrintWriter textOut;
    private BufferedReader textIn;
    private OutputStream dataOut;
    private InputStream dataIn;
    private ArrayList<String> messagesSent;
    private ArrayList<String> messagesReceived;

//    /**
//     * Class constructor. For a connection from Server -> Client (Connection on server end).
//     *
//     * @param networkInterace The interface associated with the connection.
//     * @param socket The socket involved in the connection
//     * @throws ConnectionSetupException If the Connection could not be setup.
//     */
    public Connection(NetworkInterface networkInterface, Socket socket) throws ConnectionSetupException{
        try{
            this.networkInterface = networkInterface;
            this.socket = socket;
            this.textOut = new PrintWriter (new OutputStreamWriter(this.socket.getOutputStream())); 
            this.textIn = new BufferedReader (new InputStreamReader(this.socket.getInputStream()));
            this.dataOut = this.socket.getOutputStream();
            this.dataIn = this.socket.getInputStream();
            this.messagesSent = new ArrayList<String>();
            this.messagesReceived = new ArrayList<String>();
        }
        catch(Exception e){
            throw new ConnectionSetupException(socket.getPort(),e);
        }
    }

//    /**
//     * Class constructor. For a connection from Client -> Server (Conenction on Client end).
//     *
//     * @param networkInterace The interface associated with the connection.
//     * @param port The port the connection will be made to.
//     * @param serverType The type of server the client is connecting to.
//     * @throws ConnectionSetupException If the Connection could not be setup.
//     */
    public Connection(NetworkInterface networkInterface, int port, ServerType serverType) throws ConnectionSetupException{
        try{
            // creating the connection
         /*在这段代码中，textOut、textIn、dataOut 和 dataIn 分别表示以下内容：
        textOut: 用于向网络连接发送文本数据的输出流。
        textIn: 用于从网络连接接收文本数据的输入流。
        dataOut: 用于向网络连接发送原始数据（如字节）的输出流。
        dataIn: 用于从网络连接接收原始数据（如字节）的输入流。
        它们的联系在于它们都是用于在网络连接中进行数据传输的流对象，但区别在于 textOut 和 textIn 是用于文本数据的流，而 dataOut 和 dataIn 是用于原始数据的流：*/
            this.networkInterface = networkInterface;
            this.socket = new Socket(InetAddress.getLocalHost(), port);
            this.textOut = new PrintWriter (new OutputStreamWriter(this.socket.getOutputStream())); 
            this.textIn = new BufferedReader (new InputStreamReader(this.socket.getInputStream()));
            this.dataOut = this.socket.getOutputStream();
            this.dataIn = this.socket.getInputStream();
            this.messagesSent = new ArrayList<String>();
            this.messagesReceived = new ArrayList<String>();

            // logging creation of connection
            this.networkInterface.getNetworkProcess().handleEvent(new ClientConnectionEvent(serverType, port));
        }
        catch(Exception e){
            throw new ConnectionSetupException(port,e);
        }
    }

    /**
     * 确定连接是否打开.
     * 
     * @return True if the connection is open, false if not.
     */
    public boolean isOpen(){
        return !this.isClosed();
    }

    /**
     * 关闭底层套接字.
     */
    public void close(){
        try{
            // closing connection
            this.socket.close();

            // passing the error to the network process
            this.networkInterface.getNetworkProcess().handleError(new ConnectionTerminatedException(this, new ConnectionClosedException(this.getPort())));
        }
        catch(Exception e){
            this.networkInterface.getNetworkProcess().handleError(new SocketCloseException(this.getPort()));
        }
    }
    
  /**
     * 向连接终结点发送消息。
     *
     * @param message 要发送的消息。
     * @throws MessageSendException 如果无法发送消息。
     */
    public void sendMessage(String message) throws MessageSendException{
        try{
            // 发送请求
            this.textOut.println(message);
            this.textOut.flush(); 

            // logging message
            this.messagesSent.add(message);
            this.networkInterface.logMessageSent(this.socket, message);
        }
        catch(Exception e){
            throw new MessageSendException(message, this.getPort(), e);
        }
    }

    /**
     *等待传入消息的时间长度为未绑定的时间长度.
     * 
     * @return The message receieved as a String.
     * @throws MessageReceivedException If the message could not be received.
     */
    public String getMessage() throws MessageReceivedException{
        try{
            // getting request from connnection
            String message = this.textIn.readLine();

            // Message is non-null
            if(message != null){
                // logging message
                this.messagesReceived.add(message);
                this.networkInterface.logMessageReceived(this.socket, message);

                return message;
            }
            // message is null - connection down
            else{
                throw new ConnectorDisconnectedException(this.getPort());
            }
        }
        catch(Exception e){
            throw new MessageReceivedException(this.getPort(), e);
        }
    }

    /**
     * Waits for a message to arrive within the given timeout.
     * 
     * @param timeout The timeout to wait for the message to arrive.
     * @return The message receieved.
     * @throws ConnectorDisconnectedException If the connector disconnected while waiting for 
     * the message to arrive.
     * @throws MessageReceivedException If the message could not be receieved, or could not 
     * be received within the timeout period.
     */
    public String getMessageWithinTimeout(int timeout) throws Exception{
        try{
            // setting socket timeout
            this.socket.setSoTimeout(timeout);

            // getting request from connnection
            String message = this.textIn.readLine();

            // Message is non-null
            if(message != null){
                this.socket.setSoTimeout(0);

                // logging message
                this.messagesReceived.add(message);
                this.networkInterface.logMessageReceived(this.socket, message);

                return message;
            }
            // message is null - connection down
            else{
                throw new ConnectorDisconnectedException(this.getPort());
            }
        }
        catch(Exception e){
            this.socket.setSoTimeout(0);

            // Handling Specific Cases

            // Socket timeout exception - throw a message timeout exception
            if(e instanceof SocketTimeoutException){
                throw new MessageReceivedException(this.getPort(), new MessageTimeoutException());
            }
            // other form of exception
            else{
                throw new MessageReceivedException(this.getPort(), e);
            }
        }
    }

    /**
     * 将字节数据发送到连接终结点。
     * 
     * @param bytes The array of bytes to be sent.
     * @throws MessageSendException If the bytes could not be sent.
     */
    public void sendBytes(byte[] bytes) throws MessageSendException{
        try{
            // Sending request
            this.dataOut.write(bytes);
            this.textOut.flush(); 

            // logging
            this.messagesSent.add("[FILE CONTENT]");
            this.networkInterface.logMessageSent(this.socket, "[FILE CONTENT]");
        }
        catch(Exception e){
            throw new MessageSendException(this.getPort(), e);
        }

    }

    /**
     * 等待 N 个字节在给定的超时内到达。
     * 
     * @param timeout The timeout to wait for the message to arrive.
     * @return The array of bytes gathered from the connection.
     * @throws ConnectorDisconnectedException If the connector disconnected while waiting for 
     * the bytes to arrive.
     * @throws MessageReceivedException If the bytes could not be receieved, or could not 
     * be received within the timeout period.
     */
    public byte[] getNBytesWithinTimeout(int n, int timeout) throws Exception{
        try{
            // setting socket timeout
            this.socket.setSoTimeout(timeout);

            // getting request from connnection - returns empty as soon as connection drops
            byte[] bytes = this.dataIn.readNBytes(n);

            // returninig the gathered bytes
            if(bytes.length == n){
                this.socket.setSoTimeout(0);

                // logging message
                this.messagesReceived.add("[FILE CONTENT]");
                this.networkInterface.logMessageReceived(this.socket, "[FILE CONTENT]");

                // returning
                return bytes;
            }
            else{
                throw new ConnectorDisconnectedException(this.getPort());
            }
        }
        catch(Exception e){
            this.socket.setSoTimeout(0);

            // Handling Specific Cases

            // Socket timeout exception - throw a message timeout exception
            if(e instanceof SocketTimeoutException){
                throw new MessageReceivedException(this.getPort(), new MessageTimeoutException());
            }
            // other form of exception
            else{
                throw new MessageReceivedException(this.getPort(), e);
            }
        }
    }

    /////////////////////////
    // GETTERS AND SETTERS //
    /////////////////////////

    public void setNetworkInterface(NetworkInterface networkInterface){
        this.networkInterface = networkInterface;
    }

    public Socket getSocket(){
        return this.socket;
    }

    public boolean isClosed(){
        return this.socket.isClosed();
    }

    public int getPort(){
        return this.socket.getPort();
    }

    public int getLocalPort(){
        return this.socket.getLocalPort();
    }

    public ArrayList<String> getMessagesSent(){
        return this.messagesSent;
    }

    public ArrayList<String> getMessagesReceived(){
        return this.messagesReceived;
    }
}