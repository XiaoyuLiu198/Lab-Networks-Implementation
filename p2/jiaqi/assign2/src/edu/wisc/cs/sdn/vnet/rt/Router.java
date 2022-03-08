package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
  /** Routing table for the router */
  private RouteTable routeTable;

  /** ARP cache for the router */
  private ArpCache arpCache;

  /**
   * Creates a router for a specific host.
   * 
   * @param host hostname for the router
   */
  public Router(String host, DumpFile logfile) {
    super(host, logfile);
    this.routeTable = new RouteTable();
    this.arpCache = new ArpCache();
  }

  /**
   * @return routing table for the router
   */
  public RouteTable getRouteTable() {
    return this.routeTable;
  }

  /**
   * Load a new routing table from a file.
   * 
   * @param routeTableFile the name of the file containing the routing table
   */
  public void loadRouteTable(String routeTableFile) {
    if (!routeTable.load(routeTableFile, this)) {
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
   * 
   * @param arpCacheFile the name of the file containing the ARP cache
   */
  public void loadArpCache(String arpCacheFile) {
    if (!arpCache.load(arpCacheFile)) {
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
   * 
   * @param etherPacket the Ethernet packet that was received
   * @param inIface     the interface on which the packet was received
   */
  public void handlePacket(Ethernet etherPacket, Iface inIface) {
    System.out.println("*** -> Received packet: " +
        etherPacket.toString().replace("\n", "\n\t"));

    /********************************************************************/
    /* Handle packets */
    
    // System.out.println("***1");
    // System.out.println(etherPacket.getEtherType());
    // System.out.println(Ethernet.TYPE_IPv4);

    System.out.println("***1");
    if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) return;  // drop packet if not IPv4
    System.out.println("***2");

    IPv4 packet = (IPv4) etherPacket.getPayload();
    //IPv4 originalPacket = packet;
    short checksum = packet.getChecksum();
    
    byte ttl = packet.getTtl();

    System.out.println("***3");
    packet.setChecksum((short) 0);
    byte[] data = packet.serialize();
    packet = (IPv4) packet.deserialize(data, 0, data.length);
    if (checksum != packet.getChecksum() || ttl <= 1) return;  // drop packet if checksum incorrect

    ttl -= 1;
    packet = packet.setTtl((byte) (ttl));
    // if (packet.getTtl() <= (byte) 0) return;  // drop packet if decremented TTL is 0

    // ZERO CHECKSUM AGAIN
    packet.setChecksum((short) 0);
    System.out.println("***4");

    byte[] newData = packet.serialize();
    packet = (IPv4) packet.deserialize(newData, 0, newData.length);
    etherPacket.setPayload(packet);

    System.out.println("***5");

    for (Iface iface : interfaces.values()) {
      if (iface.getIpAddress() == packet.getDestinationAddress()) return;  // drop packet if dest IP address matches one of the interfaces'
    }

    // Forwarding packet

    System.out.println("***6");

    int destAddress = packet.getDestinationAddress();
    RouteEntry resultEntry = routeTable.lookup(destAddress);
    
    System.out.println("destAddress: " + destAddress);
    System.out.println();

    if (resultEntry == null) return;  // drop packet if no entry in router table matches 
    int gatewayAddress = resultEntry.getGatewayAddress();

    System.out.println("***7");

    // if (resultEntry.getInterface().getMacAddress().equals(inIface.getMacAddress())) return;

    System.out.println("***8");

    // System.out.println("nextHopIpAddress: "+nextHopIpAddress);
    // ArpEntry arpEntry = null;
    ArpEntry arpEntry = null;
    if (gatewayAddress != 0) {
      arpEntry = arpCache.lookup(gatewayAddress);
    } else {
      arpEntry = arpCache.lookup(destAddress); 
    }

    System.out.println("***9");


    // ArpEntry arpEntry = arpCache.lookup(destAddress);

    // System.out.println("arpEntry: " + arpEntry.toString());
    if (arpEntry == null) return;  // drop packet if no entry in ARP table

    MACAddress srcMACAddress = resultEntry.getInterface().getMacAddress();
    MACAddress destMACAddress = arpEntry.getMac();
    

    // System.out.println(sourceMACAddress);
    // System.out.println("sourceMACAddress: " + sourceMACAddress.toString());
    // System.out.println("nextHopaddr: " + nextHopMACAddress.toString());

    etherPacket.setSourceMACAddress(srcMACAddress.toBytes());
    etherPacket.setDestinationMACAddress(destMACAddress.toBytes());

    
    System.out.println("srcMACAddress.toBytes(): " + srcMACAddress.toBytes());
    System.out.println("destMACAddress.toBytes(): " + destMACAddress.toBytes());
    System.out.println("reach before sendPacket");
    sendPacket(etherPacket, resultEntry.getInterface());
    System.out.println("reach bottom");
    /********************************************************************/
  }
}
