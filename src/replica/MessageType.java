package replica;

public enum MessageType {
	// Messages that define valid Operations, from Client
	OPERATION,
	
	// Entropy.
	ENTROPY_REQUEST, ENTROPY_COMMAND,
	
	// Read.
	READ,
	
	// DISCONNECT and CONNECT.
	DISCONNECT,
	CONNECT,
	
	// RETIRE.
	RETIRE_OK, BECOME_PRIMARY,
	
	// JOIN.
	JOIN, NAME,
}
