package edu.wisc.cs.sdn.vnet.vns;

import java.nio.ByteBuffer;

public class CommandOpen extends Command 
{
	public String mVirtualHostId;
	
	public CommandOpen()
	{
		super(Command.VNS_OPEN);
		this.mLen = this.getSize();
	}
	
	public CommandOpen deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		byte[] tmpBytes = new byte[Command.ID_SIZE];
		buf.get(tmpBytes);
		this.mVirtualHostId = new String(tmpBytes);
		
		return this;
	}
	
	public byte[] serialize()
	{
		byte[] data = new byte[this.getSize()];
        ByteBuffer bb = ByteBuffer.wrap(data);
        
        byte[] parentData = super.serialize();
        
        bb.put(parentData);
        byte[] tmp = new byte[Command.ID_SIZE];
        System.arraycopy(this.mVirtualHostId.getBytes(), 0, tmp, 0, this.mVirtualHostId.length());
        bb.put(tmp);
        
        return data;
	}
	
	public int getSize()
	{ return super.getSize() + Command.ID_SIZE; }
}
