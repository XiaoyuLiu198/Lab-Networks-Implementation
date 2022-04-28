import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TCPsocket {
    public DatagramSocket socket;
    public short port;
    public InetAddress remoteIP;
    public short remotePort;
    public String fileName;
    public int mtu;
    public int sws;
    public int sequenceNumber;
    public int ackNumber;

    public TCPsegment handlePacket(int mtu) throws IOException {
        byte[] data = new byte[mtu + 24];
        DatagramPacket packet = new DatagramPacket(data, mtu + 24);
        socket.receive(packet);
        data = packet.getData();
        TCPsegment segment = new TCPsegment();
        segment = segment.deserialize(data);

        // checksum
        short origChecksum = segment.getChecksum();
        segment.resetChecksum();
        segment.serialize();
        short calcChecksum = segment.getChecksum();
        if (origChecksum != calcChecksum) {
            System.out.println("Checksum mismatch.");
            TCPutil.numIncorrectChecksum++;
            return null;
        }

        // compute timeout
        if (segment.ack && segment.dataSize == 0) {
            TCPutil.getTimeout(segment.sequenceNum, segment.time);
        }

        TCPutil.numPacketReceived++;
        TCPutil.getReceiverStatus(segment);

        return segment;
    }

    public void sendPacket(TCPsegment segment, InetAddress remoteIP, int remotePort) {
        if (segment == null) {
            return;
        }

        byte[] data = segment.serialize();
        DatagramPacket packet = new DatagramPacket(data, data.length, remoteIP, remotePort);
        try {
            socket.send(packet);
            TCPutil.numPacketSent++;
        } catch (IOException e) {
            e.printStackTrace();
        }
        TCPutil.getSenderStatus(segment);
    }

}
