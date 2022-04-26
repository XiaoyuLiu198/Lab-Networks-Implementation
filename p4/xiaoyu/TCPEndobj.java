public class TCPEndobj{

    private int client_port;
    private int remote_ip;
    private int remote_port;
    // Object file_name;
    private String file_name;
    private byte MTU;
    private int sws;
    private int listen_port;
    private int timer; // use timer to track time before receiving ACK

    public TCPEndobj(int client_port, int remote_ip, int remote_port, String file_name, byte mTU, int sws,
            int listen_port, int timer) {
        this.client_port = client_port;
        this.remote_ip = remote_ip;
        this.remote_port = remote_port;
        this.file_name = file_name;
        this.setMTU(mTU);
        this.setSws(sws);
        this.listen_port = listen_port;
        this.timer = timer;
    }

    public int getSws() {
        return sws;
    }

    public void setSws(int sws) {
        this.sws = sws;
    }

    public byte getMTU() {
        return MTU;
    }

    public void setMTU(byte mTU) {
        this.MTU = mTU;
    }

}