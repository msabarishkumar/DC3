package replica;

import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

import util.LoggerSetup;
import util.Queue;

import communication.NetController;

public class Client {

	private int uniqueId;
	
	private HashMap<String, Long> vector;
	
	private NetController controller;
	
	private Queue<InputPacket> queue;
	
	private Logger logger;
	
	private int basePort = 5000;
	
	public Client(int myId, int serverId){
		uniqueId = myId;
		
		try {
			logger = LoggerSetup.create(LoggerSetup.defaultLogLocation() + "/logs/" + uniqueId + ".log");	
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Start NetController and start receiving messages from other servers.
		this.queue = new Queue<InputPacket>();
		controller = new NetController(NamingProtocol.myself, basePort + uniqueId, logger, queue);
		
		// connect to server
		controller.connect(NamingProtocol.serverName, basePort + serverId);
		int fd = basePort + uniqueId;
		Message msgToSend = new Message(NamingProtocol.clientName, MessageType.CONNECT, "" + fd);
		controller.sendMsg(NamingProtocol.serverName, msgToSend.toString());
		
		vector = new HashMap<String, Long>();
		vector.put("0", (long) 0);
	}
	
	/** arg0 = client's id
	 * arg1 = id of server to connect to    */
	public static void main(String[] args){
		
		Client me = new Client(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		me.messageThread();
		
		Scanner sc = new Scanner(System.in);
		while(sc.hasNext()){
			String inputline = sc.nextLine();
			
			if(inputline.equals("exit")){
				sc.close();
				me.logger.info("shutting down cause told to");
				System.exit(0);
			}
			else if(inputline.equals("printclock")){
				me.printVectors();
			}
			else if(inputline.startsWith("READ")){
				me.sendRead(inputline.substring(4));
			}
			else{
				me.sendWrite(inputline);
			}
		}
		me.logger.info("master down, shutting myself down");
		sc.close();
		
	}
	
	
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
			long wid = Long.parseLong(message.payLoad);
			logger.info("Write completed, number "+wid);
			increment(message.process_id, wid);
			System.out.println("WRITE_FIN");
			break;
			
		case READ_RESULT:
			MessageWithClock result = new MessageWithClock(message.payLoad);
			String url = result.message;
			logger.info("Received url = "+url);
			increment(result.vector.clock);
			System.out.println(url);
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
		Message msgToSend = new Message(NamingProtocol.clientName, MessageType.OPERATION, msg.toString());
		controller.sendMsg(NamingProtocol.serverName, msgToSend.toString());
	}
	
	/** */
	private void sendRead(String name){
		logger.info("Requesting "+name);
		// adding vectors for read guarantees
		MessageWithClock msg = new MessageWithClock(name, vector);
		Message msgToSend = new Message(NamingProtocol.clientName, MessageType.READ, msg.toString());
		controller.sendMsg(NamingProtocol.serverName, msgToSend.toString());
	}
	
	/** take MAX(thisclock, otherclock) or increment single server */
	private void increment(String server, long number){
		if(Memory.completeV(vector, server) < number){
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

}
