package replica;

import java.util.HashMap;
import java.util.Scanner;

import java.util.Set;
import java.util.logging.Logger;
import communication.NetController;
import util.LoggerSetup;
import util.Queue;

public class Replica {
	// Process Id attached to this replica. This is not the system ProcessID, but just an identifier to identify the server.
	static String processId;
	
	// this is the process ID as seen by the tester, as well as the socket port number
	// it is only used before a proper name has been chosen
	final int portNum;
	
	// Am I the primary server.
	static boolean isPrimary;
		
	// logger for entire process
	Logger logger;
	
	// Event queue for storing all the messages from the wire. This queue would be passed to the NetController.
	final Queue<InputPacket> queue;
	
	// This is where we maintain all the play lists.
	static Playlist playlist = new Playlist();
	static final Playlist committedPlaylist = new Playlist();
	
	// Controller instance used to send messages to all the replicas.
	final NetController controller;
	
	// if true, the server refuses any write requests and shuts down when it is safe to
	public boolean retiring;
	
	public static boolean isIsolated;
	
	Memory memory;
	
	public Replica(int uniqueId) {
		
		this.portNum = 5000 + uniqueId;
		
		if(uniqueId == 0){  // you are the first of your kind
			processId = "0";
			isPrimary = true;
		}
		else{
			processId = NamingProtocol.defaultName;
		}
		
		try {
			logger = LoggerSetup.create(LoggerSetup.defaultLogLocation() + "/logs/" + uniqueId + ".log");	
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		memory = new Memory(this);
		
		// Start NetController and start receiving messages from other servers.
		this.queue = new Queue<InputPacket>();
		controller = new NetController(processId, portNum, logger, queue);
	}
	
	public void startReceivingMessages() {
		Thread th = new Thread() {
			public void run() {
				while(true) {
					InputPacket msgPacket = queue.poll();
					String msg = msgPacket.msg;
					logger.fine("Trying to parse: " + msg);
					Message message = Message.parseMsg(msg);

					switch (message.type) {
						case OPERATION:
							MessageWithClock opmess = new MessageWithClock(message.payLoad);
							if(processId.equals(NamingProtocol.defaultName)){
								logger.info("cannot perform operation, because I have not been named yet");
							}
							else if(retiring){
								logger.info("Cannot perform operation, am retiring");
							}
							else if(!memory.clientDependencyCheck(opmess)){
								logger.info("Can't write " + opmess.message + ", dependency issue");
							}
							else{    // free to write
								Operation op = Operation.operationFromString(opmess.message);
								Command command = new Command(-1, memory.myNextCommand(), processId, op);
								memory.acceptCommand(command);
							}
							Message responseMessage = new Message(processId, MessageType.WRITE_RESULT, writeResponse().toString());
							controller.sendMsg(message.process_id, responseMessage.toString());
							break;
							
						case BECOME_PRIMARY:
							isPrimary = true;
							memory.commitDeliveredMessages();
							break;
							
						case ENTROPY_REQUEST:
							memory.checkUndeliveredMessages();
							MessageWithClock receivedClock = new MessageWithClock(message.payLoad);
							Set<Command> allcommands = memory.unseenCommands(receivedClock.vector, Integer.parseInt(receivedClock.message));
							for(Command commandToSend : allcommands){
								Message msgtoSend = new Message(processId, MessageType.ENTROPY_COMMAND, commandToSend.toString());
								controller.sendMsg(message.process_id, msgtoSend.toString());
							}
							break;
							
						case ENTROPY_COMMAND:
							memory.acceptCommand(Command.fromString(message.payLoad));
							break;

						case READ: 
							MessageWithClock clientMessage = new MessageWithClock(message.payLoad);
							String url;
							if(memory.clientDependencyCheck(clientMessage)){
								url = playlist.read(clientMessage.message);
							}
							else{
								url = "ERR_DEP";
							}
							MessageWithClock response = new MessageWithClock(url,memory.tentativeClock);
							Message msgtoSend = new Message(processId, MessageType.READ_RESULT, response.toString());
							controller.sendMsg(message.process_id, msgtoSend.toString());
							break;
							
						case DISCONNECT: 
							controller.disconnect(message.payLoad);
							break;
							
						case CONNECT:    /// currently used for client connection only
							logger.info("Connected to client " + message.payLoad);
							int port = Integer.parseInt( message.payLoad );
							controller.connect(message.process_id, port);
							break;
							
						case JOIN:
							AddRetireOperation joinop = (AddRetireOperation)
													AddRetireOperation.operationFromString(message.payLoad);
							if(processId.equals(NamingProtocol.defaultName)){
								logger.warning("Received JOIN, but have no name myself so can do nothing");
								break;
							}
							if(retiring){
								logger.warning("Received JOIN, but am retiring");
								break;
							}
							if(!joinop.process_id.equals(NamingProtocol.defaultName)){
								logger.warning("Received JOIN from already-named server");
								break;
							}
							String newName = NamingProtocol.create(processId, memory.myNextCommand());
							logger.info("Naming server at port "+joinop.port+": "+newName);
							joinop.process_id = newName;
							Message nameMessage = new Message(processId, MessageType.NAME, newName);
							Command addcommand = new Command(-1, memory.myNextCommand(), processId, joinop);
							memory.acceptCommand(addcommand);
							controller.sendMsg(newName, nameMessage.toString());
							break;
						
						case NAME:     // you have a name now
							logger.info("Have been named: "+message.payLoad);
							processId = message.payLoad;
							memory.tentativeClock.put(processId, (long) 0);

							// you now know the other server's Bayou name as well...
							if(controller.ports.containsKey(NamingProtocol.referralName)){
							controller.outSockets.put(message.process_id, controller.outSockets.get(NamingProtocol.referralName));
							controller.outSockets.remove(NamingProtocol.referralName);
							controller.ports.put(message.process_id, controller.ports.get(NamingProtocol.referralName));
							controller.ports.remove(NamingProtocol.referralName);
							}
							logger.info("done naming");
							antiEntropy();
							break;
							
						case RETIRE_OK:
							if(isPrimary){
								logger.info("Passing primary status");
								Message msgToSend = new Message("",MessageType.BECOME_PRIMARY,"a");
								controller.sendMsg(message.process_id, msgToSend.toString());
								// someone else becomes primary, doesn't matter who
							}
							System.exit(1);
							
						default:
							logger.warning("received client-side message");
					}
				}
			}

		};
		th.start();
	}
	
	
	public static void main(String[] args) {
		Replica replica = new Replica(Integer.parseInt(args[0]));
		if(!isPrimary){
			replica.askForName(5000 + Integer.parseInt(args[1]));
		}
		replica.startReceivingMessages();
		
		replica.test();
		
		replica.logger.info("   master down, shutting myself down");
		System.exit(1);
	}
	
	/** used at beginning - server must notify other server that it is joining
	 * The other server will decide its name */
	private void askForName(int referralPort){
		Operation op = new AddRetireOperation(OperationType.ADD_NODE,
				NamingProtocol.defaultName, "localhost", ""+portNum);
		Message msgToSend = new Message(NamingProtocol.defaultName, MessageType.JOIN, op.toString());
		if(referralPort == 5000){    // you're asking Mr. 0
			controller.sendMsg("0", msgToSend.toString());
		}
		else{           // asking someone else, must use a temporary name until you learn their name
			controller.connect(NamingProtocol.referralName, referralPort);
			controller.sendMsg(NamingProtocol.referralName, msgToSend.toString());
		}
	}
	
	/** writes to play list, called from Memory */
	void performOperation(Operation op){
		if(op instanceof AddRetireOperation){
			performAddRetireOp( (AddRetireOperation) op);
			return;
		}
		playlist.performOperation(op);
	}
	

	private void performAddRetireOp(AddRetireOperation op){
		switch (op.type){
		case ADD_NODE:
			if (op.process_id.equals(processId))   // I already know that I've joined
				break;
			controller.connect(op.process_id, Integer.parseInt(op.port));
			memory.tentativeClock.put(op.process_id, (long) 0);
			break;
		case RETIRE_NODE:
			memory.tentativeClock.remove(op.process_id);
			if(op.process_id.equals(processId)){
				// sent to myself
				if(memory.tentativeClock.isEmpty()){   // I was the only server left
					logger.info("only server left and retiring, shutting down");
					System.exit(1);
				}
				else{
					System.out.println(""+memory.tentativeClock.size()+"nodes left");
				}
			}
			else{
				logger.info("Acknowledging retirement of "+op.process_id);
				controller.sendMsg(op.process_id, new Message(processId, MessageType.RETIRE_OK,"a").toString());
				controller.disconnect(op.process_id);
			}
			break;
		default: logger.info("ran performAddRetireOp on wrong operation: "+op.type);
		}
	}

	
	void performCommittedOperation(Operation op){
		if(op instanceof AddRetireOperation){
			AddRetireOperation addop = (AddRetireOperation) op;
			if(addop.type == OperationType.ADD_NODE){
				memory.committedClock.put(addop.process_id, (long) 0);
			}
			else if(addop.type == OperationType.RETIRE_NODE){
				memory.committedClock.remove(addop.process_id);
			}
		}
		else{
			committedPlaylist.performOperation(op);
		}
	}
	
	private void retire(){
		Operation op = new AddRetireOperation(OperationType.RETIRE_NODE,processId,"localhost",""+portNum);
		Command command = new Command(-1, memory.myNextCommand(), processId, op);
		memory.acceptCommand(command);
		logger.info("Retiring at next opportunity");
		retiring = true;
	}
	
	private void antiEntropy(){
		MessageWithClock clockandcsn = new MessageWithClock(""+memory.csn, memory.tentativeClock);
		Message msg = new Message(processId, MessageType.ENTROPY_REQUEST,clockandcsn.toString());
		controller.sendMsgToRandom(msg.toString());
	}
	
	private VectorClock writeResponse(){
		long Wid = memory.myNextCommand() - 1;
		HashMap<String, Long> emptyclock = new HashMap<String, Long>();
		emptyclock.put(processId, Wid);
		return new VectorClock(emptyclock);
	}
	
	/** takes line input and sends it as a message to itself, to run without client / master interface */
	private void test(){
		Scanner sc = new Scanner(System.in);
		while(sc.hasNext()){
			String inputline = sc.nextLine();
			
			if(inputline.equals("retire")){
				retire();
			}
			else if(inputline.equals("print")){
				memory.printClocks();
				memory.printLogs();
			}
			else if(inputline.equals("rebuild")){
				memory.buildPlaylist();
			}
			else if(inputline.equals("printlog")){
				memory.pLog.print();
			}
			else if(inputline.equals("printlist")){
				System.out.println(playlist.toString());
			}
			else if(inputline.equals("entropy")){
				antiEntropy();
			}
			else if(inputline.equals("check")){
				memory.checkUndeliveredMessages();
			}
			else{
				controller.sendMsg(NamingProtocol.myself,inputline);
			}
		}
		sc.close();
	}
	
}
