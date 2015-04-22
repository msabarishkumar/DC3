package replica;

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

	// This is the container which would have data about all the commands stored.
	final CommandLog cmds;
	
	// This is where we maintain all the play lists.
	static final Playlist playlist = new Playlist();
	static final Playlist committedPlaylist = new Playlist();
	
	// Controller instance used to send messages to all the replicas.
	final NetController controller;
	
	//static RetiringState retiringState = RetiringState.NOT_RETIRING;
	
	// When we request some nodes to retire, we maintain a count that how
	// many have replied.
	//HashMap<String, InetAddress> retireRequestMap;
	
	// ProcessId of the server, for which this replica has stopped accepting
	// requests for more retires.
	//String parentRetiringProcessId;
	
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
		
		this.cmds = new CommandLog(processId);
		
		String directory = System.getProperty("user.dir");
		if (directory.substring(directory.length() - 4).equals("/bin")){
			directory = directory.substring(0,directory.length() - 4);
		}
		
		try {
			logger = LoggerSetup.create(directory + "/logs/" + uniqueId + ".log");	
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		memory = new Memory(this);
		
		// Start NetController and start receiving messages from other servers.
		this.queue = new Queue<InputPacket>();
		controller = new NetController(processId, portNum, logger, queue);
		//controller = null;
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
							if(processId.equals(NamingProtocol.defaultName)){
								logger.info("cannot perform operation, because I have not been named");
								break;
							}
							Operation op = Operation.operationFromString(message.payLoad);
							Command command = new Command(-1, memory.myNextCommand(), processId, op);
							memory.acceptCommand(command);
							break;
							
						case BECOME_PRIMARY:
							isPrimary = true;
							memory.commitDeliveredMessages();
							break;
							
						case ENTROPY_REQUEST:
							VectorClock receivedClock = new VectorClock(message.payLoad);
							Set<Command> allcommands = memory.unseenCommands(receivedClock);
							for(Command commandToSend : allcommands){
								Message msgToSend = new Message(processId, MessageType.ENTROPY_COMMAND, commandToSend.toString());
								controller.sendMsg(message.process_id, msgToSend.toString());
							}
							break;
							
						case ENTROPY_COMMAND:
							memory.acceptCommand(Command.fromString(message.payLoad));
							break;

						case READ: 
							  // client stuff
							String url = playlist.read(message.payLoad);
							  // send to client
							
							System.out.println(url);
							break;
							
						case DISCONNECT: 
							controller.disconnect(message.payLoad);
							break;
							
						case CONNECT:
							String id = message.payLoad.substring(3);
							int port = Integer.parseInt( message.payLoad.substring(0,3) );
							controller.connect(id, port);
							break;
							
						case JOIN:
							AddRetireOperation joinop = (AddRetireOperation)
													AddRetireOperation.operationFromString(message.payLoad);
							if(processId.equals(NamingProtocol.defaultName)){
								logger.warning("Received JOIN, but have no name myself so can do nothing");
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
							// you now know the other server's Bayou name as well...
							controller.outSockets.put(message.process_id, controller.outSockets.get(NamingProtocol.referralName));
							controller.outSockets.remove(NamingProtocol.referralName);
							controller.ports.put(message.process_id, controller.ports.get(NamingProtocol.referralName));
							controller.ports.remove(NamingProtocol.referralName);
							logger.info("done naming");
							break;
							
						case STOP_RETIRING:
						case STOP_RETIRING_REJECTED:
						case STOP_RETIRING_APPROVED: 
						case SET_FREE: 
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
		controller.connect(NamingProtocol.referralName, referralPort);
		controller.sendMsg(NamingProtocol.referralName, msgToSend.toString());
	}
	
	/** writes to play list, called from Memory */
	void performOperation(Operation op){
		if(op instanceof AddRetireOperation){
			performAddRetireOp( (AddRetireOperation) op);
			return;
		}		
		// TODO: check client conditions
		try {
			playlist.performOperation(op);
		} catch (SongNotFoundException e) {
			e.printStackTrace();
		}
	}
	

	private void performAddRetireOp(AddRetireOperation op){
		switch (op.type){
		case ADD_NODE:
			controller.connect(op.process_id, Integer.parseInt(op.port));
			memory.tentativeClock.put(op.process_id, (long) 0);
			break;
		case RETIRE_NODE:
			/// TODO: inform master that you received retirement message!
			controller.disconnect(op.process_id);
			memory.tentativeClock.remove(op.process_id);
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
			else{
				logger.info("ran performAddRetireOp on wrong operation: "+addop.type);
			}
		}
		else{
			try {
				committedPlaylist.performOperation(op);
			} catch (SongNotFoundException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void retire(){
		Operation op = new AddRetireOperation(OperationType.RETIRE_NODE,processId,"localhost",""+portNum);
		Command command = new Command(-1, memory.myNextCommand(), processId, op);
		memory.acceptCommand(command);
		// 
		//entropy()
		if(isPrimary){
			logger.info("Passing primary status");
			Message msgToSend = new Message("",MessageType.BECOME_PRIMARY,"blablabla");
			controller.sendMsgToRandom(msgToSend.toString());   // someone else becomes primary, doesn't matter who
		}
		logger.info("Shutting down");
		// TODO: actually shut down
	}
	
	private void antiEntropy(){
		VectorClock mycurrentclock = new VectorClock(memory.tentativeClock, memory.committedClock);
		Message msg = new Message(processId, MessageType.ENTROPY_REQUEST,mycurrentclock.toString());
		controller.sendMsgToRandom(msg.toString());
	}
	
	/** takes line input and sends it as a message to itself, to run without client / master interface */
	private void test(){
		Scanner sc = new Scanner(System.in);
		while(sc.hasNext()){
			String inputline = sc.nextLine();
			
			if(inputline.equals("retire")){
				retire();
				sc.close();
				return;
			}
			else if(inputline.equals("print")){
				memory.printClocks();
				memory.printLogs();
			}
			else if(inputline.equals("entropy")){
				antiEntropy();
			}
			else{
				controller.sendMsg("myself",inputline);
			}
		}
		sc.close();
	}
	
}
