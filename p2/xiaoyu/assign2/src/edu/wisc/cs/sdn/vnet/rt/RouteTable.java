package edu.wisc.cs.sdn.vnet.rt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.floodlightcontroller.packet.IPv4;

import edu.wisc.cs.sdn.vnet.Iface;

/**
 * Route table for a router.
 * @author Aaron Gember-Jacobson
 */
public class RouteTable {
	/**
	 * Entries in the route table
	 */
	private List<RouteEntry> entries;

	/**
	 * Initialize an empty route table.
	 */
	public RouteTable() {
		this.entries = new LinkedList<RouteEntry>();
	}

//	public class TrieNode {
//
//		private int value;
//		private HashMap<Character, TrieNode> children;
//		private boolean bIsEnd;
//
//		TrieNode(int val) {
//			this.value = val;
//			this.children = new HashMap<>();
//			this.bIsEnd = false;
//		}
//
//		public HashMap<Character, TrieNode> getChildren() {
//			return children;
//		}
//
//		public int getValue() {
//			return value;
//		}
//
//		public void setIsEnd(boolean bool) {
//			bIsEnd = bool;
//		}
//
//		public boolean isEnd() {
//			return bIsEnd;
//		}
//	}

//	/** implement the trie**/
//	class Trie {
//		private TrieNode root;
//		// Constructor
//		public Trie() {
//			root = new TrieNode((char) 0);
//		}
//		public void insert(int number) {
//			TrieNode crawl = root;
//			String numb_str = String.valueOf(number);
//			int length = numb_str.length();
//
//			for (int level = 0; level < length; level++) {
//				HashMap<Character, TrieNode> child = crawl.getChildren();
//				char ch = numb_str.charAt(level);
//
//				if (child.containsKey(ch))
//					crawl = child.get(ch);
//				else   // Else create a child
//				{
//					TrieNode temp = new TrieNode(ch);
//					child.put(ch, temp);
//					crawl = temp;
//				}
//			}
//			// Set bIsEnd true for last character
//			crawl.setIsEnd(true);
//		}
//
//		public String getMatchingPrefix(String input)  {
//			String result = ""; // Initialize resultant string
//			int length = input.length();  // Find length of the input string
//
//			TrieNode crawl = root;
//
//			int level, prevMatch = 0;
//			for( level = 0 ; level < length; level++ )
//			{
//				char ch = input.charAt(level);
//
//				HashMap<Character,TrieNode> child = crawl.getChildren();
//
//				if( child.containsKey(ch) ) {
//					result += ch;          //Update result
//					crawl = child.get(ch); //Update crawl to move down in Trie
//
////					if (crawl.isEnd()) {
////						prevMatch = level + 1;
////					}
//				}
//				else  break;
//			}
//		// If the last processed character did not match end of a word,
//		// return the previously matching prefix
////		if( !crawl.isEnd() )
////			return result.substring(0, prevMatch);
////		else return result;
//			return result;
//	}
//}
//	public int helper(int ip_address, int candi){
//		int match = 0;
////		while (ip_address % 2 == 0 && ip_address != 0){
////			zeros ++;
////			ip_address = ip_address % 2;
////		}
//		String ip_str = String.valueOf(ip_address);
//		String candi_str = String.valueOf(candi);
//		for (int i = 0; i < ip_str.length(); i++){
//			if ( ip_str.charAt(i) ==  candi_str.charAt(i)){
//				match ++;
//			}else{
//				break;
//			}
//		}
//		return match;
//	}
    private int getPrefixLength(int ip) {
	// int zeroes = Integer.numberOfTrailingZeros(ip);
	// return (32 - zeroes);

	  int zeroes = 0;
	  while (ip % 2 == 0 && ip != 0) {
		zeroes++;
		ip = ip / 2;
	  }
	   return (32 - zeroes);
    }

