import java.net.InetAddress;
import java.net.UnknownHostException;

public class TCPend {
    private static final short DEFAULT_PORT = 8888;
    public static void main(String[] args) throws UnknownHostException {
        short port = DEFAULT_PORT;        // port number at which the client will run
        InetAddress remoteIP = null;           // the IP address of receiver
        short remotePort = DEFAULT_PORT;  // the port at which the remote receiver is running
        String fileName = null;           // the file to be sent
        int mtu = -1;                     // maximum transmission unit in bytes
        int sws = -1;                     // sliding window size in number of segments
        
        if (args.length == 12) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-p")) {
                    port = Short.parseShort(args[++i]);
                } else if (arg.equals("-s")) {
                    remoteIP = InetAddress.getByName(args[++i]);
                } else if (arg.equals("-a")) {
                    remotePort = Short.parseShort(args[++i]);
                } else if (arg.equals("-f")) {
                    fileName = args[++i];
                } else if (arg.equals("-m")) {
                    mtu = Integer.parseInt(args[++i]);
                } else if (arg.equals("-c")) {
                    sws = Integer.parseInt(args[++i]);
                } else {
                    System.out.println("Wrong command.");
                    return;
                }
            }

            TCPsender sender = new TCPsender(port, remoteIP, remotePort, fileName, mtu, sws);

            try {
                sender.connect();
                sender.send();
                sender.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                sender.socket.close();
                System.out.println("----------------- Sender Closed -----------------");
                TCPutil.getStatistics();
            }

        } else if (args.length == 8) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-p")) {
                    port = Short.parseShort(args[++i]);
                } else if (arg.equals("-m")) {
                    mtu = Integer.parseInt(args[++i]);
                } else if (arg.equals("-c")) {
                    sws = Integer.parseInt(args[++i]);
                } else if (arg.equals("-f")) {
                    fileName = args[++i];
                } else {
                    System.out.println("Wrong command.");
                    return;
                }
            }

            TCPreceiver receiver = new TCPreceiver(port, mtu, sws, fileName);
            try {
                boolean connected = false;
                TCPsegment firstAckSegment = null;
                while (!connected) {
                    firstAckSegment = receiver.connect();
                    connected = true;
                }
                receiver.receive(firstAckSegment);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                receiver.socket.close();
                System.out.println("----------------- Receiver Closed -----------------");
                TCPutil.getStatistics();
            }

        } else {
            System.out.println("Wrong command.");
            return;
        }
    }
}
