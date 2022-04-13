package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Iface;
import java.lang.Thread;
import java.util.*;

public class ForwardingTable extends Thread {
	List<ForwardingTableRecord> fTable;

	ForwardingTable(){
		fTable  = new ArrayList<ForwardingTableRecord>();
		/* Starts a thread for timeout */
		this.start();
	}

	public void learnForwarding(MACAddress input, Iface intf){

		synchronized(this.fTable) {
		/* When the table is empty */
		if(fTable.size() == 0) {
			ForwardingTableRecord r = new ForwardingTableRecord(input, intf);
			fTable.add(r);
		} else {
			for(ForwardingTableRecord record: this.fTable){
				/* When there is a matching record, reset the start time */
				if(record.inputMAC.equals(input)){
					record.startTime = System.currentTimeMillis();
					return;
				}
			}
			ForwardingTableRecord r = new ForwardingTableRecord(input, intf);
			fTable.add(r);
		}
		}
	}

	/* Search Forwarding Table for a match of MAC address */
	public Iface getIFaceForMAC(MACAddress inputMAC) {
		synchronized(this.fTable) {
		for(ForwardingTableRecord r:fTable) {
			if(r.inputMAC.equals(inputMAC)) {
				return r.inIface;
			}
		}
		}
		return null;
	}

	/* Thread which takes care of timeout of Forwarding Table entries */
	public void run() {
		try {
			while(true) {
				Thread.sleep(1000);
				if(this.fTable.size() == 0 || this.fTable == null)
					continue;

				synchronized(this.fTable) {
				/* Iterate over Forwarding Table */
				Iterator itr = fTable.iterator();
				while(itr.hasNext()) {
					ForwardingTableRecord r = (ForwardingTableRecord)itr.next();
					long now = System.currentTimeMillis();
					int diffTime = (int)((now - r.startTime) / 1000);
					/* Removes the Forwarding Table record on timeout */
					if(diffTime > r.timeOut) {
						System.out.println("Remove Entry : " + r.inputMAC + " -> Timeout happened");
						itr.remove();
					}
				}
				}
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public String toString() {
		synchronized(this.fTable) {
			if(this.fTable.size() == 0 || this.fTable == null)
				return "Empty";

			String result = "MAC Address\t\tIFace\tTimeout\tStartTime\n";
			for(ForwardingTableRecord r: this.fTable) {
				result += r.toString() + "\n";
			}
			return result;
		}
	}
}
