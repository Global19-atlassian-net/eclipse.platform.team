package org.eclipse.team.internal.ccvs.core.client.listeners;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.client.listeners.IUpdateMessageListener;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */


public class UpdateListener implements ICommandOutputListener {
	static final String SERVER_PREFIX = "cvs server: ";
	static final String SERVER_ABORTED_PREFIX = "cvs [server aborted]: ";

	IUpdateMessageListener updateMessageListener;
	boolean merging = false;

	public UpdateListener(IUpdateMessageListener updateMessageListener) {
		this.updateMessageListener = updateMessageListener;
	}
	
	public IStatus messageLine(String line, ICVSFolder commandRoot,
		IProgressMonitor monitor) {
		if (updateMessageListener == null) return OK;
		if (line.indexOf(' ') == 1) {
			// We have a message that indicates the type of update (A, R, M, U, C, ?) and the file name
			String path = line.substring(2);
			char changeType = line.charAt(0);
			if (merging) {
				// If we are merging, use 'C' as the change type to indicate that there is a conflict
				if (changeType == 'M')
					changeType = 'C';
				merging = false;
			}
			updateMessageListener.fileInformation(changeType, path);
		} else if (line.startsWith("Merging")) {
			// We are merging two files
			merging = true;	
		}
		return OK;
	}

	/**
	 * This handler is used by the RemoteResource hierarchy to retrieve E messages
	 * from the CVS server in order to determine the folders contained in a parent folder.
	 * 
	 * WARNING: This class parses the message output to determine the state of files in the
	 * repository. Unfortunately, these messages seem to be customizable on a server by server basis.
	 * 
	 * Here's a list of responses we expect in various situations:
	 * 
	 * Directory exists remotely:
	 *    cvs server: Updating folder1/folder2
	 * Directory doesn't exist remotely:
	 *    cvs server: skipping directory folder1/folder2
	 * New (or unknown) remote directory
	 *    cvs server: New Directory folder1/folder2
	 * File removed remotely
	 *    cvs server: folder1/file.ext is no longer in the repository
	 *    cvs server: warning: folder1/file.ext is not (any longer) pertinent
	 * Locally added file was added remotely as well
	 *    cvs server: conflict: folder/file.ext created independently by second party 
	 * File removed locally and modified remotely
	 *    cvs server: conflict: removed file.txt was modified by second party
	 * File modified locally but removed remotely
	 *    cvs server: conflict: file.txt is modified but no longer in the repository
	 * Ignored Messages
	 *    cvs server: cannot open directory ...
	 *    cvs server: nothing known about ...
	 * Tag error that really means there are no files in a directory
	 *    cvs [server aborted]: no such tag
	 */
	public IStatus errorLine(String line, ICVSFolder commandRoot,
		IProgressMonitor monitor) {
		if (line.startsWith(SERVER_PREFIX)) {
			// Strip the prefix from the line
			String message = line.substring(SERVER_PREFIX.length());
			if (message.startsWith("Updating")) {
				if (updateMessageListener != null) {
					IPath path = new Path(message.substring(8));
					updateMessageListener.directoryInformation(path, false);
				}
			} else if (message.startsWith("skipping directory")) {
				if (updateMessageListener != null) {
					IPath path = new Path(message.substring(18).trim());
					updateMessageListener.directoryDoesNotExist(path);
				}
			} else if (message.startsWith("New directory")) {
				if (updateMessageListener != null) {
					IPath path = new Path(message.substring(15, message.indexOf('\'', 15)));
					updateMessageListener.directoryInformation(path, true);
				}
			} else if (message.endsWith("is no longer in the repository")) {
				if (updateMessageListener != null) {
					String filename = message.substring(0, message.indexOf(' '));
					updateMessageListener.fileDoesNotExist(filename);
				}
			} else if (message.startsWith("conflict:")) {
				/*
				 * We can get the following conflict warnings
				 *    cvs server: conflict: folder/file.ext created independently by second party 
				 *    cvs server: conflict: removed file.txt was modified by second party
				 *    cvs server: conflict: file.txt is modified but no longer in the repository
				 * If we get the above line, we have conflicting additions or deletions and we can expect a server error.
				 * We still get "C foler/file.ext" so we don't need to do anything else (except in the remotely deleted case)
				 */
				if (updateMessageListener != null) {
					if (message.endsWith("is modified but no longer in the repository")) {
						// The "C foler/file.ext" will come after this so if whould be ignored!
						String filename = message.substring(10, message.indexOf(' ', 10));
						updateMessageListener.fileDoesNotExist(filename);
					}
				}
				return new Status(IStatus.WARNING, CVSProviderPlugin.ID, CVSException.CONFLICT, line, null);
			} else if (message.startsWith("warning:")) {
				/*
				 * We can get the following conflict warnings
				 *    cvs server: warning: folder1/file.ext is not (any longer) pertinent
				 * If we get the above line, we have local changes to a remotely deleted file.
				 */
				if (updateMessageListener != null) {
					if (message.endsWith("is not (any longer) pertinent")) {
						String filename = message.substring(9, message.indexOf(' ', 9));
						updateMessageListener.fileDoesNotExist(filename);
					}
				}
				return new Status(IStatus.WARNING, CVSProviderPlugin.ID, CVSException.WARNING, line, null);
			} else if (message.startsWith("conflicts")) {
				// This line is info only. The server doesn't report an error.
				return new Status(IStatus.INFO, CVSProviderPlugin.ID, CVSException.CONFLICT, line, null);
			} else if (!message.startsWith("cannot open directory")
					&& !message.startsWith("nothing known about")) {
				return new Status(IStatus.ERROR, CVSProviderPlugin.ID, CVSException.IO_FAILED, line, null);
			}
		} else if (line.startsWith(SERVER_ABORTED_PREFIX)) {
			// Strip the prefix from the line
			String message = line.substring(SERVER_ABORTED_PREFIX.length());
			if (message.startsWith("no such tag")) {
				// This is reported from CVS when a tag is used on the update there are no files in the directory
				// To get the folders, the update request should be re-issued for HEAD
				return new Status(IStatus.WARNING, CVSProviderPlugin.ID, CVSException.NO_SUCH_TAG, line, null);
			} else {
				return new Status(IStatus.ERROR, CVSProviderPlugin.ID, CVSException.IO_FAILED, line, null);
			}
		}
		return OK;
	}
}
