/**
 * This code may be modified and used for non-commercial 
 * purposes as long as attribution is maintained.
 * 
 * @author: Isaac Levy
 */

package communication;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Config {

	/**
	 * Loads config from a file.  Optionally puts in 'procNum' if in file.
	 * See sample file for syntax
	 * @param filename
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public Config(String filename, Handler fh) throws FileNotFoundException, IOException {
		
		setUpLogger(fh);
		
		Properties prop = new Properties();
		prop.load(new FileInputStream(filename));
		numProcesses = loadInt(prop,"NumProcesses");
		addresses = new HashMap<String, InetAddress>();
		ports = new HashMap<String, Integer>();
		for (int i=0; i < numProcesses; i++) {
			ports.put(prop.getProperty("proc" + i), loadInt(prop, "port" + i));
			addresses.put(prop.getProperty("proc" + i), InetAddress.getByName(prop.getProperty("host" + i).trim()));
		}
		if (prop.getProperty("procNum") != null) {
			procNum = prop.getProperty("procNum");
		} else {
			logger.info("procNum not loaded from file");
		}
	}
	
	private int loadInt(Properties prop, String s) {
		return Integer.parseInt(prop.getProperty(s.trim()));
	}
	
	/**
	 * Default constructor for those who want to populate config file manually
	 */
		public Config(Logger logger) throws UnknownHostException {
			
			this.logger = logger;
			
			numProcesses = 1;
			addresses = new HashMap<String, InetAddress>();
			ports = new HashMap<String, Integer>();
			//procNum = procnum;
			
			//addresses = new InetAddress[numProcesses];
			//ports = new int[numProcesses];
			//for(int thisConnection = 0; thisConnection < numProcesses; thisConnection++){
			//	addresses[thisConnection] = InetAddress.getLocalHost();
			//	ports[thisConnection] = basePort + thisConnection;
			//}
			
			logger = Logger.getLogger("NetFramework");
			
		}

		private void setUpLogger(Handler fh){
			logger = Logger.getLogger("NetFramework");
			
			logger.setUseParentHandlers(false);
			
			Logger globalLogger = Logger.getLogger("global");
			Handler[] handlers = globalLogger.getHandlers();
			for(Handler handler : handlers) {
			    globalLogger.removeHandler(handler);
			}
			
			Formatter formatter = new Formatter() {

	            @Override
	            public String format(LogRecord arg0) {
	                StringBuilder b = new StringBuilder();
//	                b.append("[");
//	                b.append(arg0.getSourceClassName());
//	                b.append("-");
//	                b.append(arg0.getSourceMethodName());
//	                b.append(" ");
//	                b.append("] ")
	                b.append(arg0.getMillis() / 1000);
	                b.append(" || ");
	                b.append("[Thread:");
	                b.append(arg0.getThreadID());
	                b.append("] || ");
	                b.append(arg0.getLevel());
	                b.append(" || ");
	                b.append(arg0.getMessage());
	                b.append(System.getProperty("line.separator"));
	                return b.toString();
	            }

	        };
			fh.setFormatter(formatter);
			logger.addHandler(fh);
			
	        LogManager lm = LogManager.getLogManager();
	        lm.addLogger(logger);

			logger.setLevel(Level.FINEST);
		}
		
	/**
	 * Array of addresses of other hosts.  All hosts should have identical info here.
	 */
	public HashMap<String, InetAddress> addresses;
	
	/**
	 * Array of listening port of other hosts.  All hosts should have identical info here.
	 */
	public HashMap<String, Integer> ports;
	
	/**
	 * Total number of hosts
	 */
	public int numProcesses;
	
	/**
	 * This hosts number (should correspond to array above).  Each host should have a different number.
	 */
	public String procNum;
	
	/**
	 * Logger.  Mainly used for console printing, though be diverted to a file.
	 * Verbosity can be restricted by raising level to WARN
	 */
	public Logger logger;
}
