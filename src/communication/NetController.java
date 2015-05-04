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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import replica.InputPacket;
import replica.NamingProtocol;
import util.Queue;

/**
 * Public interface for managing network connections.
 * You should only need to use this and the Config class.
 * @author ilevy
 *
 */
public class NetController {
	
	public static final int basePort = 11000;
	
	public String procNum; // name of this process
	
	public HashMap<String, OutStub> nodes = new HashMap<String, OutStub>();  //OutSocket container
	
	public Logger logger;  // same as replica's
	
	private final List<IncomingSock> inSockets;
	private final ListenServer listener;
	
	public int lastTalk;  //used for "random" send that iterates through ids
	
	public NetController(String processId, int myId, Logger logger, Queue<InputPacket> queue){
		this.logger = logger;
		this.procNum = processId;
		
		inSockets = Collections.synchronizedList(new ArrayList<IncomingSock>());

		listener = new ListenServer(logger, procNum, basePort + myId, inSockets, queue);
		listener.start();
		
		// temporary, for sans-client instructions
		nodes.put( NamingProtocol.myself, new OutStub(myId, false));
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
		if(!nodes.isEmpty()) {
            for (OutStub node : nodes.values())
			    if(node.sock != null)
                    node.sock.cleanShutdown();
        }
	}

	public synchronized boolean sendMsg(String process, String msg) {
		if(!nodes.containsKey(process)){
			logger.info("Tried to send to " + process + ", but don't have a connection");
			return false;
		}
		OutStub stub = nodes.get(process);
		if(!stub.connected){
			logger.info("Tried to send to " + process + ", but currently disconnected");
			return false;
		}
		logger.info("Sending Message to " + process + ":	" + msg);
		try {
			if(stub.sock == null){
				stub.restartSock();
			}
			stub.sock.sendMsg(msg);
		} catch (IOException e) { 
			if (stub.sock != null) {
				stub.closeSock();
				try{
					stub.restartSock();
				} catch(IOException e1){
					if (stub.sock != null) {
						stub.closeSock();
					}
                    return false;
				}
				return true;
			}
			logger.log(Level.FINE, String.format("Server %s: Socket to %s error", 
				procNum, process), e);
			return false;
		}
		return true;
	}
	
	/** Sends to any server but self
	 * currently does not notice if server is disconnected - this is easy to change*/
	public synchronized void sendMsgToRandom(String msg){
		Object[] processNames = nodes.keySet().toArray();
		if(processNames.length == 0) return;
		boolean successfulsend = false;
		int timeout = 0;
		while(!successfulsend && (timeout <= processNames.length)){
			//int randomindex = new Random().nextInt(processNames.length);
			if(lastTalk >= processNames.length){   lastTalk = 0;  } //not really random anymore
			int randomindex = lastTalk++;                          // but need guarantees or stabilize will take a long time
			String randomProcess = (String) processNames[randomindex];
			if(randomProcess.equals(NamingProtocol.myself)){
				// don't ask yourself
				timeout++; 
			}
			else if(nodes.get(randomProcess).isClient){
				// don't ask clients...
				timeout++;
			}
			else{
				logger.info("randomly chose "+randomProcess);
				sendMsg(randomProcess, msg);
				successfulsend = true;
			}
		}
	}
	
	/** establish connection and add to nodes */
	public void connect(String name, int id){
		if(nodes.containsKey(name)){
			logger.warning("already connected to process "+name);
			return;
		}
		if(nodes.containsKey(NamingProtocol.getTempName(id))){   // now you actually know who this server is
			nodes.put(name,  nodes.get(NamingProtocol.getTempName(id)));
			nodes.remove(NamingProtocol.getTempName(id));
		}
		logger.info("CONTROL: connecting to "+name+" at "+id+", client: "+NamingProtocol.isClientName(name));
		nodes.put(name, new OutStub(id, NamingProtocol.isClientName(name)));
	}
	
	/** called when a process will not be connecting again (when it is retiring, for instance) */
	public void removeNode(String nodeToDisconnect) {
		nodes.get(nodeToDisconnect).closeSock();
		nodes.remove(nodeToDisconnect);
	}
	
	/** for Master testing, disconnect to a particular node - should be invisible to the Replica*/
	public void disconnect(int id){
		for(String name : nodes.keySet()){
			if(nodes.get(name).id == id){
				logger.info("CONTROL: disconnecting "+name+" at port "+id);
				nodes.get(name).connected = false;
				return;
			}
		}
	}
	
	/** remove block caused by earlier disconnect */
	public void restoreConnection(int id){
		for(String name : nodes.keySet()){
			if(nodes.get(name).id == id){
				logger.info("CONTROL: reconnecting "+name+" at port "+id);
				nodes.get(name).connected = true;
				return;
			}
		}
	}
	
	
	// unused methods
	/*
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
	
	private synchronized void initOutgoingConn(String proc) throws IOException {
		if (outSockets.get(proc) != null)
			throw new IllegalStateException("proc " + proc + " not null");
		
		outSockets.put(proc, new OutgoingSock(new Socket(address, ports.get(proc))));
		logger.info(String.format("Server %s: Socket to %s established", 
				procNum, proc));
	}
	*/
}
