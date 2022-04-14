package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.rt.Router;
import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.*;

public class ARPRequestTable {
	ArrayList<ARPRequestEntry> ARPRequestTab;

	public ARPRequestTable() {
		ARPRequestTab = new ArrayList<ARPRequestEntry>();
	}

	public ARPRequestEntry newARPRequest(int IP, Ethernet pkt, Iface inIface, Iface outIface) {
		synchronized(this.ARPRequestTab) {
			ARPRequestEntry entry = new ARPRequestEntry(IP, pkt, outIface, inIface);
			ARPRequestTab.add(entry);
			return entry;
		}
	}

}
