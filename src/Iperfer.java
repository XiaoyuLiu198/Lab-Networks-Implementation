import java.io.DataOutputStream;
import java.net.Socket;

public class Iperfer {
    public static void main(String[] args){
        byte[] bytes = new byte[1000];
        Arrays.fill( bytes, (byte) 0 );
        if (args[0] == "-c"){
            //client mode
            if (args.length == 7){
            String hostname = args[2];
            Int port = args[4];
            // port 1024 to 65535
            if (port < 1024 || port > 65535){
                System.out.println("Error: port number must be in the range 1024 to 65535");
                System.exit(0);
            }
            Int time = args[6];
            }
            else {
                System.out.println("Error: wrong type of mode");
                System.exit(0);
            }
            //connect
            Socket client = new Socket(hostname, port);
            CountingOutputStream out = new CountingOutputStream(socket.getOutputStream());
            long start = System.currentTimeMillis();
            long elapsed = 0L;
            while (elapsed < time * 1000){
                out.write(bytes);
//                out.flush();
                elapsed = (new Date()).getTime() - start;
            }
            out.close();
            long sent_bytes = out.getByteCount();
        }
        else if(args[0] == "-s"){
            //server mode
            if (args.length == 3){
                Int port = args[2];
                // port 1024 to 65535
                if (port < 1024 || port > 65535){
                    System.out.println("Error: port number must be in the range 1024 to 65535");
                    System.exit(0);
                }
            }
            else {
                System.out.println("Error: wrong type of mode");
                System.exit(0);
            }
            //connect
//            ServerSocket client = new Socket(hostname, port);
//            CountingOutputStream out = new CountingOutputStream(socket.getOutputStream());
//            long start = System.currentTimeMillis();
//            long elapsed = 0L;
//            while (elapsed < time * 1000){
//                out.write(bytes);
////                out.flush();
//                elapsed = (new Date()).getTime() - start;
//            }
//            out.close();
//            long sent_bytes = out.getByteCount();
        }
        else{
            System.out.println("Error: wrong type of mode");
            System.exit(0);
        }

    }
}
