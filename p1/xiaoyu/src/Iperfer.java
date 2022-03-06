import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.lang.Integer;
import java.lang.String;
import java.util.Date;

public class Iperfer {
    public static void main(String[] args){
        byte[] bytes = new byte[1000];
        Arrays.fill( bytes, (byte) 0 );
        if (args[0] == "-c") {
            //client mode
            String hostname = null;
            Integer port_num = null;
            Integer time = null;
            if (args.length == 7) {
                hostname = args[2];
                port_num = Integer.valueOf(args[4]);
                // port 1024 to 65535
                if (port_num < 1024 || port_num > 65535) {
                    System.out.println("Error: port number must be in the range 1024 to 65535");
                    System.exit(0);
                }
                time = Integer.valueOf(args[6]);
            } else {
                System.out.println("Error: wrong type of mode");
                System.exit(0);
            }
            //connect
            Socket client = null;
            try {
                client = new Socket(hostname, port_num);
            } catch (IOException e) {
                e.printStackTrace();
            }
            DataOutputStream out = null;
            try {
                out = new DataOutputStream(client.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            long start = System.currentTimeMillis();
            long elapsed = 0L;
            long sent_bytes = 0L;
            while (elapsed < time * 1000) {
                try {
                    out.write(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                out.flush();
                sent_bytes = sent_bytes + 1;
                elapsed = (new Date()).getTime() - start;
            }
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if(args[0] == "-s"){
            //server mode
            if (args.length == 3){
                Integer port = Integer.valueOf(args[2]);
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
