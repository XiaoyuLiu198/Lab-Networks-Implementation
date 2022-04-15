package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.*;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
	ForwardingTable ft;
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		ft = new ForwardingTable();
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
		/* Learing MAC Address and its Interface */
		ft.learnForwarding(etherPacket.getSourceMAC(), inIface);

		/* Forwarding packets to the correct Interface */
		Iface outIface = ft.getIFaceForMAC(etherPacket.getDestinationMAC());
		if(outIface == null) {
			/* If no matching entry of MAC address in Forwarind Table,
			 * broadcast the packet on every interface (Except Incomming
			 * Interface) */
			for(Map.Entry<String,Iface> entry: interfaces.entrySet()) {
				if(entry.getKey().equals(inIface.getName())) {
					/* Move to next entry */
				} else {
					sendPacket(etherPacket, entry.getValue());
				}
			}
		} else {
			sendPacket(etherPacket, outIface);
		}
		/********************************************************************/
	}
}
