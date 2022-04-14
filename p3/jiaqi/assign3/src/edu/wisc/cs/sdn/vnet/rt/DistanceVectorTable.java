package edu.wisc.cs.sdn.vnet.rt;

import java.util.*;
import edu.wisc.cs.sdn.vnet.rt.*;

public class DistanceVectorTable
{
	List<DistanceVectorEntry> DVTable;

	public DistanceVectorTable()
	{
		this.DVTable = new ArrayList<DistanceVectorEntry>();
	}

	public void addDVTableEntry(DistanceVectorEntry entry)
	{
		DVTable.add(entry);
	}

	/* Method to return update DVTable */
	public boolean updateDVTableEntry(DistanceVectorEntry entry)
	{
		for(DistanceVectorEntry DVEntry : DVTable)
		{
			if(entry.IPAddress == DVEntry.IPAddress){
				if(entry.distance < DVEntry.distance){
					DVEntry.distance = entry.distance;
					return true;
				}
				else{
					return false;
				}
			}

		}
		DVTable.add(entry);
		return true;
	}

	public void printDVTable() {
		System.out.println("--------------------------");
		System.out.println("Subnet Number\tDistance");
		System.out.println("--------------------------");
		for(DistanceVectorEntry e : this.DVTable) {
			System.out.println(e);
		}
		System.out.println("--------------------------");
	}
}
