import java.nio.ByteBuffer;
import java.util.Arrays;

enum ConnectionState {
    SYN,
    SYN_ACK,
    ACK,
    FIN,
    FIN_ACK
}

public class TCPsegment implements Comparable<TCPsegment>{

    private boolean syn;
    private boolean ack;
    private boolean fin;
    private int sequenceNum;
    private int ackNum;
    private byte[] data;
    private int dataSize;
    private short checksum;
    private long time;

    public TCPsegment(boolean syn, boolean ack, boolean fin, int sequenceNum, int ackNum, byte[] data, int dataSize) {
        this.syn = syn;
        this.ack = ack;
        this.fin = fin;
        this.sequenceNum = sequenceNum;
        this.ackNum = ackNum;
        this.data = data;
        this.dataSize = dataSize;
        this.time = System.nanoTime();
    }

    public TCPsegment(boolean syn, boolean ack, boolean fin, int sequenceNum, int ackNum, byte[] data, int dataSize, long time) {
        this.syn = syn;
        this.ack = ack;
        this.fin = fin;
        this.sequenceNum = sequenceNum;
        this.ackNum = ackNum;
        this.data = data;
        this.dataSize = dataSize;
        this.checksum = 0;
        this.time = time;
    }
    
    public TCPsegment() {
        this.syn = false;
        this.ack = false;
        this.fin = false;
        this.sequenceNum = 0;
        this.ackNum = 0;
        this.data = new byte[0];
        this.dataSize = 0;
        this.checksum = 0;
        this.time = System.nanoTime();
    }

    @Override
    public int compareTo(TCPsegment seg) {
        return Integer.compare(this.sequenceNum, seg.sequenceNum);
    }

    public static TCPsegment getConnectionSegment(int sequenceNum, int ackNum, ConnectionState state) {
        switch(state) {
            case SYN:
                return new TCPsegment(true, false, false, sequenceNum, ackNum, new byte[0], 0);
            case SYN_ACK:
                return new TCPsegment(true, true, false, sequenceNum, ackNum, new byte[0], 0);
            case ACK:
                return new TCPsegment(false, true, false, sequenceNum, ackNum, new byte[0], 0);
            case FIN:
                return new TCPsegment(false, false, true, sequenceNum, ackNum, new byte[0], 0);
            case FIN_ACK:
                return new TCPsegment(false, true, true, sequenceNum, ackNum, new byte[0], 0);
            default:
                return null;
        }
    }

    public static TCPsegment getAckSegment(int sequenceNum, int ackNum, long time) {
        return new TCPsegment(false, true, false, sequenceNum, ackNum, new byte[0], 0, time);
    }

    public static TCPsegment getDataSegment(int sequenceNum, int ackNum, byte[] data) {
        return new TCPsegment(false, true, false, sequenceNum, ackNum, data, data.length);
    }
    
    public byte[] serialize() {
        int size = this.data.length;  // total segment size
        if (this.data == null) {
            size = 24;
        } else {
            size = this.data.length + 24;
        }

        byte[] segment = new byte[size];
        ByteBuffer bb = ByteBuffer.wrap(segment);
        bb.putInt(this.sequenceNum);
        bb.putInt(this.ackNum);
        bb.putLong(this.time);

        // set length and flags
        int flags = 4 * (this.syn ? 1 : 0) + 2 * (this.ack ? 1 : 0) + (this.fin ? 1 : 0); 
        bb.putInt((this.dataSize << 3) + flags);  
        
        bb.putInt(0);  // set checksum

        if (this.dataSize != 0) {
            bb.put(this.data);
        }

        bb.rewind();

        // calculate checksum
        int temp = 0;
        for (int i = 0; i < segment.length/2 ; i++) {
            temp += bb.getShort();
        }
        if (segment.length % 2 == 1) {
            temp += (bb.get() & 0xFF) << 8;
        }
        while (temp > 0xFFFF) {
            temp = (temp >> 16) + (temp - ((temp >> 16) << 16));
        }

        this.checksum = (short) (0xFFFF & ~temp);
        bb.putShort(22, this.checksum);
        
        return segment;
    }

    public TCPsegment deserialize(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        this.sequenceNum = bb.getInt();
        this.ackNum = bb.getInt();
        this.time = bb.getLong();

        int flags = bb.getInt();
        this.dataSize = flags >> 3;
        this.syn = ((flags & 4) == 4);
        this.ack = ((flags & 2) == 2);
        this.fin = ((flags & 1) == 1);

        bb.getShort();
        this.checksum = bb.getShort();
        this.data = Arrays.copyOfRange(data, bb.position(), bb.position()+this.dataSize);
        return this;
    }

    public boolean isSyn() {
        return syn;
    }

    public void setSyn(boolean syn) {
        this.syn = syn;
    }

    public boolean isAck() {
        return ack;
    }

    public void setAck(boolean ack) {
        this.ack = ack;
    }

    public boolean isFin() {
        return fin;
    }

    public void setFin(boolean fin) {
        this.fin = fin;
    }

    public int getSequenceNum() {
        return sequenceNum;
    }

    public void setSequenceNum(int sequenceNum) {
        this.sequenceNum = sequenceNum;
    }

    public int getAckNum() {
        return ackNum;
    }

    public void setAckNum(int ackNum) {
        this.ackNum = ackNum;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getDataSize() {
        return dataSize;
    }

    public void setDataSize(int dataSize) {
        this.dataSize = dataSize;
    }

    public short getChecksum() {
        return this.checksum;
    }

    public void setChecksum(short checksum) {
        this.checksum = checksum;
    }

    public void resetChecksum() {
        this.checksum = 0;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