	/**
	 * Lookup the route entry that matches a given IP address.
	 * @param ip IP address
	 * @return the matching route entry, null if none exists
	 */
	public RouteEntry lookup(int ip)
	{
		synchronized(this.entries)
		{
			/*****************************************************************/
			long maxi = 0;
			RouteEntry matched = null;
			for (RouteEntry candi_entry: this.entries){
				int mask_address = candi_entry.getMaskAddress();
				int ip_address = candi_entry.getDestinationAddress();
				int d1 = mask_address & ip;
				if (d1 == ip_address){
					if (candi_entry.getInterface()){
						int matched_length = helper(ip_address);
						if (matched_length > maxi){
							maxi = matched_length;
							matched = candi_entry;
						}
					}
				}
			return matched;
			/*****************************************************************/
		}
	}
	
	/**
	 * Populate the route table from a file.
	 * @param filename name of the file containing the static route table
	 * @param router the route table is associated with
	 * @return true if route table was successfully loaded, otherwise false
	 */
	public boolean load(String filename, Router router)
	{
		// Open the file
		BufferedReader reader;
		try 
		{
			FileReader fileReader = new FileReader(filename);
			reader = new BufferedReader(fileReader);
		}
		catch (FileNotFoundException e) 
		{
			System.err.println(e.toString());
			return false;
		}
		
		while (true)
		{
			// Read a route entry from the file
			String line = null;
			try 
			{ line = reader.readLine(); }
			catch (IOException e) 
			{
				System.err.println(e.toString());
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			// Stop if we have reached the end of the file
			if (null == line)
			{ break; }
			
			// Parse fields for route entry
			String ipPattern = "(\\d+\\.\\d+\\.\\d+\\.\\d+)";
			String ifacePattern = "([a-zA-Z0-9]+)";
			Pattern pattern = Pattern.compile(String.format(
					"%s\\s+%s\\s+%s\\s+%s", 
					ipPattern, ipPattern, ipPattern, ifacePattern));
			Matcher matcher = pattern.matcher(line);
			if (!matcher.matches() || matcher.groupCount() != 4)
			{
				System.err.println("Invalid entry in routing table file");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}

			int dstIp = IPv4.toIPv4Address(matcher.group(1));
			if (0 == dstIp)
			{
				System.err.println("Error loading route table, cannot convert "
						+ matcher.group(1) + " to valid IP");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			int gwIp = IPv4.toIPv4Address(matcher.group(2));
			
			int maskIp = IPv4.toIPv4Address(matcher.group(3));
			if (0 == maskIp)
			{
				System.err.println("Error loading route table, cannot convert "
						+ matcher.group(3) + " to valid IP");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			String ifaceName = matcher.group(4).trim();
			Iface iface = router.getInterface(ifaceName);
			if (null == iface)
			{
				System.err.println("Error loading route table, invalid interface "
						+ matcher.group(4));
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			// Add an entry to the route table
			this.insert(dstIp, gwIp, maskIp, iface);
		}
	
		// Close the file
		try { reader.close(); } catch (IOException f) {};
		return true;
	}
	
	/**
	 * Add an entry to the route table.
	 * @param dstIp destination IP
	 * @param gwIp gateway IP
	 * @param maskIp subnet mask
	 * @param iface router interface out which to send packets to reach the 
	 *		destination or gateway
	 */
	public void insert(int dstIp, int gwIp, int maskIp, Iface iface)
	{
		RouteEntry entry = new RouteEntry(dstIp, gwIp, maskIp, iface);
		synchronized(this.entries)
		{ 
			this.entries.add(entry);
		}
	}
	
	/**
	 * Remove an entry from the route table.
	 * @param maskIp subnet mask of the entry to remove
	 * @return true if a matching entry was found and removed, otherwise false
	 */
	public boolean remove(int dstIp, int maskIp)
	{ 
		synchronized(this.entries)
		{
			RouteEntry entry = this.find(dstIp, maskIp);
			if (null == entry) { return false; }
			this.entries.remove(entry);
		}
		return true;
	}
	
	/**
	 * Update an entry in the route table.
	 * @param maskIp subnet mask of the entry to update
	 * @param iface new router interface for matching entry
	 * @return true if a matching entry was found and updated, otherwise false
	 */
	public boolean update(int dstIp, int maskIp, int gwIp, Iface iface)
	{
		synchronized(this.entries)
		{
			RouteEntry entry = this.find(dstIp, maskIp);
			if (null == entry) { return false; }
			entry.setGatewayAddress(gwIp);
			entry.setInterface(iface);
		}
		return true;
	}

	/**
	 * Find an entry in the route table.
	 * @param maskIp subnet mask of the entry to find
	 * @return a matching entry if one was found, otherwise null
	 */
	private RouteEntry find(int dstIp, int maskIp)
	{
		synchronized(this.entries)
		{
			for (RouteEntry entry : this.entries)
			{
				if ((entry.getDestinationAddress() == dstIp)
					&& (entry.getMaskAddress() == maskIp)) 
				{ return entry; }
			}
		}
		return null;
	}
	
	public String toString()
	{
		synchronized(this.entries)
		{ 
			if (0 == this.entries.size())
			{ return " WARNING: route table empty"; }
			
			String result = "Destination\tGateway\t\tMask\t\tIface\n";
			for (RouteEntry entry : entries)
			{ result += entry.toString()+"\n"; }
			return result;
		}
	}
}
