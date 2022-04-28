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

  public TCPsender(int senderSourcePort, InetAddress receiverIp, int receiverPort, String filename,
      int mtu, int sws) {
    this.senderSourcePort = senderSourcePort;
    this.receiverIp = receiverIp;
    this.port = receiverPort;
    this.filename = filename;
    this.mtu = mtu;
    this.sws = sws;
  }

  public void openConnection() throws IOException, MaxRetransmitException {
    this.socket = new DatagramSocket(senderSourcePort);
    this.socket.setSoTimeout(INITIAL_TIMEOUT_MS);

    boolean isSynAckReceived = false;
    while (!isSynAckReceived) {
      TCPsegment handshakeFirstSyn = TCPsegment.getConnectionSegment(bsn, nextByteExpected, ConnectionState.SYN);
      sendPacket(handshakeFirstSyn, receiverIp, port);
      bsn++;

      try {
        TCPsegment handshakeSecondSynAck;
        try {
          handshakeSecondSynAck = handlePacket();
        } catch (SegmentChecksumMismatchException e) {
          e.printStackTrace();
          continue;
        }

        if (handshakeSecondSynAck.syn && handshakeSecondSynAck.ack) {
          nextByteExpected++;
          isSynAckReceived = true;

          TCPsegment handshakeThirdAck = TCPsegment.getConnectionSegment(bsn, nextByteExpected, ConnectionState.ACK);
          sendPacket(handshakeThirdAck, receiverIp, port);
        } else {
          this.numRetransmits++;
          if (this.numRetransmits >= (MAX_RETRANSMITS + 1)) {
            throw new MaxRetransmitException("Max SYN retransmits!");
          }
          bsn--;
          continue;
        }
      } catch (SocketTimeoutException e) {
        System.err.println("Timeout while waiting for SYNACK!");
        this.numRetransmits++;
        if (this.numRetransmits % (MAX_RETRANSMITS + 1) == 0) {
          throw new MaxRetransmitException("Max SYN retransmits!");
        }
        bsn--;
        continue;
      }
    }
  }

  public void sendData() throws MaxRetransmitException {

    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename))) {
      DataInputStream inputStream = new DataInputStream(in);
      byte[] sendBuffer = new byte[mtu * sws];

      int lastByteAcked = 0;
      int lastByteWritten = 0;
      short currRetransmit = 0;
      int byteReadCount;
      int currDupAck = 0;

      inputStream.mark(mtu * sws);

      while ((byteReadCount = inputStream.read(sendBuffer, 0, mtu * sws)) != -1) {
        lastByteWritten += byteReadCount;

        for (int j = 0; j < (byteReadCount / mtu) + 1; j++) {
          byte[] onePayload;
          int payloadLength;
          if (j == byteReadCount / mtu) {
            payloadLength = byteReadCount % mtu;
            if (payloadLength != 0) {
              payloadLength = byteReadCount % mtu;
            } else {
              break;
            }
          } else {
            payloadLength = mtu;
          }
          onePayload = new byte[payloadLength];
          onePayload = Arrays.copyOfRange(sendBuffer, j * mtu, (j * mtu) + payloadLength);

          TCPsegment dataSegment = TCPsegment.getDataSegment(bsn, nextByteExpected, onePayload);
          sendPacket(dataSegment, receiverIp, port);
          this.lastByteSent += payloadLength;
          bsn += payloadLength;
        }

        while (lastByteAcked < this.lastByteSent) {
          try {
            TCPsegment currAck;
            try {
              currAck = handlePacket();
            } catch (SegmentChecksumMismatchException e) {
              e.printStackTrace();
              continue;
            }

            if (!currAck.ack) {
              throw new UnexpectedFlagException("Expected ACK!", currAck);
            }
            this.socket.setSoTimeout((int) (timeout / 1000000));

            int prevAck = lastByteAcked;
            lastByteAcked = currAck.ackNum - 1;

            if (prevAck == lastByteAcked) {
              currDupAck++;
              this.numDupAcks++;
              if (currDupAck == 3) {
                if (currRetransmit >= MAX_RETRANSMITS) {
                  throw new MaxRetransmitException("Max data retransmits!");
                }

                slideWindow(inputStream, lastByteAcked, lastByteWritten, byteReadCount);
                lastByteWritten = this.lastByteSent;
                currRetransmit++;
                break;
              }
            } else {
              currDupAck = 0;
            }
          } catch (SocketTimeoutException e) {
            System.err.println("Timeout while waiting for ACK!");
            if (currRetransmit >= MAX_RETRANSMITS) {
              throw new MaxRetransmitException("Max data retransmits!");
            }

            slideWindow(inputStream, lastByteAcked, lastByteWritten, byteReadCount);
            lastByteWritten = this.lastByteSent;
            currRetransmit++;
            break;
          } catch (UnexpectedFlagException e) {
            e.printStackTrace();
            continue;
          }

          currRetransmit = 0;
        }

        inputStream.mark(mtu * sws);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void slideWindow(DataInputStream inputStream, int lastByteAcked, int lastByteWritten,
      int byteReadCount) throws IOException {
    inputStream.reset();
    inputStream.skip(lastByteAcked - (lastByteWritten - byteReadCount));
    this.lastByteSent = lastByteAcked;
    this.bsn = lastByteAcked + 1;
    this.numRetransmits++;
  }

  public void closeConnection()
      throws IOException, MaxRetransmitException, UnexpectedFlagException {

    boolean isFinAckReceived = false;
    short currNumRetransmits = 0;
    while (!isFinAckReceived) {
      TCPsegment finSegment = TCPsegment.getConnectionSegment(bsn, nextByteExpected, ConnectionState.FIN);
      sendPacket(finSegment, receiverIp, port);
      bsn++;

      try {
        TCPsegment returnFinAckSegment = null;
        do {

          try {
            returnFinAckSegment = handlePacket();
          } catch (SegmentChecksumMismatchException e) {
            e.printStackTrace();
            bsn--;
            continue;
          }
        } while (returnFinAckSegment.ack && !returnFinAckSegment.fin && !returnFinAckSegment.syn);

        if (returnFinAckSegment.fin && returnFinAckSegment.ack && !returnFinAckSegment.syn) {
          nextByteExpected++;
          isFinAckReceived = true;

          TCPsegment lastAckSegment = TCPsegment.getConnectionSegment(bsn, nextByteExpected, ConnectionState.ACK);
          sendPacket(lastAckSegment, receiverIp, port);
        } else {
          throw new UnexpectedFlagException();
        }
      } catch (SocketTimeoutException e) {
        System.err.println("Timeout while waiting for FINACK!");
        currNumRetransmits++;
        if (currNumRetransmits >= (MAX_RETRANSMITS + 1)) {
          throw new MaxRetransmitException("Max FIN retransmits!");
        }
        this.numRetransmits++;
        bsn--;
        continue;
      }
    }
  }

  public void printFinalStatsHeader() {
    System.out.println("TCPEnd Sender Finished==========");
    this.printFinalStats();
  }

}
