package edu.wisc.cs.sdn.vnet.rt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import edu.wisc.cs.sdn.vnet.rt.Router.DVEntry;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.Data;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.distanceVectorTable = new DVtable();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

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

	public void initRouterTable()
	{
		for(Map.Entry<String, Iface> entry: this.getInterfaces().entrySet()){
			int subnet = entry.getValue().getIpAddress() & entry.getValue().getSubnetMask();
			this.routeTable.insert(subnet, 0, entry.getValue().getSubnetMask(), entry.getValue());
			DVEntry de = new DVEntry(subnet, 1, -1);
			this.distanceVectorTable.addtoDVs(de);
		}

		/* Broadcast DV Info in RIP packets */
		sendRip((byte)1);

		/* DV Table tracking thread - Timeout & periodic updates */
		DVTableTO dvTTO = new DVTableTO(this.distanceVectorTable);
		Thread dvThread = new Thread(dvTTO);
		dvThread.start();
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
		/* TODO: Handle packets                                             */

		// switch(etherPacket.getEtherType())
		// {
		// case Ethernet.TYPE_IPv4:
		
		IPv4 ip = (IPv4)etherPacket.getPayload();
		if (IPv4.toIPv4Address("224.0.0.9") == ip.getDestinationAddress())
		{
			if (IPv4.PROTOCOL_UDP == ip.getProtocol()) 
			{
				UDP udp = (UDP)ip.getPayload();
				if (UDP.RIP_PORT == udp.getDestinationPort())
				{ 
					RIPv2 rip = (RIPv2)udp.getPayload();
					this.handleRipPacket(rip, ip, inIface, false, false);
				}
				else{
					boolean routerip = false;
					for(Map.Entry<String, Iface> entry: interfaces.entrySet()){
						if(ip.getDestinationAddress() == entry.getValue().getIpAddress()){
							routerip = true;
							break;
						}
					}
					if(routerip == true){
						boolean match = false;
						boolean updatedDistance = false;
						RIPv2 rip = (RIPv2)udp.getPayload();
						synchronized(this.distanceVectorTable){
							for (RIPv2Entry entry: rip.getEntries()){
								match = false;
								for(DVEntry dv: distanceVectorTable.DVs){
										if(dv.addr == entry.getAddress()){
											dv.timestamp = System.currentTimeMillis();
											match = true;
											if(dv.metric > (entry.getMetric() + 1)){
												updatedDistance = true;
												dv.metric = entry.getMetric() + 1;
												routeTable.update(dv.addr, entry.getSubnetMask(), ip.getSourceAddress(), inIface);
											}
										}
									}
								if(match == false){
									updatedDistance = true;
									DVEntry newDVEntry = new DVEntry(entry.getAddress(), entry.getMetric()+1, 1);
									distanceVectorTable.addtoDVs(newDVEntry);
									DVEntryTO TO = new DVEntryTO(newDVEntry);
									Thread TOThread = new Thread(TO);
									TOThread.start();
									routeTable.insert(entry.getAddress(), ip.getSourceAddress(), entry.getSubnetMask(), inIface);
								}
							}
						}
						if(updatedDistance == true){
							sendRip((byte)2);
						}
						return;
					}
				}
			}
		}

		this.handleIpPacket(etherPacket, inIface);
		// Ignore all other packet types, for now
		}

		/********************************************************************/
	

	private void ICMPMessage(IPv4 pkt, Iface inIface, byte type, byte code, boolean Echo){
		// IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		// prepare ether packet header
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		// prepare ICMP header
		icmp.setIcmpType(type); // try no byte wrapper
		icmp.setIcmpCode(code);

		// prepare icmp data
		byte[] inData;
		if (Echo == false){
			// ip.setSourceAddress(pkt.getSourceAddress()); // set source ip
			System.out.println("Handle icmp data");
			// ip.setSourceAddress(inIface.getIpAddress());
			byte[] oriIpHeaderPayload = pkt.serialize();
			System.out.println(oriIpHeaderPayload);
			int i, j, k;
			int ipHeaderLen = pkt.getHeaderLength()*4;
			inData = new byte[4 + ipHeaderLen + 8];
			Arrays.fill(inData, 0, 4, (byte)0);
			for(i = 0, j = 4; i < ipHeaderLen; i ++, j++){
				inData[j] = oriIpHeaderPayload[i]; 
			}
			k = i;
			while(k < (i + 8)){
				inData[j] = oriIpHeaderPayload[k];
				j++;
				k++;
			}
		}
		else{
			// ip.setSourceAddress(pkt.getDestinationAddress());
			inData = ((ICMP)pkt.getPayload()).getPayload().serialize();
		}
		data.setData(inData);

		System.out.println("Handle ip v4 header");
		ip.setTtl((byte) 64); // set ttl
		ip.setProtocol((byte) IPv4.PROTOCOL_ICMP); // set protocol
		ip.setSourceAddress(inIface.getIpAddress());
		ip.setDestinationAddress(pkt.getSourceAddress()); // set destination ip
		// System.out.println("Handle ip packet header");

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		System.out.println("ether header");
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toString());

		// int srcAddr = pkt.getSourceAddress(); // get destionation ip address
		// RouteEntry bestMatch = this.routeTable.lookup(srcAddr); // Find matching route table entry 
		// if (bestMatch == null){
		// 	System.out.println("No matched route entry found");
		// 	return;
		// }
		// // int nextHop = bestMatch.getInterface().getIpAddress();
		// int nextHop = bestMatch.getGatewayAddress();
		// if(nextHop == 0){
		// 	nextHop = srcAddr;
		// }
		// ArpEntry nextHopMac = arpCache.lookup(nextHop);
		// if(nextHopMac == null){
		// 	return;
		// }
		// MACAddress nextHopMacAddress = nextHopMac.getMac();
		// // ether.setSourceMACAddress(bestMatch.getInterface().getMacAddress().toBytes()); // update source MACaddress
		// if(nextHopMacAddress == null){
		// 	RouteEntry routeEntry = routeTable.lookup(pkt.getSourceAddress());
		// 	int nextHopIP = routeEntry.getGatewayAddress();
		// 	if(nextHopIP == 0){
		// 		nextHopIP = pkt.getSourceAddress();
		// 	}
		// 	// this.sendARPRequest(ether, inIface, inIface, nextHopIP);
		// 	System.out.println("arp request?");
		// 	return;
		// }
		MACAddress destMAC = findMACFromRTLookUp(pkt.getSourceAddress());
		if(destMAC == null) {
			RouteEntry rEntry = routeTable.lookup(pkt.getSourceAddress());
			/* Find the next hop IP Address */
			int nextHopIPAddress = rEntry.getGatewayAddress();
			if(nextHopIPAddress == 0){
				nextHopIPAddress = pkt.getSourceAddress();
			}
			System.out.println("no next hop");
			// this.sendARPRequest(ether, inIface, rEntry.getInterface(), nextHopIPAddress);
			// return;
		}
		ether.setDestinationMACAddress(destMAC.toString()); // update destination MACaddress
		// System.out.println("Handle ether packet header");
		System.out.println("Combined");

		this.sendPacket(ether, inIface);
		System.out.println("sent");
	}

	public MACAddress findMACFromRTLookUp(int ip) {

		RouteEntry rEntry = routeTable.lookup(ip);
		System.out.println("res in routtable");
		System.out.println(rEntry.toString());
		if(rEntry == null) {
			/* No matching route table entry */
			System.out.println("route table null");
			return null;
		}
		/* Find the next hop IP Address */
		int nextHopIPAddress = rEntry.getGatewayAddress();
		System.out.println("nexthop is:");
		System.out.println(nextHopIPAddress);
		if(nextHopIPAddress == 0){
			System.out.println("next hop null");
			nextHopIPAddress = ip;
		}
		/* Find the next hop MAC address from ARP Cache */
		ArpEntry ae = arpCache.lookup(nextHopIPAddress);
		System.out.println("nexthop arp  mac is:");
		System.out.println(ae.toString());
		if(ae == null) {
			/* No such host in the network - Dropping */
			System.out.println("arp null");
			return null;
		}
		/* Next hop MAC addresses */
		return ae.getMac();
	}


	// public void sendARPRequest(Ethernet etherPacket, Iface inIface, Iface outIface, int IP) {
	// 	ARPRequestEntry entry;
	// 	synchronized(arpReqTable) {
	// 	for(ARPRequestEntry ARE : arpReqTable.ARPRequestTab) {
	// 		if(ARE.IPAddress == IP) {
	// 			ARE.addPacketQueue(etherPacket, outIface, inIface);
	// 			return;
	// 		}
	// 	}

	// 	entry = arpReqTable.newARPRequest(IP, etherPacket, inIface, outIface);
	// 	}
	// 	EntryThreadImpl obj = new EntryThreadImpl(entry);
	// 	Thread t = new Thread(obj);
	// 	t.start();
	// }

	// class EthernetPkt{
	// 	Ethernet pkt;
	// 	Iface infIface;

	// 	public EthernetPkt(Ethernet etherPacket, Iface inIface){
	// 		this.pkt = etherPacket;
	// 		this.infIface = inIface;
	// 	}
	// }

	// public class ArpRequest{
	// 	int ip;
	// 	Queue<EthernetPkt> etherPkt;
	// 	Iface outIface;

	// }

	// public void sendARPRequest(Ethernet etherPacket, Iface inIface, Iface outIface, int ip){
	// 	ARPRequestEntry entry;
	// 	synchronized(arpReqTable) {
	// 	for(ARPRequestEntry ARE : arpReqTable.ARPRequestTab) {
	// 		if(ARE.IPAddress == IP) {
	// 			ARE.addPacketQueue(etherPacket, outIface, inIface);
	// 			return;
	// 		}
	// 	}

	// 	entry = arpReqTable.newARPRequest(IP, etherPacket, inIface, outIface);
	// 	}
	// 	EntryThreadImpl obj = new EntryThreadImpl(entry);
	// 	Thread t = new Thread(obj);
	// 	t.start();
	// }

	// private void echoReply(Ethernet etherPacket, Iface inIface){
	// 	IPv4 ipPacket = (IPv4)etherPacket.getPayload();
	// 	byte[] serialized = ipPacket.serialize(); // TODO: original icmp header??
	// 	// prepare ether packet header
	// 	Ethernet ether = new Ethernet();
	// 	ether.setEtherType(Ethernet.TYPE_IPv4);
	// 	int dstAddr = ipPacket.getDestinationAddress(); // get destionation ip address
	// 	boolean exit = true;
	// 	for (Iface iface: this.interfaces.values()){
	// 		if (iface.getIpAddress() == dstAddr){
	// 			exit = false;
	// 		}
	// 	}
	// 	if (exit == true){
	// 		return;
	// 	}
		
	// 	RouteEntry bestMatch = this.routeTable.lookup(dstAddr); // Find matching route table entry 
	// 	ether.setSourceMACAddress(bestMatch.getInterface().getMacAddress().toBytes()); // update source MACaddress
	// 	ether.setDestinationMACAddress(this.arpCache.lookup(bestMatch.getInterface().getIpAddress()).getMac().toBytes()); // update destination MACaddress

	// 	// prepare IP header
	// 	IPv4 ip = new IPv4();
	// 	ip.setProtocol((byte) IPv4.PROTOCOL_ICMP); // set protocol
	// 	ip.setSourceAddress(ipPacket.getDestinationAddress()); // set source ip
	// 	ip.setDestinationAddress(ipPacket.getSourceAddress()); // set destination ip
		
	// 	Data data = new Data();
	// 	ICMP icmp = new ICMP();

	// 	// prepare ICMP header
	// 	icmp.setIcmpType((byte) 0); // try no byte wrapper
	// 	icmp.setIcmpCode((byte) 0);

	// 	// link the header together
	// 	ByteBuffer temp = ByteBuffer.allocate(32);
	// 	temp.put(ether.serialize());
	// 	System.out.println("Ether header size is: " + ether.serialize().length); // double check the header size
	// 	temp.put(ip.serialize());
	// 	System.out.println("IPv4 header size is: " + ip.serialize().length); // double check the header size
	// 	temp.put(icmp.serialize());
	// 	System.out.println("ICMP header size is: " + icmp.serialize().length); // double check the header size
	// 	temp.put(serialized);
	// 	System.out.println("Original IPv4 header size is: " + icmp.serialize().length); // double check the header size
		
	// 	data.setData(temp.array());
	// 	icmp.setPayload(data);
	// 	ip.setPayload(icmp);
	// 	ether.setPayload(ip);

	// 	this.sendPacket(ether, bestMatch.getInterface());
	// }
	
	private final int rip_request = 1;
	private final int rip_unsol = 2;
	private final int rip_response = 3;

	public class DVEntry
	{
		int addr, mask, metric, valid;
		long timestamp;
		public DVEntry(int addr, int metric, int valid) {
			this.addr = addr;
			// this.mask = mask;
			this.metric = metric;
			this.timestamp = System.currentTimeMillis();
			this.valid = valid;
		}
	}



	// private HashMap<Integer, RipEntry> ripDict;
	public class DVtable
	{
		List<DVEntry> DVs;

		public DVtable(){
			this.DVs = new ArrayList<DVEntry>();
		}

		public void addtoDVs(DVEntry de){
			DVs.add(de);
		}

		public boolean updateDVtable(DVEntry de){
			for(DVEntry dve: DVs){
				if(dve.addr == de.addr){
					if(de.metric < dve.metric){
						dve.metric = de.metric;
						return true;
					}
					else{
						return false;
					}
				}
			}
			DVs.add(de)	;
			return true;
		}
	}

	private DVtable distanceVectorTable;

	// public void rip(){
	// 	for (Iface iface : this.interfaces.values())
	// 	{
	// 		int mask = iface.getSubnetMask();
	// 		int addr = mask & iface.getIpAddress();
	// 		ripDict.put(addr, new RipEntry(addr, mask, 0, -1));
	// 		this.routeTable.insert(iface.getIpAddress(), mask, 0, iface);
	// 		sendRip(rip_request, null, iface);
	// 	}

	// 	// send unsolicited RIP response every 10 seconds
	// 	TimerTask unsol = new TimerTask()
	// 	{
	// 		public void run()
	// 		{
	// 			for (Iface iface: interfaces.values())
	// 			{ sendRip(rip_unsol, null, iface); }
	// 		}
	// 	};

	// 	// timeout route table
	// 	TimerTask clean = new TimerTask()
	// 	{
	// 		public void run()
	// 		{
	// 			for (RipEntry entry : ripDict.values()) {
	// 				if (entry.timestamp != -1 && System.currentTimeMillis() - entry.timestamp >= 30000)
	// 				{	
	// 					ripDict.remove(entry.addr);
	// 					// whether directly connected with router
	// 					boolean move = true;
	// 					for (Iface inface: interfaces.values()){
	// 						if ((inface.getIpAddress() & inface.getSubnetMask()) == entry.addr){
	// 							move = false;
	// 						}
	// 					}
	// 					if (move == true){
	// 						routeTable.remove(entry.addr, entry.mask);
	// 					}
	// 				}
	// 			}
	// 		}
	// 	};

	// 	Timer timer = new Timer(true);
	// 	timer.schedule(unsol, 0, 10000);
	// 	timer.schedule(clean, 0, 1000);

	// }

	private void sendRip(byte type){

		// // prepare ether header
		// Ethernet ether = new Ethernet();
		// ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		// ether.setEtherType(Ethernet.TYPE_IPv4);
		// switch(type){
		// 	case rip_request:
		// 		ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
		// 	case rip_unsol:
		// 		ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
		// 	case rip_response:
		// 		ether.setDestinationMACAddress(ether.getSourceMACAddress());
		// }

		// // prepare ip header
		// IPv4 ip = new IPv4();
		// ip.setTtl((byte)64);
		// ip.setProtocol(IPv4.PROTOCOL_UDP);
		// ip.setSourceAddress(inIface.getIpAddress());
		// switch(type){
		// 	case rip_request:
		// 		ip.setDestinationAddress(IPv4.toIPv4Address("224.0.0.9"));
		// 	case rip_unsol:
		// 		ip.setDestinationAddress(IPv4.toIPv4Address("224.0.0.9"));
		// 	case rip_response:
		// 		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		// 		ip.setDestinationAddress(ipPacket.getSourceAddress());
		// }
		// switch(type){
		// 	case rip_request:
		// 		rip.setCommand(RIPv2.COMMAND_REQUEST);
		// 	case rip_unsol:
		// 		rip.setCommand(RIPv2.COMMAND_RESPONSE);
		// 	// case rip_response:
		// 	// 	rip.setCommand(RIPv2.COMMAND_RESPONSE);
		// }


		// ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		// ether.setEtherType(Ethernet.TYPE_IPv4);

		// ip.setTtl((byte)64);
		// ip.setProtocol(IPv4.PROTOCOL_UDP);
		// ip.setSourceAddress(inIface.getIpAddress());

		// udp.setSourcePort(UDP.RIP_PORT);
		// udp.setDestinationPort(UDP.RIP_PORT);

		// prepare ether packet payload
		// List<RIPv2Entry> entries = new ArrayList<RIPv2Entry>();
		// prepare rip
		RIPv2 rip = new RIPv2();
		synchronized(this.distanceVectorTable)
		{
			for (DVEntry entry: this.distanceVectorTable.DVs)
			{
				RouteEntry re = this.routeTable.lookup(entry.addr);
				RIPv2Entry new_entry = new RIPv2Entry(re.getDestinationAddress(), re.getMaskAddress(), entry.metric);
				// RIPv2Entry new_entry = new RIPv2Entry(entry.addr, entry.mask, entry.metric);
				// entries.add(new_entry);
				rip.addEntry(new_entry);
			}
		}
		rip.setCommand(type);
		// prepare udp header
		UDP udp = new UDP();
		udp.setSourcePort(UDP.RIP_PORT);
		udp.setDestinationPort(UDP.RIP_PORT);
		udp.setPayload(rip);

		for(Map.Entry<String, Iface> entry: this.getInterfaces().entrySet()){
			// prepare ip header
			IPv4 ip = new IPv4();
			ip.setTtl((byte)15); //TODO:64/15??
			ip.setProtocol(IPv4.PROTOCOL_UDP);
			ip.setSourceAddress(entry.getValue().getIpAddress());
			// switch(type){
			// 	case rip_request:
			ip.setDestinationAddress("224.0.0.9");
			// 	case rip_unsol:
			// 		ip.setDestinationAddress("224.0.0.9");
			// 	// case rip_response:
			// 	// 	IPv4 ipPacket = (IPv4)etherPacket.getPayload();
			// 	// 	ip.setDestinationAddress(ipPacket.getSourceAddress());
			// }
			ip.setPayload(udp);

			// prepare ether header
			Ethernet ether = new Ethernet();
			ether.setSourceMACAddress(entry.getValue().getMacAddress().toString());
			ether.setEtherType(Ethernet.TYPE_IPv4);
			ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
			ether.setPayload(ip);

			this.sendPacket(ether, entry.getValue());

		}
		// ether.setPayload(ip);
		// ip.setPayload(udp);
		// udp.setPayload(rip);
		// rip.setEntries(entries);

		// this.sendPacket(ether, inIface);
	}

	public void sendRipUni(byte type, int sourceip, MACAddress srcmac, Iface inIface){
		RIPv2 rip = new RIPv2();
		for (DVEntry entry: this.distanceVectorTable.DVs)
			{
				RouteEntry re = this.routeTable.lookup(entry.addr);
				RIPv2Entry new_entry = new RIPv2Entry(re.getDestinationAddress(), re.getMaskAddress(), entry.metric);
				// RIPv2Entry new_entry = new RIPv2Entry(entry.addr, entry.mask, entry.metric);
				// entries.add(new_entry);
				rip.addEntry(new_entry);
			}
		rip.setCommand(type);

		// prepare udp header
		UDP udp = new UDP();
		udp.setSourcePort(UDP.RIP_PORT);
		udp.setDestinationPort(UDP.RIP_PORT);
		udp.setPayload(rip);

		// prepare ip header
		IPv4 ip = new IPv4();
		ip.setTtl((byte)64); //TODO:64/15??
		ip.setProtocol(IPv4.PROTOCOL_UDP);
		ip.setSourceAddress(inIface.getIpAddress());
		ip.setDestinationAddress(sourceip);
		ip.setPayload(udp);

		// prepare ether header
		Ethernet ether = new Ethernet();
		ether.setSourceMACAddress(inIface.getMacAddress().toString());
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setDestinationMACAddress(srcmac.toString());
		ether.setPayload(ip);

		this.sendPacket(ether, inIface);

		}

	// private void handleRipPacket(byte type, Ethernet etherPacket, Iface inIface){
		// switch(type){
		// 	case RIPv2.COMMAND_REQUEST:
		// 		sendRip(rip_response, etherPacket, inIface);
		// 		break;
		// 	case RIPv2.COMMAND_RESPONSE:
		// 		// update the route table and DV when command rip response
		// 		IPv4 ip = (IPv4)etherPacket.getPayload();
		// 		UDP udp = (UDP)ip.getPayload();
		// 		RIPv2 rip = (RIPv2)udp.getPayload();

	private void handleRipPacket(RIPv2 rip, IPv4 pkt, Iface inIface, boolean match, boolean updatedDistance){
		synchronized(this.distanceVectorTable){
			for (RIPv2Entry entry: rip.getEntries()){
				match = false;
				for(DVEntry dv: distanceVectorTable.DVs){
					synchronized(dv){
						if(dv.addr == entry.getAddress()){
							dv.timestamp = System.currentTimeMillis();
							match = true;
							if(dv.metric > (entry.getMetric() + 1)){
								updatedDistance = true;
								dv.metric = entry.getMetric() + 1;
								routeTable.update(dv.addr, entry.getSubnetMask(), pkt.getSourceAddress(), inIface);
							}
						}
					}
				}
				if(match == false){
					updatedDistance = true;
					DVEntry newDVEntry = new DVEntry(entry.getAddress(), entry.getMetric()+1, 1);
					distanceVectorTable.addtoDVs(newDVEntry);
					DVEntryTO TO = new DVEntryTO(newDVEntry);
					Thread TOThread = new Thread(TO);
					TOThread.start();
					routeTable.insert(entry.getAddress(), pkt.getSourceAddress(), entry.getSubnetMask(), inIface);
				}
			}
		}
		if(updatedDistance == true){
			sendRip((byte)2);
		}
		return;

			// for (RIPv2Entry entry: rip.getEntries()){
			// 	int addr = entry.getAddress();
			// 	int mask = entry.getSubnetMask();
			// 	int netaddr = addr & mask;
			// 	int nextHop = ip.getSourceAddress();
			// 	int metric = entry.getMetric() + 1;

			// 	synchronized(this.ripDict)
			// 	{
			// 		if (ripDict.containsKey(netaddr)){
			// 			RipEntry newEntry = ripDict.get(netaddr);
			// 			newEntry.timestamp = System.currentTimeMillis();
			// 			if (metric < newEntry.metric){
			// 				newEntry.metric = metric;
			// 				this.routeTable.update(addr, mask, nextHop, inIface);
			// 			}
			// 		}
			// 		else{
			// 			ripDict.put(netaddr, new RipEntry(addr, mask, metric, System.currentTimeMillis()));
			// 			this.routeTable.insert(addr, mask, nextHop, inIface);
			// 		}
			// 	}
			// }
			// break;

	}



	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		// int broadcastAddr = ipPacket.toIPv4Address("224.0.0.9");
		System.out.println("Handle IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		// byte[] serialized = ipPacket.serialize();
		ipPacket.serialize();
		// ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum)
		{ return; }

		// Check TTL
		byte current = ipPacket.getTtl();
		current--;
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == current)
		{
			// time exceeded
			System.out.println("time exceeded");
			System.out.println(ipPacket.toString());
			this.ICMPMessage(ipPacket, inIface, (byte) 11, (byte) 0, false);
			return;
		}

		// Reset checksum now that TTL is decremented
		ipPacket.setTtl(current);
		ipPacket.resetChecksum();
		ipPacket.serialize(); //TODO: Serialize or not

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress())
			{  
				byte protocol = ipPacket.getProtocol();
				// Check headers after IP headers
				if(protocol == IPv4.PROTOCOL_TCP || protocol == IPv4.PROTOCOL_UDP) 
				{
					System.out.println("destination port unreachable");
					ICMPMessage(ipPacket, inIface, (byte) 3, (byte) 3, false);
				} 
				else if (protocol == IPv4.PROTOCOL_ICMP) 
				{
					ICMP icmpPacket = (ICMP) ipPacket.getPayload();
					if(icmpPacket.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) 
					{
						System.out.println("echo reply");
						ICMPMessage(ipPacket, inIface, (byte)0, (byte)0, true);
					}
				}
				return; }
		}

		// Do route lookup and forward
		this.forwardIpPacket(etherPacket, inIface);
	}

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
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		// If no entry matched, do nothing
		if (null == bestMatch)
		{ 
			// destination network unreachable
			System.out.println("no route matched");
			this.ICMPMessage(ipPacket, inIface, (byte) 3, (byte) 0, false);
			return;
		 }

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if (outIface.getName().equals(inIface.getName()))
		{ return; }

		// Set source MAC address in Ethernet header
		etherPacket.setDestinationMACAddress(outIface.getMacAddress().toString());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{ 
			// destination host unreachable
			System.out.println("destination host unreachable");
			this.ICMPMessage(ipPacket, inIface, (byte) 3, (byte) 1, false); // TODO: ARP REQUEST?
			return; 
		}
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toString());

		this.sendPacket(etherPacket, outIface);
	}

	class DVEntryTO implements Runnable {
		DVEntry entry;
	
		public DVEntryTO(DVEntry entry) {
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
				if(this.entry.valid != -1 && (now - this.entry.timestamp) > 30000) {
					this.entry.valid = 0;
					break;
				}
				}
			}
		}
	}

	class DVTableTO implements Runnable{
		DVtable table;
		long timestamp;

		public DVTableTO(DVtable table) {
			this.table = table;
			this.timestamp = System.currentTimeMillis();
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
						Iterator<DVEntry> itr = table.DVs.iterator();
						updated = false;
						while(itr.hasNext()) {
							DVEntry entry = itr.next();
							if(entry.valid == 0) {
								updated = true;
								RouteEntry re = routeTable.lookup(entry.addr);
								routeTable.remove(entry.addr, re.getMaskAddress());
								itr.remove();
							}
						}
				}
				if(updated == true) {
						/* RIP Response due to update */
						sendRip((byte)2);
				}

				/* Periodic Updates */
				long now = System.currentTimeMillis();
				if((now - this.timestamp) > 10000) {
						/* Broadcast every 10 seconds */
						sendRip((byte)2);
						this.timestamp = System.currentTimeMillis();
				}
			}
		}
	}
	

}
