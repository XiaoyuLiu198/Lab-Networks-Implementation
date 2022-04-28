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
  protected InetAddress senderIp;
  protected int senderPort;

  public TCPreceiver(int port, String filename, int mtu, int sws) {
    this.port = port;
    this.filename = filename;
    this.mtu = mtu;
    this.sws = sws;
    this.numByteReceived = 0;
    this.numPacketSent = 0;
    this.numPacketReceived = 0;
  }

  public TCPsegment openConnection() throws IOException, MaxRetransmitException,
      SegmentChecksumMismatchException, UnexpectedFlagException {
    TCPsegment firstReceivedAck = null;

    this.socket = new DatagramSocket(port);
    this.socket.setSoTimeout(0);

    byte[] bytes = new byte[mtu + 24];
    DatagramPacket handshakeSynPacket = new DatagramPacket(bytes, mtu + 24);
    socket.receive(handshakeSynPacket);
    byte[] handshakeSynBytes = handshakeSynPacket.getData();
    TCPsegment handshakeSyn = new TCPsegment();
    handshakeSyn.deserialize(handshakeSynBytes);

    short origChk = handshakeSyn.getChecksum();
    handshakeSyn.resetChecksum();
    handshakeSyn.serialize();
    short calcChk = handshakeSyn.getChecksum();
    if (origChk != calcChk) {
      throw new SegmentChecksumMismatchException();
    }
    if (handshakeSyn.syn && !handshakeSyn.ack && !handshakeSyn.fin) {
      printOutput(handshakeSyn, false);
      senderIp = handshakeSynPacket.getAddress();
      senderPort = handshakeSynPacket.getPort();
      nextByteExpected++;
      numPacketReceived++;
    } else {
      throw new UnexpectedFlagException("Expected SYN flags!", handshakeSyn);
    }

    boolean isFirstAckReceived = false;
    while (!isFirstAckReceived)
      try {

        TCPsegment handshakeSynAck = TCPsegment.getConnectionSegment(bsn, nextByteExpected, ConnectionState.SYN_ACK);
        sendPacket(handshakeSynAck, senderIp, senderPort);
        bsn++;

        do {

          try {
            socket.setSoTimeout(INITIAL_TIMEOUT_MS);
            firstReceivedAck = handlePacket();
          } catch (SegmentChecksumMismatchException e) {
            e.printStackTrace();
            continue;
          }
        } while (firstReceivedAck.syn);

        if (firstReceivedAck.ack && !firstReceivedAck.fin && !firstReceivedAck.syn) {
          isFirstAckReceived = true;
        } else {
          throw new UnexpectedFlagException();
        }
      } catch (SocketTimeoutException e) {
        System.err.println("Timeout while waiting for first ACK!");
        this.numRetransmits++;
        if (this.numRetransmits % (MAX_RETRANSMITS + 1) == 0) {

          throw new MaxRetransmitException("Max SYNACK retransmits!");
        }
        bsn--;
        continue;
      }

    if (firstReceivedAck != null && firstReceivedAck.dataLength >= 0) {
      return firstReceivedAck;
    } else {
      return null;
    }
  }

  public void receiveDataAndClose(TCPsegment firstReceivedAck) throws MaxRetransmitException {
    try (OutputStream out = new FileOutputStream(filename)) {
      DataOutputStream outStream = new DataOutputStream(out);

      boolean isOpen = true;

      PriorityQueue<TCPsegment> sendBuffer = new PriorityQueue<>(sws);
      HashSet<Integer> bsnBufferSet = new HashSet<>();

      if (firstReceivedAck != null && firstReceivedAck.ack && firstReceivedAck.dataLength > 0) {

        sendBuffer.add(firstReceivedAck);
        bsnBufferSet.add(firstReceivedAck.sequenceNum);
      }

      while (isOpen) {

        TCPsegment data;
        try {
          data = handlePacket();
        } catch (SegmentChecksumMismatchException e) {
          e.printStackTrace();
          continue;
        }

        long mostRecentTimestamp = data.time;

        int currBsn = data.sequenceNum;
        int firstByteBeyondSws = nextByteExpected + (sws * mtu);

        if (currBsn >= firstByteBeyondSws) {

          System.err.println("Rcv - discard out-of-order packet!!!");
          TCPsegment ackSegment = TCPsegment.getAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
          sendPacket(ackSegment, senderIp, senderPort);
          numDiscardPackets++;
          continue;
        } else if (currBsn < nextByteExpected) {

          TCPsegment ackSegment = TCPsegment.getAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
          sendPacket(ackSegment, senderIp, senderPort);
          numDiscardPackets++;
          continue;
        } else {

          if (!bsnBufferSet.contains(currBsn)) {
            bsnBufferSet.add(currBsn);
            sendBuffer.add(data);

          } else {
            continue;
          }

          while (!sendBuffer.isEmpty()) {
            TCPsegment minSegment = sendBuffer.peek();

            if (minSegment.sequenceNum == nextByteExpected) {

              if (!minSegment.ack || minSegment.data.length <= 0) {

                if (minSegment.fin) {
                  outStream.close();
                  closeConnection(mostRecentTimestamp);
                  sendBuffer.remove(minSegment);
                  bsnBufferSet.remove(minSegment.sequenceNum);
                  isOpen = false;
                } else {
                  throw new UnexpectedFlagException("Expected ACK and data or FIN!", minSegment);
                }
              } else {

                outStream.write(minSegment.data);

                nextByteExpected += minSegment.data.length;
                numByteReceived += minSegment.data.length;
                TCPsegment ackSegment = TCPsegment.getAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
                sendPacket(ackSegment, senderIp, senderPort);

                bsnBufferSet.remove(minSegment.sequenceNum);
                sendBuffer.remove(minSegment);
              }
            } else {

              TCPsegment ackSegment = TCPsegment.getAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
              sendPacket(ackSegment, senderIp, senderPort);
              break;
            }
          }
        }
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (UnexpectedFlagException e) {
      e.printStackTrace();
    }
  }

  private void closeConnection(long mostRecentTimestamp)
      throws IOException, MaxRetransmitException, UnexpectedFlagException {
    boolean isLastAckReceived = false;
    short currNumRetransmits = 0;
    while (!isLastAckReceived) {

      TCPsegment returnFinAckSegment = TCPsegment.getConnectionSegment(bsn, nextByteExpected, ConnectionState.FIN_ACK);
      sendPacket(returnFinAckSegment, senderIp, senderPort);
      bsn++;

      try {
        this.socket.setSoTimeout(INITIAL_TIMEOUT_MS);
        TCPsegment lastAckSegment;
        try {
          lastAckSegment = handlePacket();
        } catch (SegmentChecksumMismatchException e) {
          e.printStackTrace();
          continue;
        }

        if (lastAckSegment.ack) {
          isLastAckReceived = true;
        } else if (lastAckSegment.fin) {

          bsn--;
          continue;
        } else {
          throw new UnexpectedFlagException();
        }
      } catch (SocketTimeoutException e) {
        System.err.println("Timeout while waiting for last ACK!");
        currNumRetransmits++;
        if (currNumRetransmits >= (MAX_RETRANSMITS + 1)) {

          throw new MaxRetransmitException("Max FINACK retransmits!");
        }
        this.numRetransmits++;
        bsn--;
      }
    }
  }

  public void printFinalStatsHeader() {
    System.out.println("TCPEnd Receiver Finished==========");
    this.printFinalStats();
  }

}
