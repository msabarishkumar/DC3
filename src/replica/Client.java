package replica;

import java.util.HashMap;

import java.util.Scanner;
import java.util.logging.Logger;

import util.LoggerSetup;
import util.Queue;

import communication.NetController;

public class Client {
	
	private String name;
	
	private HashMap<String, Long> vector;
	
	private NetController controller;
	
	private Queue<InputPacket> queue;
	
	private Logger logger;
	
	public Client(int myId, int serverId){
		name = NamingProtocol.getClientName(myId);
		
		try {
			logger = LoggerSetup.create(LoggerSetup.defaultLogLocation() + "/logs/" + myId + ".log");	
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Start NetController and set up server connection
		this.queue = new Queue<InputPacket>();
		controller = new NetController(NamingProtocol.myself, myId, logger, queue);
		connectToServer(serverId);
		
		// create vector clock
		vector = new HashMap<String, Long>();
		vector.put("0", (long) 0);
	}
	
	/** arg0 = client's id
	 * arg1 = id of server to connect to    */
	public static void main(String[] args){
		Client me = new Client(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		me.messageThread();
		
		//me.test();
		me.listenToMaster();
		
		me.controller.shutdown();
		System.exit(1);
	}
	
	/** client only connects to one server at a time, but must be able to change the server in question */
	private void connectToServer(int serverId){
		String server = NamingProtocol.serverName;
		if(controller.nodes.containsKey(server)){
			logger.info("Closing connection to old server");
			controller.nodes.get(server).sock.cleanShutdown();
			controller.nodes.remove(server);
		}
		logger.info("connecting to server " + serverId);
		controller.connect(NamingProtocol.serverName, serverId);
		Message msgToSend = new Message(name, MessageType.CONNECT, "" + controller.nodes.get(NamingProtocol.myself).id);
		controller.sendMsg(NamingProtocol.serverName, msgToSend.toString());
	}
	
	/** Receives responses from server
	 * to update clock and inform master when Write/Read is complete */
	private void messageThread(){
		Thread th = new Thread() {
			public void run() {
				while(true) {
					checkMessage();
					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		th.start();
	}
	private void checkMessage(){
		InputPacket msgPacket = queue.poll();
		String msg = msgPacket.msg;
		logger.fine("Trying to parse: " + msg);
		Message message = Message.parseMsg(msg);

		switch(message.type){
		case WRITE_RESULT:
			VectorClock vc = new VectorClock(message.payLoad);
			logger.info("Write completed");
			increment(vc.clock);
			System.out.println("WRITTEN");
			break;
			
		case READ_RESULT:
			MessageWithClock result = new MessageWithClock(message.payLoad);
			String url = result.message;
			logger.info("Received url = "+url);
			increment(result.vector.clock);
			System.out.println(url);
			System.out.println("-END");
			break;
			
		default: 
			logger.info("received non-client message");
		}
	}
	
	/** */
	private void sendWrite(String op){
		logger.info("Sending "+op);
		// adding vectors for write guarantees
		MessageWithClock msg = new MessageWithClock(op, vector);
		Message msgToSend = new Message(name, MessageType.OPERATION, msg.toString());
		controller.sendMsg(NamingProtocol.serverName, msgToSend.toString());
	}
	
	/** */
	private void sendRead(String song){
		logger.info("Requesting "+song);
		// adding vectors for read guarantees
		MessageWithClock msg = new MessageWithClock(song, vector);
		Message msgToSend = new Message(name, MessageType.READ, msg.toString());
		controller.sendMsg(NamingProtocol.serverName, msgToSend.toString());
	}
	
	/** take MAX(thisclock, otherclock) or increment single server */
	private void increment(String server, long number){
		if(!vector.containsKey(server)  || vector.get(server) < number){
			vector.put(server, number);
		}
	}
	private void increment(HashMap<String, Long> otherclock){
		for(String s : otherclock.keySet()){
			increment(s, otherclock.get(s));
		}
	}
	
	/** for testing */
	private void printVectors(){
		System.out.println("---- client vector:");
		for(String s : vector.keySet()){
			System.out.println(s + " = "+ vector.get(s));
		}
	}

	private void listenToMaster(){
		Scanner sc = new Scanner(System.in);
		while(sc.hasNext()){
			String inputline = sc.nextLine();
			logger.info("Received " + inputline + " from master");
			
			if(inputline.equals("EXIT")){
				sc.close();
				logger.info("shutting down cause told to");
				break;
			}
			else if(inputline.startsWith("CONNECT")){
				int port = Integer.parseInt(inputline.substring(7));
				connectToServer(port);
			}
			else if(inputline.startsWith("DISCONNECT")){
				int port = Integer.parseInt(inputline.substring(10));
				// at the moment, ignore this as CONNECT gets rid of old server
				// and you will only receive instructions if properly connected
			}
			else if(inputline.startsWith("PUT") || inputline.startsWith("DELETE")){
				sendWrite(inputline);
			}
			else if(inputline.startsWith("GET")){
				sendRead(inputline.substring(3));
			}
			else{
				logger.info("I didn't understand "+inputline);
			}
		}
		logger.info("master down, shutting myself down");
		sc.close();
	}
	
	/** for testing without master constraint */
	private void test(){
		Scanner sc = new Scanner(System.in);
		while(sc.hasNext()){
			String inputline = sc.nextLine();
			
			if(inputline.equals("exit")){
				sc.close();
				logger.info("shutting down cause told to");
				break;
			}
			else if(inputline.startsWith("connect")){
				int port = Integer.parseInt(inputline.substring(7));
				connectToServer(port);
			}
			else if(inputline.equals("printclock")){
				printVectors();
			}
			else if(inputline.startsWith("READ")){
				sendRead(inputline.substring(4));
			}
			else{
				sendWrite(inputline);
			}
		}
		logger.info("master down, shutting myself down");
		sc.close();
	}
}
