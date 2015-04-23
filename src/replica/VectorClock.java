package replica;

import java.util.HashMap;
import java.util.Map;

public class VectorClock {
	
	public final HashMap<String, Long> clock;
	public final HashMap<String, Long> committedclock;
	
	public static final String tuplesep = "=";
	public static final String entrysep = ":";
	public static final String listsep  = "~~";
	
	public VectorClock(HashMap<String, Long> clock, HashMap<String, Long> committedclock){
		this.clock = new HashMap<String, Long>(clock);
		this.committedclock = new HashMap<String, Long>(committedclock);
	}
	
	public VectorClock(String all){
		this.clock = new HashMap<String, Long>();
		this.committedclock = new HashMap<String, Long>();
		
		String[] ff = all.split(listsep);
		if(ff.length > 0){
			String normalClock = ff[0];
			for(String entry : normalClock.split(entrysep)){
				if(!entry.isEmpty()){
					clock.put(entry.split(tuplesep)[0],Long.parseLong(entry.split(tuplesep)[1]));
				}
			}
		}
		if(ff.length > 1){
			String commitClock = ff[1];
			for(String entry : commitClock.split(entrysep)){
				if(!entry.isEmpty()){
					committedclock.put(entry.split(tuplesep)[0],Long.parseLong(entry.split(tuplesep)[1]));
				}
			}
		}
	}
	
	public String toString(){
		StringBuilder build = new StringBuilder();
		for(Map.Entry<String, Long> entry : clock.entrySet()){
			build.append(entry.getKey());
			build.append(tuplesep);
			build.append(entry.getValue());
			build.append(entrysep);
		}
		build.append(listsep);
		for(Map.Entry<String, Long> entry : committedclock.entrySet()){
			build.append(entry.getKey());
			build.append(tuplesep);
			build.append(entry.getValue());
			build.append(entrysep);
		}
		return build.toString();
	}
	
	/** returns true if set2 contains (or did contain) everything set1 contained */
	static boolean compareClocks(HashMap<String, Long> set1, HashMap<String, Long> set2){
		for(String s : set1.keySet()){
			if(Memory.completeV(set2,s) < set1.get(s)){
				return false;
			}
		}
		for(String s : set2.keySet()){
			if(Memory.completeV(set1,s) == Integer.MAX_VALUE){
				return false;
			}
		}
		return true;
	}
	
	/** returns true if clock2 contains (or did contain) everything this clock contains */
	boolean compareTo(VectorClock clock2){
		return compareClocks(this.clock, clock2.clock) &&
					compareClocks(this.committedclock, clock2.committedclock);
	}
}
