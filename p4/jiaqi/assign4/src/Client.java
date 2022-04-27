
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
/* 
Clarification: 
1. The checksum is used whever to check and drop the packets, must match the checkSum first;
2. MTU includes the packets header, whenever lost connection it will continuly to resend for several times and finally exit the system;
3. If the established ACK is dropped but the next packet is not dropped, then everything is fine. Otherwise, the connection must be reset.
You still can send out data packets as usual;
*/
public class Client {
    private int port;                       // port which client is running on
    private InetAddress remoteIP;           // IP of remote process
    private int serverport;                 // port at which the remote process is running
    String filename;                        // Name of the file to be transferred
    private int mtu;                        // maximum transmission unit, in byte
    private int sws;                        // Sliding window size for buffering, in byte

    Map<Integer, Packet>sendBuffer;            // send buffer for unacknowledgement data
    private DatagramSocket sendsocket;

    public static int Maximum_Retransmission = 16;
    private final double alpha = 0.875;     //  params used for RTT estimation
    private final double beta = 0.75;
    private volatile int curbufferSize;
    private volatile int curSequencenumber;
    private File sendFile;
    private long filesize;
    private volatile double curTimeout;               // Estimate the timeout based on RTT time, each client has ony 1 single value based on the ACK received
    private double EDEV;
    private double ERTT;
    
    private Map<Integer, Integer>duplicateACK;  // statistics of times of ACK packets received


    private final String recvtype = "rcv";
    private final String sendtype = "snd";
    // Transmission  
    private int dataSend;
    private int pktSend;
    private int retransmissionNo;
    private int dupAckNo;

    private ReadWriteLock lk;

    public Client(int port, String remoteIP, int serverport, String filename, int mtu, int sws) {
        this.port = port;
        this.serverport = serverport;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        duplicateACK = new ConcurrentSkipListMap<Integer, Integer>();
        sendBuffer = new ConcurrentSkipListMap<Integer, Packet>();
        curbufferSize = 0;
        curTimeout = 5*1000000000;
        try {
            this.remoteIP = InetAddress.getByName(remoteIP);
            sendsocket = new DatagramSocket(port);            
        } catch(SocketException e) {
            e.printStackTrace();
        } catch(UnknownHostException e) {
            e.printStackTrace();
        }

        sendFile = new File(this.filename);
        filesize = sendFile.length();

        dataSend = 0;
        pktSend = 0;
        retransmissionNo = 0;
        dupAckNo = 0;
        lk = new ReentrantReadWriteLock();
    }

    public void connectionRequest() {
        Packet connectionreq = null;
        byte[] datapayload = filename.getBytes();
        dataSend += datapayload.length;
        if(sendBuffer.containsKey(0) == false) {
            connectionreq = new Packet(datapayload.length);
        } else {
            connectionreq = sendBuffer.get(0);
        }
        connectionreq.setSequencenumber(0);
        connectionreq.setTimestamp(System.nanoTime());
        connectionreq.setSYN();
        connectionreq.setChecksum();
        connectionreq.setLength(datapayload.length);
        sendPacket(connectionreq);
        curSequencenumber = 1;                          // The sequencenumber is set to 1
        printoutInfo(sendtype, connectionreq.getTimestamp(), "S---", connectionreq.getSequencenumber(), connectionreq.getLength(), connectionreq.getAckmber());
        sendBuffer.put(0, connectionreq);
    }

