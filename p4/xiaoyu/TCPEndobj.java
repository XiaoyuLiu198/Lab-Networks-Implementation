import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import javax.swing.text.Segment;

public class TCPEndobj{

    protected int client_port;
    protected int remote_ip;
    protected int remote_port;
    // Object file_name;
    protected String file_name;
    protected byte MTU;
    protected int sws;
    protected int listen_port;
    protected int timer; // use timer to track time before receiving ACK
    protected long ertt;
    protected long edev;
    protected long srtt;
    protected long sdev;
    protected long timeout;
    protected DatagramSocket socket;
    
    protected int packetsSent;
    protected int packetsReceived;
    protected int lastSentPosi;
    protected int lastReceivedPosi;
    protected int discardPackets;
    protected int retransmitt;
    protected int dupAcks;
    protected int lastSeq = 0; // TODO: Should we start sequence number from 0?

    public TCPEndobj(int client_port, int remote_ip, int remote_port, String file_name, byte mTU, int sws,
            int listen_port, int timer) {
        this.client_port = client_port;
        this.remote_ip = remote_ip;
        this.remote_port = remote_port;
        this.file_name = file_name;
        this.setMTU(mTU);
        this.setSws(sws);
        this.listen_port = listen_port;
        this.timer = timer;
    }

    public int getSws() {
        return sws;
    }

    public void setSws(int sws) {
        this.sws = sws;
    }

    public byte getMTU() {
        return MTU;
    }

    public void setMTU(byte mTU) {
        this.MTU = mTU;
    }

    public TCP sendResponse() throws IOException{
        // retrieve received packet
        byte[] receiveDataCon = new byte[this.getMTU() + 24];
        DatagramPacket receivePacket = new DatagramPacket(receiveDataCon, this.getMTU() + 24);
        this.socket.receive(receivePacket);
        receiveDataCon = receivePacket.getData();
        TCP tcpSeg = new TCP();
        tcpSeg = tcpSeg.deserialize(receiveDataCon);

        // checksum validation
        short ori= tcpSeg.checksum;
        tcpSeg.checksum = 0;
        tcpSeg.serializer();
        short newChe = tcpSeg.checksum;
        if (ori != newChe) {
        // discard packet if checksum validation failed
        System.out.println("Error: Checksum validation failed");
        }

        // timeout calculation
        if(tcpSeg.sequencenum == 0){
            this.ertt = (long) System.nanoTime() - tcpSeg.timestamp; // TODO: or current.milliseconds?
            this.edev = 0;
            timeout = 2 * ertt;
            this.timeout = timeout;
        }
        else{
            long srtt = System.nanoTime() - tcpSeg.timestamp;
            this.sdev = Math.abs(this.srtt - this.ertt);
            this.ertt = (long) (0.875 * this.ertt + 0.125 * this.srtt);
            this.edev = (long) (0.75 * this.edev + 0.25 * this.sdev);
            timeout = this.ertt + 4 * this.edev;
            this.timeout = timeout;
        }

        this.packetsReceived ++;

        return tcpSeg;

    }

    

}

