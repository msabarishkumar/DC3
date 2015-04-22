package replica;

public class Input {

	public final InputType type;
	
	public final String payload;
	
	public static final String sep = "%%%";
	
	public Input(InputType type, String payload){
		this.type = type;
		this.payload = payload;
	}
	
	public Input(String all){
		type = InputType.valueOf(all.split(sep)[0]);
		payload = all.split(sep)[1];
	}
	
	public String toString(){
		return type.toString() + sep + payload;
	}
	
}
