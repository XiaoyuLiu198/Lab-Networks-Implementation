package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.MACAddress;
import java.util.Map;
import java.util.HashMap;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {

  private Map<MACAddress, SwitchPort> switchingTable = new HashMap<>();

  /**
   * Creates a router for a specific host.
   * 
   * @param host hostname for the router
   */
  public Switch(String host, DumpFile logfile) {
    super(host, logfile);
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
   * 
   * @param etherPacket the Ethernet packet that was received
   * @param inIface     the interface on which the packet was received
   */
  public void handlePacket(Ethernet etherPacket, Iface inIface) {
    System.out.println("*** -> Received packet: " +
        etherPacket.toString().replace("\n", "\n\t"));

    /********************************************************************/
    /* Handle packets */
    SwitchPort srcPort = new SwitchPort(inIface, System.currentTimeMillis());
    MACAddress srcMAC = etherPacket.getSourceMAC();
    switchingTable.put(srcMAC, srcPort);

    MACAddress destMAC = etherPacket.getDestinationMAC();
    SwitchPort destPort = switchingTable.get(destMAC);

    if (System.currentTimeMillis() - destPort.startTime > 15000) {
      // timeout after 15 seconds. Not sure if this is correct?
      switchingTable.remove(destMAC);
      destPort = null;
    }

    if (destPort == null) {
      // broadcast
      for (Iface iface : interfaces.values()) {
        if (!iface.equals(inIface)) {
          sendPacket(etherPacket, iface);
        }
      }
    } else {
      // unicast
      sendPacket(etherPacket, destPort.iface);
    }

    /********************************************************************/
  }
}
