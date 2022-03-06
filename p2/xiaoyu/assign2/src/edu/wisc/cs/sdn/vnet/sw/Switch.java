package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;

import java.util.*;


/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	HashMap<MACAddress, SwitchPort> switchTable = new HashMap<MACAddress, SwitchPort>();

	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
	}


	protected class SwitchPort {
		protected Iface iface;
		protected long startTime;

		SwitchPort(Iface iface, long startTime) {
			this.iface = iface;
			this.startTime = startTime;
		}
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
//		Ethernet cEther = new Ethernet();
		MACAddress destination = etherPacket.getDestinationMAC();
		MACAddress source = etherPacket.getSourceMAC();
		SwitchPort srcPort = new SwitchPort(inIface, System.currentTimeMillis());
		switchTable.put(source, srcPort);
		boolean update = true;
		if (switchTable.containsKey(destination)){
			if (switchTable.get(destination).iface == inIface){
				update = false;
			}
			if(System.currentTimeMillis() - switchTable.get(destination).startTime < 15000){
				sendPacket(etherPacket, switchTable.get(destination).iface);
				update = false;
			}
			else{
				update = true;
			}
		}
		// update the switch table here
		if (update == true) {
			for (String name : super.interfaces.keySet()) {
				Iface potential_inf = super.interfaces.get(name);
				if (potential_inf != inIface) {
					boolean success = sendPacket(etherPacket, potential_inf);
					if (success == true) {
						SwitchPort desPort = new SwitchPort(potential_inf, System.currentTimeMillis());
						switchTable.put(destination, desPort);
						break;
					}
				}
			}
		}
		
		/********************************************************************/
	}
}
