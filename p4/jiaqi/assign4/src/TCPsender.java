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
            byte[] data = new byte[mtu * sws];

            int currAckNum = 0;
            int currRetransmit = 0;
            int currDuplicateAck = 0;
            int numByteWritten = 0;
            int numByteRead = 0;

            dis.mark(mtu * sws);

            while ((numByteRead = dis.read(data, 0, mtu * sws)) != -1) {
                numByteWritten += numByteRead;

                for (int i = 0; i < (numByteRead / mtu + 1); i++) {
                    byte[] chunk;
                    int chunkSize;
                    if (i == numByteRead / mtu) {
                        chunkSize = numByteRead % mtu;
                        if (chunkSize != 0) {
                            chunkSize = numByteRead % mtu;
                        } else {
                            break;
                        }
                    } else {
                        chunkSize = mtu;
                    }
                    chunk = new byte[chunkSize];
                    chunk = Arrays.copyOfRange(data, mtu * i, mtu * i + chunkSize);

                    TCPsegment dataSegment = TCPsegment.getDataSegment(this.sequenceNumber, this.ackNumber, chunk);
                    sendPacket(dataSegment, remoteIP, remotePort);

                    this.sequenceNumber += chunkSize;
                    TCPutil.numByteSent += chunkSize;
                }

                while (currAckNum < TCPutil.numByteSent) {
                    try {
                        TCPsegment currAckSegment = handlePacket(this.mtu);
                        if (currAckSegment == null) {
                            continue;
                        }

                        this.socket.setSoTimeout((int) (TCPutil.timeout / 1E6));

                        int prevAck = currAckNum;
                        currAckNum = currAckSegment.ackNum - 1;

                        if (prevAck == currAckNum) {
                            currDuplicateAck++;
                            TCPutil.numDuplicateAck++;
                            if (currDuplicateAck == 3) {
                                if (currRetransmit > 16) {
                                    System.out.println("Reached maximum number of retransmissions.");
                                    return;
                                }

                                slideWindow(dis, currAckNum, numByteWritten, numByteRead);
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
                        slideWindow(dis, currAckNum, numByteWritten, numByteRead);
                        numByteWritten = TCPutil.numByteSent;
                        currRetransmit++;
                        break;
                    }
                    currRetransmit = 0;
                }
                bis.mark(mtu * sws);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connect() throws IOException {
        boolean receivedSynAck = false;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(5000); // default 5 seconds timeout

        while (!receivedSynAck) {
            // send SYN
            TCPsegment synSegment = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber, ConnectionState.SYN);
            sendPacket(synSegment, remoteIP, remotePort);
            this.sequenceNumber++;

            try {
                // receive SYN+ACK
                TCPsegment synAckSegment = handlePacket(this.mtu);
                if (synAckSegment == null) {
                    continue;
                }

                // send ACK
                if (synAckSegment.syn && synAckSegment.ack) {
                    this.ackNumber++;
                    receivedSynAck = true;
                    TCPsegment ackSegment = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber, ConnectionState.ACK);
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
    }

    public void close() {
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
                } while (finAckSegment.ack && !finAckSegment.fin && !finAckSegment.syn);

                if (finAckSegment.fin && finAckSegment.ack && !finAckSegment.syn) {
                    this.ackNumber++;
                    receivedFinAck = true;
                    TCPsegment ackSegment = TCPsegment.getConnectionSegment(this.sequenceNumber, this.ackNumber, ConnectionState.ACK);
                    sendPacket(ackSegment, remoteIP, remotePort);
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
    }

    private void slideWindow(DataInputStream dis, int currAckNum, int numByteWritten, int numByteRead)
            throws IOException {
        dis.reset();
        dis.skip(currAckNum - (numByteWritten - numByteRead));
        this.sequenceNumber = currAckNum + 1;
        TCPutil.numByteSent = currAckNum;
        TCPutil.numRetransmission++;
    }
}
