/**
 * This code may be modified and used for non-commercial 
 * purposes as long as attribution is maintained.
 * 
 * @author: Isaac Levy
 */

package communication;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import replica.InputPacket;
import util.Queue;

public class ListenServer extends Thread {

	public volatile boolean killSig = false;
	final int port;
	final String procNum;
	final List<IncomingSock> socketList;
	//final Config conf;
	final Logger logger;
	final ServerSocket serverSock;
	Queue<InputPacket> queue;

	protected ListenServer(Logger logger, String procNum, int port, List<IncomingSock> sockets, 
			Queue<InputPacket> queue) {
		this.logger = logger;
		this.socketList = sockets;
		this.queue = queue;

		this.procNum = procNum;
		this.port = port;
		try {
			serverSock = new ServerSocket(port);
			logger.info(String.format(
					"Server %s: Server connection established", procNum));
		} catch (IOException e) {
			String errStr = String.format(
					"Server %s: [FATAL] Can't open server port %d", procNum,
					port);
			logger.log(Level.SEVERE, errStr);
			throw new Error(errStr);
		}
	}

	public void run() {
		while (!killSig) {
			try {
				IncomingSock incomingSock = new IncomingSock(
						serverSock.accept(), logger, queue);
				socketList.add(incomingSock);
				incomingSock.start();
				logger.info(String.format(
						"Server %s: New incoming connection accepted from %s",
						procNum, incomingSock.sock.getInetAddress()
								.getHostName()));
			} catch (IOException e) {
				if (!killSig) {
					logger.log(Level.INFO, String.format(
							"Server %s: Incoming socket failed", procNum), e);
				}
			}
		}
	}

	protected void cleanShutdown() {
		killSig = true;
		try {
			serverSock.close();
		} catch (IOException e) {
			logger.log(Level.INFO,String.format(
					"Server %s: Error closing server socket", procNum), e);
		}
	}
}
