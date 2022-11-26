package edu.wisc.cs.sdn.vnet.vns;

import java.nio.ByteBuffer;

public class CommandClose extends Command
{
	public String mErrorMessage;
	
	public CommandClose()
	{ super(Command.VNS_CLOSE); }
	
	public CommandClose deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
				
		byte[] tmpBytes = new byte[256];
		buf.get(tmpBytes);
		this.mErrorMessage = new String(tmpBytes);
		
		return this;
	}
}
