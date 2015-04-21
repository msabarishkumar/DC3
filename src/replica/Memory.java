package replica;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Memory {
	// This class has no thread or locks - synchrony must be handled outside
	
	public static void main(String[] args){
		String name = "o";
		String name2  = "<3,o>";
		Replica rex = new Replica(name,0);
		Memory me = rex.memory;
		
		Operation op1 = Operation.operationFromString("PUT==lo==hi");
		Command c1 = new Command(-1, 1, name, op1);
		Operation op2 = Operation.operationFromString("PUT==lo==hurr");
		Command c2 = new Command(-1, 2, name, op2);		
		Operation op3 = new AddRetireOperation(OperationType.ADD_NODE,name2,"localhost","3");
		Command c3 = new Command(-1, 3, name, op3);
		Operation op4 = Operation.operationFromString("PUT==med==sand");
		Command c4 = new Command(-1, 1, name2, op4);
		
		me.acceptCommand(c2);
		me.garbageCollect();
		me.printLogs();
		me.printClocks();
		System.out.println(rex.playlist.toString());
		System.out.println(rex.committedPlaylist.toString());
		
		me.acceptCommand(c1);
		me.checkUndeliveredMessages();
		me.printLogs();
		me.printClocks();
		System.out.println(rex.playlist.toString());
		System.out.println(rex.committedPlaylist.toString());
		
		me.acceptCommand(c4);
		me.printLogs();
		me.printClocks();
		System.out.println(rex.playlist.toString());
		System.out.println(rex.committedPlaylist.toString());
		
		me.acceptCommand(c3);
		me.checkUndeliveredMessages();
		me.garbageCollect();
		me.printLogs();
		me.printClocks();
		System.out.println(rex.playlist.toString());
		System.out.println(rex.committedPlaylist.toString());
		
		rex.isPrimary = true;
		me.commitDeliveredMessages();
		me.garbageCollect();
		me.printLogs();
		me.printClocks();
		System.out.println(rex.playlist.toString());
		System.out.println(rex.committedPlaylist.toString());
	}
	
	
	public HashMap<String, Long> tentativeClock = new HashMap<String, Long>();
	public HashMap<String, Long> committedClock = new HashMap<String, Long>();
	public List<Command> deliveredWriteLog = new LinkedList<Command>();
	public List<Command> undeliveredWriteLog = new LinkedList<Command>();
	public int csn = 0;
	
	public Replica replica;
	final CommandComparator comparator = new CommandComparator(); 
	
	public Memory(Replica replica){
		this.replica = replica;
		tentativeClock.put(replica.processId, (long) 0);
		committedClock.put(replica.processId, (long) 0);
	}
	
	/** Add a command to the correct log, and perform if possible*/
	public boolean acceptCommand(Command command){
		if(command.CSN == csn){              // next committed message
			replica.performCommittedOperation(command.operation);
			replica.logger.info("Committing: "+command.toString());
			committedClock.put(command.serverId, command.acceptStamp);
			csn++;
			return true;
		}
		else if (command.CSN > csn){         // higher-commit message
			replica.logger.info("Can't commit yet: "+command.toString());
			undeliveredWriteLog.add(command);
			return false;
		}
		else if (command.CSN >= 0){           // already-committed message, shouldn't occur
			replica.logger.info("Already committed this, deleting: "+command.toString());
			return false;
		}
	
		//uncommitted message
		if(completeV(tentativeClock, command.serverId) == command.acceptStamp - 1){  // ready to perform
			replica.logger.info("Performing: "+command.toString());
			replica.performOperation(command.operation);
			deliveredWriteLog.add(command);
			tentativeClock.put(command.serverId, command.acceptStamp);
			if(Replica.isPrimary){    // make a committed version
				commit(command);
			}
			return true;
		}
		else if(completeV(tentativeClock, command.serverId) < command.acceptStamp - 1){   // can't perform yet
			replica.logger.info("Can't perform yet: "+command.toString());
			undeliveredWriteLog.add(command);
			return false;
		}
			// already performed command, this shouldn't occur
		   // includes case where sender's retirement has already been seen
		replica.logger.info("Already performed this, deleting: "+command.toString());
		return false;
	}
	
	/** see if you can perform messages */
	public void checkUndeliveredMessages(){
		List<Command> logClone = new LinkedList<Command>(undeliveredWriteLog);
		Collections.sort(logClone, comparator); // sort for efficiency - earlier messages will be checked first
		undeliveredWriteLog.clear();           // emptying write log, will recreate in loop
		for(Command command : logClone){
			acceptCommand(command);
		}
	}
	
	/** commit all uncommitted messages, only called when becoming primary */
	public void commitDeliveredMessages(){
		int timeout = 0;
		while(!clocksEqual() && (timeout < 10)){
		Collections.sort(deliveredWriteLog, comparator); // sort for efficiency
		for(Command command : deliveredWriteLog){
			if(completeV(committedClock, command.serverId) == command.acceptStamp - 1){  // ready to commit
					commit(command);
			} else{
				replica.logger.info("can't commit yet...");
			}
		}
		timeout++;
		}
		if(timeout == 10){
			replica.logger.warning("commitDeliveredMessages tried 10 times and failed to complete!");
		}
	}
	
	/** remove elements for writeLog if they have been committed */
	public void garbageCollect(){
		List<Command> logClone = new LinkedList<Command>(deliveredWriteLog);
		deliveredWriteLog.clear();
		for(Command command : logClone){
			if(completeV(committedClock, command.serverId) < command.acceptStamp){
					deliveredWriteLog.add(command);
			}
		}
	}
	
	/** only called by primary */
	private void commit(Command c){
		Command command = new Command(csn, c.acceptStamp, c.serverId,c.operation);
		replica.logger.info("As primary, creating: "+command.toString());
		acceptCommand(command);
	}
	
	/** Method for distinguishing between retired and never-seen servers */
	static long completeV(HashMap<String, Long> clock, String serverId){
		if(clock.containsKey(serverId)){
			return clock.get(serverId);
		}
		if(serverId.equals("0")){
			return Integer.MAX_VALUE;
		}
		String creatorsId = NamingProtocol.getCreator(serverId);
		long recursiveresult = completeV(clock, creatorsId);
		if(recursiveresult >= NamingProtocol.getClock(serverId)){
			return Integer.MAX_VALUE;
		}
		return Integer.MIN_VALUE;
	}

	
	// below are methods for testing
	
	/** compare vector clocks, for error-checking only */
	private boolean clocksEqual(){
		for(String s : tentativeClock.keySet()){
			if(completeV(committedClock,s) < tentativeClock.get(s)){
				return false;
			}
		}
		for(String s : committedClock.keySet()){
			if(completeV(tentativeClock,s) < committedClock.get(s)){
				replica.logger.warning("commit clock higher than tentative clock");
				return false;
			}
		}
		return true;
	}
	
	public void printLogs(){
		System.out.println("---- undelivered messages:");
		for(Command c : undeliveredWriteLog){
			System.out.println(c.toString());
		}
		System.out.println("---- delivered messages:");
		for(Command c : deliveredWriteLog){
			System.out.println(c.toString());
		}
	}
	
	public void printClocks(){
		System.out.println("---- tentative clock:");
		for(String s : tentativeClock.keySet()){
			System.out.println(s + " = "+ tentativeClock.get(s));
		}
		System.out.println("---- committed clock:");
		for(String s : committedClock.keySet()){
			System.out.println(s + " = "+ committedClock.get(s));
		}
	}
}
