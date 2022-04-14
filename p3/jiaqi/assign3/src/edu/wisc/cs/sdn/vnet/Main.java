package edu.wisc.cs.sdn.vnet;

import edu.wisc.cs.sdn.vnet.rt.Router;
import edu.wisc.cs.sdn.vnet.rt.RouteEntry;
import edu.wisc.cs.sdn.vnet.sw.Switch;
import edu.wisc.cs.sdn.vnet.vns.Command;
import edu.wisc.cs.sdn.vnet.vns.VNSComm;
import edu.wisc.cs.sdn.vnet.rt.RouteTable;
import java.util.Timer;
import java.util.TimerTask;
import net.floodlightcontroller.packet.*;

public class Main 
{
	private static final short DEFAULT_PORT = 8888;
	private static final String DEFAULT_SERVER = "localhost";
	
	public static void main(String[] args)
	{
		String host = null;
		String server = DEFAULT_SERVER;
		String routeTableFile = null;
		String arpCacheFile = null;
		String logfile = null;
		short port = DEFAULT_PORT;
		VNSComm vnsComm = null;
		Device dev = null;
		
		// Parse arguments
		for(int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			if (arg.equals("-h"))
			{
				usage();
				return;
			}
			else if(arg.equals("-p"))
			{ port = Short.parseShort(args[++i]); }
			else if (arg.equals("-v"))
			{ host = args[++i]; }
			else if (arg.equals("-s"))
			{ server = args[++i]; }
			else if (arg.equals("-l"))
			{ logfile = args[++i]; }
			else if (arg.equals("-r"))
			{ routeTableFile = args[++i]; }
			else if (arg.equals("-a"))
			{ arpCacheFile = args[++i]; }
		}

       
		
		if (null == host)
		{
			usage();
			return;
		}
		
		// Open PCAP dump file for logging packets sent/received by the router
		DumpFile dump = null;
		if (logfile != null)
		{
			dump = DumpFile.open(logfile);
			if (null == dump)
			{
				System.err.println("Error opening up dump file "+logfile);
				return;
			}
		}
		
		if (host.startsWith("s"))
		{ dev = new Switch(host, dump); }
		else if (host.startsWith("r"))
		{
			// Create router instance
			dev = new Router(host, dump);
		}
		else 
		{
			System.err.println("Device name must start with 's' or 'r'");
			return;
		}
		
		// Connect to Virtual Network Simulator server and negotiate session
		System.out.println(String.format("Connecting to server %s:%d", 
				server, port));
		vnsComm = new VNSComm(dev);
		if (!vnsComm.connectToServer(port, server))
		{ System.exit(1); }
		vnsComm.readFromServerExpect(Command.VNS_HW_INFO);	
		
		if (dev instanceof Router) 
		{
			// Read static route table
			if (routeTableFile != null) { 
				((Router)dev).loadRouteTable(routeTableFile); 
			} else {
                // Starting RIP: implement RIP since not using a static route table.
				// Add to route table things that are directly connected to this router.
                RouteTable routeTable = ((Router) dev).getRouteTable();
                for (Iface i : dev.getInterfaces().values()) {
                    routeTable.insert((i.getIpAddress() & i.getSubnetMask()), 0, i.getSubnetMask(), i, 1, System.currentTimeMillis());
                }

				// send out initial RIP requests out of each interface on the router
				for (Iface i : dev.getInterfaces().values()) {
                    RIPv2 ripPacket = new RIPv2();
						ripPacket.setCommand(RIPv2.COMMAND_REQUEST);
						ripPacket.resetChecksum();

						UDP udpPacket = new UDP();
						udpPacket.setSourcePort(UDP.RIP_PORT);
						udpPacket.setDestinationPort(UDP.RIP_PORT);
						udpPacket.resetChecksum();

						IPv4 ipPacket = new IPv4();
						ipPacket.setSourceAddress(i.getIpAddress());
						ipPacket.setDestinationAddress("224.0.0.9");


						Ethernet ethernetPacket = new Ethernet();
						ethernetPacket.setEtherType(Ethernet.TYPE_IPv4);
						ethernetPacket.setSourceMACAddress(i.getMacAddress().toString());
						ethernetPacket.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");

						udpPacket.setPayload(ripPacket);
						ipPacket.setPayload(udpPacket);
						ethernetPacket.setPayload(ipPacket);
						dev.sendPacket(ethernetPacket, i);
                }
            }

			
			// Read static ACP cache
			if (arpCacheFile != null)
			{ ((Router)dev).loadArpCache(arpCacheFile); }
		}

		// Read messages from the server until the server closes the connection
		System.out.println("<-- Ready to process packets -->");
		if (routeTableFile != null) {
			// not running in RIP, avoid printing all the RIP stuff out
			while (vnsComm.readFromServer());
		} else {
			while (vnsComm.readFromServer()) {
				if (routeTableFile == null) {
	
					// implement 30 second timeout for entries in route table learned from RIP
					int numberOfRouteEntries = ((Router) dev).getRouteTable().getEntries().size();
					for (int i = (numberOfRouteEntries - 1); i >= 0; i--) {
						RouteEntry routeEntry = ((Router) dev).getRouteTable().getEntries().get(i);
						if (routeEntry.getGatewayAddress() == 0) {
							// we don't want to remove entries for devices directly connected to this router
							continue;
						}
						if ((routeEntry.getLastUpdateTimestamp() + 30000) < System.currentTimeMillis()) {
							((Router) dev).getRouteTable().remove(routeEntry.getDestinationAddress(), routeEntry.getMaskAddress());
						}
					}
	
					// send unsolicited RIP responses every 10 seconds
					if ((System.currentTimeMillis() - ((Router) dev).getLastSent()) > 10000) {
						for (RouteEntry routeEntry : ((Router) dev).getRouteTable().getEntries()) {
							RIPv2 ripPacket = new RIPv2();
							ripPacket.setCommand(RIPv2.COMMAND_RESPONSE);
							for (RouteEntry r : ((Router) dev).getRouteTable().getEntries()) {
								RIPv2Entry ripEntry = new RIPv2Entry(r.getDestinationAddress(), r.getMaskAddress(), r.getMetric()); // need to construct this properly
								ripPacket.addEntry(ripEntry);
							}
							ripPacket.resetChecksum();
	
							UDP udpPacket = new UDP();
							udpPacket.setSourcePort(UDP.RIP_PORT);
							udpPacket.setDestinationPort(UDP.RIP_PORT);
							udpPacket.resetChecksum();
	
							IPv4 ipPacket = new IPv4();
							ipPacket.setSourceAddress(routeEntry.getInterface().getIpAddress());
							ipPacket.setDestinationAddress("224.0.0.9");
	
	
							Ethernet ethernetPacket = new Ethernet();
							ethernetPacket.setEtherType(Ethernet.TYPE_IPv4);
							ethernetPacket.setSourceMACAddress(routeEntry.getInterface().getMacAddress().toString());
							ethernetPacket.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
	
							udpPacket.setPayload(ripPacket);
							ipPacket.setPayload(udpPacket);
							ethernetPacket.setPayload(ipPacket);
							dev.sendPacket(ethernetPacket,routeEntry.getInterface());
							((Router) dev).setLastSent(System.currentTimeMillis());
						}
					}		
				}
			} 
		}
		
		// Shutdown the router
		dev.destroy();
	}
	
	static void usage()
	{
		System.out.println("Virtual Network Client");
		System.out.println("VNet -v host [-s server] [-p port] [-h]");
		System.out.println("     [-r routing_table] [-a arp_cache] [-l log_file]");
		System.out.println(String.format("  defaults server=%s port=%d", 
				DEFAULT_SERVER, DEFAULT_PORT));
	}
}