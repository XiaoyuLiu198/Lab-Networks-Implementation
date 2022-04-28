import java.nio.ByteBuffer;
import java.util.Arrays;

enum HandshakeType {
  SYN, 
  SYNACK, 
  ACK, 
  FIN, 
  FINACK
}

public class TCPsegment implements Comparable<TCPsegment> {

  public int sequenceNum;
  public int ackNum;
  public long time;
  public boolean syn;
  public boolean fin;
  public boolean ack;
  public short checksum;
  public byte[] data;
  public int dataLength;

  public TCPsegment() {
    this(0, 0, System.nanoTime(), false, false, false, new byte[0], 0);
  }

  public TCPsegment(int bsNum, int ackNum, boolean syn, boolean fin, boolean ack,
      byte[] payloadData, int dataLength) {
    this(bsNum, ackNum, System.nanoTime(), syn, fin, ack, payloadData, dataLength);
  }

  public TCPsegment(int bsNum, int ackNum, long timestamp, boolean syn, boolean fin, boolean ack,
      byte[] payloadData, int dataLength) {
    this.sequenceNum = bsNum;
    this.ackNum = ackNum;
    this.time = timestamp;
    this.syn = syn;
    this.fin = fin;
    this.ack = ack;
    this.checksum = 0;
    this.data = payloadData;
    this.dataLength = dataLength;
  }

  public static TCPsegment getDataSegment(int bsNum, int ackNum, byte[] payloadData) {
    return new TCPsegment(bsNum, ackNum, false, false, true, payloadData, payloadData.length);
  }

  public static TCPsegment getConnectionSegment(int bsNum, int ackNum, HandshakeType type) {
    if (type == HandshakeType.SYN) {
      return new TCPsegment(bsNum, ackNum, true, false, false, new byte[0], 0);
    } else if (type == HandshakeType.SYNACK) {
      return new TCPsegment(bsNum, ackNum, true, false, true, new byte[0], 0);
    } else if (type == HandshakeType.ACK) {
      return new TCPsegment(bsNum, ackNum, false, false, true, new byte[0], 0);
    } else if (type == HandshakeType.FIN) {
      return new TCPsegment(bsNum, ackNum, false, true, false, new byte[0], 0);
    } else if (type == HandshakeType.FINACK) {
      return new TCPsegment(bsNum, ackNum, false, true, true, new byte[0], 0);
    } else {
      return null;
    }
  }

  public static TCPsegment getAckSegment(int bsNum, int ackNum, long timestamp) {
    return new TCPsegment(bsNum, ackNum, timestamp, false, false, true, new byte[0], 0);
  }

  public byte[] serialize() {
    int length;
    if (data == null) {
      length = 24;
    } else {
      length = data.length + 24;
    }
    byte[] allSegmentData = new byte[length];

    ByteBuffer bb = ByteBuffer.wrap(allSegmentData);
    bb.putInt(sequenceNum);
    bb.putInt(ackNum);
    bb.putLong(time);

    int lengthAndFlags = 0b0;
    lengthAndFlags = dataLength << 3;
    if (syn) {
      lengthAndFlags += (0b1 << 2);
    }
    if (fin) {
      lengthAndFlags += (0b1 << 1);
    }
    if (ack) {
      lengthAndFlags += (0b1 << 0);
    }
    bb.putInt(lengthAndFlags);

    bb.putInt(0x0000);

    if (dataLength != 0) {
      bb.put(data);
    }

    bb.rewind();
    int tempSum = 0;
    for (int i = 0; i < allSegmentData.length / 2; i++) {
      tempSum += bb.getShort();
    }
    if (allSegmentData.length % 2 == 1) {
      tempSum += (bb.get() & 0xff) << 8;
    }

    while (tempSum > 0xffff) {
      int carryoverBits = tempSum >> 16;
      int lastSixteenBits = tempSum - ((tempSum >> 16) << 16);
      tempSum = lastSixteenBits + carryoverBits;
    }
    this.checksum = (short) (~tempSum & 0xffff);

    bb.putShort(22, this.checksum);

    return allSegmentData;
  }

  public TCPsegment deserialize(byte[] data) {
    ByteBuffer bb = ByteBuffer.wrap(data);

    this.sequenceNum = bb.getInt();
    this.ackNum = bb.getInt();
    this.time = bb.getLong();

    int lengthAndFlags = bb.getInt();
    this.dataLength = lengthAndFlags >> 3;
    this.syn = false;
    this.ack = false;
    this.fin = false;
    if (((lengthAndFlags >> 2) & 0b1) == 1) {
      this.syn = true;
    }
    if (((lengthAndFlags >> 1) & 0b1) == 1) {
      this.fin = true;
    }
    if ((lengthAndFlags & 0b1) == 1) {
      this.ack = true;
    }
    bb.getShort();
    this.checksum = bb.getShort();

    this.data = Arrays.copyOfRange(data, bb.position(), dataLength + bb.position());

    return this;
  }

  public int getByteSequenceNum() {
    return sequenceNum;
  }

  public void setByteSequenceNum(int byteSequenceNum) {
    this.sequenceNum = byteSequenceNum;
  }

  public int getAckNum() {
    return ackNum;
  }

  public void setAckNum(int ackNum) {
    this.ackNum = ackNum;
  }

  public long getTimestamp() {
    return time;
  }

  public void setTimestamp(long timestamp) {
    this.time = timestamp;
  }

  public int getDataLength() {
    return dataLength;
  }

  public void setLength(int dataLength) {
    this.dataLength = dataLength;
  }

  public boolean syn() {
    return syn;
  }

  public void setSyn(boolean syn) {
    this.syn = syn;
  }

  public boolean ack() {
    return ack;
  }

  public void setAck(boolean ack) {
    this.ack = ack;
  }

  public boolean fin() {
    return fin;
  }

  public void setFin(boolean fin) {
    this.fin = fin;
  }

  public short getChecksum() {
    return checksum;
  }

  public void setChecksum(short checksum) {
    this.checksum = checksum;
  }

  public void resetChecksum() {
    this.checksum = 0;
  }

  public byte[] getPayload() {
    return this.data;
  }

  @Override
  public int compareTo(TCPsegment o) {
    return Integer.compare(this.sequenceNum, o.sequenceNum);
  }

}
