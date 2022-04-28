import java.nio.ByteBuffer;
import java.util.Arrays;

enum ConnectionState {
  SYN, 
  SYN_ACK, 
  ACK, 
  FIN, 
  FIN_ACK
}

public class TCPsegment implements Comparable<TCPsegment> {

  public int sequenceNum;
  public int ackNum;
  public boolean syn;
  public boolean fin;
  public boolean ack;
  public byte[] data;
  public int dataLength;
  public short checksum;
  public long time;

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

  public static TCPsegment getConnectionSegment(int bsNum, int ackNum, ConnectionState type) {
    if (type == ConnectionState.SYN) {
      return new TCPsegment(bsNum, ackNum, true, false, false, new byte[0], 0);
    } else if (type == ConnectionState.SYN_ACK) {
      return new TCPsegment(bsNum, ackNum, true, false, true, new byte[0], 0);
    } else if (type == ConnectionState.ACK) {
      return new TCPsegment(bsNum, ackNum, false, false, true, new byte[0], 0);
    } else if (type == ConnectionState.FIN) {
      return new TCPsegment(bsNum, ackNum, false, true, false, new byte[0], 0);
    } else if (type == ConnectionState.FIN_ACK) {
      return new TCPsegment(bsNum, ackNum, false, true, true, new byte[0], 0);
    } else {
      return null;
    }
  }

  public static TCPsegment getAckSegment(int bsNum, int ackNum, long timestamp) {
    return new TCPsegment(bsNum, ackNum, timestamp, false, false, true, new byte[0], 0);
  }

  public byte[] serialize() {
    // int length;
    // if (data == null) {
    //   length = 24;
    // } else {
    //   length = data.length + 24;
    // }

    int length = (data == null) ? 24 : data.length + 24;
    byte[] segmentData = new byte[length];

    ByteBuffer bb = ByteBuffer.wrap(segmentData);
    bb.putInt(sequenceNum);
    bb.putInt(ackNum);
    bb.putLong(time);

    int flags = 0b0;
    flags = dataLength << 3;
    if (syn) {
      flags += (0b1 << 2);
    }
    if (fin) {
      flags += (0b1 << 1);
    }
    if (ack) {
      flags += (0b1 << 0);
    }
    bb.putInt(flags);

    bb.putInt(0x0000);

    if (dataLength != 0) {
      bb.put(data);
    }

    bb.rewind();
    int tempSum = 0;
    for (int i = 0; i < segmentData.length / 2; i++) {
      tempSum += bb.getShort();
    }
    if (segmentData.length % 2 == 1) {
      tempSum += (bb.get() & 0xff) << 8;
    }

    while (tempSum > 0xffff) {
      int carryoverBits = tempSum >> 16;
      int lastSixteenBits = tempSum - ((tempSum >> 16) << 16);
      tempSum = lastSixteenBits + carryoverBits;
    }
    this.checksum = (short) (~tempSum & 0xffff);

    bb.putShort(22, this.checksum);

    return segmentData;
  }

  public TCPsegment deserialize(byte[] data) {
    ByteBuffer bb = ByteBuffer.wrap(data);

    this.sequenceNum = bb.getInt();
    this.ackNum = bb.getInt();
    this.time = bb.getLong();

    int flags = bb.getInt();
    this.dataLength = flags >> 3;
    this.syn = false;
    this.ack = false;
    this.fin = false;
    if (((flags >> 2) & 0b1) == 1) {
      this.syn = true;
    }
    if (((flags >> 1) & 0b1) == 1) {
      this.fin = true;
    }
    if ((flags & 0b1) == 1) {
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
  public int compareTo(TCPsegment seg) {
    return Integer.compare(this.sequenceNum, seg.sequenceNum);
  }

}
