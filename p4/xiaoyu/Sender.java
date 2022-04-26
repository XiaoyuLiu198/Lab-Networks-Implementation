import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class Sender{
    // private HashMap<Integer, Boolean> connected = new HashMap<Integer, Boolean>();
    private byte start;
    private long timestamp;
    private int seqnum;
    private byte written_len = 0;
    private int lastSeq = 0; // TODO: Should we start from 0?
    private int ack = 0;
    byte[] responses = new byte[16];
    ByteBuffer resB = ByteBuffer.wrap(responses);

    public byte[] Sender(TCPEndobj send, int thisPort, int destPort, int destIp, byte[] load){

        //TODO: Decide size of responses 
        

        // initiate three way handshake here - both start and FIN

        // set up two threads here to check every second of the input thread, at the same time execute the output thread??
        HashMap<Byte, Long> buffer = new HashMap<Byte, Long>();
        while ((this.written_len < send.getSws()) & (this.timestamp < limited)){
            while(this.start < load.length){
                byte ori_start = this.start;
                buffer.put(ori_start, timestamp);
                byte[] segment = chunckMTU(send.getMTU(), load, this.start);
                byte len = (byte) (this.start - ori_start);

                
                boolean res = sendSingle(send, destPort, destIp, thisPort, segment, this.lastSeq, len);
                if (res == true){
                    written_len = (byte) (written_len + len);
                }
                else{
                    this.resB.put("failed to send");
                }
                // written_len = (byte) (written_len + len);
            }
        }
        return responses;
        }

    // TODO: size of each segment
    public byte[] chunckMTU(byte MTU, byte[] payload, byte start){
        byte left_bytes = (byte)((byte) payload.length - start);
        byte[] single_chunck;
        if(left_bytes >= MTU){
            single_chunck = new byte[24 + 8 + 24];
            // input ip header and udp header

            // update start point
            this.start = (byte) (start + MTU);

            return single_chunck;
        }
        return null; 
    }
    
    public boolean sendSingle(TCPEndobj obj, int destPort, int destIp, int thisPort, byte[] segment, int lastSeq, int len){
        // retrieve received packet
        DatagramSocket receiveSocket = new DatagramSocket(thisPort);
        byte[] receiveData = new byte[obj.getMTU() + 32];
        DatagramPacket receivePacket = new DatagramPacket(receiveData,
                        receiveData.length);
        receiveSocket.receive(receivePacket);
        // obtain ACK in the packet
        //--------------------------------------------------------------------
        byte[] wholePacket = receivePacket.getData();
        this.ack = wholePacket[8];

        // obtain timestamp in the packet
        long time = wholePacket[12];
        int S = wholePacket[16];

        // calculate timeout before enter while loop
        timeout = getTimeOut(S, time);
        //--------------------------------------------------------------------

        // check timeout, ack num, and checksum
        while (timeout <= limited){
            if (ack == this.lastSeq + 1) & checksum != ){
                DatagramSocket socket = new DatagramSocket();
                InetAddress listen_addr = InetAddress.getByName(String.valueOf(destIp));
                
                // append TCP into each segement
                // [flag(4), seq_num?, ack_num(4), checksum(4), ]
                TCP newTcp = new TCP();
                newTcp.sequencenum = this.ack;
                newTcp.timestamp = System.nanoTime();
                newTcp.flags = 'A';
                newTcp.ACK = newTcp.sequencenum + 1;
                byte[] payload = new byte[obj.getMTU()];
                ByteBuffer bb = ByteBuffer.wrap(payload);
                bb.put(newTcp.serializer());
                bb.put(segment);
                DatagramPacket datagram = new DatagramPacket(payload, len, listen_addr, destPort);
                socket.send(datagram);

                this.written_len = (byte) (written_len + len);
                this.lastSeq += 1; // TODO: or plus length of the packet
                return true;
                this.resB.put();
            }
            else{
                TimeUnit.SECONDS.sleep(1); // TODO: Time interval of retransmitt
            }
            timeout = getTimeOut();
        }
        return false;
        }
        
    long ertt;
    long edev;
    long srtt;
    long sdev;
    long timeout;
    public long getTimeOut(int S, long T){
        if(S == 0){
            this.ertt = System.nanoTime() - T; // TODO: or current.milliseconds?
            this.edev = 0;
            timeout = 2 * ertt;
            return timeout;
        }
        else{
            long srtt = System.nanoTime() - T;
            this.sdev = Math.abs(this.srtt - this.ertt);
            this.ertt = (long) (0.875 * this.ertt + 0.125 * this.srtt);
            this.edev = (long) (0.75 * this.edev + 0.25 * this.sdev);
            timeout = this.ertt + 4 * this.edev;
            return timeout;
        }
    }
}