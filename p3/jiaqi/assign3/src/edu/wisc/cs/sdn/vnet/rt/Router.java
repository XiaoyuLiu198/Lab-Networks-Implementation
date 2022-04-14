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
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}

		/********************************************************************/
	}

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
		
		MACAddress destMAC = findMACFromRTLookUp(pkt.getSourceAddress());
		if(destMAC == null) {
			RouteEntry rEntry = routeTable.lookup(pkt.getSourceAddress());
			/* Find the next hop IP Address */
			int nextHopIPAddress = rEntry.getGatewayAddress();
			if(nextHopIPAddress == 0){
				nextHopIPAddress = pkt.getSourceAddress();
			}
			// System.out.println("no next hop");
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
			nextHopIPAddress = ip;
		}
		/* Find the next hop MAC address from ARP Cache */
		ArpEntry ae = arpCache.lookup(nextHopIPAddress);
		System.out.println("nexthop arp mac is:");
		// System.out.println(ae.toString());
		if(ae == null) {
			/* No such host in the network - Dropping */
			System.out.println("arp null");
			return null;
		}
		/* Next hop MAC addresses */
		return ae.getMac();
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
			this.ICMPMessage(ipPacket, inIface, (byte) 11, (byte) 0, false);
			return; }

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
		{ return; }

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
		{ return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}
}
