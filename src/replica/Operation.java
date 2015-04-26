package replica;

import java.io.Serializable;

public class Operation implements Serializable {
	
	public static final String SEPARATOR = "==";
	
	OperationType type;
	String song;
	String url;
	
	public Operation(OperationType type, String song, String url){
		this.type = type;
		this.song = song;
		this.url = url;
	}
	
	public static Operation operationFromString(String str) {
		String[] strSplit = str.split(Operation.SEPARATOR);
		
		OperationType optype = OperationType.valueOf(strSplit[0]);
		String opsong = strSplit[1];
		String opurl;
		if (optype == OperationType.PUT) {
			opurl = strSplit[2];
		} else {
			opurl = null;
		}
		
		Operation op = new Operation(optype,opsong,opurl);
		return op;
	}
	
	@Override
	public String toString() {
		StringBuilder operation = new StringBuilder();
		operation.append(type);
		operation.append(Operation.SEPARATOR);
		operation.append(song);
		operation.append(Operation.SEPARATOR);
		if (url == null) {
			operation.append("");
		} else {
			operation.append(url);
		}
		
		return operation.toString();
	}
}