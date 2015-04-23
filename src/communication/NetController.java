/**
 * This code may be modified and used for non-commercial 
 * purposes as long as attribution is maintained.
 * 
 * @author: Isaac Levy
 */

/**
* The sendMsg method has been modified by Navid Yaghmazadeh to fix a bug regarding to send a message to a reconnected socket.
*/

package communication;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import replica.InputPacket;
import replica.NamingProtocol;
import replica.Replica;
import util.Queue;

/**
 * Public interface for managing network connections.
 * You should only need to use this and the Config class.
 * @author ilevy
 *
 */
public class NetController {
	public String procNum;
	public InetAddress address;
	public HashMap<String, Integer> ports;
	
	//public final Config config;
	public Logger logger;  // same as replica's
	private final List<IncomingSock> inSockets;
	public final HashMap<String, OutgoingSock> outSockets;
	public final Set<String> disconnectedNodes = new HashSet<String>();
	private final ListenServer listener;
	
	/*
	public NetController(String processId, Config config, Queue<InputPacket> queue) {
		this.config = config;
		this.procNum = processId;
		addresses = config.addresses;
		ports = config.ports;
		
		inSockets = Collections.synchronizedList(new ArrayList<IncomingSock>());
		outSockets = new HashMap<String, OutgoingSock>();
		
		for (String process: config.addresses.keySet()) {
			outSockets.put(process, null);
		}

		listener = new ListenServer(config, inSockets, queue);
		listener.start();
	}*/
	
	public NetController(String processId, int myPort, Logger logger, Queue<InputPacket> queue){
		this.logger = logger;
		this.procNum = processId;
		try {
			address = InetAddress.getByName("localhost");
		} catch (UnknownHostException e) { e.printStackTrace();}
		ports = new HashMap<String, Integer>();
		
		//ports.put(procNum, myPort);
		
		inSockets = Collections.synchronizedList(new ArrayList<IncomingSock>());
		outSockets = new HashMap<String, OutgoingSock>();

		listener = new ListenServer(logger, procNum, myPort, inSockets, queue);
		listener.start();
		
		// temporary, for sans-client instructions
		connect("myself",myPort);
		
		// needed, will never get a JOIN message from "0"
		if(!processId.equals("0")){
			connect("0",5000);
		}
	}
	
	// Establish outgoing connection to a process.
	private synchronized void initOutgoingConn(String proc) throws IOException {
		if (outSockets.get(proc) != null)
			throw new IllegalStateException("proc " + proc + " not null");
		
		outSockets.put(proc, new OutgoingSock(new Socket(address, ports.get(proc))));
		logger.info(String.format("Server %s: Socket to %s established", 
				procNum, proc));
	}
	
	public synchronized void sendMsgs(Set<String> processes, String msg) {//, int partial_count) {
		for(String processNo: processes) {	
			logger.info("Sending: " + msg + " to " + processNo);
			sendMsg(processNo, msg);
		}
	}
	
	public synchronized void broadCastMsgs(String msg, HashSet<String> exceptProcess)
	{
		Set<String> keySet = new HashSet<String>(outSockets.keySet());
		for (String processId: keySet) {
			if (processId.equals(this.procNum) || (exceptProcess != null && exceptProcess.contains(processId))) {
				continue;
			}
			sendMsg(processId, msg);
		}
	}
	
	public synchronized void sendMsgToRandom(String msg){
		Set<String> nodes = outSockets.keySet();
		nodes.removeAll(disconnectedNodes);
		nodes.remove("myself");
		Object[] servers = nodes.toArray();
		int randomindex = new Random().nextInt(servers.length);
		
		String chosenServer = (String) servers[randomindex];
		logger.info("randomly picked server "+chosenServer);
		logger.info("   sending "+msg);
		sendMsg(chosenServer, msg);
	}

	
	public synchronized boolean sendMsg(String process, String msg) {
		if(disconnectedNodes.contains(process)){
			logger.info("Tried to send message to "+ process + ", but currently disconnected");
			return false;
		}
		logger.info("Sending Message to " + process + ":	" + msg);
		try {
			if (outSockets.get(process) == null)
				initOutgoingConn(process);
			outSockets.get(process).sendMsg(msg);
		} catch (IOException e) { 
			OutgoingSock sock = outSockets.get(process);
			if (sock != null) {
				sock.cleanShutdown();
				outSockets.remove(process);
				try{
					initOutgoingConn(process);
					sock = outSockets.get(process);
					sock.sendMsg(msg);	
				} catch(IOException e1){
					if (sock != null) {
						sock.cleanShutdown();
						outSockets.remove(process);
					}
					//config.logger.info(String.format("Server %d: Msg to %d failed.",
                    //    config.procNum, process));
        		    //config.logger.log(Level.FINE, String.format("Server %d: Socket to %d error",
                    //    config.procNum, process), e);
                    return false;
				}
				return true;
			}
			//config.logger.info(String.format("Server %d: Msg to %d failed.", 
			//	config.procNum, process));
			logger.log(Level.FINE, String.format("Server %s: Socket to %s error", 
				procNum, process), e);
			return false;
		}
		return true;
	}
	
	/**
	 * Return a list of msgs received on established incoming sockets
	 * @return list of messages sorted by socket, in FIFO order. *not sorted by time received*
	 */
	public synchronized List<InputPacket> getReceivedMsgs() {
		List<InputPacket> objs = new ArrayList<InputPacket>();
		logger.log(Level.INFO, "Looking for messages.");
		synchronized(inSockets) {
			ListIterator<IncomingSock> iter  = inSockets.listIterator();
			while (iter.hasNext()) {
				IncomingSock curSock = iter.next();
				try {
					objs.addAll(curSock.getMsgs());
				} catch (Exception e) {
					logger.log(Level.INFO, 
							"Server " + procNum + " received bad data on a socket", e);
					curSock.cleanShutdown();
					iter.remove();
				}
			}
		}
		
		return objs;
	}
	/**
	 * Shuts down threads and sockets.
	 */
	public synchronized void shutdown() {
		listener.cleanShutdown();
        if(inSockets != null) {
		    for (IncomingSock sock : inSockets)
			    if(sock != null)
                    sock.cleanShutdown();
        }
		if(outSockets != null) {
            for (OutgoingSock sock : outSockets.values())
			    if(sock != null)
                    sock.cleanShutdown();
        }
		
	}

	public void disconnect(String nodeToDisconnect) {
		disconnectedNodes.add(nodeToDisconnect);
		//outSockets.remove(nodeToDisconnect);
	}
	
	public void connect(String nodeToConnect, int portnum) {
		disconnectedNodes.remove(nodeToConnect);
		
		if(ports.containsKey(nodeToConnect)){
			logger.warning("tried to connect to "+nodeToConnect+" when already connected");
		} else{
			ports.put(nodeToConnect, portnum);
			
			outSockets.put(nodeToConnect, null);
			try {
				initOutgoingConn(nodeToConnect);
			} catch (IOException e) { e.printStackTrace(); }
		}
	}
	
	public void forgetAll() {
		for (String pid : outSockets.keySet()) {
			disconnectedNodes.add(pid);
		}
		outSockets.clear();
	}
	
	/*
	public void connectAll() {
		for (String node: this.ports.keySet()) {
			connect(node);
		}
		Replica.disconnectedNodes.clear();
	}*/

}
