package replica;

import java.util.ArrayList;
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
		Replica rex = new Replica(1,false);
		Memory me = rex.memory;
		
		Operation op1 = Operation.operationFromString("PUT==lo==hi");
		Command c1 = new Command(-1, 1, name, op1);
		Operation op2 = Operation.operationFromString("PUT==lo==hurr");
		Command c2 = new Command(-1, 2, name, op2);		
		Operation op3 = new AddRetireOperation(OperationType.ADD_NODE,name2,"localhost","3");
		Command c3 = new Command(-1, 3, name, op3);
		Operation op4 = Operation.operationFromString("PUT==med==sand");
		Command c4 = new Command(-1, 1, name2, op4);
		
		Command c5 = new Command(0,1,name,op1);
		
		me.tentativeClock.put("o", (long) 0);
		me.committedClock.put("o", (long) 0);
		
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
		
		me.acceptCommand(c5);
		me.printLogs();
		me.printClocks();
		System.out.println(rex.playlist.toString());
		System.out.println(rex.committedPlaylist.toString());
		
		me.buildPlaylist();
		me.printLogs();
		me.printClocks();
		System.out.println(rex.playlist.toString());
		System.out.println(rex.committedPlaylist.toString());
		me.pLog.print();
	}
	
	
	public HashMap<String, Long> tentativeClock = new HashMap<String, Long>();
	public HashMap<String, Long> committedClock = new HashMap<String, Long>();
	public List<Command> committedWriteLog = new ArrayList<Command>();
	public List<Command> deliveredWriteLog = new LinkedList<Command>();
	public List<Command> undeliveredWriteLog = new LinkedList<Command>();
	public PrintLog pLog = new PrintLog();
	public int csn = 0;
	
	public Replica replica;
	final CommandComparator comparator = new CommandComparator(); 
	
	public Memory(Replica replica){
		this.replica = replica;
		tentativeClock.put("0", (long) 0);
		committedClock.put("0", (long) 0);
	}
	
	/** Add a command to the correct log, and perform if possible*/
	public boolean acceptCommand(Command command){
		if(command.CSN == csn){              // next committed message
			replica.performCommittedOperation(command.operation);
			replica.logger.info("Committing: "+command.toString());
			committedClock.put(command.serverId, command.acceptStamp);
			committedWriteLog.add(csn, command);
			pLog.add(command);
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
			deliveredWriteLog.add(command);
			tentativeClock.put(command.serverId, command.acceptStamp);
			replica.performOperation(command.operation);
			pLog.add(command);
			if(replica.isPrimary){    // make a committed version
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
		while(!VectorClock.compareClocks(tentativeClock, committedClock) && (timeout < 10)){
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
	
	/** remove elements from tentative log if they have been committed
	 *  dangerous if not all commands have been seen by everyone, only use during stabilization */
	public void garbageCollect(){
		List<Command> logClone = new LinkedList<Command>(deliveredWriteLog);
		deliveredWriteLog.clear();
		for(Command command : logClone){
			if(completeV(committedClock, command.serverId) < command.acceptStamp){
					deliveredWriteLog.add(command);
			}
		}
	}
	
	/** commit commands, only called by primary */
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
	
	/** the next available stamp for this server, called when making new operation */
	long myNextCommand(){
		return tentativeClock.get(replica.processId)+1;
	}
	
	/** locate which commands are needed for anti-entropy */
	HashSet<Command> unseenCommands(VectorClock other, int otherCSN){
		Collections.sort(deliveredWriteLog, comparator);
		Collections.sort(committedWriteLog, comparator);
		
		HashSet<Command> output = new HashSet<Command>();
		for(Command c : deliveredWriteLog){
			if(completeV(other.clock, c.serverId) == c.acceptStamp - 1){
				output.add(c);
				other.clock.put(c.serverId, c.acceptStamp);
			}
		}
		for(Command c : committedWriteLog){
			if(c.CSN >= otherCSN){
				output.add(c);
			}
		}
		return output;
	}
	
	/** Compare client's current VC to yours*/
	boolean clientDependencyCheck(MessageWithClock message){
		return VectorClock.compareClocks(message.vector.clock, tentativeClock);
	}
	
	/** recreates play list in a set order, so that everyone's is eventually the same */
	void buildPlaylist(){
		garbageCollect(); // remove commands that were already used on CommittedPlaylist
		Collections.sort(deliveredWriteLog, comparator);
		// if all commands have been seen by all servers, then sorted command set should be the same
		replica.playlist = replica.committedPlaylist.clone();
		for(Command c : deliveredWriteLog){
			replica.playlist.performOperation(c.operation);
		}
	}
	
	
	// below are methods for testing

	public void printLogs(){
		System.out.println("---- undelivered messages:");
		for(Command c : undeliveredWriteLog){
			System.out.println(c.toString());
		}
		System.out.println("---- delivered messages:");
		for(Command c : deliveredWriteLog){
			System.out.println(c.toString());
		}
		System.out.println("---- committed messages:");
		for(Command c : committedWriteLog){
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
