package org.robovm.debug.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

import org.robovm.compiler.log.ConsoleLogger;
import org.robovm.compiler.log.ErrorOutputStream;
import org.robovm.compiler.log.Logger;

public class JDWPServer implements Runnable {
	private ServerSocket serverSocket;
	private Socket socket;
	private int port;
	private boolean running;
	private boolean handshakeCompleted;
	
	private DataInputStream inputStream;
	private DataOutputStream outputStream;
	private Logger log;
	
	private static final String HS_QUESTION = "JDWP-Handshake";
	
	public JDWPServer(Logger log, int port) {
		this.log = log;
		this.port = port;
		this.running = false;
		this.handshakeCompleted = false;
	}
	
	public void createSocket() throws IOException, InterruptedException {
		this.serverSocket = new ServerSocket(port);
	}
	
	public void shutdown() {
		
		if (this.socket == null) {
			return;
		}
		try {
			this.socket.close();
			this.inputStream.close();
			this.outputStream.close();
		}
		catch (IOException e) {
			//Bad luck for now
		}
		finally {
			this.socket = null;
		}
	}
	
	
	@Override
	public void run() {
		if (this.serverSocket == null) {
			logError("ServerSocket not initialized!");
			throw new RuntimeException("Socket not initialized!");
		}
		this.running = true;
		while (this.running) {
			try {
				if (this.socket == null) {
					this.socket = this.serverSocket.accept();
					this.inputStream = new DataInputStream(this.socket.getInputStream());
					this.outputStream = new DataOutputStream(this.socket.getOutputStream());
				}
			
				if (inputStream.available() > 0) {
					if (handshakeCompleted) {
						
					}
					else {
						byte[] hsBytes = new byte[HS_QUESTION.length()];
						inputStream.readFully(hsBytes, 0, hsBytes.length);
						final String handshakeQuestion = new String(hsBytes);
						
						if (handshakeQuestion.equals(HS_QUESTION)) {
							handshakeCompleted = true;
							outputStream.write(hsBytes);
							logDebug("JDWP Handshake completed");
						}
					}
				}
			}
			catch(IOException e) {
				logException(e, "Exception in main debugserver thread.");
			}
		}
		
		logDebug("Shutting down JDWP Server");
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}
	
	private void logDebug(String format, Object...args) {
		this.log.debug("[JDWPServer] " + format, args);
	}
	
	private void logWarn(String format, Object...args) {
		this.log.warn("[JDWPServer] " +format, args);
	}
	
	private void logError(String format, Object...args) {
		this.log.error("[JDWPServer] " +format, args);
	}
	
	private void logException(Throwable t) {
		logException(t, null);
	}
	
	private void logException(Throwable t, String message) {
		logError("Exception occured: " + String.valueOf(message));
		t.printStackTrace(new PrintStream(new ErrorOutputStream(log), true));
	}
	
	public static void main(String[] args) throws IOException, InterruptedException {
		int port = 11224;
		
		final JDWPServer server = new JDWPServer(new ConsoleLogger(true), port);
		server.createSocket();
		
		Thread t = new Thread(server);
		t.start();
		
		Socket socket = new Socket((String)null, port);
		socket.setTcpNoDelay(true);
		
		DataInputStream is = new DataInputStream(socket.getInputStream());
		DataOutputStream os = new DataOutputStream(socket.getOutputStream());
		
		byte[] buffer = new byte[1024];
		
		String command = "proceed";
		Scanner console = new Scanner(System.in);

		while (!command.equals("exit")) {
			System.out.print("Command: ");
			command = console.nextLine();
		    
		    os.write(command.getBytes());
		   
		    Thread.sleep(100);
		    
		    int read = 0;
	        while ((read = is.read(buffer, 0, buffer.length)) != -1) {
	        	System.out.println("Server said: " + new String(buffer, 0, read));
	        }
		}
		
		server.setRunning(false);
		server.shutdown();
		is.close();
		os.close();
		socket.close();
		console.close();
	}
	
}
