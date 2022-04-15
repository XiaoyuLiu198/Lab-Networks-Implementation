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
				if(entry.metric < DVEntry.metric){
					DVEntry.metric = entry.metric;
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

}
