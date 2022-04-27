import java.net.DatagramPacket;
import java.nio.ByteBuffer;

public class TCPpacket  implements Comparable<TCPpacket>{
    // actual TCP packet headers, will be serialized
    private int sequenceNum;
    private int ack;
    private long timeStamp;
    private int length;
    private boolean synFlag;
    private boolean finFlag;
    private boolean ackFlag;
    private short zeros;
    private short checksum;
    private byte[] payload;

    // constants and other attributes
    private static final int MAX_DATA_LEN = 1446;

    // Constructors
    public TCPpacket() {
        this.sequenceNum = -1;
        this.ack = -1;
        this.timeStamp = -1;
        this.length = 0;
        this.synFlag = false;
        this.finFlag = false;
        this.ackFlag = false;
        this.zeros = -1;
        this.checksum = -1;
        this.payload = null;
    }

    public TCPpacket(byte[] payload) {
        this.sequenceNum = -1;
        this.ack = -1;
        this.timeStamp = -1;
        this.length = 0;
        this.synFlag = false;
        this.finFlag = false;
        this.ackFlag = false;
        this.zeros = -1;
        this.checksum = -1;
        this.payload = payload;
    }

    // Setters
    public void setPayload(byte[] payload) {
        if (payload.length > MAX_DATA_LEN){
            System.out.println("Error: Payload bigger than max allowed (1446)");
            return;
        }
        this.payload = payload;
    }
    public void setSequenceNum(int sequenceNum) {
        this.sequenceNum = sequenceNum;
    }
    public void setAck(int ack) {
        this.ack = ack;
    }
    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }
    public void setLength(int length) {
        this.length = length;
    }
    public void setSynFlag(boolean synFlag) {
        this.synFlag = synFlag;
    }
    public void setFinFlag(boolean finFlag) {
        this.finFlag = finFlag;
    }
    public void setAckFlag(boolean ackFlag) {
        this.ackFlag = ackFlag;
    }
    public void setChecksum(short checksum) {
        this.checksum = checksum;
    }

    // Getters
    public byte[] getPayload() {
        return this.payload;
    }
    public int getSequenceNum() {
        return this.sequenceNum;
    }
    public int getAck() {
        return this.ack;
    }
    public long getTimeStamp() {
        return this.timeStamp;
    }
    public int getLength() {
        return this.length;
    }
    public boolean getSynFlag() {
        return this.synFlag;
    }
    public boolean getFinFlag() {
        return this.finFlag;
    }
    public boolean getAckFlag() {
        return this.ackFlag;
    }
    public short setChecksum() {
        return this.checksum;
    }

    // internal helper methods

    private int determineLengthAndFlags() {
        int length = this.length;
        boolean synFlag = this.synFlag;
        boolean finFlag = this.finFlag;
        boolean ackFlag = this.ackFlag;
        int serializedLength = length << 3;
        int synMask = 1 << 2;
        int finMask = 1 << 1;
        int ackMask = 1;

        if (synFlag) {
            serializedLength = serializedLength | synMask;
        }
        if (finFlag) {
            serializedLength = serializedLength | finMask;
        }
        if (ackFlag) {
            serializedLength = serializedLength | ackMask;
        }

        return serializedLength;

    }

    private int deserializeLength(int lengthField) {
        return lengthField >> 3;
    }
    private boolean deserializeSyn(int lengthField) {
        int syn = lengthField & (1 << 2);
        if (syn == 4) {
            return true;
        }
        return false;
    }
    private boolean deserializeFin(int lengthField) {
        int fin = lengthField & (1 << 1);
        if (fin == 2) {
            return true;
        }
        return false;
    }

    private boolean deserializeAck(int lengthField) {
        int ack = lengthField & 1;
        if (ack == 1) {
            return true;
        }
        return false;
    }


    // Useful Methods

    public byte[] serialize() {
        // find length of total TCP Packet
        int length = 24; // length of TCP header
        if (this.payload != null) {
            length += this.payload.length;
        }
        // create byte[]
        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);

        // add header info to byte[]
        bb.putInt(this.sequenceNum);
        bb.putInt(this.ack);
        bb.putLong(this.timeStamp);
        bb.putInt(determineLengthAndFlags()); // compute length along with flags
        bb.putShort(this.zeros);
        bb.putShort(this.checksum);

        // add payload to byte[] if present
        if (this.payload != null) {
            bb.put(this.payload);
        }
        return data;
    }

    public void deserialize(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);

        // create tcp headers
        this.sequenceNum = bb.getInt();
        this.ack = bb.getInt();
        this.timeStamp = bb.getLong();
        int lengthField = bb.getInt();
        this.length = deserializeLength(lengthField);
        this.synFlag = deserializeSyn(lengthField);
        this.finFlag = deserializeFin(lengthField);
        this.ackFlag = deserializeAck(lengthField);
        this.zeros = bb.getShort();
        this.checksum = bb.getShort();

        // if payload exists, add
        if (this.length > 0) {
            this.payload = new byte[this.length];
            for (int i = 0; i < this.length; i++) {
                byte b = bb.get();
                this.payload[i] = b;
            }
            
        }
    }

    @Override
    public String toString() {
        String str = "";
        str+= "sequenceNum: " + this.sequenceNum + "\n";
        str+= "ack: " + this.ack + "\n";
        str+= "timeStamp: " + this.timeStamp + "\n";
        str+= "length: " + this.length + "\n";
        str+= "synFlag: " + this.synFlag + "\n";
        str+= "finFlag: " + this.finFlag + "\n";
        str+= "ackFlag: " + this.ackFlag + "\n";
        str+= "zeros: " + this.zeros + "\n";
        str+= "checksum: " + this.checksum + "\n";
        if (this.payload != null) {
            System.out.println(this.payload.length - 21);
            str+= "payLoad length: " + this.payload.length + "\n";
            if (this.payload.length > 40){
                str+= "first 20 characters: " + new String(this.payload, 0, 20) + "\n";
                str+= "last 20 characters: " + new String(this.payload, this.payload.length - 20, 20) + "\n";
            }
            else {
                str+= "first char: " + new String(this.payload, 0, 1) + "\n";
                str+= "last char: " + new String(this.payload, this.payload.length - 1, 1) + "\n";
            }
        }
        else {
            str+= "Payload: null\n";
        }
        return str;
    }

    @Override
    public int compareTo(TCPpacket o) {
        if (this.sequenceNum < o.sequenceNum) {
            return -1;
        }
        else if (this.sequenceNum > o.sequenceNum) {
            return 1;
        }
        else {
            return 0;
        }
    }

    // Main method is for local testing purposes
    public static void main(String[] args) {
        // create arbitrary packet
        TCPpacket packet = new TCPpacket();

        // set packets fields
        packet.setSequenceNum(1000);
        packet.setAck(1000);
        packet.setTimeStamp(123456789);
        packet.setLength(1000);
        packet.setSynFlag(true);
        packet.setFinFlag(false);
        packet.setAckFlag(true);
        packet.setChecksum((short)10);
        String str = "12345678901234567890-----09876543210987654321";
        //String str = "12345";
        byte[] strByte = str.getBytes();
        packet.setPayload(strByte);


        // serialize the packet into a byte[]
        byte[] serialzied = packet.serialize();

        // create new packet
        TCPpacket desrialized = new TCPpacket();

        // set new packet equal to desialized of old packet
        desrialized.deserialize(serialzied);
        System.out.println(desrialized);


    }
}