package edu.wisc.cs.sdn.vnet.rt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.ICMP;
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

		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			IPv4 ip = (IPv4)etherPacket.getPayload();
			if (IPv4.toIPv4Address("224.0.0.9") == ip.getDestinationAddress())
			{
				if (IPv4.PROTOCOL_UDP == ip.getProtocol()) 
				{
					UDP udp = (UDP)ip.getPayload();
					if (UDP.RIP_PORT == udp.getDestinationPort())
					{ 
						RIPv2 rip = (RIPv2)udp.getPayload();
						this.handleRipPacket(rip.getCommand(), etherPacket, inIface);
						break;
					}
				}
			}

			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}

		/********************************************************************/
	}

	private void ICMPMessage(Ethernet etherPacket, Iface inIface, byte type, byte code, boolean ttl){
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		byte[] serialized = ipPacket.serialize();
		// prepare ether packet header
		Ethernet ether = new Ethernet();
		ether.setEtherType(Ethernet.TYPE_IPv4);
		int dstAddr = ipPacket.getDestinationAddress(); // get destionation ip address
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr); // Find matching route table entry 
		ether.setSourceMACAddress(bestMatch.getInterface().getMacAddress().toBytes()); // update source MACaddress
		ether.setDestinationMACAddress(this.arpCache.lookup(bestMatch.getInterface().getIpAddress()).getMac().toBytes()); // update destination MACaddress

		// prepare IP header
		IPv4 ip = new IPv4();
		if (ttl == true){
			ip.setTtl((byte) 64); // set ttl
		}
		ip.setProtocol((byte) IPv4.PROTOCOL_ICMP); // set protocol
		ip.setSourceAddress(bestMatch.getInterface().getIpAddress()); // set source ip
		ip.setDestinationAddress(ipPacket.getSourceAddress()); // set destination ip
		
		Data data = new Data();
		ICMP icmp = new ICMP();

		// prepare ICMP header
		icmp.setIcmpType(type); // try no byte wrapper
		icmp.setIcmpCode(code);

		// prepare padding
		byte[] padding_4 = new byte[4];
		for (int i = 0; i < 4; i ++){
			padding_4[i] = (byte) 0;
		}
		byte[] padding_8 = new byte[8];
		for (int i = 0; i < 8; i ++){
			padding_8[i] = (byte) 0;
		}

		// link the header together
		ByteBuffer temp = ByteBuffer.allocate(32);
		temp.put(ether.serialize());
		System.out.println("Ether header size is: " + ether.serialize().length); // double check the header size
		temp.put(ip.serialize());
		System.out.println("IPv4 header size is: " + ip.serialize().length); // double check the header size
		temp.put(icmp.serialize());
		System.out.println("ICMP header size is: " + icmp.serialize().length); // double check the header size
		temp.put(padding_4);
		temp.put(serialized);
		System.out.println("Original IPv4 header size is: " + icmp.serialize().length); // double check the header size
		temp.put(padding_8);
		
		data.setData(temp.array());
		icmp.setPayload(data);
		ip.setPayload(icmp);
		ether.setPayload(ip);

		this.sendPacket(ether, bestMatch.getInterface());
	}

	private void echoReply(Ethernet etherPacket, Iface inIface){
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		byte[] serialized = ipPacket.serialize(); // TODO: original icmp header??
		// prepare ether packet header
		Ethernet ether = new Ethernet();
		ether.setEtherType(Ethernet.TYPE_IPv4);
		int dstAddr = ipPacket.getDestinationAddress(); // get destionation ip address
		boolean exit = true;
		for (Iface iface: this.interfaces.values()){
			if (iface.getIpAddress() == dstAddr){
				exit = false;
			}
		}
		if (exit == true){
			return;
		}
		
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr); // Find matching route table entry 
		ether.setSourceMACAddress(bestMatch.getInterface().getMacAddress().toBytes()); // update source MACaddress
		ether.setDestinationMACAddress(this.arpCache.lookup(bestMatch.getInterface().getIpAddress()).getMac().toBytes()); // update destination MACaddress

		// prepare IP header
		IPv4 ip = new IPv4();
		ip.setProtocol((byte) IPv4.PROTOCOL_ICMP); // set protocol
		ip.setSourceAddress(ipPacket.getDestinationAddress()); // set source ip
		ip.setDestinationAddress(ipPacket.getSourceAddress()); // set destination ip
		
		Data data = new Data();
		ICMP icmp = new ICMP();

		// prepare ICMP header
		icmp.setIcmpType((byte) 0); // try no byte wrapper
		icmp.setIcmpCode((byte) 0);

		// link the header together
		ByteBuffer temp = ByteBuffer.allocate(32);
		temp.put(ether.serialize());
		System.out.println("Ether header size is: " + ether.serialize().length); // double check the header size
		temp.put(ip.serialize());
		System.out.println("IPv4 header size is: " + ip.serialize().length); // double check the header size
		temp.put(icmp.serialize());
		System.out.println("ICMP header size is: " + icmp.serialize().length); // double check the header size
		temp.put(serialized);
		System.out.println("Original IPv4 header size is: " + icmp.serialize().length); // double check the header size
		
		data.setData(temp.array());
		icmp.setPayload(data);
		ip.setPayload(icmp);
		ether.setPayload(ip);

		this.sendPacket(ether, bestMatch.getInterface());
	}
	
	private final int rip_request = 1;
	private final int rip_unsol = 2;
	private final int rip_response = 3;

	class RipEntry
	{
		protected int addr, mask, nextHop, metric;
		protected long timestamp;
		public RipEntry(int addr, int mask, int metric, long timestamp) {
			this.addr = addr;
			this.mask = mask;
			this.metric = metric;
			this.timestamp = timestamp;
		}
	}

	private HashMap<Integer, RipEntry> ripDict;

	public void rip(){
		for (Iface iface : this.interfaces.values())
		{
			int mask = iface.getSubnetMask();
			int addr = mask & iface.getIpAddress();
			ripDict.put(addr, new RipEntry(addr, mask, 0, -1));
			this.routeTable.insert(iface.getIpAddress(), 0, mask, iface);
			sendRip(rip_request, null, iface);
		}

		// send unsolicited RIP response every 10 seconds
		TimerTask unsol = new TimerTask()
		{
			public void run()
			{
				for (Iface iface: interfaces.values())
				{ sendRip(rip_unsol, null, iface); }
			}
		};

		// timeout route table
		TimerTask clean = new TimerTask()
		{
			public void run()
			{
				for (RipEntry entry : ripDict.values()) {
					if (entry.timestamp != -1 && System.currentTimeMillis() - entry.timestamp >= 30000)
					{	
						ripDict.remove(entry.addr);
						// whether directly connected with router
						boolean move = true;
						for (Iface inface: interfaces.values()){
							if ((inface.getIpAddress() & inface.getSubnetMask()) == entry.addr){
								move = false;
							}
						}
						if (move == true){
							routeTable.remove(entry.addr, entry.mask);
						}
					}
				}
			}
		};

		Timer timer = new Timer(true);
		timer.schedule(unsol, 0, 10000);
		timer.schedule(clean, 0, 1000);

	}

	private void sendRip(int type, Ethernet etherPacket, Iface inIface){

		// prepare ether header
		Ethernet ether = new Ethernet();
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		ether.setEtherType(Ethernet.TYPE_IPv4);
		switch(type){
			case rip_request:
				ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
			case rip_unsol:
				ether.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
			case rip_response:
				ether.setDestinationMACAddress(ether.getSourceMACAddress());
		}

		// prepare ip header
		IPv4 ip = new IPv4();
		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_UDP);
		ip.setSourceAddress(inIface.getIpAddress());
		switch(type){
			case rip_request:
				ip.setDestinationAddress(IPv4.toIPv4Address("224.0.0.9"));
			case rip_unsol:
				ip.setDestinationAddress(IPv4.toIPv4Address("224.0.0.9"));
			case rip_response:
				IPv4 ipPacket = (IPv4)etherPacket.getPayload();
				ip.setDestinationAddress(ipPacket.getSourceAddress());
		}

		// prepare udp header
		UDP udp = new UDP();
		udp.setSourcePort(UDP.RIP_PORT);
		udp.setDestinationPort(UDP.RIP_PORT);

		// prepare rip
		RIPv2 rip = new RIPv2();
		switch(type){
			case rip_request:
				rip.setCommand(RIPv2.COMMAND_REQUEST);
			case rip_unsol:
				rip.setCommand(RIPv2.COMMAND_RESPONSE);
			case rip_response:
			rip.setCommand(RIPv2.COMMAND_RESPONSE);
		}

		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		ether.setEtherType(Ethernet.TYPE_IPv4);

		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_UDP);
		ip.setSourceAddress(inIface.getIpAddress());

		udp.setSourcePort(UDP.RIP_PORT);
		udp.setDestinationPort(UDP.RIP_PORT);

		// prepare ether packet payload
		List<RIPv2Entry> entries = new ArrayList<RIPv2Entry>();
		synchronized(this.ripDict)
		{
			for (RipEntry entry: ripDict.values())
			{
				RIPv2Entry new_entry = new RIPv2Entry(entry.addr, entry.mask, entry.metric);
				entries.add(new_entry);
			}
		}

		ether.setPayload(ip);
		ip.setPayload(udp);
		udp.setPayload(rip);
		rip.setEntries(entries);

		sendPacket(ether, inIface);
	}

	private void handleRipPacket(byte type, Ethernet etherPacket, Iface inIface){
		switch(type){
			case RIPv2.COMMAND_REQUEST:
				sendRip(rip_response, etherPacket, inIface);
				break;
			case RIPv2.COMMAND_RESPONSE:
				IPv4 ip = (IPv4)etherPacket.getPayload();
				UDP udp = (UDP)ip.getPayload();
				RIPv2 rip = (RIPv2)udp.getPayload();

				for (RIPv2Entry entry: rip.getEntries()){
					int addr = entry.getAddress();
					int mask = entry.getSubnetMask();
					int netaddr = addr & mask;
					int nextHop = ip.getSourceAddress();
					int metric = entry.getMetric() + 1;

					synchronized(this.ripDict)
					{
						if (ripDict.containsKey(netaddr)){
							RipEntry newEntry = ripDict.get(netaddr);
							newEntry.timestamp = System.currentTimeMillis();
							if (metric < newEntry.metric){
								newEntry.metric = metric;
								this.routeTable.update(addr, mask, nextHop, inIface);
							}
						}
						else{
							ripDict.put(netaddr, new RipEntry(addr, mask, metric, System.currentTimeMillis()));
							this.routeTable.insert(addr, nextHop, mask, inIface);
						}
					}
				}
				break;

		}

	}

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		System.out.println("Handle IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum)
		{ return; }

		// Check TTL
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl())
		{
			// time exceeded
			this.ICMPMessage(etherPacket, inIface, (byte) 11, (byte) 0, true);
			return;
		}

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress())
			{  
				byte protocol = ipPacket.getProtocol();
				// Check headers after IP headers
				if(protocol == IPv4.PROTOCOL_TCP || protocol == IPv4.PROTOCOL_UDP) 
				{
					ICMPMessage(etherPacket, inIface, (byte) 3, (byte) 3, false);
				} 
				else if (protocol == IPv4.PROTOCOL_ICMP) 
				{
					ICMP icmpPacket = (ICMP) ipPacket.getPayload();
					if(icmpPacket.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) 
					{
						echoReply(etherPacket, inIface);
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
			this.ICMPMessage(etherPacket, inIface, (byte) 3, (byte) 0, false);
			return;
		 }

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface)
		{ return; }

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
			// destination host unreachable
			this.ICMPMessage(etherPacket, inIface, (byte) 3, (byte) 1, false);
			return; 
		}
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}
}
