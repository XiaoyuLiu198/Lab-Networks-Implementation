public class Iperfer {
  public static void main(String[] args) {
    boolean client = false;
    boolean server = false;
    int i = 1;  // start from the second argument
    String hostName = "";
    int portNum = -1;
    long time = -1;
    
    if (args.length != 7 && args.length != 3) {
      System.out.println("Error: missing or additional arguments");
      System.exit(0);
    }

    if (args[0].equals("-c")) {  // client mode
      if (args.length == 7) {
        client = true;
      } else {
        System.out.println("Error: missing or additional arguments");
        System.exit(0);
      }
    }

    if (args[0].equals("-s")) {  // server mode
      if (args.length == 3) {
        server = true;
      } else {
        System.out.println("Error: missing or additional arguments");
        System.exit(0);
      }
    }

    if (!client && !server) {
      System.out.println("Error: missing or additional arguments");
      System.exit(0);
    }

    while (i < args.length) {
      switch(args[i++]) {
        case "-h":
          if (!client) {
            System.out.println("Error: missing or additional arguments");
            System.exit(0);
          }
          hostName = args[i++];
          break;

        case "-p":
          try {
            portNum = Integer.parseInt(args[i++]);
          } catch (NumberFormatException e) {
            System.out.println("Error: port number must be in the range 1024 to 65535");
            System.exit(0);
          }
          if (portNum < 1024 || portNum > 65535) {
            System.out.println("Error: port number must be in the range 1024 to 65535");
            System.exit(0);
          }
          break;

        case "-t":
          if (!client) {
            System.out.println("Error: missing or additional arguments");
            System.exit(0);
          }
          try {
            time = Integer.parseInt(args[i++]);
          } catch (NumberFormatException e) {
            System.out.println("Error: missing or additional arguments");
            System.exit(0);
          }
          break;

        default:
          System.out.println("Error: missing or additional arguments");
          System.exit(0);
      }
    }

    if (client) {
      Client myClient = new Client(hostName, portNum, time);
      myClient.run();
    }

    if (server) {
      Server myServer = new Server(portNum);
      myServer.run();
    }
  }
}
