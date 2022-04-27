import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class UDP {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args[0].equals("-s"))
            sender();
        else if (args[0].equals("-r")) 
            reciever();

    }

    public static void reciever() throws IOException {
        System.out.println("Starting Reciever");

        // create reciever socket that listens on port 1234
        DatagramSocket socket = new DatagramSocket(1234);

        // listen indefinetly
        while (true) {
            // recieve packet
            byte[] buf = new byte[256];
            DatagramPacket packetIn = new DatagramPacket(buf, buf.length);
            socket.receive(packetIn);

            String messageIn = new String(packetIn.getData(), 0, packetIn.getLength());
            System.out.println(messageIn);

        }
    }

    public static void sender() throws IOException, InterruptedException {
        System.out.println("Starting Sender");

        // create sender socket
        DatagramSocket socket = new DatagramSocket(); // NOTE: sender cannot have same port number as reciever port if on same machine
        InetAddress outAddr = InetAddress.getByName("localhost");
        int outPort = 1234;

        while (true) {
            // create payLoad
            String payLoad = "Hello";
            byte[] buf = payLoad.getBytes();

            // create Packet
            DatagramPacket packet = new DatagramPacket(buf, buf.length, outAddr, outPort);
            
            // Send Packet every 5 seconds
            socket.send(packet);
            Thread.sleep(5000);


        }
      
    }
}
