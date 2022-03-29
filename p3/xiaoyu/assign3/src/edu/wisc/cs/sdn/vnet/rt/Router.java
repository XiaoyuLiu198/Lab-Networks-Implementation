package edu.wisc.cs.sdn.vnet.rt;

import java.nio.ByteBuffer;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
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
	 * RIP based on empty table
	 */
	public void RIP(Iface inIface){

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
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}

		/********************************************************************/
	}

	private void outputMessage(Ethernet etherPacket, Iface inIface, byte type, byte code, boolean ttl){
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

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// echo reply
		// TODO: right place to insert echo reply??
		if (ICMP.TYPE_ECHO_REQUEST != (byte) 0){
			this.echoReply(etherPacket, inIface);
		}
		
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		System.out.println("Handle IP packet");

		// Check headers after IP headers
		if (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP | ipPacket.getProtocol() == IPv4.PROTOCOL_UDP){
			// TCP/UDP message
			this.outputMessage(etherPacket, inIface, (byte) 3, (byte) 3, false);
			return;
		}
		// if (ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP){
		// 	if (ipPacket.getIcmpType() == 8){

		// 	}
		// }

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
			this.outputMessage(etherPacket, inIface, (byte) 11, (byte) 0, true);
			return;
		}

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress())
			{ return; }
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
			this.outputMessage(etherPacket, inIface, (byte) 3, (byte) 0, false);
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
			this.outputMessage(etherPacket, inIface, (byte) 3, (byte) 1, false);
			return; 
		}
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}
}
