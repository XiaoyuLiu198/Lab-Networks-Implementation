import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;

public class TCPreceiver extends TCPsocket {
    private InetAddress remoteIP;
    private int remotePort;

    public TCPreceiver(short port, int mtu, int sws, String fileName) {
        this.port = port;
        this.mtu = mtu;
        this.sws = sws;
        this.fileName = fileName;
    }

    public void receive(TCPsegment ackSegment) {

        try (OutputStream out = new FileOutputStream(fileName)) {
            DataOutputStream dos = new DataOutputStream(out);
            boolean open = true;

            // PriorityQueue<TCPsegment> receive = new PriorityQueue<>(sws);
            PriorityQueue<TCPsegment> receive = new PriorityQueue<>();
            HashSet<Integer> sequenceNumbers = new HashSet<Integer>();

            if (ackSegment != null){
                if(ackSegment.getDataSize() != 0){ // data size can not be 0
                    if(ackSegment.isAck()) {
                        receive.add(ackSegment);
                        sequenceNumbers.add(ackSegment.getSequenceNum());
                    }
                }
            }

            while (open) {  // receive data
                TCPsegment dataSegment;
                dataSegment = handlePacket(this.mtu);

                if (dataSegment == null) {
                    System.out.println("empty tcp segment received");
                    continue;
                }

                long currTime = dataSegment.getTime();
                int currSequenceNum = dataSegment.getSequenceNum();

                if (currSequenceNum < this.ackNumber) {
                    TCPsegment currAckSegment = TCPsegment.getAckSegment(this.sequenceNumber, this.ackNumber, currTime);
                    sendPacket(currAckSegment, remoteIP, remotePort); // retransmitt now TODO: Retransmitt here?
                    TCPutil.numOutofSequence++;
                    continue;
                } else {  // add to queue
                    if (!sequenceNumbers.contains(currSequenceNum)) {
                        sequenceNumbers.add(currSequenceNum);
                        receive.add(dataSegment);
                    } else {
                        continue;
                    }

                    while (!receive.isEmpty()) {
                        TCPsegment firstSegment = receive.peek();
                        if (firstSegment.getSequenceNum() == this.ackNumber) {
                            if (!firstSegment.isAck() || firstSegment.getDataSize() <= 0) {  // end if no more data
                                if (firstSegment.isFin()) {
                                    dos.close();
                                    handshake("close", currTime);
                                    receive.remove(firstSegment);
                                    sequenceNumbers.remove(firstSegment.getSequenceNum());
                                    open = false;
                                }
                            } else {  // send and write file, send ACK
                                dos.write(firstSegment.getData());

                                this.ackNumber += firstSegment.getDataSize();
                                TCPsegment currAckSegment = TCPsegment.getAckSegment(this.sequenceNumber,
                                        this.ackNumber, currTime);
                                sendPacket(currAckSegment, remoteIP, remotePort);
                                sequenceNumbers.remove(firstSegment.getSequenceNum());
                                receive.remove(firstSegment);
                                TCPutil.numByteReceived += firstSegment.getDataSize();
                            }
                        } else {
                            // send duplicate ACK as original ACK
                            TCPsegment currAckSegment = TCPsegment.getAckSegment(this.sequenceNumber, this.ackNumber,
                                    currTime);
                            sendPacket(currAckSegment, remoteIP, remotePort);
                            break;
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public TCPsegment handshake(String type, long currTime) throws IOException {
        switch (type) {
            case "connect":
                TCPsegment ackSegment = null;

                this.socket = new DatagramSocket(port);
                this.socket.setSoTimeout(5000);

                // receive packet
                byte[] data = new byte[mtu + 24];
                DatagramPacket synPacket = new DatagramPacket(data, mtu + 24);
                socket.receive(synPacket);
                byte[] synData = synPacket.getData();
                TCPsegment synSegment = new TCPsegment();
                synSegment.deserialize(synData);

                // checksum
                short origChecksum = synSegment.getChecksum();
                synSegment.resetChecksum();
                synSegment.serialize();
                short calcChecksum = synSegment.getChecksum();

                if (origChecksum != calcChecksum) {
                    TCPutil.numIncorrectChecksum++;
                    System.out.println("Checksum mismatch.");
                    return null;
                }
                if (synSegment.isSyn()){
                    if(!synSegment.isAck() && !synSegment.isFin()) {
                        TCPutil.getReceiverStatus(synSegment);
                        remoteIP = synPacket.getAddress();
                        remotePort = synPacket.getPort();
                        this.ackNumber++;
                        TCPutil.numPacketReceived++;
                    }
                }

                boolean receivedAck = false;

                while (!receivedAck) {
                    try {
                        // send SYN+ACK
                        TCPsegment handshakeSynAck = TCPsegment.getConnectionSegment(this.sequenceNumber,
                                this.ackNumber,ConnectionState.SYN_ACK);
                        sendPacket(handshakeSynAck, remoteIP, remotePort);
                        this.sequenceNumber++;

                        // receive ACK
                        do {
                            socket.setSoTimeout(5000);
                            ackSegment = handlePacket(this.mtu);
                            if (ackSegment == null) {
                                System.out.println("Checksum mismatch.");
                                continue;
                            }

                        } while (ackSegment.isSyn());

                        if (ackSegment.isAck() && !ackSegment.isFin() && !ackSegment.isSyn()) {
                            receivedAck = true;
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout for first ACK.");
                        TCPutil.numRetransmission++;
                        if (TCPutil.numRetransmission > 16) {
                            System.exit(1);
                        }
                        this.sequenceNumber--;
                        continue;
                    }
                }
                return ackSegment;

            case "close":
                boolean receivedLastAck = false;
                short currNumRetransmits = 0;
                while (!receivedLastAck) {

                    TCPsegment returnFinAckSegment = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber, ConnectionState.FIN_ACK);
                    sendPacket(returnFinAckSegment, remoteIP, remotePort);
                    this.sequenceNumber++;
                    try {
                        this.socket.setSoTimeout(5000);
                        TCPsegment lastAckSegment;

                        lastAckSegment = handlePacket(this.mtu);

                        if (lastAckSegment == null) {
                            System.out.println("Checksum mismatch.");
                            continue;
                        }

                        if (lastAckSegment.isAck()) {
                            receivedLastAck = true;

                            // do not retransmit FIN
                        } else if (lastAckSegment.isFin()) {
                            this.sequenceNumber--;
                            continue;

                        } else {
                            System.exit(1);
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout for last ACK.");
                        currNumRetransmits++;
                        if (currNumRetransmits > 16) {
                            System.out.println("Reached maximum number of retransmissions.");
                            return null;
                        }
                        TCPutil.numRetransmission++;
                        this.sequenceNumber--;
                    }
                }
                return null;

            default:
                return null;
        }
    }
}
