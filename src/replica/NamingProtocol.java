package replica;

public class NamingProtocol {
	
	public static void main(String[] args){
		int f = Integer.MAX_VALUE;
		int g = f + 1;
		System.out.println(f);
		System.out.println(g);
	}
	
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
}
