package replica;

import java.util.ArrayList;
import java.util.List;

public class PrintLog {

	/// this is a side copy of the command log, meant to make printing in the 
	/// required format much easier
	
	public final List<Command> log;
	
	public PrintLog(){
		log = new ArrayList<Command>();
	}
	
	public void add(Command logEntry){
		if(logEntry.operation instanceof AddRetireOperation){
			return;   // only log writes
		}
		
		//clone command, to be safe
		Command entry = Command.fromString(logEntry.toString());
		
		for(Command c : log){
			if((c.acceptStamp == entry.acceptStamp) && (c.serverId.equals(entry.serverId))){
				if(entry.CSN != c.CSN){   // both committed and uncommitted copy are present
					c.CSN = 1;  // make it a non-negative CSN, doesn't matter what
					return;
				}
				else{    // added same command twice
					return;
				}
			}
		}
		// haven't added this command yet:
		log.add(entry);
	}
	
	
	public void print(){
		if(log.isEmpty()){
			return;
		}
		for(Command c : log){
			String line = opToString(c.operation);
			if(c.CSN == -1){
				line += ":FALSE";
			}
			else{
				line += ":TRUE";
			}
			System.out.println(line);
		}
		System.out.println("-END");
	}
	
	
	private String opToString(Operation op){
		StringBuilder builder = new StringBuilder();
		builder.append(op.type.name());
		builder.append(':');
		builder.append(op.song);
		if(op.url != null){
			builder.append(", ");
			builder.append(op.url);
		}
		return(builder.toString());
	}
	
}
