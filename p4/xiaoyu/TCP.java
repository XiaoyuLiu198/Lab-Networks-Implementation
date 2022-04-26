import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;

// import javax.xml.crypto.Data;

public class TCP {

    public static final int SEND = 1;
    public static final int RECEIVE = 2;

    protected long timestamp;
    protected int ACK;
    protected short checksum;
    protected HashSet<ArrayList<Byte>> datasets;
    protected int sequencenum;
    protected byte dataOffset;
    protected short sourcePort;
    protected short destinationPort;
    protected short flags;
    protected short windowSize;
    protected short urgentPointer;
    protected byte[] options;
    protected byte[] payload;
    protected short connectedport;

    public TCP setSequenceNum(int seq){
        this.sequencenum = seq;
        return this;
    }

    public TCP setChecksum(short checksum){
        this.checksum = checksum;
        return this;
    }

    public TCP setACK(int ack){
        this.ACK = ack;
        return this;
    }

    public TCP setTimestamp(long time){
        this.timestamp = time;
        return this;
    }

    public byte[] serializer(){
        int length;
        if (dataOffset == 0)
            dataOffset = 5;  // default header length
        length = dataOffset << 2;
        byte[] payloadData = null;
        // if (payload != null) {
        //     payload.setParent(this);
        //     payloadData = payload.serialize();
        //     length += payloadData.length;
        // }

        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.putShort(this.sourcePort);
        bb.putShort(this.destinationPort);
        bb.putInt(this.sequencenum);
        bb.putInt(this.ACK);
        bb.putShort((short) (this.flags | (dataOffset << 12)));
        bb.putShort(this.windowSize);
        bb.putShort(this.checksum);
        bb.putShort(this.urgentPointer);
        bb.put(payload); // warp payload data
        if (dataOffset > 5) {
            int padding;
            bb.put(options);
            padding = (dataOffset << 2) - 20 - options.length;
            for (int i = 0; i < padding; i++)
                bb.put((byte) 0);
        }
        if (payloadData != null)
            bb.put(payloadData);

        // if (this.parent != null && this.parent instanceof IPv4)
        //     ((IPv4)this.parent).setProtocol(IPv4.PROTOCOL_TCP);

        // compute checksum if needed
        if (this.checksum == 0) {
            bb.rewind();
            int accumulation = 0;

            // compute pseudo header mac
            // if (this.parent != null && this.parent instanceof IPv4) {
            //     IPv4 ipv4 = (IPv4) this.parent;
            //     accumulation += ((ipv4.getSourceAddress() >> 16) & 0xffff)
            //             + (ipv4.getSourceAddress() & 0xffff);
            //     accumulation += ((ipv4.getDestinationAddress() >> 16) & 0xffff)
            //             + (ipv4.getDestinationAddress() & 0xffff);
            //     accumulation += ipv4.getProtocol() & 0xff;
            //     accumulation += length & 0xffff;
            // }

            for (int i = 0; i < length / 2; ++i) {
                accumulation += 0xffff & bb.getShort();
            }
            // pad to an even number of shorts
            if (length % 2 > 0) {
                accumulation += (bb.get() & 0xff) << 8;
            }

            accumulation = ((accumulation >> 16) & 0xffff)
                    + (accumulation & 0xffff);
            this.checksum = (short) (~accumulation & 0xffff);
            bb.putShort(16, this.checksum);
        }
        return data;
    }

    public TCP deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        this.sourcePort = bb.getShort();
        this.destinationPort = bb.getShort();
        this.sequencenum = bb.getInt();
        this.ACK = bb.getInt();
        this.flags = bb.getShort();
        this.dataOffset = (byte) ((this.flags >> 12) & 0xf);
        this.flags = (short) (this.flags & 0x1ff);
        this.windowSize = bb.getShort();
        this.checksum = bb.getShort();
        this.urgentPointer = bb.getShort();
        if (this.dataOffset > 5) {
            int optLength = (dataOffset << 2) - 20;
            if (bb.limit() < bb.position()+optLength) {
                optLength = bb.limit() - bb.position();
            }
            try {
                this.options = new byte[optLength];
                bb.get(this.options, 0, optLength);
            } catch (IndexOutOfBoundsException e) {
                this.options = null;
            }
        }
        
        // this.payload = new Data();
        // this.payload = payload.deserialize(data, bb.position(), bb.limit()-bb.position());
        // this.payload.setParent(this);
        return this;
    }


    // public void handleTCP(int type){
    //     switch(type){
    //         case SEND:
    //             System.out.println("Initiate a sending TCP");

    //             break;
    //         case RECEIVE:
    //             System.out.println("Initiate a receiving TCP");

    //             break;
    //     }
    // }

    // public void updateSend(){

    // }
    
}
