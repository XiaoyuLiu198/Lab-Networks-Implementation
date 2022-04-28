import java.io.IOException;
import java.net.InetAddress;

public class TCPend {
  public static void main(String[] args) throws IOException {
    int port = -1;
    int remotePort = -1;
    InetAddress remoteIP = null;
    String fileName = null;
    int mtu = -1;
    int sws = -1;

    if (args.length == 12) { 
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (arg.equals("-p")) {
          port = Integer.parseInt(args[++i]);
        } else if (arg.equals("-s")) {
          remoteIP = InetAddress.getByName(args[++i]);
        } else if (arg.equals("-a")) {
          remotePort = Integer.parseInt(args[++i]);
        } else if (arg.equals("-f")) {
          fileName = args[++i];
        } else if (arg.equals("-m")) {
          mtu = Integer.parseInt(args[++i]);
        } else if (arg.equals("-c")) {
          sws = Integer.parseInt(args[++i]);
        }
      }

      if (remoteIP == null || remotePort == -1 || port == -1 || sws == -1
          || mtu == -1 || fileName == null) {
        System.out.println(
            "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      }

      TCPsender sender = new TCPsender(port, remoteIP, remotePort, fileName, mtu, sws);
      try {
        sender.openConnection();
        sender.sendData();
        sender.closeConnection();
      } catch (MaxRetransmitException e) {
        e.printStackTrace();
      } catch (UnexpectedFlagException e) {
        e.printStackTrace();
      } finally {
        sender.socket.close();
        sender.printFinalStatsHeader();

      }
    } else if (args.length == 8) { 
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (arg.equals("-p")) {
          remotePort = Integer.parseInt(args[++i]);
        } else if (arg.equals("-m")) {
          mtu = Integer.parseInt(args[++i]);
        } else if (arg.equals("-c")) {
          sws = Integer.parseInt(args[++i]);
        } else if (arg.equals("-f")) {
          fileName = args[++i];
        } else {
          System.out.println("Wrong command.");
        }
      }

      TCPreceiver receiver = new TCPreceiver(remotePort, mtu, sws, fileName);
      try {
        boolean isConnected = false;
        TCPsegment firstAckReceived = null;
        while (!isConnected) {
          try {
            firstAckReceived = receiver.openConnection();
          } catch (SegmentChecksumMismatchException e) {
            e.printStackTrace();
            continue;
          } catch (UnexpectedFlagException e) {
            e.printStackTrace();
            continue;
          }
          isConnected = true;
        }
        receiver.receiveDataAndClose(firstAckReceived);
      } catch (MaxRetransmitException e) {
        e.printStackTrace();
      }
      receiver.socket.close();
      receiver.printFinalStatsHeader();

    } else {
      System.out.println("Wrong command.");
    }
  }
}
