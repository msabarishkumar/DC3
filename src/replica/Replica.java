package replica;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import communication.Config;
import communication.NetController;
import util.Queue;

public class Replica {
	// Process Id attached to this replica. This is not the system ProcessID, but just an identifier to identify the server.
	static String processId;
	
	// Am I the primary server.
	static boolean isPrimary;
		
	// Instance of the config associated with this replica.
	//static Config config;
	Logger logger;
	
	// Event queue for storing all the messages from the wire. This queue would be passed to the NetController.
	final Queue<InputPacket> queue;

	// This is the container which would have data about all the commands stored.
	final CommandLog cmds;
	
	// This is where we maintain all the playlist.
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
	
	public static HashMap<String, Boolean> disconnectedNodes = new HashMap<String, Boolean>();
	
	Memory memory;
	
	public Replica(String processId, int port) {
		this.processId = processId;
		this.cmds = new CommandLog(this.processId);
		
		String directory = System.getProperty("user.dir");
		if (directory.substring(directory.length() - 4).equals("/bin")){
			directory = directory.substring(0,directory.length() - 4);
		}
		
		try {
			Handler fh = new FileHandler(directory + "/logs/" + processId + ".log");
			fh.setLevel(Level.FINEST);
			setUpLogger(fh);
			
			//config = new Config(System.getProperty("CONFIG_NAME"), fh);		
			
			if (this.processId.equals("0")) {
				isPrimary = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		memory = new Memory(this);
		
		// Start NetController and start receiving messages from other servers.
		this.queue = new Queue<InputPacket>();
		//controller = new NetController(processId, port, logger, queue);
		controller = null;
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
							Operation op = Operation.operationFromString(message.payLoad);
							Command command = new Command(-1, memory.tentativeClock.get(processId), processId, op);
							memory.acceptCommand(command);
							break;
							
						case BECOME_PRIMARY:
							isPrimary = true;
							memory.commitDeliveredMessages();
						case ENTROPY:
						case READ: 
							// client stuff
							String url = playlist.read(message.payLoad);
							// send to client
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
						case RETIRE: 
							AddRetireOperation addop = (AddRetireOperation)
								AddRetireOperation.operationFromString(message.payLoad);
							if(addop.process_id.equals("unknown")){   // you're the first server to receive from this server
								addop.process_id = NamingProtocol.create(processId, memory.tentativeClock.get(processId));
							}
							Command addcommand = new Command(-1, memory.tentativeClock.get(processId), processId, addop);
							memory.acceptCommand(addcommand);
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
		Replica replica = new Replica(args[0], Integer.parseInt(args[1]));
		replica.startReceivingMessages();
	}
	
	
	private void setUpLogger(Handler fh){
		logger = Logger.getLogger("NetFramework");
		logger.setUseParentHandlers(false);
		Logger globalLogger = Logger.getLogger("global");
		Handler[] handlers = globalLogger.getHandlers();
		for(Handler handler : handlers) {
		    globalLogger.removeHandler(handler);
		}
		Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord arg0) {
                StringBuilder b = new StringBuilder();
                b.append(arg0.getMillis() / 1000);
                b.append(" || ");
                b.append("[Thread:");
                b.append(arg0.getThreadID());
                b.append("] || ");
                b.append(arg0.getLevel());
                b.append(" || ");
                b.append(arg0.getMessage());
                b.append(System.getProperty("line.separator"));
                return b.toString();
            }
        };
		fh.setFormatter(formatter);
		logger.addHandler(fh);
        LogManager lm = LogManager.getLogManager();
        lm.addLogger(logger);
		logger.setLevel(Level.FINEST);
	}
	
	
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
			//controller.connect(op.process_id, Integer.parseInt(op.port));
			memory.tentativeClock.put(op.process_id, (long) 0);
			break;
		case RETIRE_NODE:
			/// TODO: inform master that you received retirement message!
			//controller.disconnect(op.process_id);
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
	
}
