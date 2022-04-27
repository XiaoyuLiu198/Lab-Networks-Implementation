import java.util.*;

class TCPend {

    public static void main(String[] args) {
        if(args.length != 12 && args.length != 6) {
            return;
        }
        int port = 0;
        String remoteIP = null;
        int serverport = 0;
        String filename = null;
        int mtu = 0;
        int sws = 0;
        if(args.length == 12) {
            if(args[0].equals("-p") && args[2].equals("-s") && args[4].equals("-a") && args[6].equals("-f") && args[8].equals("-m") && args[10].equals("-c")) {
                // Get respective params
                port = Integer.parseInt(args[1]);
                remoteIP = args[3];
                serverport = Integer.parseInt(args[5]);
                filename = args[7];
                mtu = Integer.parseInt(args[9]);
                sws = Integer.parseInt(args[11]);

                // Construct the entity Host
                Client curClient = new Client(port, remoteIP, serverport, filename, mtu, sws);
                curClient.connectionRequest();
                curClient.runClient();
            } else {
                System.out.println("Usage for client: java TCPend -p <port> -s <remote-IP> -a <remote-port> -f <file name> -m <mtu> -c <sws>");
            } 
        } else if(args.length == 6) {
            if(args[0].equals("-p") && args[2].equals("-m") && args[4].equals("-c")) {
                // Get respective params
                port = Integer.parseInt(args[1]);
                mtu = Integer.parseInt(args[3]);
                sws = Integer.parseInt(args[5]);

                // Construct the entity Client
                Host curHost = new Host(port, mtu, sws);
                curHost.runHost();
            } else {
                System.out.println("Usage for server: java TCPend -p <port> -m <mtu> -c <sws>");
            }
        }

    }
}