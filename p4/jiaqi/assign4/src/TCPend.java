import java.net.InetAddress;
<<<<<<< HEAD

public class TCPend {
  public static void main(String[] args) throws IOException {
    int senderSourcePort = -1;
    int receiverPort = -1;
    InetAddress receiverIp = null;
    String filename = null;
    int mtu = -1;
    int sws = -1;

    if (args.length == 12) { // TCPEnd sender mode
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (arg.equals("-p")) {
          senderSourcePort = Integer.parseInt(args[++i]);
        } else if (arg.equals("-s")) {
          receiverIp = InetAddress.getByName(args[++i]);
        } else if (arg.equals("-a")) {
          receiverPort = Integer.parseInt(args[++i]);
        } else if (arg.equals("-f")) {
          filename = args[++i];
        } else if (arg.equals("-m")) {
          mtu = Integer.parseInt(args[++i]);
        } else if (arg.equals("-c")) {
          sws = Integer.parseInt(args[++i]);
=======
import java.net.UnknownHostException;

public class TCPend {
    public static void main(String[] args) {
        // -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>
        if(args.length == 12 && args[0].equals("-p") && args[2].equals("-s") &&
            args[4].equals("-a") && args[6].equals("-f") && args[8].equals("-m")
            && args[10].equals("-c")) {
            
            int port = Integer.parseInt(args[1]);
            InetAddress remoteAddress = null;
            try {
                remoteAddress = InetAddress.getByName(args[3]);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            int remotePort = Integer.parseInt(args[5]);
            String file = args[7];
            int mtu = Integer.parseInt(args[9]);
            int sws = Integer.parseInt(args[11]);
            sender(port, remoteAddress, remotePort, file, mtu, sws);
>>>>>>> c848019eb4bb51107a00cddf3c68383835bf8734
        }
        // -p <port> -m <mtu> -c <sws> -f <file name>
        else if(args.length == 8 && args[0].equals("-p") && args[2].equals("-m") &&
            args[4].equals("-c") && args[6].equals("-f")) {
            int port = Integer.parseInt(args[1]);
            int mtu = Integer.parseInt(args[3]);
            int sws = Integer.parseInt(args[5]);
            String file = args[7];
            receiver(port, mtu, sws, file);
        }
        // error
        else {
            System.out.println("usage:\n"
                + "java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>\n"
                + "java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
            System.exit(1);
        }
    }

    public static void sender(int port, InetAddress remoteAddress, int remotePort,
        String file, int mtu, int sws) {
        Sender sender = new Sender(port, mtu, sws, file, remoteAddress, remotePort);
        sender.connect();
        sender.run();
    }

    public static void receiver(int port, int mtu, int sws, String file) {
        Receiver receiver = new Receiver(port, mtu, sws, file);
        receiver.connect();
        receiver.run();
    }
}
