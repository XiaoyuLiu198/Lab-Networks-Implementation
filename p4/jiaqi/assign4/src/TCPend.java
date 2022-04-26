public class TCPend {
    private static final short DEFAULT_PORT = 8888;
    public static void main(String[] args) {
        short port = DEFAULT_PORT;        // port number at which the client will run
        String remoteIP = null;           // the IP address of receiver
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
                    remoteIP = args[++i];
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
            
            Sender sender = new Sender(port, remoteIP, remotePort, fileName, mtu, sws);

        } else if (args.length == 6) {
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

            Receiver receiver = new Receiver(port, mtu, sws, fileName);

        } else {
            System.out.println("Wrong command.");
            return;
        }
    }
}
