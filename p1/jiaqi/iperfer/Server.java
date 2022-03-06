import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
  private int portNum;
  Server(int portNum) {
    this.portNum = portNum;
  }

  public void run() {
    long singleRead = 0;
    long byteReceived = 0;
    long kbReceived = 0;
    double rate = 0;
    try {
      byte packet[] = new byte[1000];
      ServerSocket serverSocket = new ServerSocket(portNum);
      Socket clientSocket = serverSocket.accept();
      long startTime = System.currentTimeMillis();
      
      while ((singleRead = clientSocket.getInputStream().read(packet, 0, 1000)) > -1) {
        byteReceived += singleRead;
      }
      long endTime = System.currentTimeMillis();
      clientSocket.close();
      serverSocket.close();

      double time = (double) ((endTime - startTime) / 1000.0);
      kbReceived = byteReceived / 1000;
      rate = (8.0*kbReceived/1000.0) / time;  // Mbps = Megabits per second

    } catch (IOException e) {
      e.printStackTrace();
    }
    System.out.format("received=%d KB rate=%.3f Mbps%n", kbReceived, rate);
  }
}
