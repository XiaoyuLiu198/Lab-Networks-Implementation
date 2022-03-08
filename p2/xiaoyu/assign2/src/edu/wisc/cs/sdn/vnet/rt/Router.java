package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

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
		short ether_type = etherPacket.getEtherType();
		if (ether_type != Ethernet.TYPE_IPv4){
			return;
		}
		else{
			IPv4 header = (IPv4)etherPacket.getPayload();
			byte origin = (byte)header.getChecksum();
//			header.setChecksum((short)0);
			header.resetChecksum();
			byte[] res = header.serialize();
			IPacket returned = header.deserialize(res, 0, res.length);
			IPv4 returned_ipv4 = (IPv4)returned.getPayload();
			byte accumulate = (byte)returned_ipv4.getChecksum();
			if (accumulate != origin){
				return;
			}
			else{
				byte ttl = header.getTtl();
				ttl --;
				header.setTtl(ttl);
				byte curr_ttl = header.getTtl();
				if (curr_ttl == 0){
					return;
				}
				else{
					int dest_add = returned_ipv4.getDestinationAddress();
					if (inIface.getIpAddress() == returned_ipv4.getDestinationAddress()){
						return;
					}
					else{
						RouteEntry matched_add = routeTable.lookup(dest_add);
						if (matched_add == null){
							return;
						}
						else{
							Iface matched_int = matched_add.getInterface();
							ArpEntry hop_up = arpCache.lookup(matched_add.getDestinationAddress());
							byte[] hop_up_mac = hop_up.getMac().toBytes();
							etherPacket.setDestinationMACAddress(hop_up_mac);
							sendPacket(etherPacket, matched_int);
						}
					}
				}
			}
		}
		
		/********************************************************************/
	}
}
