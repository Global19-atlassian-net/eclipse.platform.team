/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.core.resources;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.Update;
import org.eclipse.team.internal.ccvs.core.client.Command.GlobalOption;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.client.Command.QuietOption;
import org.eclipse.team.internal.ccvs.core.client.listeners.IStatusListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.IUpdateMessageListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.StatusListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.UpdateListener;
import org.eclipse.team.internal.ccvs.core.connection.CVSServerException;
import org.eclipse.team.internal.ccvs.core.util.Util;

/**
 * Fetch the children for the given parent folder. When fetchMembers is invoked,
 * the children of the folder will be fecthced from the server and assigned to
 * the children of the parent folder.
 */
public class RemoteFolderMemberFetcher implements IUpdateMessageListener, IStatusListener {
	
	private final RemoteFolder parentFolder;
	private CVSTag tag;
	
	List folders = new ArrayList(); // RemoteFolder
	List files = new ArrayList(); // RemoteFile
	boolean exists = true;
	List exceptions = new ArrayList(); // CVSException
	
	protected RemoteFolderMemberFetcher(RemoteFolder parentFolder, CVSTag tag) {
		this.tag = tag;
		this.parentFolder = parentFolder;
	}
	
	/**
	 * Fetch the members for a given tag and returns them.
	 * During the execution of this method, the instance variable children
	 * will be used to contain the children. However, the variable is reset
	 * and the result returned. Thus, instances of RemoteFolder do not
	 * persist the children. Subclasses (namely RemoteFolderTree) may
	 * persist the children.
	 */
	public void fetchMembers(IProgressMonitor monitor) throws CVSException {
		final IProgressMonitor progress = Policy.monitorFor(monitor);
		progress.beginTask(Policy.bind("RemoteFolder.getMembers"), 100); //$NON-NLS-1$
		try {
			// Update the parent folder children so there are no children
			updateParentFolderChildren();
			// Perform an update to retrieve the child files and folders
			IStatus status = performUpdate(Policy.subMonitorFor(progress, 50));
			// Update the parent folder with the new children
			updateParentFolderChildren();
			Policy.checkCanceled(monitor);
			
			// Handle any errors that were identified by the listener
			performErrorCheck(status, Policy.bind("RemoteFolder.errorFetchingMembers")); //$NON-NLS-1$
			
			// Get the revision numbers for the files
			ICVSFile[] remoteFiles = getFiles();
			if (remoteFiles.length > 0) {
				updateFileRevisions(remoteFiles, Policy.subMonitorFor(progress, 50));
			} else {
				progress.worked(50);
			}
		} catch (CVSServerException e) {
			if ( ! e.isNoTagException() && e.containsErrors())
				throw e;
			if (tag == null)
				throw e;
			// we now know that this is an exception caused by a cvs bug.
			// if the folder has no files in it (just subfolders) cvs does not respond with the subfolders...
			// workaround: retry the request with no tag to get the directory names (if any)
			Policy.checkCanceled(progress);
			fetchMembers(Policy.subMonitorFor(progress, 50));
		} finally {
			progress.done();
		}
	}

	protected IStatus performUpdate(IProgressMonitor progress) throws CVSException {
		progress.beginTask(null, 100);
		Session session = new Session(parentFolder.getRepository(), parentFolder, false /* output to console */);
		session.open(Policy.subMonitorFor(progress, 10));
		try {
			// Build the local options
			final List localOptions = new ArrayList();
			localOptions.add(Update.RETRIEVE_ABSENT_DIRECTORIES);
			if (tag != null) localOptions.add(Update.makeTagOption(tag));
			
			return Command.UPDATE.execute(
				session,
				new GlobalOption[] { Command.DO_NOT_CHANGE },
				(LocalOption[])localOptions.toArray(new LocalOption[localOptions.size()]),
				new ICVSResource[] { parentFolder },
				new UpdateListener(this),
			Policy.subMonitorFor(progress, 90));
		} finally {
			session.close();
		}
	}
	
