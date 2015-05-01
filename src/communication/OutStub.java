package communication;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class OutStub{
	/* this is really just a container for OutGoingSock, but it simplifies accessing the socks by both
	   name (for normal interaction) and id (for master instructions)
	*/
	public int id;
	public OutgoingSock sock;
	public boolean connected;
	public boolean isClient;
	
	public OutStub(int id, boolean isClient){
		this.id = id;
		this.connected = true;
		this.isClient = isClient;

		// from initOutgoingConn
		try {
			this.sock = new OutgoingSock(new Socket(InetAddress.getLocalHost(), NetController.basePort + id));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void closeSock(){
		sock.cleanShutdown();
		sock = null;
	}
	
	public void restartSock() throws UnknownHostException, IOException{
		this.sock = new OutgoingSock(new Socket("localhost", NetController.basePort + id));
	}
}
