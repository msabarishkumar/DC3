package replica;

import java.util.HashMap;

public class MessageWithClock {
	
	public VectorClock2 vector;
	public String message;
	
	public static final String SEP = ";;;";
	
	public MessageWithClock(String payload, HashMap<String, Long> vector){
		this.message = payload;
		this.vector = new VectorClock2(vector);
	}
	
	// hijacking VectorClock2's read and toString methods
	public MessageWithClock(String all){
		String[] pieces = all.split(SEP);
		message = pieces[0];
		if(pieces.length > 1){
			vector = new VectorClock2(pieces[1]);
		}
		else{
			vector = new VectorClock2();
		}
	}
	
	public String toString(){
		return message + SEP + vector.toString();
	}
}