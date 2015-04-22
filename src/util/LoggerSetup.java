package util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LoggerSetup {
	public static Logger create(String location) throws SecurityException, IOException{
		
		Handler fh = new FileHandler(location);
		fh.setLevel(Level.FINEST);
		
		Logger logger = Logger.getLogger("NetFramework");
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
		
		return logger;
	}
}
