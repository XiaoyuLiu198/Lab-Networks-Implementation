import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;

public class TCPsocket {
  public static final int INITIAL_TIMEOUT_MS = 5000;
  public static final float ALPHA_RTTFACTOR = 0.875F;
  public static final float BETA_DEVFACTOR = 0.75F;
  public static final DecimalFormat threePlaces = new DecimalFormat("0.000");
  public static final short MAX_RETRANSMITS = 16;

  protected int senderSourcePort;
  protected int port;
  protected InetAddress receiverIp;
  protected String filename;
  protected int mtu;
  protected int sws;
  protected int bsn;
  protected int nextByteExpected;
  protected DatagramSocket socket;
  protected long effRTT;
  protected long effDev;
  protected long timeout;

  protected int numPacketSent;
  protected int numPacketReceived;
  protected int lastByteSent;
  protected int numByteReceived;
  protected int numDiscardPackets;
  protected int numRetransmits;
  protected int numDupAcks;

  public int getSenderSourcePort() {
    return senderSourcePort;
  }

  public void setSenderSourcePort(int senderSourcePort) {
    this.senderSourcePort = senderSourcePort;
  }

  public int getReceiverPort() {
    return port;
  }

  public void setReceiverPort(int receiverPort) {
    this.port = receiverPort;
  }

  public InetAddress getReceiverIp() {
    return receiverIp;
  }

  public void setReceiverIp(InetAddress receiverIp) {
    this.receiverIp = receiverIp;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public int getMtu() {
    return mtu;
  }

  public void setMtu(int mtu) {
    this.mtu = mtu;
  }

  public int getSws() {
    return sws;
  }

  public void setSws(int sws) {
    this.sws = sws;
  }

  public int getBsn() {
    return bsn;
  }

  public void setBsn(int bsn) {
    this.bsn = bsn;
  }

  public TCPsegment handlePacket() throws IOException, SegmentChecksumMismatchException {

    byte[] bytes = new byte[mtu + 24];
    DatagramPacket packet = new DatagramPacket(bytes, mtu + 24);
    this.socket.receive(packet);
    bytes = packet.getData();
    TCPsegment segment = new TCPsegment();
    segment = segment.deserialize(bytes);

    short origChk = segment.getChecksum();
    segment.resetChecksum();
    segment.serialize();
    short calcChk = segment.getChecksum();
    if (origChk != calcChk) {

      throw new SegmentChecksumMismatchException("Error: Checksum does not match!");
    }

    if (segment.ack && segment.dataLength == 0) {
      if (segment.sequenceNum == 0) {
        this.effRTT = (long) (System.nanoTime() - segment.time);
        this.effDev = 0;
        this.timeout = 2 * effRTT;
      } else {
        long sampRTT = (long) (System.nanoTime() - segment.time);
        long sampDev = Math.abs(sampRTT - effRTT);
        this.effRTT = (long) (ALPHA_RTTFACTOR * effRTT + (1 - ALPHA_RTTFACTOR) * sampRTT);
        this.effDev = (long) (BETA_DEVFACTOR * effDev + (1 - BETA_DEVFACTOR) * sampDev);
        this.timeout = this.effRTT + 4 * this.effDev;
      }
    }

    this.numPacketReceived++;

    printOutput(segment, false);

    return segment;
  }

  public void printOutput(TCPsegment segment, boolean isSender) {
    if (isSender) {
      System.out.print("snd ");
    } else {
      System.out.print("rcv ");
    }
    System.out.print(segment.time);
    System.out.print(segment.syn ? " S" : " -");
    System.out.print(segment.ack ? " A" : " -");
    System.out.print(segment.fin ? " F" : " -");
    System.out.print((segment.data.length > 0) ? " D" : " -");
    System.out.print(" " + segment.sequenceNum);
    System.out.print(" " + segment.data.length);
    System.out.print(" " + segment.ackNum);
    System.out.println();
  }

  public void sendPacket(TCPsegment segment, InetAddress destIp, int destPort) {
    byte[] segmentBytes = segment.serialize();
    DatagramPacket packet = new DatagramPacket(segmentBytes, segmentBytes.length, destIp, destPort);
    try {
      socket.send(packet);
      this.numPacketSent++;
    } catch (IOException e) {
      e.printStackTrace();
    }
    printOutput(segment, true);
  }

  public void printFinalStats() {
    System.out.println("  Data Sent (KB): " + threePlaces.format((double) (this.lastByteSent / 1000.0F)));
    System.out.println("  Data Received (KB) : " + threePlaces.format((double) (this.numByteReceived / 1000.0F)));
    System.out.println("  Packets Sent: " + this.numPacketSent);
    System.out.println("  Packets Received: " + this.numPacketReceived);
    System.out.println("  Out-of-Sequence Packets Discarded: " + this.numDiscardPackets);
    System.out.println("  Number of Retransmissions: " + this.numRetransmits);
    System.out.println("  Number of Duplicate Acknowledgements: " + this.numDupAcks);
  }
}
