package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import edu.wisc.cs.sdn.vnet.rt.*;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import java.util.*;
import java.nio.ByteBuffer;
/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	/** ARP Request Table */
	private ARPRequestTable arpReqTable;
	
	/** Distance Vector Table */
	private DistanceVectorTable distanceVectorTable;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.arpReqTable = new ARPRequestTable();
		TableThreadImpl obj = new TableThreadImpl(this.arpReqTable);
		Thread t = new Thread(obj);
		t.start();
		this.distanceVectorTable = new DistanceVectorTable();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/** Init Router Table */
	public void initRouterTable()
	{
		System.out.println("Initializing Route Table");
		for(Map.Entry<String, Iface> entry: this.getInterfaces().entrySet()){
			int subnetNumber = entry.getValue().getIpAddress() & entry.getValue().getSubnetMask();
			this.routeTable.insert(subnetNumber, 0, entry.getValue().getSubnetMask(), entry.getValue());
			DistanceVectorEntry e = new DistanceVectorEntry(subnetNumber, 1, -1);
			this.distanceVectorTable.addDVTableEntry(e);
		}

		/* Broadcast DV Info in RIP packets */
		sendRIPPacket((byte)1);

		/* DV Table tracking thread - Timeout & periodic updates */
		DVTableThreadImpl dvThreadObj = new DVTableThreadImpl(this.distanceVectorTable);
		Thread dvThread = new Thread(dvThreadObj);
		dvThread.start();
	}

	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* Handle packets */

		/* CHECK 1 : Ethernet Packet */
		/* Handle ARP Request */
		if(etherPacket.getEtherType() == Ethernet.TYPE_ARP) {
			ARP arpPacket = (ARP)etherPacket.getPayload();
			int targetIp = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();
			if(arpPacket.getOpCode() == ARP.OP_REQUEST && targetIp == inIface.getIpAddress()) {
				/* Send ARP Reply */
				this.sendARPReply(etherPacket, arpPacket, inIface);
				return;
			}
			else if(arpPacket.getOpCode() == ARP.OP_REPLY) {
				/* Got ARP Reply */
				IPv4 dummyPkt = new IPv4();
				int arpReplyIPAddress = dummyPkt.toIPv4Address(arpPacket.getSenderProtocolAddress());
				MACAddress destinationMAC = new MACAddress(arpPacket.getSenderHardwareAddress());

				/* Ivalidate Entry in ARP Request Table : Get Sender protocol address from ARP header */
				synchronized(arpReqTable) {
				for(ARPRequestEntry ARE : arpReqTable.ARPRequestTab) {
					if(ARE.IPAddress == arpReplyIPAddress) {
						ARE.invalidateARPRequestEntry(destinationMAC);
						break;
					}
				}
				}

				/* Add MAC Address to ARP Cache */
				arpCache.insert(destinationMAC, arpReplyIPAddress);
				return;
			}
			else
			{
				/* Drop Pakcet */
				return;
			}
		}
		else if(etherPacket.getEtherType() != 0x800) {
			/* Not IP Packet - Dropping */
			return;
		}
		IPv4 pkt = (IPv4)etherPacket.getPayload();

		int expectedRIPMulticastAddress = pkt.toIPv4Address("224.0.0.9");
		/* Checking if the recived packet is RIP Request/Response */
		if(pkt.getProtocol() == IPv4.PROTOCOL_UDP) {
			UDP udpPkt = (UDP)pkt.getPayload();
			if(udpPkt.getDestinationPort() == UDP.RIP_PORT) {
				if(pkt.getDestinationAddress() == expectedRIPMulticastAddress) {

					boolean match = false, updated = false;
					/* RIP Request/Response Packet */
					RIPv2 ripPkt = (RIPv2)udpPkt.getPayload();

					/* Check if there are any updates */
					synchronized(this.distanceVectorTable) {
					for(RIPv2Entry ripEntry : ripPkt.getEntries()) {
						match = false;
						for(DistanceVectorEntry dvEntry : distanceVectorTable.DVTable) {
							synchronized(dvEntry) {
							if(dvEntry.IPAddress == ripEntry.getAddress()) {
								/* Refresh DV Entry */
								dvEntry.updateTime();
								match = true;
								if(dvEntry.distance > (ripEntry.getMetric() + 1)) {
									updated = true;
									dvEntry.distance = ripEntry.getMetric() + 1;
									routeTable.update(dvEntry.IPAddress, ripEntry.getSubnetMask(), pkt.getSourceAddress(), inIface);
								} else {
									//System.out.println("Matching IP found but no update");
								}
							}
							}
						}
						if(match == false) {
							updated = true;
							DistanceVectorEntry newDVEntry = new DistanceVectorEntry(ripEntry.getAddress(), ripEntry.getMetric()+1, 1);
							distanceVectorTable.addDVTableEntry(newDVEntry);
							DVEntryTOThreadImpl TOThreadObj = new DVEntryTOThreadImpl(newDVEntry);
							Thread TOThread = new Thread(TOThreadObj);
							TOThread.start();
							routeTable.insert(ripEntry.getAddress(), pkt.getSourceAddress(), ripEntry.getSubnetMask(), inIface);
						}
					}
					}
					if(updated == true) {
						sendRIPPacket((byte)2);
					}
					return;
				} else {
					boolean isRouterIP = false;
					for(Map.Entry<String, Iface> entry: interfaces.entrySet()) {
						if(pkt.getDestinationAddress() == entry.getValue().getIpAddress()) {
							isRouterIP = true;
							break;
						}
					}
					if(isRouterIP == true) {
							/* RIP Request/Response Packet */
							/* Check if there are any updates */
							/* Refresh DV Entry */
							boolean match = false, updated = false;
							RIPv2 ripPkt = (RIPv2)udpPkt.getPayload();

							System.out.println("RIP Entries");
							System.out.println(ripPkt);
							synchronized(this.distanceVectorTable) {
							for(RIPv2Entry ripEntry : ripPkt.getEntries()) {
								match = false;
								for(DistanceVectorEntry dvEntry : distanceVectorTable.DVTable) {
									if(dvEntry.IPAddress == ripEntry.getAddress()) {
										dvEntry.updateTime();
										match = true;
										if(dvEntry.distance > (ripEntry.getMetric() + 1)) {
											updated = true;
											dvEntry.distance = ripEntry.getMetric() + 1;
											routeTable.update(dvEntry.IPAddress, ripEntry.getSubnetMask(), pkt.getSourceAddress(), inIface);
										} else {
											System.out.println("Matching IP found but no update");
										}
									}
								}
								if(match == false) {
									System.out.println("New entry");
									updated = true;
									DistanceVectorEntry newDVEntry = new DistanceVectorEntry(ripEntry.getAddress(), ripEntry.getMetric()+1, 1);
									distanceVectorTable.addDVTableEntry(newDVEntry);
									DVEntryTOThreadImpl TOThreadObj = new DVEntryTOThreadImpl(newDVEntry);
									Thread TOThread = new Thread(TOThreadObj);
									TOThread.start();
									routeTable.insert(ripEntry.getAddress(), pkt.getSourceAddress(), ripEntry.getSubnetMask(), inIface);
								}
							}
							}
							if(updated == true) {
								sendRIPPacket((byte)2);
							}
							/* RIP Unicast */
							//sendRIPPacketUnicast((byte)2, pkt.getSourceAddress(), etherPacket.getSourceMAC(), inIface);
							return;
					}
					/* Not destinerd for Router IP */
				}
				/* Not a Multicast Address */
			} else {
				/* Not a RIP */
			}
		} else {
			/* Not a UDP */
		}

		/* CHECK 2 : Checksum Validation */
		short actualCheckSum = pkt.getChecksum();
		pkt.resetChecksum();
		pkt.serialize();
		short currentChecksum = pkt.getChecksum();
		if(actualCheckSum != currentChecksum) {
			/* Checksum mismatch - Dropping */
			return;
		}

		/* CHECK 3 : TTL Validation */
		byte currentTTL = pkt.getTtl();
		currentTTL--;
		if(currentTTL == 0) {
			/* TTL 0 - ICMP TLE message to sender */
			this.sendICMPPacket(pkt, inIface, (byte)11, (byte)0);
			return;
		}

		/* Updating Packet : New TTL & Checksum */
		pkt.setTtl(currentTTL);
		pkt.resetChecksum();
		pkt.serialize();

		/* CHECK 4 : Is packet destined for router interface IP Address */
		for(Map.Entry<String, Iface> entry: interfaces.entrySet()){
			if(pkt.getDestinationAddress() == entry.getValue().getIpAddress()){
				/* Packet Destined for Routers IP - Dropping */
				byte protocolType = pkt.getProtocol();
				if(protocolType == IPv4.PROTOCOL_TCP || protocolType == IPv4.PROTOCOL_UDP){
					this.sendICMPPacket(pkt, inIface, (byte)3, (byte)3);
				}
				else if(protocolType == IPv4.PROTOCOL_ICMP) {
					ICMP icmpPkt = (ICMP)pkt.getPayload();
					if(icmpPkt.getIcmpType() == (byte)8) {
						this.sendEchoReplyPacket(pkt, inIface, (byte)0, (byte)0);
					}
				}
				return;
			}
		}

		/* Forwarding Packets */
		/* STEP 1 : Route Table Look up */
		RouteEntry rEntry = routeTable.lookup(pkt.getDestinationAddress());
		if(rEntry == null) {
			/* No matching route table entry */
			/* Send ICMP Error Reply as Destination Net Unreachable */
			this.sendICMPPacket(pkt, inIface, (byte)3, (byte)0);
			return;
		}

		/* CHECK 5 : Check if incoming and outgoing interfaces are same */
		if(inIface.getName().equals(rEntry.getInterface().getName())){
		/* Incoming Interface is same as outgoing interface - dropping */
			return;
		}

		/* Outgoing router Interface MAC address */
		MACAddress sourceMac = rEntry.getInterface().getMacAddress();
		etherPacket.setSourceMACAddress(sourceMac.toString());

		/* STEP 2 : Find the next hop IP Address */
		int nextHopIPAddress = rEntry.getGatewayAddress();
		if(nextHopIPAddress == 0){
			nextHopIPAddress = pkt.getDestinationAddress();
		}

		/* Find the next hop MAC address from ARP Cache */
		/* CHECK 6 : Checking non-existent Host in any network connected to Router */
		ArpEntry ae = arpCache.lookup(nextHopIPAddress);
		if(ae == null) {
			this.sendARPRequest(etherPacket, inIface, rEntry.getInterface(), nextHopIPAddress);
			//this.sendICMPPacket(pkt, inIface, (byte)3, (byte)1);
			/* No such host in the network - Dropping */
			return;
		}

		/* Next hop MAC addresses */
		MACAddress destinationMac = ae.getMac();
		/* STEP 3 : Update Ethernet Pakcet to send */
		etherPacket.setDestinationMACAddress(destinationMac.toString());
		
		/* Send Packet on the interface found from Route Table */
		sendPacket(etherPacket, rEntry.getInterface());

		/********************************************************************/
	}

	/* Construct ICMP packet with given type and command values and send it */
	public void sendICMPPacket(IPv4 pktIn, Iface inIface, byte type, byte code) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		/* ICMP Header construction */
		icmp.setIcmpType(type);
		icmp.setIcmpCode(code);

		/* ICMP Data construction */
		int len = 4 /* 4 byte padding */ +
			+ (pktIn.getHeaderLength() * 4) /* IP Header length*/ +
			+ 8 /* 8 bytes of IP payload */;
		byte[] icmpData = new byte[len];
		/* Padding */
		Arrays.fill(icmpData, 0, 4, (byte)0);
		/* IP Header copying */
		byte[] serializedIPPkt = pktIn.serialize();
		int i, j, k;
		for(i = 0, j = 4; i < (pktIn.getHeaderLength() * 4); i++, j++) {
			icmpData[j] = serializedIPPkt[i];
		}
		/* 8 byte of IP playload */
		k = i;
		while(k < (i + 8)) {
			icmpData[j] = serializedIPPkt[k];
			j++;
			k++;
		}
		data.setData(icmpData);

		/* IPv4 header construction */
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setSourceAddress(inIface.getIpAddress());
		ip.setDestinationAddress(pktIn.getSourceAddress());

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		/* Ether packet constructed */
		/* Ethernet header construction */
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toString());
		MACAddress destMAC = findMACFromRTLookUp(pktIn.getSourceAddress());
		if(destMAC == null) {
			RouteEntry rEntry = routeTable.lookup(pktIn.getSourceAddress());
			/* Find the next hop IP Address */
			int nextHopIPAddress = rEntry.getGatewayAddress();
			if(nextHopIPAddress == 0){
				nextHopIPAddress = pktIn.getSourceAddress();
			}
			this.sendARPRequest(ether, inIface, rEntry.getInterface(), nextHopIPAddress);
			return;
		}
		ether.setDestinationMACAddress(destMAC.toString());

		/* Send ICMP packet */
		sendPacket(ether, inIface);
	}

	public void sendEchoReplyPacket(IPv4 pktIn, Iface inIface, byte type, byte code) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		/* ICMP Header construction */
		icmp.setIcmpType(type);
		icmp.setIcmpCode(code);

		/* ICMP Data construction */
		ICMP inIcmpPkt = (ICMP)pktIn.getPayload();
		byte[] inIcmpPktPayload = inIcmpPkt.getPayload().serialize();
		data.setData(inIcmpPktPayload);

		/* IPv4 header construction */
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setSourceAddress(pktIn.getDestinationAddress());
		ip.setDestinationAddress(pktIn.getSourceAddress());

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		/* Ethernet header construction */
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toString());
		MACAddress destMAC = findMACFromRTLookUp(pktIn.getSourceAddress());
		if(destMAC == null) {
			RouteEntry rEntry = routeTable.lookup(pktIn.getSourceAddress());
			/* Find the next hop IP Address */
			int nextHopIPAddress = rEntry.getGatewayAddress();
			if(nextHopIPAddress == 0){
				nextHopIPAddress = pktIn.getSourceAddress();
			}
			this.sendARPRequest(ether, inIface, inIface, nextHopIPAddress);
			return;
		}
		ether.setDestinationMACAddress(destMAC.toString());

		/* Send ICMP packet */
		sendPacket(ether, inIface);
	}

	/* Wrapper Function for IP lookup + ARP cache lookup */
	public MACAddress findMACFromRTLookUp(int ip) {

		RouteEntry rEntry = routeTable.lookup(ip);
		if(rEntry == null) {
			/* No matching route table entry */
			return null;
		}
		/* Find the next hop IP Address */
		int nextHopIPAddress = rEntry.getGatewayAddress();
		if(nextHopIPAddress == 0){
			nextHopIPAddress = ip;
		}
		/* Find the next hop MAC address from ARP Cache */
		ArpEntry ae = arpCache.lookup(nextHopIPAddress);
		if(ae == null) {
			/* No such host in the network - Dropping */
			return null;
		}
		/* Next hop MAC addresses */
		return ae.getMac();
	}

	/* ARP Reply */
	public void sendARPReply(Ethernet inEtherPkt, ARP inArpPkt, Iface inIface) {
		Ethernet ether = new Ethernet();
		ARP arpPkt = new ARP();

		/* Construct Ethernet header */
		ether.setEtherType(Ethernet.TYPE_ARP);
		ether.setSourceMACAddress(inIface.getMacAddress().toString());
		ether.setDestinationMACAddress(inEtherPkt.getSourceMACAddress());

		/* ARP Header */
		arpPkt.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arpPkt.setProtocolType(ARP.PROTO_TYPE_IP);
		arpPkt.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
		arpPkt.setProtocolAddressLength((byte)4);
		arpPkt.setOpCode(ARP.OP_REPLY);
		arpPkt.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
		arpPkt.setSenderProtocolAddress(inIface.getIpAddress());
		arpPkt.setTargetHardwareAddress(inArpPkt.getSenderHardwareAddress());
		arpPkt.setTargetProtocolAddress(inArpPkt.getSenderProtocolAddress());

		/* Set Ethernet Payload */
		ether.setPayload(arpPkt);
		/* Send ARP Reply */
		sendPacket(ether, inIface);
	}

	/* ARP Request */
	public void sendARPRequest(Ethernet etherPacket, Iface inIface, Iface outIface, int IP) {
		ARPRequestEntry entry;
		synchronized(arpReqTable) {
		for(ARPRequestEntry ARE : arpReqTable.ARPRequestTab) {
			if(ARE.IPAddress == IP) {
				ARE.addPacketQueue(etherPacket, outIface, inIface);
				return;
			}
		}

		entry = arpReqTable.newARPRequest(IP, etherPacket, inIface, outIface);
		}
		EntryThreadImpl obj = new EntryThreadImpl(entry);
		Thread t = new Thread(obj);
		t.start();
	}

	public void sendARPRequestPacket(int IPAddress, Iface outIface, EthernetPktInfo p1) {
		Ethernet ether = new Ethernet();
		ARP arpPkt = new ARP();

		ether.setEtherType(Ethernet.TYPE_ARP);
		ether.setSourceMACAddress(p1.inIface.getMacAddress().toString());
		ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");

		arpPkt.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arpPkt.setProtocolType(ARP.PROTO_TYPE_IP);
		arpPkt.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
		arpPkt.setProtocolAddressLength((byte)4);
		arpPkt.setOpCode(ARP.OP_REQUEST);
		arpPkt.setSenderHardwareAddress(p1.inIface.getMacAddress().toBytes());
		arpPkt.setSenderProtocolAddress(p1.inIface.getIpAddress());
		byte val[] = new byte[6];
		arpPkt.setTargetHardwareAddress(val);
		arpPkt.setTargetProtocolAddress(IPAddress);

		ether.setPayload(arpPkt);

		sendPacket(ether, outIface);
	}

	public void sendRIPPacket(byte command) {
		RIPv2 ripPkt = new RIPv2();
		synchronized(this.distanceVectorTable) {
				for(DistanceVectorEntry dvEntry: this.distanceVectorTable.DVTable) {
					RouteEntry re = this.routeTable.lookup(dvEntry.IPAddress);
					RIPv2Entry ripEntry = new RIPv2Entry(re.getDestinationAddress(), re.getMaskAddress(), dvEntry.distance);
					ripPkt.addEntry(ripEntry);
				}
		}
		ripPkt.setCommand(command);

		/* UDP Packet */
		UDP udpPkt = new UDP();
		udpPkt.setSourcePort(UDP.RIP_PORT);
		udpPkt.setDestinationPort(UDP.RIP_PORT);
		udpPkt.setPayload(ripPkt);

		for(Map.Entry<String, Iface> entry: this.getInterfaces().entrySet()){
				/* IP Packet */
				IPv4 ipPkt = new IPv4();
				ipPkt.setProtocol(IPv4.PROTOCOL_UDP);
				ipPkt.setTtl((byte)15);
				ipPkt.setDestinationAddress("224.0.0.9");
				ipPkt.setSourceAddress(entry.getValue().getIpAddress());
				ipPkt.setPayload(udpPkt);

				/* Ether Packet */
				Ethernet ether = new Ethernet();
				ether.setEtherType(Ethernet.TYPE_IPv4);
				ether.setPayload(ipPkt);
				ether.setSourceMACAddress(entry.getValue().getMacAddress().toString());
				ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");

				/* Broadcast RIP to all interfaces */
				sendPacket(ether, entry.getValue());
		}
	}

	public void sendRIPPacketUnicast(byte command, int sourceIPAddress, MACAddress sourceMACAddress, Iface inIface) {
		RIPv2 ripPkt = new RIPv2();
		for(DistanceVectorEntry dvEntry: this.distanceVectorTable.DVTable) {
			RouteEntry re = this.routeTable.lookup(dvEntry.IPAddress);
			RIPv2Entry ripEntry = new RIPv2Entry(re.getDestinationAddress(), re.getMaskAddress(), dvEntry.distance);
			ripPkt.addEntry(ripEntry);
		}
		ripPkt.setCommand(command);

		/* UDP Packet */
		UDP udpPkt = new UDP();
		udpPkt.setSourcePort(UDP.RIP_PORT);
		udpPkt.setDestinationPort(UDP.RIP_PORT);
		udpPkt.setPayload(ripPkt);

		/* IP Packet */
		IPv4 ipPkt = new IPv4();
		ipPkt.setProtocol(IPv4.PROTOCOL_UDP);
		ipPkt.setTtl((byte)64);
		ipPkt.setDestinationAddress(sourceIPAddress);
		ipPkt.setSourceAddress(inIface.getIpAddress());
		ipPkt.setPayload(udpPkt);

		/* Ether Packet */
		Ethernet ether = new Ethernet();
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setPayload(ipPkt);
		ether.setSourceMACAddress(inIface.getMacAddress().toString());
		ether.setDestinationMACAddress(sourceMACAddress.toString());

		/* Broadcast RIP to all interfaces */
		sendPacket(ether, inIface);
	}

	/* Class implementing thread functionality of ARPRequest Table Entry */
	class EntryThreadImpl implements Runnable {
		ARPRequestEntry entry;

		public EntryThreadImpl(ARPRequestEntry entry) {
			this.entry = entry;
		}

		public void run() {
			while(true) {
				if(this.entry.nTry <= 0)
					break;

				synchronized(this.entry) {
					sendARPRequestPacket(this.entry.IPAddress, this.entry.outIface, this.entry.etherPktQ.peek());
				}
				try {
					Thread.sleep(1000);
				} catch(Exception e) {
					System.out.println(e);
				}
				synchronized(this.entry) {
					this.entry.nTry--;
				}
			}
		}
	}

	class TableThreadImpl implements Runnable {
		ARPRequestTable table;

		public TableThreadImpl(ARPRequestTable table) {
			this.table = table;
		}

		public void run() {
			while(true) {
				if(table.ARPRequestTab.size() == 0) {
					try {
						Thread.sleep(1000);
					} catch(Exception e) {
						System.out.println(e);
					}
					continue;
				} else {
					synchronized(this.table) {
					//System.out.println("Scanning ARP Request table");
					Iterator<ARPRequestEntry> iterator = table.ARPRequestTab.iterator();
					while(iterator.hasNext()) {
						ARPRequestEntry entry = iterator.next();
						/* Condition 1 : 3 ARP request sent but no ARP Replies yet */
						/* -> Send ICMP - Destination host not rechable packet for every
						/* 	queued Ethernet Packet in the Incoming interface */
						if(entry.nTry == 0) {
							while(!entry.etherPktQ.isEmpty()) {
								EthernetPktInfo infoNode = entry.etherPktQ.poll();
								IPv4 myPkt = (IPv4)infoNode.pkt.getPayload();
								sendICMPPacket(myPkt, infoNode.inIface, (byte)3, (byte)1);
							}
							iterator.remove();
						}
						/* Condition 2 : ARP reply received for the IP */
						/* -> Forward the packet for the MAC address updated in the entry */
						else if(entry.nTry == -1) {
							while(!entry.etherPktQ.isEmpty()) {
								EthernetPktInfo infoNode = entry.etherPktQ.poll();
								Ethernet ether = infoNode.pkt;
								ether.setDestinationMACAddress(entry.destinationMAC.toString());
								sendPacket(ether, entry.outIface);
							}
							iterator.remove();
						}
					}
					}
				}
			}
		}
	}

	class DVTableThreadImpl implements Runnable {
		DistanceVectorTable table;
		long time;

		public DVTableThreadImpl(DistanceVectorTable table) {
			this.table = table;
			this.time = System.currentTimeMillis();
		}

		public void run() {
			while(true) {
				try {
					Thread.sleep(1000);
				} catch(Exception e) {
					System.out.println(e);
				}

				boolean updated = false;
				/* Time out checking */
				synchronized(this.table) {
						Iterator<DistanceVectorEntry> itr = table.DVTable.iterator();
						updated = false;
						while(itr.hasNext()) {
							DistanceVectorEntry entry = itr.next();
							if(entry.valid == 0) {
								updated = true;
								RouteEntry re = routeTable.lookup(entry.IPAddress);
								routeTable.remove(entry.IPAddress, re.getMaskAddress());
								itr.remove();
							}
						}
				}
				if(updated == true) {
						/* RIP Response due to update */
						sendRIPPacket((byte)2);
				}

				/* Periodic Updates */
				long now = System.currentTimeMillis();
				if((now - this.time) > 10000) {
						/* Broadcast every 10 seconds */
						sendRIPPacket((byte)2);
						this.time = System.currentTimeMillis();
				}
			}
		}
	}

	class DVEntryTOThreadImpl implements Runnable {
		DistanceVectorEntry entry;

		public DVEntryTOThreadImpl(DistanceVectorEntry entry) {
			this.entry = entry;
		}

		public void run() {
			while(true) {
				try {
					Thread.sleep(1000);
				} catch(Exception e) {
					System.out.println(e);
				}
				long now = System.currentTimeMillis();
				synchronized(this.entry) {
				if(this.entry.valid != -1 && (now - this.entry.time) > 30000) {
					this.entry.valid = 0;
					break;
				}
				}
			}
		}
	}
}
