import java.io.BufferedReader;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Master {
	
  public static void main(String [] args) {
	
	// where all replicas and clients are stored
	HashMap<Integer, ProcessHandler> processes = new HashMap<Integer,ProcessHandler>();
	// so that pause/start/stabilize can be sent to only non-retired replicas, not clients
	Set<Integer> awakeServers = new HashSet<Integer>();
	// a counter so that replicas can know if they are fully caught up during stabilize
	int numOperations = 0;
	// 
	HashMap<Integer, HashSet<Integer>> cuts = new HashMap<Integer, HashSet<Integer>>();
	
    Scanner scan = new Scanner(System.in);
    while (scan.hasNextLine()) {
      String [] inputLine = scan.nextLine().split(" ");
      int clientId, serverId, id1, id2;
      String songName, URL;
      switch (inputLine[0]) {
        case "joinServer":
            serverId = Integer.parseInt(inputLine[1]);
            /*
             * Start up a new server with this id and connect it to all servers
             */
            if(awakeServers.isEmpty()){  // you're the first server
            	processes.put(serverId,createFirstServer(serverId));
            }
            else{
            	int serverToTalkTo = Collections.min(awakeServers);   // choose the oldest server to talk to
            	processes.put(serverId,createServer(serverId, serverToTalkTo));
            	blockUntil(processes.get(serverId).in, "NAMED");
            }
            awakeServers.add(serverId);
            numOperations++;
            cuts.put(serverId, new HashSet<Integer>());
            try {
            	Thread.sleep(100);
			} catch (InterruptedException e) { e.printStackTrace(); }
            break;
            
            
        case "retireServer":
            serverId = Integer.parseInt(inputLine[1]);
            /*
             * Retire the server with the id specified. This should block until
             * the server can tell another server of its retirement
             */
            processes.get(serverId).out.println("RETIRE");
            blockUntil(processes.get(serverId).in, "RETIRED");
            awakeServers.remove(serverId);
            numOperations++;
            cuts.remove(serverId);
            break;
            
            
        case "joinClient":
            clientId = Integer.parseInt(inputLine[1]);
            serverId = Integer.parseInt(inputLine[2]);
            /*
             * Start a new client with the id specified and connect it to 
             * the server
             */
            processes.put(clientId,createClient(clientId, serverId));
            break;
            
            
        case "breakConnection":
        	id1 = Integer.parseInt(inputLine[1]);
	    	id2 = Integer.parseInt(inputLine[2]);
            /*
             * Break the connection between a client and a server or between
             * two servers
             */
	    	processes.get(id1).out.println("DISCONNECT"+id2);
	    	processes.get(id2).out.println("DISCONNECT"+id1);
	    	if(awakeServers.contains(id1) && awakeServers.contains(id2)){
	    		cuts.get(id1).add(id2);
	    		cuts.get(id2).add(id1);
	    	}
            break;
            
            
        case "restoreConnection":
	    	id1 = Integer.parseInt(inputLine[1]);
	    	id2 = Integer.parseInt(inputLine[2]);
            /*
             * Restore the connection between a client and a server or between
             * two servers
             */
	    	processes.get(id1).out.println("CONNECT"+id2);
	    	processes.get(id2).out.println("CONNECT"+id1);
	    	if(awakeServers.contains(id1) && awakeServers.contains(id2)){
	    		cuts.get(id1).remove(id2);
	    		cuts.get(id2).remove(id1);
	    	}
            break;
            
            
        case "pause":
            /*
             * Pause the system and don't allow any Anti-Entropy messages to
             * propagate through the system
             */
        	for(int i : awakeServers){
        		processes.get(i).out.println("PAUSE");
        	}
            break;
            
            
        case "start":
            /*
             * Resume the system and allow any Anti-Entropy messages to
             * propagate through the system
             */
        	for(int i : awakeServers){
        		processes.get(i).out.println("START");
        	}
            break;
            
            
        case "stabilize":
            /*
             * Block until there are enough Anti-Entropy messages for all values to 
             * propagate through the currently connected servers. In general, the 
             * time that this function blocks for should increase linearly with the 
             * number of servers in the system.
             */
        	long longestchain = awakeServers.size() - 1;
    		long timeToWait = longestchain * longestchain * 500;
    		
        	if(allConnected(cuts)){
        		 // there are no partitions, so wait until everyone is up to date
        		//  EDIT: must time out anyways, because dependent writes can just be ignored
        		int numStable = 0;
        		long timeWaited = 0;
        		while((numStable < awakeServers.size()) && (timeWaited < timeToWait)){
        			numStable = 0;
    				try {
						Thread.sleep( 1000 );
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
    				timeWaited += 1000;
        			for(int i : awakeServers){
        				processes.get(i).out.println("STABILIZE" + numOperations);
        				String response = blockUntil(processes.get(i).in, "STABLE_");
        				if(response.equals("YES")){
        					numStable++;
        				}
        			}
        		}
        	}
        	else{
        		// its very hard to make guarantees for a partitioned system, so just wait for a long time
        		try {
					Thread.sleep(timeToWait);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
        		for(int i : awakeServers){
            		processes.get(i).out.println("STABILIZE" + 0);
            		blockUntil(processes.get(i).in, "STABLE_");
            	}
        	}
            break;
            
            
        case "printLog":
            serverId = Integer.parseInt(inputLine[1]);
            /*
             * Print out a server's operation log in the format specified in the
             * handout.
             */
            processes.get(serverId).out.println("PRINTLOG");
            readAndPrint(processes.get(serverId).in);
            break;
            
            
        case "put":
            clientId = Integer.parseInt(inputLine[1]);
            songName = inputLine[2];
            URL = inputLine[3];
            /*
             * Instruct the client specified to associate the given URL with the given
             * songName. This command should block until the client communicates with
             * one server.
             */
            //String putline = new Operation(OperationType.PUT, songName, URL).toString();
            String putline = "PUT=="+songName+"=="+URL;   // because importing Operation is a pain outside of /bin
            processes.get(clientId).out.println(putline);
            blockUntil(processes.get(clientId).in, "WRITTEN");
            numOperations++;
            break;
            
            
        case "get":
            clientId = Integer.parseInt(inputLine[1]);
            songName = inputLine[2];
            /*
             * Instruct the client specified to attempt to get the URL associated with
             * the given songName. The value should then be printed to standard out of 
             * the master script in the format specified in the handout. This command 
             * should block until the client communicates with one server.
             */
            processes.get(clientId).out.println("GET"+songName);
            System.out.print(songName+":");
            readAndPrint(processes.get(clientId).in);
            break;
            
            
        case "delete":
            clientId = Integer.parseInt(inputLine[1]);
            songName = inputLine[2];
            /*
             * Instruct the client to delete the given songName from the playlist. 
             * This command should block until the client communicates with one server.
             */ 
            //String deleteline = new Operation(OperationType.DELETE, songName, "").toString();
            String deleteline = "DELETE=="+songName+"==";
            processes.get(clientId).out.println(deleteline);
            blockUntil(processes.get(clientId).in, "WRITTEN");
            numOperations++;
            break;
            
            
        case "echo":    //for testing
        	System.out.println("echo");
        	break;
        default:
        	System.out.println("I didn't understand that command");
      }
    }
    //shut down
    for(ProcessHandler process : processes.values()){
    	process.out.println("EXIT");
    }
    scan.close();
  }

  
  private static ProcessHandler createFirstServer(int uniqueId){
	  ProcessBuilder pb = new ProcessBuilder("java","replica.Replica",""+uniqueId);
	  pb.directory(getProjectLocation());
	  pb.redirectErrorStream();
		
	  Process process =  null;
	  try {
		  process = pb.start();
	  } catch (IOException e) {
		  e.printStackTrace();
	  }

	  return new ProcessHandler(uniqueId,process);
  }

  private static ProcessHandler createServer(int uniqueId, int serverToTalkTo){
	  ProcessBuilder pb = new ProcessBuilder("java","replica.Replica",""+uniqueId,""+serverToTalkTo);
	  pb.directory(getProjectLocation());
	  pb.redirectErrorStream();
		
	  Process process =  null;
	  try {
		  process = pb.start();
	  } catch (IOException e) {
		  e.printStackTrace();
	  }

	  return new ProcessHandler(uniqueId,process);
  }
  
  private static ProcessHandler createClient(int uniqueId, int serverToTalkTo){
	  ProcessBuilder pb = new ProcessBuilder("java","replica.Client",""+uniqueId,""+serverToTalkTo);
	  pb.directory(getProjectLocation());
	  pb.redirectErrorStream();
		
	  Process process =  null;
	  try {
		  process = pb.start();
	  } catch (IOException e) {
		  e.printStackTrace();
	  }

	  return new ProcessHandler(uniqueId,process);
  }
  
  
  /** location of .class files, set up so that Master can run from either /bin or one level above */
  private static File getProjectLocation(){
		String directory = System.getProperty("user.dir");
		if (!directory.substring(directory.length() - 4).equals("/bin")){
			directory = directory + "/bin";
		}
		return new File(directory);
  }
  
  
  /** Simple method to block until a desired response, throws out any other input
   *  returns anything printed after codeword (in same line) */
  private static String blockUntil(BufferedReader input, String codeword){
	  while(true){
		  try{
			  String line = input.readLine();
			  if(line == null){
			  }
			  else if(line.startsWith(codeword)){
				  return line.substring(codeword.length());
			  }
			  else{
				  System.out.println("Was blocking for "+codeword+",");
				  System.out.println("   but got "+line);
				  return "";
			  }
		  } catch(IOException e){ e.printStackTrace(); }
	  }
  }
  
  /** blocks until it receives "-END", or hits the end of file.  Prints anything else it receives */
  private static void readAndPrint(BufferedReader input){
	  while(true){
		  try {
			  String line;
		
			  line = input.readLine();
			  if(line == null){
				  System.out.println("server is down");
				  return;
			  }
			  else if(line.equals("-END")){
				  return;
			  }
			  else{
				  System.out.println(line);
			  }
		  } catch (IOException e) { e.printStackTrace(); }
	  }
  }
  
  
  /** determines if there is a partition in the replica graph
   *  this may overestimate partitions in very sparse cases , like test 1_2*/
  private static boolean allConnected(HashMap<Integer, HashSet<Integer>> cuts){
	  
	  Set<Integer> servers = new HashSet<Integer>(cuts.keySet());
	  recursiveCut(cuts, servers, servers.iterator().next());
	  return servers.isEmpty();
  }
  
  private static void recursiveCut(HashMap<Integer, HashSet<Integer>> cuts, Set<Integer> servers, int thisServer){
	  servers.remove(thisServer);
	  if(servers.isEmpty()){
		  return;
	  }
	  for(int i : cuts.keySet()){
		  if(servers.contains(i)){
			  if(!cuts.get(thisServer).contains(i)){
				  recursiveCut(cuts, servers, i);
			  }
		  }
	  }
  }
  
}