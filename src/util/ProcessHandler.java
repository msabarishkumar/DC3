package util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

public class ProcessHandler {

	public int processID;
	public Process process;
	public BufferedReader in;
	public PrintWriter out;
	
	public ProcessHandler(int id, Process process){
		this.processID = id;
		this.process = process;
		
		InputStream inputStream = process.getInputStream();
		InputStreamReader inst = new InputStreamReader(inputStream);
		this.in = new BufferedReader(inst);
		
		OutputStream outputStream = process.getOutputStream();
		this.out = new PrintWriter(outputStream,true);
	}
	
}