/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.team.tests.ftp;

import java.net.MalformedURLException;
import java.net.URL;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import junit.extensions.TestSetup;
import junit.framework.Test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.internal.ftp.FTPException;
import org.eclipse.team.internal.ftp.client.FTPClient;
import org.eclipse.team.internal.ftp.client.FTPDirectoryEntry;
import org.eclipse.team.internal.ftp.client.FTPServerLocation;
import org.eclipse.team.internal.ftp.client.IFTPClientListener;

/**
 * Provides the FTP tests with a host to ftp to.
 */
public class FTPTestSetup extends TestSetup {

	public static final String FTP_URL;
	public static final boolean SCRUB_URL;
	public static final boolean DEBUG;
	public static final String SCRUB_DISABLE_FILE = ".donotscrub";
	
	private static final IProgressMonitor DEFAULT_PROGRESS_MONITOR = new NullProgressMonitor();
	
	public static URL ftpURL;
	
	// Static initializer for constants
	static {
		loadProperties();
		FTP_URL = System.getProperty("eclipse.ftp.url");
		SCRUB_URL = Boolean.valueOf(System.getProperty("eclipse.ftp.init", "false")).booleanValue();
		DEBUG = Boolean.valueOf(System.getProperty("eclipse.ftp.debug", "false")).booleanValue();
	}
	
	public static void loadProperties() {
		String propertiesFile = System.getProperty("eclipse.ftp.properties");
		if (propertiesFile == null) return;
		File file = new File(propertiesFile);
		if (file.isDirectory()) file = new File(file, "ftp.properties");
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			try {
				for (String line; (line = reader.readLine()) != null; ) {						
					int sep = line.indexOf("=");
					String property = line.substring(0, sep).trim();
					String value = line.substring(sep + 1).trim();
					System.setProperty("eclipse.ftp." + property, value);
				}
			} finally {
				reader.close();
			}
		} catch (Exception e) {
			System.err.println("Could not read ftp properties file: " + file.getAbsolutePath());
		}
	}
	
	/**
	 * Constructor for FTPTestSetup.
	 * @param test
	 */
	public FTPTestSetup(Test test) {
		super(test);
	}

	public void setUp()  throws MalformedURLException, FTPException {
		if (ftpURL == null)
			ftpURL = setupURL(FTP_URL);
	}

	protected void scrubCurrentDirectory(FTPClient client) throws FTPException {
		FTPDirectoryEntry[] entries = client.listFiles(null, DEFAULT_PROGRESS_MONITOR);
		for (int i = 0; i < entries.length; i++) {
			if (entries[i].getName().equals(SCRUB_DISABLE_FILE)) return;
		}
		for (int i = 0; i < entries.length; i++) {
			FTPDirectoryEntry entry = entries[i];
			if (entry.hasFileSemantics()) {
				client.deleteFile(entry.getName(), DEFAULT_PROGRESS_MONITOR);
			}
			if (entry.hasDirectorySemantics()) {
				client.changeDirectory(entry.getName(), DEFAULT_PROGRESS_MONITOR);
				scrubCurrentDirectory(client);
				client.changeDirectory(FTPClient.PARENT_DIRECTORY, DEFAULT_PROGRESS_MONITOR);
				client.deleteDirectory(entry.getName(), DEFAULT_PROGRESS_MONITOR);
			}
		}
	}
	
	protected URL setupURL(String urlString) throws MalformedURLException, FTPException {

		// Give some info about which repository the tests are running against
		if (DEBUG) System.out.println("Connecting to: " + urlString);
		
		// Validate that we can connect, also creates and caches the repository location. This
		// is important for the UI tests.
		URL url = new URL(urlString);
		FTPServerLocation location = FTPServerLocation.fromURL(url, false);
		FTPClient client = openFTPConnection(url);
		try {
			// Initialize the repo if requested
			// For safety, do not scrub if no path is provided
			if( SCRUB_URL && ! new Path(url.getPath()).isEmpty()) {
				if (DEBUG) System.out.println("Scrubbing: " + url.getPath());
				scrubCurrentDirectory(client);
			}
		} finally {
			client.close(DEFAULT_PROGRESS_MONITOR);
		}
		
		return url;
	}
	
	public void tearDown() {
		// Nothing to do here
	}
	
	public static FTPClient openFTPConnection(URL url) throws FTPException {
		FTPServerLocation location = FTPServerLocation.fromURL(url, false);
		FTPClient client = new FTPClient(location, null, getListener(), FTPClient.USE_DEFAULT_TIMEOUT);
		client.open(DEFAULT_PROGRESS_MONITOR);
		String urlPath = url.getPath();
		// Strip leading slash
		if (urlPath.indexOf('/') == 0) {
			urlPath = urlPath.substring(1);
		}
		try {
			client.createDirectory(urlPath, DEFAULT_PROGRESS_MONITOR);
		} catch (FTPException e) {
			// Ignore the exception
		}
		try {
			client.changeDirectory(urlPath, DEFAULT_PROGRESS_MONITOR);
		} catch (FTPException e) {
			client.close(DEFAULT_PROGRESS_MONITOR);
			throw e;
		}
		return client;
	}
	
	public static IFTPClientListener getListener() {
		return new IFTPClientListener() {
			public void responseReceived(int responseCode, String responseText) {
				if (DEBUG) System.out.println(responseText);
			}
			public void requestSent(String command, String argument) {
				if (argument != null) {
					if (DEBUG) System.out.println(command + " " + argument);
				} else {
					if (DEBUG) System.out.println(command);
				}
			}
		};
	}
	
}
