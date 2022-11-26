import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TCPEnd{

    private static HashMap<Integer, Boolean> connected = new HashMap<Integer, Boolean>();
    private final int SYN = 1;
    private final int FIN = 2;
    private final int ACK = 3;
    byte[] response;
    public static byte[] main(String[] args){

        // check if arguments are complete
        if (args.length != 12 && args.length != 8) {
            System.out.println("Error: missing or additional arguments");
            System.exit(0);
            }

        switch(args.length){
            case 12:
                System.out.println("This is a sender");
                int client_port = Integer.parseInt(args[1]);
                int remote_ip = Integer.parseInt(args[3]);
                int remote_port = Integer.parseInt(args[5]);
                String file_name = args[7];
                byte MTU = Byte.parseByte(args[9]);
                int sws = Integer.parseInt(args[11]);
                TCPEndobj send = new TCPEndobj(client_port, remote_ip, remote_port, file_name, MTU, sws, remote_port, 0);
                byte[] data = Files.readAllBytes(Paths.get(file_name));
                if(!connected.containsKey(remote_ip)){
                    Boolean handshake_res =  handshake(remote_port, remote_ip, SYN);
                    if (handshake_res == true){
                        response = new Sender(send, client_port, remote_port, remote_ip, data);
                    }
                    return response;
                }
                // send.client_port = client_port;
                // send.remote_ip = remote_ip;
                // send.remote_port = remote_port;
                // send.file_name = file_name;
                // send.MTU = MTU;
                // send.sws = sws;
                // // this.listen_port = listen_port;
                // send.timer = 0; // use timer to track time before receiving ACK

                // read payload from file path
                // byte[] payload = 
                // send TCP packets
                // sendTCP()

                break;
            case 8:
                System.out.println("This is a receiver");
                listen_port = Integer.parseInt(args[1]);
                MTU = Byte.parseByte(args[3]);
                sws = Integer.parseInt(args[5]);
                file_name = args[7];
                

                break;
        }

    }

    public boolean handshake(int destIP, int destPort, byte type){
        // three way handshake
        switch(type){
            case SYN:

                break;
            case FIN:

                break;
            case ACK:

                break;
        }
        return true;
    }



}