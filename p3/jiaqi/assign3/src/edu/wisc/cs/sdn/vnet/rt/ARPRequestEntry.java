package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.rt.Router;
import java.util.*;

class EthernetPktInfo {
	Ethernet pkt;
	Iface inIface;

	public EthernetPktInfo(Ethernet pkt, Iface inIface) {
		this.pkt = pkt;
		this.inIface = inIface;
	}
}

public class ARPRequestEntry {
	int IPAddress;
	Queue<EthernetPktInfo> etherPktQ;
	Iface outIface;

	/* Initial Value : 3
	 * When ARP request send : value--
	 * When ARP reply recieved : -1
	*/
	int nTry;
	MACAddress destinationMAC;

	public ARPRequestEntry(int IP, Ethernet pkt, Iface outIface, Iface inIface) {
		this.IPAddress = IP;
		this.etherPktQ = new LinkedList<EthernetPktInfo>();
		EthernetPktInfo infoNode = new EthernetPktInfo(pkt, inIface);
		this.etherPktQ.add(infoNode);
		this.outIface = outIface;
		this.nTry = 3;
		this.destinationMAC = null;
	}

	public void addPacketQueue(Ethernet pkt, Iface outIface, Iface inIface) {
		synchronized(this) {
			EthernetPktInfo infoNode = new EthernetPktInfo(pkt, inIface);
			this.etherPktQ.add(infoNode);
			Iterator<EthernetPktInfo> itr = etherPktQ.iterator();
			while (itr.hasNext()) {
			    EthernetPktInfo e = itr.next();
				IPv4 pkt1 = (IPv4)e.pkt.getPayload();
			}
		}
	}

	public void invalidateARPRequestEntry(MACAddress destinationMAC) {
		synchronized(this) {
			this.nTry = -1;
			this.destinationMAC = destinationMAC;
		}
	}
}
