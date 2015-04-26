package replica;

public class AddRetireOperation extends Operation {
	OperationType type;
	String process_id;
	String host;
	String port;
	
	
	public AddRetireOperation(OperationType type, String process_id, String host, String port) {
		super(null,null,null);
		this.type = type;
		this.process_id = process_id;
		this.host = host;
		this.port = port;
	}
	
	public static Operation operationFromString(String str) {
		AddRetireOperation op;
		String[] strSplit = str.split(Operation.SEPARATOR);
		
		OperationType optype = OperationType.valueOf(strSplit[0]);
		if (!(optype == OperationType.ADD_NODE || optype == OperationType.RETIRE_NODE)) {
			return Operation.operationFromString(str);
		}
		String opprocess_id = strSplit[1];
		
		if (optype == OperationType.ADD_NODE) {
			String ophost = strSplit[2];
			String opport = strSplit[3];
			op = new AddRetireOperation(optype,opprocess_id, ophost, opport);
		} else{
			op = new AddRetireOperation(optype,opprocess_id,null,null);
		}
		
		return op;
	}
	
	@Override
	public String toString() {
		StringBuilder operation = new StringBuilder();
		operation.append(type);
		operation.append(Operation.SEPARATOR);
		operation.append(process_id);
		operation.append(Operation.SEPARATOR);
		if (host == null) {
			operation.append("");
		} else {
			operation.append(host);
			operation.append(Operation.SEPARATOR);
			operation.append(port);
		}
		
		return operation.toString();
	}
}