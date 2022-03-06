import java.net.Socket;

public class Client {
  private String hostName;
  private int portNum;
  private long time;

  Client(String hostName, int portNum, long time) {
    this.hostName = hostName;
    this.portNum = portNum;
    this.time = time;
  }

  public void run() {
    long kbSent = 0;
    double rate = 0;
    try {
      byte packet[] = new byte[1000];  // data sent in chunks of 1000 bytes; all zeros
      Socket socket = new Socket(hostName, portNum);
      long endTime = System.currentTimeMillis() + time*1000;

      while (System.currentTimeMillis() < endTime) {
        socket.getOutputStream().write(packet);
        kbSent++;
      }
      socket.close();
      rate = (8.0*kbSent/1000.0) / (double)time;  // Mbps = Megabits per second
      
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.format("sent=%d KB rate=%.3f Mbps%n", kbSent, rate);
  }
}
