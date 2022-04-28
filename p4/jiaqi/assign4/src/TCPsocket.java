import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;

public class TCPsocket {
  public static final DecimalFormat threePlaces = new DecimalFormat("0.000");
  public static final short MAX_RETRANSMITS = 16;

  public int senderSourcePort;
  public int receiverPort;
  public InetAddress receiverIp;
  public String fileName;
  public int mtu;
  public int sws;
  public int bsn;
  public int nextByteExpected;
  public DatagramSocket socket;
  public long ertt;
  public long edev;
  public long timeout;

  public int numPacketsSent;
  public int numPacketsReceived;
  public int lastByteSent;
  public int lastByteReceived;
  public int numDiscardPackets;
  public int numRetransmits;
  public int numDupAcks;

  public int getSenderSourcePort() {
    return senderSourcePort;
  }

  public void setSenderSourcePort(int senderSourcePort) {
    this.senderSourcePort = senderSourcePort;
  }

  public int getReceiverPort() {
    return receiverPort;
  }

  public void setReceiverPort(int receiverPort) {
    this.receiverPort = receiverPort;
  }

  public InetAddress getReceiverIp() {
    return receiverIp;
  }

  public void setReceiverIp(InetAddress receiverIp) {
    this.receiverIp = receiverIp;
  }

  public String getfileName() {
    return fileName;
  }

  public void setfileName(String fileName) {
    this.fileName = fileName;
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
        this.ertt = (long) (System.nanoTime() - segment.time);
        this.edev = 0;
        this.timeout = 2 * ertt;
      } else {
        long srtt = (long) (System.nanoTime() - segment.time);
        long sdev = Math.abs(srtt - ertt);
        this.ertt = (long) (0.875f * ertt + (1 - 0.875f) * srtt);
        this.edev = (long) (0.75f * edev + (1 - 0.75f) * sdev);
        this.timeout = this.ertt + 4 * this.edev;
      }
    }

    this.numPacketsReceived++;

    printOutput(segment, false);

    return segment;
  }

  public void printOutput(TCPsegment segment, boolean isSender) {
    if (isSender) {
      System.out.print("snd ");
    } else {
      System.out.print("rcv ");
    }
    System.out.print(segment.getTimestamp());
    System.out.print(segment.syn ? " S" : " -");
    System.out.print(segment.ack ? " A" : " -");
    System.out.print(segment.fin ? " F" : " -");
    System.out.print((segment.getDataLength() > 0) ? " D" : " -");
    System.out.print(" " + segment.sequenceNum);
    System.out.print(" " + segment.getDataLength());
    System.out.print(" " + segment.ackNum);
    System.out.println();
  }

  public void sendPacket(TCPsegment segment, InetAddress destIp, int destPort) {
    byte[] segmentBytes = segment.serialize();
    DatagramPacket packet = new DatagramPacket(segmentBytes, segmentBytes.length, destIp, destPort);
    try {
      socket.send(packet);
      this.numPacketsSent++;
    } catch (IOException e) {
      e.printStackTrace();
    }
    printOutput(segment, true);
  }

  public void printFinalStats() {
    System.out.println("  Data Sent (KB): " + threePlaces.format((double) (this.lastByteSent / 1000.0F)));
    System.out.println("  Data Received (KB) : " + threePlaces.format((double) (this.lastByteReceived / 1000.0F)));
    System.out.println("  Packets Sent: " + this.numPacketsSent);
    System.out.println("  Packets Received: " + this.numPacketsReceived);
    System.out.println("  Out-of-Sequence Packets Discarded: " + this.numDiscardPackets);
    System.out.println("  Number of Retransmissions: " + this.numRetransmits);
    System.out.println("  Number of Duplicate Acknowledgements: " + this.numDupAcks);
  }
}
