import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashSet;
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

    public TCPsegment connect() throws IOException {
        TCPsegment ackSegment = null;

        this.socket = new DatagramSocket(port);
        this.socket.setSoTimeout(0);

        // receive SYN
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
        if (synSegment.syn && !synSegment.ack && !synSegment.fin) {
            TCPutil.getReceiverStatus(synSegment);
            remoteIP = synPacket.getAddress();
            remotePort = synPacket.getPort();
            this.ackNumber++;
            TCPutil.numPacketReceived++;
        }

        boolean receivedAck = false;

        while (!receivedAck) {
            try {
                // send SYN+ACK
                TCPsegment handshakeSynAck = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber,
                        ConnectionState.SYN_ACK);
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

                } while (ackSegment.syn);

                if (ackSegment.ack && !ackSegment.fin && !ackSegment.syn) {
                    receivedAck = true;
                }
            } catch (SocketTimeoutException e) {
                System.err.println("Timeout while waiting for first ACK!");
                TCPutil.numRetransmission++;
                if (TCPutil.numRetransmission > 16) {
                    System.exit(1);
                }
                this.sequenceNumber--;
                continue;
            }
        }

        // if (ackSegment != null && ackSegment.dataSize >= 0) {
        // return ackSegment;
        // } else {
        // return null;
        // }

        return ackSegment;
    }

    public void receive(TCPsegment ackSegment) {

        try (OutputStream out = new FileOutputStream(fileName)) {
            DataOutputStream dos = new DataOutputStream(out);
            boolean open = true;

            // out of sequence
            PriorityQueue<TCPsegment> receive = new PriorityQueue<>(sws);
            HashSet<Integer> sequenceNumbers = new HashSet<>();

            if (ackSegment != null && ackSegment.ack && ackSegment.data.length > 0) {
                // if handshake ACK is lost, then the first ACK might contain data.
                receive.add(ackSegment);
                sequenceNumbers.add(ackSegment.sequenceNum);
            }

            while (open) {
                TCPsegment dataSegment;
                dataSegment = handlePacket(this.mtu);

                if (dataSegment == null) {
                    System.out.println("Checksum mismatch.");
                    continue;
                }

                long currTime = dataSegment.time;

                int currSequenceNum = dataSegment.sequenceNum;
                int outsideSws = this.ackNumber + (sws * mtu);

                // check received packet within sws or not
                if (currSequenceNum >= outsideSws) {
                    // out of sequence packets (outside sliding window size)
                    TCPsegment currAckSegment = TCPsegment.getAckSegment(this.sequenceNumber, this.ackNumber, currTime);
                    sendPacket(currAckSegment, remoteIP, remotePort);
                    TCPutil.numOutofSequence++;
                    continue;
                } else if (currSequenceNum < this.ackNumber) {
                    TCPsegment currAckSegment = TCPsegment.getAckSegment(this.sequenceNumber, this.ackNumber, currTime);
                    sendPacket(currAckSegment, remoteIP, remotePort);
                    TCPutil.numOutofSequence++;
                    continue;
                } else {

                    if (!sequenceNumbers.contains(currSequenceNum)) {
                        sequenceNumbers.add(currSequenceNum);
                        receive.add(dataSegment);
                    } else {
                        continue;
                    }

                    while (!receive.isEmpty()) {
                        TCPsegment firstSegment = receive.peek();

                        if (firstSegment.sequenceNum == this.ackNumber) {
                            if (!firstSegment.ack || firstSegment.data.length <= 0) {
                                if (firstSegment.fin) {
                                    dos.close();
                                    close(currTime);
                                    receive.remove(firstSegment);
                                    sequenceNumbers.remove(firstSegment.sequenceNum);
                                    open = false;
                                } 
                            } else {
                                dos.write(firstSegment.data);

                                this.ackNumber += firstSegment.data.length;
                                TCPutil.numByteReceived += firstSegment.data.length;
                                TCPsegment currAckSegment = TCPsegment.getAckSegment(this.sequenceNumber, this.ackNumber, currTime);
                                sendPacket(currAckSegment, remoteIP, remotePort);
                                sequenceNumbers.remove(firstSegment.sequenceNum);
                                receive.remove(firstSegment);
                            }
                        } else {
                            // send duplicate ACK
                            TCPsegment currAckSegment = TCPsegment.getAckSegment(this.sequenceNumber, this.ackNumber, currTime);
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

    private void close(long currTime) throws IOException{
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

                if (lastAckSegment.ack) {
                    receivedLastAck = true;

                // do not retransmit FIN
                } else if (lastAckSegment.fin) {
                    this.sequenceNumber--;
                    continue;
                    
                // wrong flag
                } else {
                    System.exit(1);
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout for last ACK.");
                currNumRetransmits++;
                if (currNumRetransmits > 16) {
                    System.out.println("Reached maximum number of retransmissions.");
                    return;
                }
                TCPutil.numRetransmission++;
                this.sequenceNumber--;
            }
        }
    }

}
