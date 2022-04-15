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
	// ARP table
	private ARPRTable arpTable;

	public static final int BROADCAST = 8;
	public static final int UNICAST = 18;
	
	// Distance Vector Table
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
		this.arpTable = new ARPRTable();
		Tableto obj = new Tableto(this.arpTable);
		Thread t = new Thread(obj);
		t.start();
		this.distanceVectorTable = new DistanceVectorTable();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	class etherwrap {
		Ethernet pkt;
		Iface inIface;
	
		public etherwrap(Ethernet pkt, Iface inIface) {
			this.pkt = pkt;
			this.inIface = inIface;
		}
	}
	
	public class ARPREntry {
		int IPAddress;
		Queue<etherwrap> etherPktl;
		Iface outIface;
		int nTry;
		MACAddress destinationMAC;
	
		public ARPREntry(int IP, Ethernet pkt, Iface outIface, Iface inIface) {
			this.etherPktl = new LinkedList<etherwrap>();
			etherwrap infoNode = new etherwrap(pkt, inIface);
			this.IPAddress = IP;
			this.etherPktl.add(infoNode);
			this.outIface = outIface;
			this.nTry = 3;
			this.destinationMAC = null;
		}
	}

	public class ARPRTable {
		ArrayList<ARPREntry> ARPs;
	
		public ARPRTable() {
			ARPs = new ArrayList<ARPREntry>();
		}
	
		public ARPREntry newARPRequest(int IP, Ethernet pkt, Iface inIface, Iface outIface) {
			synchronized(this.ARPs) {
				ARPREntry entry = new ARPREntry(IP, pkt, outIface, inIface);
				ARPs.add(entry);
				return entry;
			}
		}
	
	}
	
	/** Init Router Table */
	public void initrip()
	{
		System.out.println("Initializing Route Table");
		for(Map.Entry<String, Iface> entry: this.getInterfaces().entrySet()){
			int subnet = entry.getValue().getIpAddress() & entry.getValue().getSubnetMask();
			this.routeTable.insert(subnet, 0, entry.getValue().getSubnetMask(), entry.getValue());
			DistanceVectorEntry e = new DistanceVectorEntry(subnet, 1, -1);
			this.distanceVectorTable.addDVTableEntry(e);
		}

		/* Broadcast DV Info in RIP packets */
		// sendRIPPacket((byte)1);
		sendRIPPacket((byte)1, BROADCAST, 0, (MACAddress)null, null);

		/* DV Table tracking thread - Timeout & periodic updates */
		DVTableto dvThreadObj = new DVTableto(this.distanceVectorTable);
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
				System.out.println("need to send arp reply");
				// this.sendARPReply(etherPacket, arpPacket, inIface);
				return;
			}
			else if(arpPacket.getOpCode() == ARP.OP_REPLY) {
				/* Got ARP Reply */
				IPv4 dummyPkt = new IPv4();
				int arpReplyIPAddress = dummyPkt.toIPv4Address(arpPacket.getSenderProtocolAddress());
				MACAddress destinationMAC = new MACAddress(arpPacket.getSenderHardwareAddress());

				/* Ivalidate Entry in ARP Request Table : Get Sender protocol address from ARP header */
				synchronized(arpTable) {
				for(ARPREntry ARE : arpTable.ARPs) {
					if(ARE.IPAddress == arpReplyIPAddress) {
						ARE.nTry = -1;
						ARE.destinationMAC = destinationMAC;
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
				return;
			}
		}
		else if(etherPacket.getEtherType() != 0x800) {
			return;
		}
		this.handleIpPacket(etherPacket, inIface);
	}

	public void handleIpPacket(Ethernet etherPacket, Iface inIface){
		if(etherPacket.getEtherType() == Ethernet.TYPE_IPv4){
		IPv4 pkt = (IPv4)etherPacket.getPayload();

		int braodcastip = pkt.toIPv4Address("224.0.0.9");
		/* Checking if the recived packet is RIP Request/Response */
		if(pkt.getProtocol() == IPv4.PROTOCOL_UDP) {
			UDP udp = (UDP)pkt.getPayload();
			if(udp.getDestinationPort() == UDP.RIP_PORT) {
				if(pkt.getDestinationAddress() == braodcastip) {

					boolean find = false;
					boolean updated = false;
					RIPv2 rip = (RIPv2)udp.getPayload();

					// check if any existing entry in dv table and update distance
					synchronized(this.distanceVectorTable) {
					for(RIPv2Entry ripEntry : rip.getEntries()) {
						find = false;
						for(DistanceVectorEntry dvEntry : distanceVectorTable.DVTable) {
							synchronized(dvEntry) {
							if(dvEntry.IPAddress == ripEntry.getAddress()) {
								dvEntry.updateTime();
								find = true;
								if(dvEntry.distance > (ripEntry.getMetric() + 1)) {
									updated = true;
									dvEntry.distance = ripEntry.getMetric() + 1;
									routeTable.update(dvEntry.IPAddress, ripEntry.getSubnetMask(), pkt.getSourceAddress(), inIface);
								} else {
								}
							}
							}
						}
						if(find == false) {
							updated = true;
							DistanceVectorEntry newDVEntry = new DistanceVectorEntry(ripEntry.getAddress(), ripEntry.getMetric()+1, 1);
							distanceVectorTable.addDVTableEntry(newDVEntry);
							DVEntryto TOThreadObj = new DVEntryto(newDVEntry);
							Thread TOThread = new Thread(TOThreadObj);
							TOThread.start();
							routeTable.insert(ripEntry.getAddress(), pkt.getSourceAddress(), ripEntry.getSubnetMask(), inIface);
						}
					}
					}
					if(updated == true) {
						// sendRIPPacket((byte)2);
						sendRIPPacket((byte)2, BROADCAST, 0, (MACAddress)null, null);
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
							boolean match = false, updated = false;
							RIPv2 ripPkt = (RIPv2)udp.getPayload();

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
										}
									}
								}
								if(match == false) {
									updated = true;
									DistanceVectorEntry newDVEntry = new DistanceVectorEntry(ripEntry.getAddress(), ripEntry.getMetric()+1, 1);
									distanceVectorTable.addDVTableEntry(newDVEntry);
									DVEntryto TOThreadObj = new DVEntryto(newDVEntry);
									Thread TOThread = new Thread(TOThreadObj);
									TOThread.start();
									routeTable.insert(ripEntry.getAddress(), pkt.getSourceAddress(), ripEntry.getSubnetMask(), inIface);
								}
							}
							}
							if(updated == true) {
								sendRIPPacket((byte)2, BROADCAST, 0, (MACAddress)null, null);
							}
							return;
					}
					
				}
				
			} else {}
		} else {}

		// Verify checksum
		short actualCheckSum = pkt.getChecksum();
		pkt.resetChecksum();
		pkt.serialize();
		short currentChecksum = pkt.getChecksum();
		if(actualCheckSum != currentChecksum) {
			return;
		}

		// Check TTL
		byte currentTTL = pkt.getTtl();
		currentTTL--;
		if(currentTTL == 0) {
			System.out.println("time out");
			this.sendICMPPacket(pkt, inIface, (byte)11, (byte)0, false);
			return;
		}

		// Reset checksum now that TTL is decremented
		pkt.setTtl(currentTTL);
		pkt.resetChecksum();
		pkt.serialize();

		// Check if packet is destined for one of router's interfaces
		for(Map.Entry<String, Iface> entry: interfaces.entrySet()){
			if(pkt.getDestinationAddress() == entry.getValue().getIpAddress()){
				byte protocolType = pkt.getProtocol();
				if(protocolType == IPv4.PROTOCOL_TCP || protocolType == IPv4.PROTOCOL_UDP){
					this.sendICMPPacket(pkt, inIface, (byte)3, (byte)3, false);
				}
				else if(protocolType == IPv4.PROTOCOL_ICMP) {
					ICMP icmpPkt = (ICMP)pkt.getPayload();
					if(icmpPkt.getIcmpType() == (byte)8) {
						this.sendEchoReply(pkt, inIface, (byte)0, (byte)0);
					}
				}
				return;
			}
		}

		// RouteEntry rEntry = routeTable.lookup(pkt.getDestinationAddress());
		// if(rEntry == null) {
		// 	System.out.println("Destination Net Unreachable");
		// 	this.sendICMPPacket(pkt, inIface, (byte)3, (byte)0, false);
		// 	return;
		// }

		// /* CHECK 5 : Check if incoming and outgoing interfaces are same */
		// if(inIface.getName().equals(rEntry.getInterface().getName())){
		// 	return;
		// }

		// MACAddress sourceMac = rEntry.getInterface().getMacAddress();
		// etherPacket.setSourceMACAddress(sourceMac.toString());

		// int nextHopIPAddress = rEntry.getGatewayAddress();
		// if(nextHopIPAddress == 0){
		// 	nextHopIPAddress = pkt.getDestinationAddress();
		// }

		// /* CHECK 6 : Checking non-existent Host in any network connected to Router */
		// ArpEntry ae = arpCache.lookup(nextHopIPAddress);
		// if(ae == null) {
		// 	this.arpupdate(etherPacket, inIface, rEntry.getInterface(), nextHopIPAddress);
		// 	//this.sendICMPPacket(pkt, inIface, (byte)3, (byte)1);
		// 	return;
		// }

		// MACAddress destinationMac = ae.getMac();
		// etherPacket.setDestinationMACAddress(destinationMac.toString());
		
		// sendPacket(etherPacket, rEntry.getInterface());
		this.forwardIpPacket(etherPacket, inIface);
	}}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry 
		// RouteEntry bestMatch = this.routeTable.lookup(dstAddr);
		RouteEntry bestMatch = routeTable.lookup(dstAddr);
		if(bestMatch == null) {
			System.out.println("Destination Net Unreachable");
			this.sendICMPPacket(ipPacket, inIface, (byte)3, (byte)0, false);
			return;
		}

		// // If no entry matched, do nothing
		// if (null == bestMatch)
		// { return; }

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		// if (outIface == inIface)
		// { return; }
		if(inIface.getName().equals(bestMatch.getInterface().getName())){
				return;
			}

		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{ 
			this.arpupdate(etherPacket, inIface, bestMatch.getInterface(), nextHop);
			return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}

	public void sendICMPPacket(IPv4 pkt, Iface inIface, byte type, byte code, boolean echo) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		// prepare icmp header
		icmp.setIcmpType(type);
		icmp.setIcmpCode(code);

		if(echo == false){
			int len = 4 + (pkt.getHeaderLength() * 4) + 8;
			byte[] inData = new byte[len];
			Arrays.fill(inData, 0, 4, (byte)0);
			byte[] ipheaderpay = pkt.serialize();
			// for(int i = 0; i < len; i++){
			// 	inData[i+4] = ipheaderpay[i];
			// }
			int i, j, k;
			for(i = 0, j = 4; i < (pkt.getHeaderLength() * 4); i++, j++) {
				inData[j] = ipheaderpay[i];
			}
			k = i;
			while(k < (i + 8)) {
				inData[j] = ipheaderpay[k];
				j++;
				k++;
			}
			data.setData(inData);
		}
		else{
			ICMP icmpp = (ICMP)pkt.getPayload();
			byte[] icmpheaderpay = icmpp.getPayload().serialize();
			data.setData(icmpheaderpay);
		}

		// prepare ipv4 header
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		if(echo == false){
			ip.setSourceAddress(inIface.getIpAddress());
		}
		else{
			ip.setSourceAddress(pkt.getDestinationAddress());
		}
		ip.setDestinationAddress(pkt.getSourceAddress());

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		// prepare ether header
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toString());
		MACAddress destMAC = findmac(pkt.getSourceAddress());
		if(destMAC == null) {
			RouteEntry rEntry = routeTable.lookup(pkt.getSourceAddress());
			int nextHopIPAddress = rEntry.getGatewayAddress();
			if(nextHopIPAddress == 0){
				nextHopIPAddress = pkt.getSourceAddress();
			}
			if(echo == false){
				this.arpupdate(ether, inIface, rEntry.getInterface(), nextHopIPAddress);
			}
			else{
				this.arpupdate(ether, inIface, inIface, nextHopIPAddress);
			}
			return;
		}
		ether.setDestinationMACAddress(destMAC.toString());

		/* Send ICMP packet */
		sendPacket(ether, inIface);
	}

	public void sendEchoReply(IPv4 pktIn, Iface inIface, byte type, byte code) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		// prepare icmp header
		icmp.setIcmpType(type);
		icmp.setIcmpCode(code);

		// prepare payload
		ICMP inIcmpPkt = (ICMP)pktIn.getPayload();
		byte[] inIcmpPktPayload = inIcmpPkt.getPayload().serialize();
		data.setData(inIcmpPktPayload);

		// prepare ipv4 header
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setSourceAddress(pktIn.getDestinationAddress());
		ip.setDestinationAddress(pktIn.getSourceAddress());

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		// prepare ether header
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toString());
		MACAddress destMAC = findmac(pktIn.getSourceAddress());
		if(destMAC == null) {
			RouteEntry rEntry = routeTable.lookup(pktIn.getSourceAddress());
			int nextHopIPAddress = rEntry.getGatewayAddress();
			if(nextHopIPAddress == 0){
				nextHopIPAddress = pktIn.getSourceAddress();
			}
			this.arpupdate(ether, inIface, inIface, nextHopIPAddress);
			return;
		}
		ether.setDestinationMACAddress(destMAC.toString());

		sendPacket(ether, inIface);
	}

	// find mac address when lookup in router table then arpcache table
	public MACAddress findmac(int ip) {

		RouteEntry re = routeTable.lookup(ip);
		if(re == null) {
			return null;
		}
		int nextHopIPAddress = re.getGatewayAddress();
		if(nextHopIPAddress == 0){
			nextHopIPAddress = ip;
		}
		ArpEntry ae = arpCache.lookup(nextHopIPAddress);
		if(ae == null) {
			return null;
		}
		return ae.getMac();
	}


	// update arp table
	public void arpupdate(Ethernet etherPacket, Iface inIface, Iface outIface, int IP) {
		ARPREntry entry;
		synchronized(arpTable) {
		for(ARPREntry ARE : arpTable.ARPs) {
			if(ARE.IPAddress == IP) {
				etherwrap i = new etherwrap(etherPacket, inIface);
				ARE.etherPktl.add(i);
				return;
			}
		}

		entry = arpTable.newARPRequest(IP, etherPacket, inIface, outIface);
		}
		Entryto timeout = new Entryto(entry);
		Thread t = new Thread(timeout);
		t.start();
	}

	public void sendRIPPacket(byte command, int type, int sourceIPAddress, MACAddress sourceMACAddress, Iface inIface) {
		switch(type){
			case BROADCAST:
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
				UDP udp = new UDP();
				udp.setSourcePort(UDP.RIP_PORT);
				udp.setDestinationPort(UDP.RIP_PORT);
				udp.setPayload(ripPkt);
		
				for(Map.Entry<String, Iface> entry: this.getInterfaces().entrySet()){
						IPv4 ipPkt = new IPv4();
						ipPkt.setProtocol(IPv4.PROTOCOL_UDP);
						ipPkt.setTtl((byte)15);
						ipPkt.setDestinationAddress("224.0.0.9");
						ipPkt.setSourceAddress(entry.getValue().getIpAddress());
						ipPkt.setPayload(udp);
		
						Ethernet ether = new Ethernet();
						ether.setEtherType(Ethernet.TYPE_IPv4);
						ether.setPayload(ipPkt);
						ether.setSourceMACAddress(entry.getValue().getMacAddress().toString());
						ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");

						sendPacket(ether, entry.getValue());
				}
				break;
			case UNICAST:
				RIPv2 rip = new RIPv2();
				for(DistanceVectorEntry dvEntry: this.distanceVectorTable.DVTable) {
					RouteEntry re = this.routeTable.lookup(dvEntry.IPAddress);
					RIPv2Entry ripEntry = new RIPv2Entry(re.getDestinationAddress(), re.getMaskAddress(), dvEntry.distance);
					rip.addEntry(ripEntry);
				}
				rip.setCommand(command);
		
				/* UDP Packet */
				UDP udpPkt = new UDP();
				udpPkt.setSourcePort(UDP.RIP_PORT);
				udpPkt.setDestinationPort(UDP.RIP_PORT);
				udpPkt.setPayload(rip);
		
				IPv4 ipPkt = new IPv4();
				ipPkt.setProtocol(IPv4.PROTOCOL_UDP);
				ipPkt.setTtl((byte)64);
				ipPkt.setDestinationAddress(sourceIPAddress);
				ipPkt.setSourceAddress(inIface.getIpAddress());
				ipPkt.setPayload(udpPkt);
		
				Ethernet ether = new Ethernet();
				ether.setEtherType(Ethernet.TYPE_IPv4);
				ether.setPayload(ipPkt);
				ether.setSourceMACAddress(inIface.getMacAddress().toString());
				ether.setDestinationMACAddress(sourceMACAddress.toString());
		
				sendPacket(ether, inIface);
				break;
			}
		}

	public void sendRIPPacketUni(byte command, int sourceIPAddress, MACAddress sourceMACAddress, Iface inIface) {
		RIPv2 ripPkt = new RIPv2();
		for(DistanceVectorEntry dvEntry: this.distanceVectorTable.DVTable) {
			RouteEntry re = this.routeTable.lookup(dvEntry.IPAddress);
			RIPv2Entry ripEntry = new RIPv2Entry(re.getDestinationAddress(), re.getMaskAddress(), dvEntry.distance);
			ripPkt.addEntry(ripEntry);
		}
		ripPkt.setCommand(command);

		UDP udpPkt = new UDP();
		udpPkt.setSourcePort(UDP.RIP_PORT);
		udpPkt.setDestinationPort(UDP.RIP_PORT);
		udpPkt.setPayload(ripPkt);

		IPv4 ipPkt = new IPv4();
		ipPkt.setProtocol(IPv4.PROTOCOL_UDP);
		ipPkt.setTtl((byte)64);
		ipPkt.setDestinationAddress(sourceIPAddress);
		ipPkt.setSourceAddress(inIface.getIpAddress());
		ipPkt.setPayload(udpPkt);

		Ethernet ether = new Ethernet();
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setPayload(ipPkt);
		ether.setSourceMACAddress(inIface.getMacAddress().toString());
		ether.setDestinationMACAddress(sourceMACAddress.toString());

		sendPacket(ether, inIface);
}

	// arp entry renew
	class Entryto implements Runnable {
		ARPREntry entry;

		public Entryto(ARPREntry entry) {
			this.entry = entry;
		}

		public void run() {
			while(true) {
				if(this.entry.nTry <= 0)
					break;

				synchronized(this.entry) {
					ARPRupdate(this.entry.IPAddress, this.entry.outIface, this.entry.etherPktl.peek());
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

	public void ARPRupdate(int IPAddress, Iface outIface, etherwrap p1) {
		Ethernet ether = new Ethernet();
		ARP pkt = new ARP();

		ether.setEtherType(Ethernet.TYPE_ARP);
		ether.setSourceMACAddress(p1.inIface.getMacAddress().toString());
		ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");

		pkt.setHardwareType(ARP.HW_TYPE_ETHERNET);
		pkt.setProtocolType(ARP.PROTO_TYPE_IP);
		pkt.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
		pkt.setProtocolAddressLength((byte)4);
		pkt.setOpCode(ARP.OP_REQUEST);
		pkt.setSenderHardwareAddress(p1.inIface.getMacAddress().toBytes());
		pkt.setSenderProtocolAddress(p1.inIface.getIpAddress());
		byte val[] = new byte[6];
		pkt.setTargetHardwareAddress(val);
		pkt.setTargetProtocolAddress(IPAddress);

		ether.setPayload(pkt);

		sendPacket(ether, outIface);
	}

	class Tableto implements Runnable {
		ARPRTable table;

		public Tableto(ARPRTable table) {
			this.table = table;
		}

		public void run() {
			while(true) {
				if(table.ARPs.size() == 0) {
					try {
						Thread.sleep(1000);
					} catch(Exception e) {
						System.out.println(e);
					}
					continue;
				} else {
					synchronized(this.table) {
					Iterator<ARPREntry> iterator = table.ARPs.iterator();
					while(iterator.hasNext()) {
						ARPREntry entry = iterator.next();
						if(entry.nTry == 0) {
							while(!entry.etherPktl.isEmpty()) {
								etherwrap infoNode = entry.etherPktl.poll();
								IPv4 myPkt = (IPv4)infoNode.pkt.getPayload();
								System.out.println("destination host not reachable");
								sendICMPPacket(myPkt, infoNode.inIface, (byte)3, (byte)1, false);
							}
							iterator.remove();
						}
						else if(entry.nTry == -1) {
							while(!entry.etherPktl.isEmpty()) {
								etherwrap infoNode = entry.etherPktl.poll();
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

	class DVTableto implements Runnable {
		DistanceVectorTable table;
		long time;

		public DVTableto(DistanceVectorTable table) {
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
						// sendRIPPacket((byte)2);
						sendRIPPacket((byte)2, BROADCAST, 0, (MACAddress)null, null);
				}

				/* Periodic Updates */
				long now = System.currentTimeMillis();
				if((now - this.time) > 10000) {
						// broadcast every 10 seconds
						// sendRIPPacket((byte)2);
						sendRIPPacket((byte)2, BROADCAST, 0, (MACAddress)null, null);
						this.time = System.currentTimeMillis();
				}
			}
		}
	}

	class DVEntryto implements Runnable {
		DistanceVectorEntry entry;

		public DVEntryto(DistanceVectorEntry entry) {
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
