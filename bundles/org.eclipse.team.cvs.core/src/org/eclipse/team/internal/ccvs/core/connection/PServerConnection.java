package org.eclipse.team.internal.ccvs.core.connection;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;

import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.ccvs.core.*;
import org.eclipse.team.ccvs.core.*;

/**
 * A connection used to talk to an cvs pserver.
 */
public class PServerConnection implements IServerConnection {

	protected static final String SLEEP_PROPERTY = "cvs.pserver.wait";
	protected static final String milliseconds = System.getProperty(SLEEP_PROPERTY);
	
	public static final char NEWLINE= 0xA;
	
	/** default CVS pserver port */
	private static final int DEFAULT_PORT= 2401;
	
	/** error line indicators */
	private static final char ERROR_CHAR = 'E';
	private static final String ERROR_MESSAGE = "error 0";
	private static final String NO_SUCH_USER = "no such user";
	
	private static final char[] SCRAMBLING_TABLE=new char[] {
	0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,
	16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,
	114,120,53,79,96,109,72,108,70,64,76,67,116,74,68,87,
	111,52,75,119,49,34,82,81,95,65,112,86,118,110,122,105,
	41,57,83,43,46,102,40,89,38,103,45,50,42,123,91,35,
	125,55,54,66,124,126,59,47,92,71,115,78,88,107,106,56,
	36,121,117,104,101,100,69,73,99,63,94,93,39,37,61,48,
	58,113,32,90,44,98,60,51,33,97,62,77,84,80,85,223,
	225,216,187,166,229,189,222,188,141,249,148,200,184,136,248,190,
	199,170,181,204,138,232,218,183,255,234,220,247,213,203,226,193,
	174,172,228,252,217,201,131,230,197,211,145,238,161,179,160,212,
	207,221,254,173,202,146,224,151,140,196,205,130,135,133,143,246,
	192,159,244,239,185,168,215,144,139,165,180,157,147,186,214,176,
	227,231,219,169,175,156,206,198,129,164,150,210,154,177,134,127,
	182,128,158,208,162,132,167,209,149,241,153,251,237,236,171,195,
	243,233,253,240,194,250,191,155,142,137,245,235,163,242,178,152
	};

	/** Communication strings */
	private static final String BEGIN= "BEGIN AUTH REQUEST";
	private static final String END=   "END AUTH REQUEST";
	private static final String LOGIN_OK= "I LOVE YOU";
	private static final String LOGIN_FAILED= "I HATE YOU";
	
	private String password;
	private ICVSRepositoryLocation cvsroot;

	private Socket fSocket;
	
	private InputStream inputStream;
	private OutputStream outputStream;
	
	/**
	 * @see Connection#doClose()
	 */
	public void close() throws IOException {
		fSocket.close();
		fSocket= null;
	}

	/**
	 * @see Connection#doOpen()
	 */
	public void open() throws IOException, CVSAuthenticationException {
		
		// XXX see sleepIfPropertyIsSet() for comments.
		// This should be removed once we have corrected the
		// CVS plugin's bad behavior with connections.
		sleepIfPropertyIsSet();
		
		fSocket = createSocket();
		try {
			this.inputStream = new BufferedInputStream(fSocket.getInputStream());
			this.outputStream = new BufferedOutputStream(fSocket.getOutputStream());
			authenticate();
		} catch (IOException e) {
			cleanUpAfterFailedConnection();
			throw e;
		} catch (CVSAuthenticationException e) {
			cleanUpAfterFailedConnection();
			throw e;
		}
	}

	/**
	 * @see Connection#getInputStream()
	 */
	public InputStream getInputStream() {
		return inputStream;
	}
	/**
	 * @see Connection#getOutputStream()
	 */
	public OutputStream getOutputStream() {
		return outputStream;
	}