	protected void updateFileRevisions(final ICVSFile[] files, IProgressMonitor monitor) throws CVSException {
			
		// Perform a "cvs status..." with a listener
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 100);
		QuietOption quietness = CVSProviderPlugin.getPlugin().getQuietness();
		try {
			CVSProviderPlugin.getPlugin().setQuietness(Command.VERBOSE);
			Session session = new Session(parentFolder.getRepository(), parentFolder, false /* output to console */);
			session.open(Policy.subMonitorFor(monitor, 10));
			try {
				IStatus status = Command.STATUS.execute(
					session,
					Command.NO_GLOBAL_OPTIONS,
					Command.NO_LOCAL_OPTIONS,
					files,
					new StatusListener(this),
					Policy.subMonitorFor(monitor, 90));
				performErrorCheck(status, Policy.bind("RemoteFolder.errorFetchingRevisions")); //$NON-NLS-1$
				// TODO: Ensure all files have a revision?
			} finally {
				session.close();
			}
		} finally {
			CVSProviderPlugin.getPlugin().setQuietness(quietness);
		}
	}

	private void performErrorCheck(IStatus status, String errorTitle) throws CVSException {
		if (status.getCode() == CVSStatus.SERVER_ERROR) {
			// Only throw the exception if no files or folders were found
			if (folders.size() + files.size() == 0) {
				throw new CVSServerException(status);
			} else {
				CVSProviderPlugin.log(new CVSServerException(status));
			}	
		}
		if (!exists) {
			throw new CVSException(new CVSStatus(CVSStatus.ERROR, CVSStatus.DOES_NOT_EXIST, Policy.bind("RemoteFolder.doesNotExist", this.parentFolder.getRepositoryRelativePath()))); //$NON-NLS-1$
		}
		
		// Report any internal exceptions that occured fetching the members
		if ( ! exceptions.isEmpty()) {
			if (exceptions.size() == 1) {
				throw (CVSException)exceptions.get(0);
			} else {
				MultiStatus multi = new MultiStatus(CVSProviderPlugin.ID, 0, errorTitle, null);
				for (int i = 0; i < exceptions.size(); i++) {
					multi.merge(((CVSException)exceptions.get(i)).getStatus());
				}
				throw new CVSException(multi);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IUpdateMessageListener#directoryInformation(org.eclipse.team.internal.ccvs.core.ICVSFolder, java.lang.String, boolean)
	 */
	public void directoryInformation(ICVSFolder commandRoot, String stringPath, boolean newDirectory) {
		try {
			IPath path = this.parentFolder.getRelativePathFromRootRelativePath(commandRoot, new Path(stringPath));
			if (newDirectory && path.segmentCount() == 1) {
				recordFolder(path.lastSegment());
			}
		} catch (CVSException e) {
			exceptions.add(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IUpdateMessageListener#directoryDoesNotExist(org.eclipse.team.internal.ccvs.core.ICVSFolder, java.lang.String)
	 */
	public void directoryDoesNotExist(ICVSFolder parent, String stringPath) {
		try {
			IPath path = this.parentFolder.getRelativePathFromRootRelativePath(parent, new Path(stringPath));
			if (path.isEmpty()) {
				parentDoesNotExist();
			}
		} catch (CVSException e) {
			exceptions.add(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IUpdateMessageListener#fileInformation(int, org.eclipse.team.internal.ccvs.core.ICVSFolder, java.lang.String)
	 */
	public void fileInformation(int type, ICVSFolder parent, String filename) {
		try {
			IPath filePath = new Path(filename);
			filePath = this.parentFolder.getRelativePathFromRootRelativePath(parent, filePath);	
			if( filePath.segmentCount() == 1 ) {
				String properFilename = filePath.lastSegment();
				recordFile(properFilename);
			}
		} catch (CVSException e) {
			exceptions.add(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IUpdateMessageListener#fileDoesNotExist(org.eclipse.team.internal.ccvs.core.ICVSFolder, java.lang.String)
	 */
	public void fileDoesNotExist(ICVSFolder parent, String filename) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IStatusListener#fileStatus(org.eclipse.team.internal.ccvs.core.ICVSFolder, java.lang.String, java.lang.String)
	 */
	public void fileStatus(ICVSFolder commandRoot, String path, String remoteRevision) {
		if (remoteRevision == IStatusListener.FOLDER_REVISION)
			// Ignore any folders
			return;
		try {
			((RemoteFile)parentFolder.getChild(Util.getLastSegment(path))).setRevision(remoteRevision);
		} catch (CVSException e) {
			exceptions.add(e);
		}
	}
	
	/**
	 * This method is invoked for each child folder as the reponses are being recieved from
	 * the server. Default behavior is to record the folder for later retrieval using <code>getChilren()</code>. 
	 * Subclasses may override but should invoke the inherited method to ensure the folder gets recorded.
	 * @param name the name of the child folder
	 */
	protected RemoteFolder recordFolder(String name) {
		RemoteFolder folder = new RemoteFolder(
			parentFolder, 
			parentFolder.getRepository(), 
			Util.appendPath(parentFolder.getRepositoryRelativePath(), name), 
			tag);
		folders.add(folder);
		return folder;
	}

	/**
	 * This method is invoked for each child file as the reponses are being recieved from
	 * the server. Default behavior is to record the file for later retrieval using <code>getChilren()</code>. 
	 * Subclasses may override but should invoke the inherited method to ensure the file gets recorded.
	 * This is important because the file revisions for any files are fetched subsequent to the fecthing
	 * of the children.
	 * @param name the name of the child folder
	 */
	protected RemoteFile recordFile(String name) {
		RemoteFile file = new RemoteFile(
			parentFolder, 
			Update.STATE_NONE, 
			name, 
			tag);
		files.add(file);
		return file;
	}
	
	/**
	 * This method is invoked to indicate that the parent beig queried for children
	 * does not exist. Subclasses may override to get early notification of this but 
	 * should still invoke the inherited method.
	 */
	protected void parentDoesNotExist() {
		exists = false;
	}

	/**
	 * Update the parent folder such that it's children are the
	 * children that have been fecthed by the reciever.
	 */
	protected void updateParentFolderChildren() {
		parentFolder.setChildren(getFetchedChildren());
	}
	
	/**
	 * Return the child files fetched from the server.
	 * @return
	 */
	protected ICVSFile[] getFiles() {
		return (ICVSFile[]) files.toArray(new ICVSFile[files.size()]);
	}
	
	/**
	 * Return an array of all fecthed children.
	 * @return
	 */
	public ICVSRemoteResource[] getFetchedChildren() {
		ICVSRemoteResource[] resources = new ICVSRemoteResource[folders.size() + files.size()];
		int count = 0;
		for (Iterator iter = folders.iterator(); iter.hasNext();) {
			ICVSRemoteResource resource = (ICVSRemoteResource) iter.next();
			resources[count++] = resource;
		}
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			ICVSRemoteResource resource = (ICVSRemoteResource) iter.next();
			resources[count++] = resource;
		}
		return resources;
	}

}