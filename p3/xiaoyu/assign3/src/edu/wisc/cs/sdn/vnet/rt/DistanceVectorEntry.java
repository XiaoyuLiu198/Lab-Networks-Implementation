package edu.wisc.cs.sdn.vnet.rt;

// import net.floodlightcontroller.packet.IPv4;
public class DistanceVectorEntry
{
	int IPAddress;
	int metric;
	long time;
	int valid;

	public DistanceVectorEntry(int IPAddress, int distance, int valid)
	{
		this.IPAddress = IPAddress;
		this.metric = distance;
		this.time = System.currentTimeMillis();
		this.valid = valid;
	}
}