	/**
	 * Creates a new <code>PServerConnection</code> for the given
	 * cvs root.
	 */
	PServerConnection(ICVSRepositoryLocation cvsroot, String password) {
		this.cvsroot = cvsroot;
		this.password = password;
	}
	/**
	 * Does the actual authentification.
	 */
	private void authenticate() throws IOException, CVSAuthenticationException {
		String scrambledPassword = scramblePassword(password);
	
		String user = cvsroot.getUsername();
		OutputStream out = getOutputStream();
		
		StringBuffer request = new StringBuffer();
		request.append(BEGIN);
		request.append(NEWLINE);
		request.append(cvsroot.getRootDirectory());
		request.append(NEWLINE);
		request.append(user);
		request.append(NEWLINE);
		request.append(scrambledPassword);
		request.append(NEWLINE);
		request.append(END);
		request.append(NEWLINE);
		out.write(request.toString().getBytes());
		out.flush();
		String line = Connection.readLine(getInputStream());
		
		// Return if we succeeded
		if (LOGIN_OK.equals(line))
			return;
			
		// Otherwise, determine the type of error
		if (line.length() == 0)
			throw new IOException(Policy.bind("PServerConnection.noResponse"));
		if (LOGIN_FAILED.equals(line))
			throw new CVSAuthenticationException(cvsroot.getLocation(), Policy.bind("PServerConnection.loginRefused"));
		String message = "";
		// Skip any E messages for now
		while (line.charAt(0) == ERROR_CHAR) {
			// message += line.substring(1) + " ";
			line = Connection.readLine(getInputStream());
		}
		// Remove leading "error 0"
		if (line.startsWith(ERROR_MESSAGE))
			message += line.substring(ERROR_MESSAGE.length() + 1);
		else
			message += line;
		if (message.indexOf(NO_SUCH_USER) != -1)
			throw new CVSAuthenticationException(cvsroot.getLocation(), Policy.bind("PServerConnection.invalidUser", new Object[] {message}));
		throw new IOException(Policy.bind("PServerConnection.connectionRefused", new Object[] { message }));
	}
	/*
	 * Called if there are exceptions when connecting.
	 * This method makes sure that all connections are closed.
	 */
	private void cleanUpAfterFailedConnection() throws IOException {
		try {
			if (inputStream != null)
				inputStream.close();
		} finally {
			try {
				if (outputStream != null)
					outputStream.close();
			} finally {
				try {
					if (fSocket != null)
						fSocket.close();
				} finally {
					fSocket = null;
				}
			}
		}
	
	}
	/**
	 * Creates the actual socket
	 */
	protected Socket createSocket() throws IOException {
		// Determine what port to use
		int port = cvsroot.getPort();
		if (port == cvsroot.USE_DEFAULT_PORT)
			port = DEFAULT_PORT;
		// Make the connection
		Socket result;
		try {
			result= new Socket(cvsroot.getHost(), port);
		} catch (InterruptedIOException e) {
			// If we get this exception, chances are the host is not responding
			throw new InterruptedIOException(Policy.bind("PServerConnection.socket", new Object[] {cvsroot.getHost()}));
		}
		result.setSoTimeout(cvsroot.getTimeout() * 1000);
		return result;
	}

	private String scramblePassword(String password) throws CVSAuthenticationException {
		int length = password.length();
		char[] out= new char[length];
		for (int i= 0; i < length; i++) {
			char value = password.charAt(i);
			if( value < 0 || value > 255 )
				throwInValidCharacter();
			out[i]= SCRAMBLING_TABLE[value];			
		}
		return "A" + new String(out);
	}
	
	private void throwInValidCharacter() throws CVSAuthenticationException {
		throw new CVSAuthenticationException(cvsroot.getLocation(), 
			Policy.bind("PServerConnection.invalidChars"));
	}

	/**
	 * XXX This is provided to allow slowing down of pserver connections in cases
	 * where the inetd connections per second setting is not set high enough. The 
	 * CVS plugin has a known problem of creating too many unnecessary connections.
	 */	
	private void sleepIfPropertyIsSet()
	{
		try {
			if( milliseconds == null )
				return;
			
			long sleepMilli = new Long(milliseconds).longValue();

			if( sleepMilli > 0 )
				Thread.currentThread().sleep(sleepMilli);
		} catch( InterruptedException e ) {
			// keep going
		} catch( NumberFormatException e ) {
			// don't sleep if number format is wrong
		}
	}
}