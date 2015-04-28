package util;

import java.io.BufferedReader;
import java.io.PrintWriter;

public class ProcessHandler {

	public int processID;
	public Process process;
	public BufferedReader in;
	public PrintWriter out;
	
	public ProcessHandler(int id, Process proc, BufferedReader inread, PrintWriter outwrite){
		this.processID = id;
		this.process = proc;
		this.in = inread;
		this.out = outwrite;
	}
	
}