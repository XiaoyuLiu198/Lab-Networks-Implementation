public class TCPutil {

    public static long ertt = 0;
    public static long edev = 0;
    public static long timeout = 0;
    public static int numPacketReceived = 0;
    public static int numPacketSent = 0;
    public static int numByteSent = 0;
    public static int numByteReceived = 0;
    public static int numOutofSequence = 0;
    public static int numIncorrectChecksum = 0;
    public static int numRetransmission = 0;
    public static int numDuplicateAck = 0;

    public static void getTimeout(int S, long T) { // sequence S, current time T
        if (S == 0) {
            ertt = (long) (System.nanoTime() - T);
            edev = 0;
            timeout = 2 * ertt;
        } else {
            long srtt = (long) (System.nanoTime() - T);
            long sdev = Math.abs(srtt - ertt);
            ertt = (long) (0.875 * ertt + (1 - 0.875) * srtt);
            edev = (long) (0.75 * edev + (1 - 0.75) * sdev);
            timeout = ertt + 4 * edev;
        }
    }

    public static void getSenderStatus(TCPsegment segment) {
        char SYN = segment.syn ? 'S' : '-';
        char ACK = segment.ack ? 'A' : '-';
        char FIN = segment.fin ? 'F' : '-';
        char data = (segment.data.length != 0) ? 'D' : '-';
        System.out.println(String.format("snd %.3f %c %c %c %c %d %d %d",
                segment.time / 1E9, SYN, ACK, FIN, data, segment.sequenceNum, segment.data.length, segment.ackNum));
    }

    public static void getReceiverStatus(TCPsegment segment) {
        char SYN = segment.syn ? 'S' : '-';
        char ACK = segment.ack ? 'A' : '-';
        char FIN = segment.fin ? 'F' : '-';
        char data = (segment.data.length != 0) ? 'D' : '-';
        System.out.println(String.format("rcv %.3f %c %c %c %c %d %d %d",
                segment.time / 1E9, SYN, ACK, FIN, data, segment.sequenceNum, segment.data.length, segment.ackNum));
    }

    public static void getStatistics() {
        System.out.println("Bytes of data sent: " + (double) numByteSent);
        System.out.println("Bytes of data received: " + (double) numByteReceived);
        System.out.println("Number of packets sent: " + numPacketSent);
        System.out.println("Number of packets received: " + numPacketReceived);
        System.out.println("Number of out-of-sequence packets discarded: " + numOutofSequence);
        System.out.println("Number of checksum-incorrect packets discarded: " + numIncorrectChecksum);
        System.out.println("Number of retransmissions: " + numRetransmission);
        System.out.println("Number of duplicate acknowledgements: " + numDuplicateAck);
    }

}
