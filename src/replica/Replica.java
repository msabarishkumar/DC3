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
	static Config config;
	Logger logger;
	
	// Event queue for storing all the messages from the wire. This queue would be passed to the NetController.
	final Queue<InputPacket> queue;

	// This is the container which would have data about all the commands stored.
	final CommandLog cmds;
	
	// This is where we maintain all the playlist.
	static final Playlist playlist = new Playlist();
	
	// Controller instance used to send messages to all the replicas.
	final NetController controller;
	
	static RetiringState retiringState = RetiringState.NOT_RETIRING;
	
	// When we request some nodes to retire, we maintain a count that how
	// many have replied.
	HashMap<String, InetAddress> retireRequestMap;
	
	// ProcessId of the server, for which this replica has stopped accepting
	// requests for more retires.
	String parentRetiringProcessId;
	
	public static boolean isIsolated;
	
	public static HashMap<String, Boolean> disconnectedNodes = new HashMap<String, Boolean>();
	
	public Replica(String processId, int port) {
		this.processId = processId;
		this.cmds = new CommandLog(this.processId);
		
		try {
			Handler fh = new FileHandler(System.getProperty("LOG_FOLDER") + "/" + processId + ".log");
			fh.setLevel(Level.FINEST);
			setUpLogger(fh);
			
			config = new Config(System.getProperty("CONFIG_NAME"), fh);		
			
			if (this.processId.equals("0")) {
				isPrimary = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		// Start NetController and start receiving messages from other servers.
		this.queue = new Queue<InputPacket>();
		controller = new NetController(processId, port, logger, queue);
	}
	
	public void startReceivingMessages() {
		Thread th = new Thread() {
			public void run() {
				while(true) {
					InputPacket msgPacket = queue.poll();
					String msg = msgPacket.msg;
					config.logger.fine("Trying to parse: " + msg);
					Message message = Message.parseMsg(msg);
		
					switch (message.type) {
						case OPERATION:
						case BECOME_PRIMARY:
						case ENTROPY:
						case READ: 
						case DISCONNECT: 
						case CONNECT: 
						case RETIRE: 
						case STOP_RETIRING:
						case STOP_RETIRING_REJECTED:
						case STOP_RETIRING_APPROVED: 
						case SET_FREE: 
						case JOIN:
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
//                b.append("[");
//                b.append(arg0.getSourceClassName());
//                b.append("-");
//                b.append(arg0.getSourceMethodName());
//                b.append(" ");
//                b.append("] ")
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
}
