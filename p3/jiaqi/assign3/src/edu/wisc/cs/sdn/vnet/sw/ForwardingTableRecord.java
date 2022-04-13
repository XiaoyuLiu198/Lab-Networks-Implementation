package edu.wisc.cs.sdn.vnet.sw;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;

class ForwardingTableRecord {
    
	MACAddress inputMAC;
	Iface inIface;
	int timeOut;
	long startTime;

	ForwardingTableRecord(MACAddress inputMAC, Iface inIface){
		this.inputMAC = inputMAC;
		this.inIface = inIface;
		this.timeOut = 15;
		this.startTime = System.currentTimeMillis();
	}

	public String toString() {
		return String.format("%s\t%s\t%d\t%d",
				this.inputMAC.toString(),
				this.inIface.getName(),
				this.timeOut, this.startTime);
	}
}
