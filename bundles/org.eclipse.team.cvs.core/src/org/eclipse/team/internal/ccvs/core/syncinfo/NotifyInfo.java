/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.core.syncinfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Date;

import org.eclipse.core.resources.IContainer;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.util.CVSDateFormatter;
import org.eclipse.team.internal.ccvs.core.util.EmptyTokenizer;

/**
 * This class contains the information required by the server for edit/unedit.
 */
public class NotifyInfo {
	
	// constants for the notifiation type and watches
	public static final char EDIT = 'E';
	public static final char UNEDIT = 'U';
	public static final char COMMIT = 'C';
	public static final char[] ALL = new char[] {EDIT, UNEDIT, COMMIT};
	
	protected static final String SEPERATOR = "/"; //$NON-NLS-1$
	protected static final String TAB_SEPERATOR = "\t"; //$NON-NLS-1$
	
	private ICVSFile file;
	private char notificationType;
	private Date timeStamp;
	private char[] watches;
	
	/**
	 * Constructor for setting all variables
	 */
	public NotifyInfo(ICVSFile file, char notificationType, Date timeStamp, char[] watches) {
			
		this.file = file;
		this.notificationType = notificationType;
		this.timeStamp = timeStamp;
		this.watches = watches;
	}

	/**
	 * Constructor for a line from the CVS/Notify file
	 * @param line
	 */
	public NotifyInfo(IContainer parent, String line) throws CVSException {
		ICVSFolder cvsFolder = CVSWorkspaceRoot.getCVSFolderFor(parent);
		EmptyTokenizer tokenizer = new EmptyTokenizer(line, SEPERATOR);
		if(tokenizer.countTokens() != 4) {
			throw new CVSException(Policy.bind("NotifyInfo.MalformedLine", line)); //$NON-NLS-1$
		}
		String filename = tokenizer.nextToken();
		this.file = cvsFolder.getFile(filename);
		
		String type = tokenizer.nextToken();
		if (type.length() != 1) {
			throw new CVSException(Policy.bind("NotifyInfo.MalformedNotificationType", line)); //$NON-NLS-1$
		}
		this.notificationType = type.charAt(0);
		
		String date = tokenizer.nextToken();
		try {	
			this.timeStamp = CVSDateFormatter.entryLineToDate(date);
		} catch(ParseException e) {
			throw new CVSException(Policy.bind("NotifyInfo.MalformedNotifyDate", line)); //$NON-NLS-1$
		}
		
		String watchesString = tokenizer.nextToken();
		if (watchesString.length() > 0) {
			this.watches = new char[watchesString.length()];
			for (int i = 0; i < watchesString.length(); i++) {
				watches[i] = watchesString.charAt(i);
			}
		} else {
			this.watches = null;
		}
	}
	
	/**
	 * Answer a Sting formatted to be written to the CVS/Notify file.
	 * 
	 * XXX NOTE: This is a guess at the local format. Need to obtain proper format
	 * 
	 * @return String
	 */
	public String getNotifyLine() {
		StringBuffer buffer = new StringBuffer();
		buffer.append(getName());
		buffer.append(SEPERATOR);
		buffer.append(notificationType);
		buffer.append(SEPERATOR);
		buffer.append(CVSDateFormatter.dateToEntryLine(timeStamp));
		buffer.append(SEPERATOR);
		if (watches != null) {
			for (int i = 0; i < watches.length; i++) {
				char c = watches[i];
				buffer.append(c);
			}
		}
		return buffer.toString();
	}

	/**
	 * Answer a Sting formatted to be sent to the server.
	 * 
	 * @return String
	 */
	public String getServerLine() throws CVSException {
		StringBuffer buffer = new StringBuffer();
		buffer.append(notificationType);
		buffer.append(TAB_SEPERATOR);
		buffer.append(getServerTimestamp());
		buffer.append(TAB_SEPERATOR);
		buffer.append(getHost());
		buffer.append(TAB_SEPERATOR);
		buffer.append(getWorkingDirectory());
		buffer.append(TAB_SEPERATOR);
		if (watches != null) {
			for (int i = 0; i < watches.length; i++) {
				char c = watches[i];
				buffer.append(c);
			}
		}
		return buffer.toString();
	}

	/**
	 * Answer the timestamp in GMT format.
	 * @return String
	 */
	private String getServerTimestamp() {
		return CVSDateFormatter.dateToNotifyServer(timeStamp);
	}

	/**
	 * Answer the working directory for the receiver's file. The format
	 * is NOT device dependant (i.e. /'s are used as the path separator).
	 * 
	 * @return String
	 */
	private String getWorkingDirectory() throws CVSException {
		return file.getIResource().getParent().getLocation().toString();
	}

	/**
	 * Answer the host name of the client machine.
	 * @return String
	 */
	private String getHost() throws CVSException {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			throw CVSException.wrapException(e);
		}
	}

	/**
	 * Answer the name of the file associated with the notification
	 * @return String
	 */
	public String getName() {
		return file.getName();
	}

}
