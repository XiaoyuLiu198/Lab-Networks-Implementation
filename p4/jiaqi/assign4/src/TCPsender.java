import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class TCPsender extends TCPsocket {

    public TCPsender(short port, InetAddress remoteIP, short remotePort, String fileName, int mtu, int sws) {
        this.port = port;
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.fileName = fileName;
        this.mtu = mtu;
        this.sws = sws;
    }

    public void send() {
        try {
            FileInputStream fis = new FileInputStream(fileName);
            BufferedInputStream bis = new BufferedInputStream(fis);
            DataInputStream dis = new DataInputStream(bis);
            // construct data buffer
            byte[] data = new byte[mtu * sws];

            int currAckNum = 0;
            int currRetransmit = 0;
            int currDuplicateAck = 0;
            int numByteWritten = 0;
            int numByteRead = 0;

            dis.mark(mtu * sws);
            numByteRead = dis.read(data, 0, mtu * sws); // single buffer
            while (numByteRead > 0) {
                numByteRead = dis.read(data, 0, mtu * sws);
                numByteWritten += numByteRead;
                int lastIdx = numByteRead / mtu;

                for (int i = 0; i < (numByteRead / mtu + 1); i++) {
                    byte[] chunk;
                    int chunkSize;
                    if (i == lastIdx) {
                        if (numByteRead % mtu != 0) {
                            chunkSize = numByteRead % mtu;
                        } else {
                            break; // all chuncks has been visited
                        }
                    } else {
                        chunkSize = mtu;
                    }
                    chunk = new byte[chunkSize];
                    chunk = Arrays.copyOfRange(data, mtu * i, mtu * i + chunkSize);

                    TCPsegment dataSegment = TCPsegment.getDataSegment(this.sequenceNumber, this.ackNumber, chunk);
                    sendPacket(dataSegment, remoteIP, remotePort);

                    TCPutil.numByteSent += chunkSize; // update overall stats
                    this.sequenceNumber += chunkSize;
                }

                while (currAckNum != TCPutil.numByteSent) {
                    try {
                        TCPsegment currAckSegment = handlePacket(this.mtu); // get tcp response after sending attempt
                        if (currAckSegment == null) {
                            continue;
                        }

                        this.socket.setSoTimeout((int) (TCPutil.timeout / 1E6)); // TODO: unit correct? before checking dup?

                        // duplicate ack
                        int prevAck = currAckNum;
                        currAckNum = currAckSegment.getAckNum() - 1;

                        if (prevAck == currAckNum) {  // fast retransmit
                            currDuplicateAck++;
                            TCPutil.numDuplicateAck++;
                            if (currDuplicateAck == 3) {
                                if (currRetransmit > 16) {
                                    System.out.println("Reached maximum number of retransmissions.");
                                    return; // TODO: Should we just exit or also print messages
                                }
                                // sliding window
                                dis.reset();
                                dis.skip(currAckNum - (numByteWritten - numByteRead));
                                this.sequenceNumber = currAckNum + 1;
                                TCPutil.numByteSent = currAckNum;
                                TCPutil.numRetransmission++;
                                numByteWritten = TCPutil.numByteSent;
                                currRetransmit++;
                                break;
                            }
                        } else {
                            currDuplicateAck = 0;
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout for SYN+ACK.");
                        if (currRetransmit > 16) {
                            System.out.println("Reached maximum number of retransmissions.");
                            return;
                        }
                        // sliding window
                        dis.reset();
                        dis.skip(currAckNum - (numByteWritten - numByteRead));
                        this.sequenceNumber = currAckNum + 1;
                        TCPutil.numByteSent = currAckNum;
                        TCPutil.numRetransmission++;
                        numByteWritten = TCPutil.numByteSent;
                        currRetransmit++;
                        break;
                    }
                    currRetransmit = 0;  // reset counter for current segment
                }
                bis.mark(mtu * sws);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handshake(String type) throws IOException {
        switch (type) {
            case "connect":
                boolean receivedSynAck = false;
                this.socket = new DatagramSocket();
                this.socket.setSoTimeout(5000); // initiate 5 seconds timeout

                while (!receivedSynAck) {
                    // send SYN
                    TCPsegment synSegment = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber,
                            ConnectionState.SYN);
                    sendPacket(synSegment, remoteIP, remotePort);
                    this.sequenceNumber++;

                    try {
                        // receive SYN+ACK
                        TCPsegment synAckSegment = handlePacket(this.mtu);
                        if (synAckSegment == null) {
                            continue;
                        }

                        // send ACK
                        if (synAckSegment.isSyn() && synAckSegment.isAck()) {
                            this.ackNumber++;
                            receivedSynAck = true;
                            TCPsegment ackSegment = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber,
                                    ConnectionState.ACK);
                            sendPacket(ackSegment, remoteIP, remotePort);
                        } else {
                            TCPutil.numRetransmission++;
                            if (TCPutil.numRetransmission > 16) { // default max number of retransmissions
                                System.out.println("Reached maximum number of retransmissions.");
                                return;
                            }
                            this.sequenceNumber--;
                            continue;
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout for ACK.");
                        TCPutil.numRetransmission++;
                        if (TCPutil.numRetransmission > 16) {
                            System.out.println("Reached maximum number of retransmissions.");
                            return;
                        }
                        this.sequenceNumber--;
                        continue;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case "close":
                boolean receivedFinAck = false;
                int currRetransmit = 0;
                // send FIN
                while (!receivedFinAck) {
                    TCPsegment finSegment = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber,
                            ConnectionState.FIN);
                    sendPacket(finSegment, remoteIP, remotePort);
                    this.sequenceNumber++;

                    try {
                        // receive FIN+ACK
                        TCPsegment finAckSegment = null;
                        do {
                            finAckSegment = handlePacket(this.mtu);
                            if (finAckSegment == null) {
                                this.sequenceNumber--;
                                continue;
                            }
                        } while (finAckSegment.isAck() && !finAckSegment.isFin() && !finAckSegment.isSyn());

                        if (finAckSegment.isFin()){
                            if(finAckSegment.isAck() && !finAckSegment.isSyn()) {
                                this.ackNumber++;
                                receivedFinAck = true;
                                TCPsegment ackSegment = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber,
                                        ConnectionState.ACK);
                                sendPacket(ackSegment, remoteIP, remotePort);
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout for ACK.");
                        currRetransmit++;
                        if (currRetransmit > 16) {
                            System.out.println("Reached maximum number of retransmissions.");
                            return;
                        }
                        TCPutil.numRetransmission++;
                        this.sequenceNumber--;
                        continue;

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }
}
