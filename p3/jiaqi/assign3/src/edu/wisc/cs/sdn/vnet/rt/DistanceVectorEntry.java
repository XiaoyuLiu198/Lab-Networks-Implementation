package edu.wisc.cs.sdn.vnet.rt;

import net.floodlightcontroller.packet.IPv4;
public class DistanceVectorEntry
{
	int IPAddress;
	int distance;
	long time;
	int valid;

	public DistanceVectorEntry(int IPAddress, int distance, int valid)
	{
		this.IPAddress = IPAddress;
		this.distance = distance;
		this.time = System.currentTimeMillis();
		this.valid = valid;
	}

	public void updateTime() {
		synchronized(this) {
			this.time = System.currentTimeMillis();
		}
	}

	public String toString() {
		IPv4 dummy = new IPv4();
		String out = IPv4.fromIPv4Address(this.IPAddress) + "\t" + this.distance;
		return out;
	}
}
