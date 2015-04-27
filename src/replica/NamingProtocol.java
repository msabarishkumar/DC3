package replica;

import java.util.HashMap;
import java.util.Map;

public class NamingProtocol {
	
	public static final String defaultName = "defaultName";
	public static final String referralName = "referralName";
	public static final String myself = "myself";
	public static final String serverName = "serverName";
	
	private static final String sep = ",";
	
	public static String create(String creatorId, long acceptStamp){
		return "<" + acceptStamp + sep + creatorId + ">";
	}
	
	public static String getCreator(String id){
		int split = id.indexOf(sep);
		return id.substring(split + 1, id.length() - 1);
	}
	
	public static long getClock(String id){
		int split = id.indexOf(sep);
		return Integer.parseInt(id.substring(1,split));
	}

	public static String getClientName(int uniqueId){
		return "client"+uniqueId;
	}
	
	public static boolean isClientName(String id){
		return id.startsWith("client");
	}
	
	
	public static String printNodeNames(Map<String, Integer> nodes){
		StringBuilder f = new StringBuilder();
		for(Map.Entry<String, Integer> ll : nodes.entrySet()){
			f.append(ll.getValue());
			f.append(",");
			f.append(ll.getKey());
			f.append(";");
		}
		return f.toString();
	}
	
	public static HashMap<String, Integer> readNodeNames(String s){
		HashMap<String, Integer> result = new HashMap<String, Integer>();
		for(String entry : s.split(";")){
			int comma = entry.indexOf(',');
			int port = Integer.parseInt(entry.substring(0,comma));
			String name = entry.substring(comma+1);
			result.put(name, port);
		}
		return result;
	}

}
