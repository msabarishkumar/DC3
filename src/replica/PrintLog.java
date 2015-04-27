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
	
	public void add(Command addition){
		if(addition.operation instanceof AddRetireOperation){
			return;   // only log writes
		}
		
		//clone command, to be safe
		Command base = Command.fromString(addition.toString());
		Command alternate = Command.fromString(addition.toString());
		if(base.CSN == -1){
			alternate.CSN = 1;
		}
		else {
			alternate.CSN = -1;
		}
		for(Command c : log){
			if(c.equals(alternate)){   // both committed and uncommitted copy are present
				c.CSN = 1;    // make it a non-negative CSN, doesn't matter what
				return;
			}
			else if(c.equals(base)){   // added same command twice
				System.out.println("error, added command twice: "+c.toString());
				return;
			}
		}
		// haven't added this command yet:
		log.add(base);
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
