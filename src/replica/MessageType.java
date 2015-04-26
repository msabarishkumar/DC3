package replica;

public enum MessageType {
	// Messages that define valid Operations, from Client
	OPERATION, WRITE_RESULT,
	
	// Entropy.
	ENTROPY_REQUEST, ENTROPY_COMMAND,
	
	// Read.
	READ, READ_RESULT,
	
	// DISCONNECT and CONNECT.
	DISCONNECT,
	CONNECT,
	
	// RETIRE.
	RETIRE_OK, BECOME_PRIMARY,
	
	// JOIN.
	JOIN, NAME,
}