    // write the file contents into byte array and encapsulate into Packet
    public Packet generateDatapacket(int start, int len) {
        byte[] payload = new byte[len];
        Packet datapac = new Packet(len);
        int datalength = 0;
        InputStream ins = null;
        try {
            ins = new FileInputStream(sendFile);
            ins.skip(start - 1);                            // skip the first few bytes of the File
            datalength = ins.read(payload, 0, len);         // Actual datalength reads into the buffer
            ins.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ins.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        // the last packet which should be less than MTU size
        if(datalength < len) {
            datapac = new Packet(datalength);
            byte[] newpayload = new byte[datalength];
            for (int i = 0; i < datalength; i++) {
                newpayload[i] = payload[i];
            }
            datapac.setData(newpayload);
        } else if(datalength == len) {
            datapac.setData(payload);
        }
        datapac.setSequencenumber(start);
        datapac.setChecksum();
        datapac.setLength(datalength);
        datapac.setTimestamp(System.nanoTime());
        return datapac;
    }

    // Receive ACK packet and sending Packet from remote server
    public void runClient() {
        Packet recv = new Packet(0);
        // sendbuffer used as buffer for the received packet
        // Continuing sending out packets until the sliding window is full & wait for response
        ScheduledExecutorService sendService = Executors.newSingleThreadScheduledExecutor();
        Runnable sendRunnable = new Runnable() {
            @Override
            public void run() {
                int datalength = mtu - 24;                      // maximum data payload bytes
                // Continue to send out packets until the send Buffer becomes full                  
                while(curbufferSize < sws && curSequencenumber < filesize) {
                    Packet datapac = generateDatapacket(curSequencenumber, datalength);
                    sendPacket(datapac);
                    sendBuffer.put(curSequencenumber, datapac);
                    curSequencenumber += datapac.getLength();
                    curbufferSize += 1;                   // sws is total number of packets in the sliding window
                    printoutInfo(sendtype, datapac.getTimestamp(), "---D", datapac.getSequencenumber(), datapac.getLength(), datapac.getAckmber());
                }
                if(curSequencenumber == filesize + 1 && sendBuffer.size() == 0) {
                    Packet connclose = new Packet(0);
                    connclose.setFIN();
                    connclose.setSequencenumber(curSequencenumber);
                    connclose.setTimestamp(System.nanoTime());
                    connclose.setChecksum();
                    sendBuffer.put(curSequencenumber, connclose);
                    curSequencenumber++;
                    sendPacket(connclose);
                    printoutInfo(sendtype, connclose.getTimestamp(), "--F-", connclose.getSequencenumber(), connclose.getLength(), connclose.getAckmber());
                }
            }
        };
        
        // Retransmission policy: Timeout of packet / duplicate ACK is 3 times
        Runnable RentransRunnable = new Runnable() {
			public void run() {
                try {
                    lk.writeLock().lock();
                    // update the timeout value for each ConcurrentHashMap Entry, if timeout resend current packet
                    for(Integer cur:sendBuffer.keySet()) {
                        if(System.nanoTime() - sendBuffer.get(cur).getTimestamp() > curTimeout) {
                            Packet timeoutpac = sendBuffer.get(cur);
                            if(timeoutpac.isSYN()) {
                                connectionRequest();
                                continue;
                            }
                            timeoutpac.setResendTime(timeoutpac.getResendTime() + 1);
                            // if the resend time exceed maximum time, lost connections just exit sending
                            if(sendBuffer.get(cur).getResendTime() > Client.Maximum_Retransmission) {
                                System.err.print("Exceed the maximum transmission time\n");
                                System.exit(1);
                            }
                            timeoutpac.setTimestamp(System.nanoTime());
                            sendPacket(timeoutpac);
                            printoutInfo(sendtype, timeoutpac.getTimestamp(), "---D", timeoutpac.getSequencenumber(), timeoutpac.getLength(), timeoutpac.getAckmber());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    lk.writeLock().unlock();
                }
			}
		};
        ScheduledExecutorService manipulateTimeout = Executors.newSingleThreadScheduledExecutor();
        manipulateTimeout.scheduleAtFixedRate(RentransRunnable, 0, 1, TimeUnit.NANOSECONDS);
        // Main thread keeps receiving packets 
        while(true) {
            try {
                sendsocket.receive(recv.getPacket());
            } catch(IOException e) {
                e.printStackTrace();
                continue;
            }
            int orichecksum = recv.getChecksum();
            recv.setChecksum();
            if(recv.getChecksum() != orichecksum)
                continue;
            // Connection establishes response
            if(recv.isSYN()) {
                printoutInfo(recvtype, recv.getTimestamp(), "SA--", recv.getSequencenumber(), recv.getLength(), recv.getAckmber());                
                Packet connectAck = new Packet(0);
                connectAck.setSequencenumber(0);
                connectAck.setAcknumber(recv.getSequencenumber() + 1);
                connectAck.setACK();
                connectAck.setChecksum();
                connectAck.setLength(0);
                connectAck.setTimestamp(System.nanoTime());

                sendBuffer.remove(0);
                sendPacket(connectAck);
                sendService.scheduleAtFixedRate(sendRunnable, 0, 1, TimeUnit.NANOSECONDS); 
                printoutInfo(sendtype, connectAck.getTimestamp(), "-A--", connectAck.getSequencenumber(), connectAck.getLength(), connectAck.getAckmber());                
            }
            // Receiver side's close request, will close the connection finally
            else if(recv.isFIN()) {
                printoutInfo(recvtype, recv.getTimestamp(), "-AF-", recv.getSequencenumber(), recv.getLength(), recv.getAckmber());
                
                Packet closeAck = new Packet(0);
                closeAck.setAcknumber(2);
                closeAck.setSequencenumber(curSequencenumber);
                closeAck.setChecksum();
                closeAck.setLength(0);
                closeAck.setACK();
                closeAck.setTimestamp(System.nanoTime());

                sendPacket(closeAck);
                sendsocket.close();
                printoutInfo(sendtype, closeAck.getTimestamp(), "-A--", closeAck.getSequencenumber(), closeAck.getLength(), closeAck.getAckmber());
                // Print statistics of transmission
                System.out.println("Amount of Data Transferred " + dataSend);
                System.out.println("No of Packets Sent " + pktSend);
                System.out.println("No of Retransmissions " + retransmissionNo);
                System.out.println("No of Duplicate Acknowledgements " + dupAckNo);
                System.exit(0);
            }
            else if(recv.isACK()) {
                printoutInfo(recvtype, recv.getTimestamp(), "-A--", recv.getSequencenumber(), recv.getLength(), recv.getAckmber());                
                calculateTimeout(recv);
                int acknumber = recv.getAckmber();
                if(duplicateACK.containsKey(acknumber) == false) {
                    duplicateACK.put(acknumber, 1);
                    lk.writeLock().lock();
                    for(Integer cur:sendBuffer.keySet()) {
                        if(cur <= acknumber - 1) {
                            curbufferSize -= 1;  // move out 1 packet from the buffer                                                 
                            sendBuffer.remove(cur);
                            duplicateACK.remove(cur);
                        }
                    }
                    lk.writeLock().unlock();
                } else {
                    dupAckNo++;
                    duplicateACK.put(acknumber, duplicateACK.get(acknumber) + 1);
                    // fast retransmission
                    if(duplicateACK.get(acknumber) >= 3) {
                        retransmissionNo++;
                        if (!sendBuffer.containsKey(acknumber))
                            continue;
                        Packet resendtarget = sendBuffer.get(acknumber);
                        resendtarget.setTimestamp(System.nanoTime());
                        dataSend += resendtarget.getLength();
                        sendPacket(resendtarget);
                        resendtarget.setResendTime(resendtarget.getResendTime() + 1);
                        printoutInfo(sendtype, resendtarget.getTimestamp(), "---D", resendtarget.getSequencenumber(), resendtarget.getLength(), resendtarget.getAckmber());
                    }
                } 
            }
        }
    }

    public void printoutInfo(String pactype, double time, String flaglist, int seq, int numbytes, int acknumber) {
        System.out.printf("%s %f %s %d %d %d\n", pactype, time, flaglist, seq, numbytes, acknumber);
    }

    public void sendPacket(Packet cur) {
        try {
            cur.getPacket().setAddress(remoteIP);  
            cur.getPacket().setPort(serverport);
            pktSend++;
            sendsocket.send(cur.getPacket());            
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void calculateTimeout(Packet ack) {
        if(ack.getAckmber() == 2) {
            ERTT = System.nanoTime() - ack.getTimestamp();
            EDEV = 0;
            curTimeout = 2 * ERTT;
        } else {
            double SRTT = System.nanoTime() - ack.getTimestamp();
            double SDEV = Math.abs(SRTT - ERTT);
            ERTT = alpha * ERTT + (1 - alpha) * SRTT;
            EDEV = beta * EDEV + (1 - beta) * SDEV;
            curTimeout = ERTT + 4 * EDEV;
        }
    }
}