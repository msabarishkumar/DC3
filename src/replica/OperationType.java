package replica;

import java.io.Serializable;

public enum OperationType implements Serializable {
	PUT, DELETE, GET, RETIRE_NODE, ADD_NODE;
}
