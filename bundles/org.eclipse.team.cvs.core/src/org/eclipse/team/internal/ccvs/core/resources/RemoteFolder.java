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


import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.ICVSRunnable;
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
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.Util;

/**
 * This class provides the implementation of ICVSRemoteFolder
 * 
 * The parent of the RemoteFolder represents the folders parent in a local configuration.
 * For instance, the parent may correspond to the remote parent or may be a folder in the
 * same repository that has no physical relationship to the RemoteFolder (resulting from the use
 * of a module definition, for instance). A RemoteFolder may not have a parent, indicating that it is
 * the root of the local configuration it represents. 
 * 
 * A RemoteFolder has the following:
 *   A name in the folder's local configuration
 *   
 */
public class RemoteFolder extends RemoteResource implements ICVSRemoteFolder, ICVSFolder {
	
	private final class FolderMembersUpdateListener implements IUpdateMessageListener {
		List newRemoteDirectories = new ArrayList();
		List newRemoteFiles = new ArrayList();
		boolean exists = true;
		List exceptions = new ArrayList();
		protected List getNewRemoteDirectories() {
			return newRemoteDirectories;
		}
		protected List getNewRemoteFiles() {
			return newRemoteFiles;
		}

		public void directoryInformation(ICVSFolder commandRoot, String stringPath, boolean newDirectory) {
			try {
				IPath path = getRelativePathFromRootRelativePath(commandRoot, new Path(stringPath));
				if (newDirectory && path.segmentCount() == 1) {
					newRemoteDirectories.add(path.lastSegment());
				}
			} catch (CVSException e) {
				exceptions.add(e);
			}
		}
		public void directoryDoesNotExist(ICVSFolder parent, String stringPath) {
			try {
				IPath path = getRelativePathFromRootRelativePath(parent, new Path(stringPath));
				if (path.isEmpty()) {
					// the remote folder doesn't exist
					exists = false;
				}
			} catch (CVSException e) {
				exceptions.add(e);
			}
		}
		public void fileInformation(int type, ICVSFolder parent, String filename) {
			try {
				IPath filePath = new Path(filename);
				filePath = getRelativePathFromRootRelativePath(parent, filePath);	
				if( filePath.segmentCount() == 1 ) {
					String properFilename = filePath.lastSegment();
					newRemoteFiles.add(properFilename);
				}
			} catch (CVSException e) {
				exceptions.add(e);
			}
		}
		public void fileDoesNotExist(ICVSFolder parent, String filename) {
		}

		public void performErrorCheck(IStatus status) throws CVSException {
			if (status.getCode() == CVSStatus.SERVER_ERROR) {
				// Only throw the exception if no files or folders were found
				if (newRemoteDirectories.size() + newRemoteFiles.size() == 0) {
					throw new CVSServerException(status);
				} else {
					CVSProviderPlugin.log(new CVSServerException(status));
				}
						
			}
			if (!exists) {
				throw new CVSException(new CVSStatus(CVSStatus.ERROR, CVSStatus.DOES_NOT_EXIST, Policy.bind("RemoteFolder.doesNotExist", getRepositoryRelativePath()))); //$NON-NLS-1$
			}
			
			// Report any internal exceptions that occured fetching the members
			if ( ! exceptions.isEmpty()) {
				if (exceptions.size() == 1) {
					throw (CVSException)exceptions.get(0);
				} else {
					MultiStatus multi = new MultiStatus(CVSProviderPlugin.ID, 0, Policy.bind("RemoteFolder.errorFetchingMembers"), null); //$NON-NLS-1$
					for (int i = 0; i < exceptions.size(); i++) {
						multi.merge(((CVSException)exceptions.get(i)).getStatus());
					}
					throw new CVSException(multi);
				}
			}
		}
	}

	protected FolderSyncInfo folderInfo;
	private ICVSRemoteResource[] children;
	private ICVSRepositoryLocation repository;
	
	public static RemoteFolder fromBytes(IResource local, byte[] bytes) throws CVSException {
		Assert.isNotNull(bytes);
		Assert.isTrue(local.getType() != IResource.FILE);
		FolderSyncInfo syncInfo = FolderSyncInfo.getFolderSyncInfo(bytes);
		return new RemoteFolder(null, local.getName(), CVSProviderPlugin.getPlugin().getRepository(syncInfo.getRoot()), syncInfo.getRepository(), syncInfo.getTag(), syncInfo.getIsStatic());
	}
	
	/**
	 * Constructor for RemoteFolder.
	 */
	public RemoteFolder(RemoteFolder parent, ICVSRepositoryLocation repository, String repositoryRelativePath, CVSTag tag) {
		this(parent, 
			repositoryRelativePath == null ? "" : Util.getLastSegment(repositoryRelativePath), //$NON-NLS-1$
			repository,
			repositoryRelativePath,
			tag, 
			false);	
	}
	
	public RemoteFolder(RemoteFolder parent, String name, ICVSRepositoryLocation repository, String repositoryRelativePath, CVSTag tag, boolean isStatic) {
		super(parent, name);
		this.folderInfo = new FolderSyncInfo(repositoryRelativePath.toString(), repository.getLocation(), tag, isStatic);
		this.repository = repository;	
	}

	// Get the file revisions for the given filenames
	protected void updateFileRevisions(final ICVSFile[] files, IProgressMonitor monitor) throws CVSException {
		
		final int[] count = new int[] {0};
		
		// Create a listener for receiving the revision info
		final IStatusListener listener = new IStatusListener() {
			public void fileStatus(ICVSFolder parent, String path, String remoteRevision) {
				if (remoteRevision == IStatusListener.FOLDER_REVISION)
					// Ignore any folders
					return;
				try {
					((RemoteFile)getChild(Util.getLastSegment(path))).setRevision(remoteRevision);
					count[0]++;
				} catch (CVSException e) {
					// The count will be off to indicate an error
				}
			}
		};
			
		// Perform a "cvs status..." with a listener
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 100);
		QuietOption quietness = CVSProviderPlugin.getPlugin().getQuietness();
		try {
			CVSProviderPlugin.getPlugin().setQuietness(Command.VERBOSE);
			Session session = new Session(getRepository(), this, false /* output to console */);
			session.open(Policy.subMonitorFor(monitor, 10));
			try {
				IStatus status = Command.STATUS.execute(
					session,
					Command.NO_GLOBAL_OPTIONS,
					Command.NO_LOCAL_OPTIONS,
					files,
					new StatusListener(listener),
					Policy.subMonitorFor(monitor, 90));
				if (status.getCode() == CVSStatus.SERVER_ERROR) {
					throw new CVSServerException(status);
				}
			} finally {
				session.close();
			}
		} finally {
			CVSProviderPlugin.getPlugin().setQuietness(quietness);
		}
			
		if (count[0] != files.length)
			throw new CVSException(Policy.bind("RemoteFolder.errorFetchingRevisions")); //$NON-NLS-1$
	}
	
	/**
	 * @see ICVSResource#accept(ICVSResourceVisitor)
	 */
	public void accept(ICVSResourceVisitor visitor) throws CVSException {
		visitor.visitFolder(this);
	}

	/**
	 * @see ICVSResource#accept(ICVSResourceVisitor, boolean)
	 */
	public void accept(ICVSResourceVisitor visitor, boolean recurse) throws CVSException {
		visitor.visitFolder(this);
		ICVSResource[] resources;
		if (recurse) {
			resources = members(ICVSFolder.ALL_MEMBERS);
		} else {
			resources = members(ICVSFolder.FILE_MEMBERS);
		}
		for (int i = 0; i < resources.length; i++) {
			resources[i].accept(visitor, recurse);
		}
	}
	
	/*
	 * @see ICVSRemoteResource#exists(IProgressMonitor)
	 */
	public boolean exists(IProgressMonitor monitor) throws TeamException {
		try {
			members(monitor);
			return true;
		} catch (CVSException e) {
			if (e.getStatus().getCode() == CVSStatus.DOES_NOT_EXIST) {
				return false;
			} else {
				throw e;
			}
		}
	}

	/*
	 * Check whether the given resource is a child of the receiver remotely
	 */
	protected boolean exists(ICVSRemoteResource child, IProgressMonitor monitor) throws CVSException {
		return exists(child, getTag(), monitor);
	}
	
	/*
	 * Check whether the child exists for the given tag. This additional method is required because
	 * CVS will signal an error if a folder only contains subfolders when a tag is used. If we get this
	 * error and we're looking for a folder, we need to reissue the command without a tag.
	 */
	protected boolean exists(final ICVSRemoteResource child, CVSTag tag, IProgressMonitor monitor) throws CVSException {
		final IProgressMonitor progress = Policy.monitorFor(monitor);
		progress.beginTask(Policy.bind("RemoteFolder.exists"), 100); //$NON-NLS-1$
		try {
			// Create the listener for remote files and folders
			final boolean[] exists = new boolean[] {true};
			final IUpdateMessageListener listener = new IUpdateMessageListener() {
				public void directoryInformation(ICVSFolder parent, String path, boolean newDirectory) {
					exists[0] = true;
				}
				public void directoryDoesNotExist(ICVSFolder parent, String path) {
					exists[0] = false;
				}
				public void fileInformation(int type, ICVSFolder parent, String filename) {
					// We can't set exists true here as we may get a conflict on a deleted file.
					// i.e. remote files are always communicated to the server as modified.
				}
				public void fileDoesNotExist(ICVSFolder parent, String filename) {
					exists[0] = false;
				}
			};
			
			// Build the local options
			final List localOptions = new ArrayList();
			localOptions.add(Update.RETRIEVE_ABSENT_DIRECTORIES);
			if (tag != null && tag.getType() != CVSTag.HEAD)
				localOptions.add(Update.makeTagOption(tag));
			
			// Retrieve the children and any file revision numbers in a single connection
			// Perform a "cvs -n update -d -r tagName folderName" with custom message and error handlers
			boolean retry = false;
			Session session = new Session(getRepository(), this, false /* output to console */);
			session.open(Policy.subMonitorFor(progress, 10));
			try {
				IStatus status = Command.UPDATE.execute(
					session,
					new GlobalOption[] { Command.DO_NOT_CHANGE },
					(LocalOption[]) localOptions.toArray(new LocalOption[localOptions.size()]),
					new ICVSResource[] { child }, new UpdateListener(listener),
					Policy.subMonitorFor(progress, 70));
				if (status.getCode() == CVSStatus.SERVER_ERROR) {
					CVSServerException e = new CVSServerException(status);
					if (e.isNoTagException() && child.isContainer()) {
						retry = true;
					} else {
						if (e.containsErrors()) {
							throw e;
						}
					}
				}
			} finally {
				session.close();
			}

			// We now know that this is an exception caused by a cvs bug.
			// If the folder has no files in it (just subfolders) CVS does not respond with the subfolders...
			// Workaround: Retry the request with no tag to get the directory names (if any)
			if (retry) {
				Policy.checkCanceled(progress);
				return exists(child, null, Policy.subMonitorFor(progress, 20));
			}
			return exists[0];
		} finally {
			progress.done();
		}
	}

	/**
	 * @see ICVSRemoteFolder#getMembers()
	 */
	public ICVSRemoteResource[] getMembers(IProgressMonitor monitor) throws TeamException {
		return getMembers(getTag(), monitor);
	}

	/**
	 * This method gets the members for a given tag and returns them.
	 * During the execution of this method, the instance variable children
	 * will be used to contain the children. However, the variable is reset
	 * and the result returned. Thus, instances of RemoteFolder do not
	 * persist the children. Subclasses (namely RemoteFolderTree) may
	 * persist the children.
	 */
	protected ICVSRemoteResource[] getMembers(final CVSTag tag, IProgressMonitor monitor) throws CVSException {
		final IProgressMonitor progress = Policy.monitorFor(monitor);
		progress.beginTask(Policy.bind("RemoteFolder.getMembers"), 100); //$NON-NLS-1$
		try {
			// Forget about any children we used to know about children
			children = null;
			
			// Create the listener for remote files and folders
			FolderMembersUpdateListener listener = new FolderMembersUpdateListener();
				
			// Perform an update to retrieve the child files and folders
			IStatus status = performUpdate(tag, listener, Policy.subMonitorFor(progress, 50));
			Policy.checkCanceled(monitor);
			
			// Handle any errors that were identified by the listener
			listener.performErrorCheck(status);
			
			// Convert the file names to remote resources
			List result = new ArrayList();
			List remoteFiles = new ArrayList();
			for (int i=0;i<listener.getNewRemoteFiles().size();i++) {
				RemoteFile newFile = new RemoteFile(RemoteFolder.this, Update.STATE_NONE, (String)listener.getNewRemoteFiles().get(i), tag);
				result.add(newFile);
				remoteFiles.add(newFile);
			}
			
			// Convert the folder names to remote resources
			for (int i=0;i<listener.getNewRemoteDirectories().size();i++)
				result.add(new RemoteFolder(RemoteFolder.this, getRepository(), Util.appendPath(getRepositoryRelativePath(), (String)listener.getNewRemoteDirectories().get(i)), tag));
			children = (ICVSRemoteResource[])result.toArray(new ICVSRemoteResource[0]);
			
			// Get the revision numbers for the files
			if (remoteFiles.size() > 0) {
				updateFileRevisions((ICVSFile[])remoteFiles.toArray(new ICVSFile[remoteFiles.size()]),
					Policy.subMonitorFor(progress, 50));
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
			children = getMembers(null, Policy.subMonitorFor(progress, 20));
			// the returned children must be given the original tag
			for (int i = 0; i < children.length; i++) {
				ICVSRemoteResource remoteResource = children[i];
				if(remoteResource.isContainer()) {
					((RemoteFolder)remoteResource).setTag(tag);
				}
			}
		} finally {
			progress.done();
		}
		
		// We need to remember the children that were fetched in order to support file
		// operations that depend on the parent knowing about the child (i.e. RemoteFile#getContents)
		return children;
	}

	private IStatus performUpdate(CVSTag tag,  IUpdateMessageListener listener, IProgressMonitor progress) throws CVSException {
		progress.beginTask(null, 100);
		Session session = new Session(getRepository(), this, false /* output to console */);
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
				new ICVSResource[] { RemoteFolder.this },
				new UpdateListener(listener),
			Policy.subMonitorFor(progress, 90));
		} finally {
			session.close();
		}
	}

	/**
	 * @see ICVSFolder#members(int)
	 */
	public ICVSResource[] members(int flags) throws CVSException {		
		final List result = new ArrayList();
		ICVSRemoteResource[] resources = getChildren();
		if (children == null) {
			return new ICVSResource[0];
		}
		// RemoteFolders never have phantom members
		if ((flags & EXISTING_MEMBERS) == 0 && (flags & PHANTOM_MEMBERS) == 1) {
			return new ICVSResource[0];
		}
		boolean includeFiles = (((flags & FILE_MEMBERS) != 0) || ((flags & (FILE_MEMBERS | FOLDER_MEMBERS)) == 0));
		boolean includeFolders = (((flags & FOLDER_MEMBERS) != 0) || ((flags & (FILE_MEMBERS | FOLDER_MEMBERS)) == 0));
		boolean includeManaged = (((flags & MANAGED_MEMBERS) != 0) || ((flags & (MANAGED_MEMBERS | UNMANAGED_MEMBERS | IGNORED_MEMBERS)) == 0));
		boolean includeUnmanaged = (((flags & UNMANAGED_MEMBERS) != 0) || ((flags & (MANAGED_MEMBERS | UNMANAGED_MEMBERS | IGNORED_MEMBERS)) == 0));
		boolean includeIgnored = ((flags & IGNORED_MEMBERS) != 0);
		for (int i = 0; i < resources.length; i++) {
			ICVSResource cvsResource = resources[i];
			if ((includeFiles && ( ! cvsResource.isFolder())) 
					|| (includeFolders && (cvsResource.isFolder()))) {
				boolean isManaged = cvsResource.isManaged();
				boolean isIgnored = cvsResource.isIgnored();
				if ((isManaged && includeManaged)|| (isIgnored && includeIgnored)
						|| ( ! isManaged && ! isIgnored && includeUnmanaged)) {
					result.add(cvsResource);
				}
						
			}		
		}
		return (ICVSResource[]) result.toArray(new ICVSResource[result.size()]);
	}
	
	/**
	 * @see ICVSFolder#getFolder(String)
	 */
	public ICVSFolder getFolder(String name) throws CVSException {
		if (name.equals(Session.CURRENT_LOCAL_FOLDER) || name.equals(Session.CURRENT_LOCAL_FOLDER + Session.SERVER_SEPARATOR))
			return this;
		ICVSResource child = getChild(name);
		if (child.isFolder())
			return (ICVSFolder)child;
		throw new CVSException(Policy.bind("RemoteFolder.invalidChild", name, getName())); //$NON-NLS-1$
	}

	/**
	 * @see ICVSFolder#getFile(String)
	 */
	public ICVSFile getFile(String name) throws CVSException {
		ICVSResource child = getChild(name);
		if (!child.isFolder())
			return (ICVSFile)child;
		throw new CVSException(Policy.bind("RemoteFolder.invalidChild", name, getName())); //$NON-NLS-1$

	}

	public LocalOption[] getLocalOptions() {
		return Command.NO_LOCAL_OPTIONS;
	}
	
	public String getRepositoryRelativePath() {
		// The REPOSITORY property of the folder info is the repository relative path
		return getFolderSyncInfo().getRepository();
	}
	
	/**
	 * @see ICVSResource#getRelativePath(ICVSFolder)
	 */
	public String getRelativePath(ICVSFolder ancestor) throws CVSException {
		// Check to see if the receiver is the ancestor
		if (ancestor == this) return Session.CURRENT_LOCAL_FOLDER;
		// Otherwise, we need a parent to continue
		if (parent == null) {
			throw new CVSException(Policy.bind("RemoteFolder.invalidChild", getName(), ancestor.getName())); //$NON-NLS-1$
		}
		return super.getRelativePath(ancestor);
	}
	
	public ICVSRepositoryLocation getRepository() {
		return repository;
	}
	
	/**
	 * @see ICVSRemoteFolder#isExpandable()
	 */
	public boolean isExpandable() {
		return true;
	}
	
	/**
	 * @see ICVSResource#isFolder()
	 */
	public boolean isFolder() {
		return true;
	}
	
	/**
	 * @see ICVSFolder#childExists(String)
	 */
	public boolean childExists(String path) {
		try {
			return getChild(path) != null;
		} catch (CVSException e) {
			return false;
		}
	}

	/**
	 * @see ICVSFolder#getChild(String)
	 * 
	 * This getChild is geared to work with the Command hierarchy. Therefore it only returns 
	 * children that were previously fetched by a call to getMembers(). If the request child
	 * does not exist, an exception is thrown.
	 */
	public ICVSResource getChild(String path) throws CVSException {
		if (path.equals(Session.CURRENT_LOCAL_FOLDER) || path.length() == 0)
			return this;
		if (path.indexOf(Session.SERVER_SEPARATOR) != -1) {
			IPath p = new Path(path);
			try {
				return ((RemoteFolder)getChild(p.segment(0))).getChild(p.removeFirstSegments(1).toString());
			} catch (CVSException e) {
				// regenerate the exception to give as much info as possible
				throw new CVSException(Policy.bind("RemoteFolder.invalidChild", path, getName()));//$NON-NLS-1$
			}
		} else {
			ICVSRemoteResource[] children = getChildren();
			if (children == null) 
				throw new CVSException(Policy.bind("RemoteFolder.invalidChild", path, getName()));//$NON-NLS-1$
			for (int i=0;i<children.length;i++) {
				if (children[i].getName().equals(path))
					return (ICVSResource)children[i];
			}
		}
		throw new CVSException(Policy.bind("RemoteFolder.invalidChild", path, getName()));//$NON-NLS-1$
	}

	/**
	 * @see ICVSFolder#mkdir()
	 */
	public void mkdir() throws CVSException {
		throw new CVSException(Policy.bind("RemoteResource.invalidOperation"));//$NON-NLS-1$
	}

	/**
	 * @see ICVSFolder#flush(boolean)
	 */
	public void flush(boolean deep) {
	}

	/**
	 * @see ICVSFolder#getFolderInfo()
	 */
	public FolderSyncInfo getFolderSyncInfo() {
		return folderInfo;
	}

	/**
	 * @see ICVSResource#getRemoteLocation(ICVSFolder)
	 */
	public String getRemoteLocation(ICVSFolder stopSearching) throws CVSException {
		return folderInfo.getRemoteLocation();
	}
	
	/**
	 * @see ICVSFolder#isCVSFolder()
	 */
	public boolean isCVSFolder() {
		return true;
	}

	/**
	 * @see ICVSFolder#acceptChildren(ICVSResourceVisitor)
	 */
	public void acceptChildren(ICVSResourceVisitor visitor) throws CVSException {
		throw new CVSException(Policy.bind("RemoteResource.invalidOperation"));//$NON-NLS-1$
	}
	
	/*
	 * @see IRemoteResource#isContainer()
	 */
	public boolean isContainer() {
		return true;
	}
	
	/*
	 * @see IRemoteResource#members(IProgressMonitor)
	 */
	public IRemoteResource[] members(IProgressMonitor progress) throws TeamException {
		return getMembers(progress);
	}

	/*
	 * @see IRemoteResource#getContents(IProgressMonitor)
	 */
	public InputStream getContents(IProgressMonitor progress) throws TeamException {
		return null;
	}

	/*
	 * Answers the immediate cached children of this remote folder or null if the remote folder
	 * handle has not yet queried the server for the its children.
	 */	
	public ICVSRemoteResource[] getChildren() {
		return children;
	}
	/*
	 * This allows subclass to set the children
	 */
	protected void setChildren(ICVSRemoteResource[] children) {
		this.children = children;
	}
	/*
	 * @see ICVSRemoteFolder#setTag(String)
	 */
	public void setTag(CVSTag tag) {
		this.folderInfo = new FolderSyncInfo(folderInfo.getRepository(), folderInfo.getRoot(), tag, folderInfo.getIsStatic());
	}

	/*
	 * @see ICVSRemoteFolder#getTag()
	 */
	public CVSTag getTag() {
		return folderInfo.getTag();
	}
	/*
	 * @see ICVSFolder#setFolderInfo(FolderSyncInfo)
	 */
	public void setFolderSyncInfo(FolderSyncInfo folderInfo) throws CVSException {
		this.folderInfo = folderInfo;
		// Currently not supported
		throw new CVSException(Policy.bind("RemoteResource.invalidOperation"));//$NON-NLS-1$
	}
	
	/*
	 * @see ICVSFolder#run(ICVSRunnable, IProgressMonitor)
	 */
	public void run(ICVSRunnable job, IProgressMonitor monitor) throws CVSException {
		job.run(monitor);
	}
	
	/*
	 * @see ICVSFolder#run(ICVSRunnable, int, IProgressMonitor)
	 */
	public void run(ICVSRunnable job, int flags, IProgressMonitor monitor) throws CVSException {
		job.run(monitor);
	}
	
	/*
	 * @see ICVSFolder#tag(CVSTag, LocalOption[], IProgressMonitor)
	 */
	public IStatus tag(final CVSTag tag, final LocalOption[] localOptions, IProgressMonitor monitor) throws CVSException {
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 100);
		Session session = new Session(getRepository(), this, true /* output to console */);
		session.open(Policy.subMonitorFor(monitor, 10));
		try {
			return Command.RTAG.execute(
				session,
				Command.NO_GLOBAL_OPTIONS,
				localOptions,
				folderInfo.getTag(),
				tag,
				new ICVSRemoteResource[] { RemoteFolder.this },
			Policy.subMonitorFor(monitor, 90));
		} finally {
			session.close();
		}
	 }
	 
	/**
	 * @see ICVSFolder#fetchChildren(IProgressMonitor)
	 */
	public ICVSResource[] fetchChildren(IProgressMonitor monitor) throws CVSException {
		try {
			return getMembers(monitor);
		} catch(TeamException e) {
			throw new CVSException(e.getStatus());
		}
	}
	
	public boolean equals(Object target) {
		if ( ! super.equals(target)) return false;
		RemoteFolder folder = (RemoteFolder)target;
		CVSTag tag1 = getTag();
		CVSTag tag2 = folder.getTag();
		if (tag1 == null) tag1 = CVSTag.DEFAULT;
		if (tag2 == null) tag2 = CVSTag.DEFAULT;
		return tag1.equals(tag2);
	}
	
	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		CVSTag tag = getTag();
		if (tag == null) tag = CVSTag.DEFAULT;
		return super.hashCode() | tag.getName().hashCode();
	}
	
	/*
	 * The given root must be an ancestor of the receiver (or the receiver)
	 * and the path of the receiver must be a prefix of the provided path.
	 */
	protected IPath getRelativePathFromRootRelativePath(ICVSFolder root, IPath path) throws CVSException {
		// If the root is the receiver, then the path is already relative to the receiver
		if (root == this) {
			return path;
		}
		Assert.isTrue( ! path.isEmpty());
		return getRelativePathFromRootRelativePath((ICVSFolder)root.getChild(path.segment(0)), path.removeFirstSegments(1));
	}

	/**
	 * @see ICVSRemoteFolder#forTag(CVSTag)
	 */
	public ICVSRemoteResource forTag(ICVSRemoteFolder parent, CVSTag tagName) {
		return new RemoteFolder((RemoteFolder)parent, getName(), repository, folderInfo.getRepository(), tagName, folderInfo.getIsStatic());
	}
	
	/**
	 * @see ICVSRemoteFolder#forTag(CVSTag)
	 */
	public ICVSRemoteResource forTag(CVSTag tagName) {
		return (ICVSRemoteFolder)forTag(null, tagName);
	}

	/**
	 * @see org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder#isDefinedModule()
	 */
	public boolean isDefinedModule() {
		return false;
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.core.resources.RemoteResource#getSyncInfo()
	 */
	public ResourceSyncInfo getSyncInfo() {
		return new ResourceSyncInfo(getName());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.resources.RemoteResource#getSyncBytes()
	 */
	public byte[] getSyncBytes() {
		try {
			return folderInfo.getBytes();
		} catch (CVSException e) {
			// This shouldn't even happen
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.IRemoteResource#getComment()
	 */
	public String getComment() throws TeamException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.IRemoteResource#getContentIdentifier()
	 */
	public String getContentIdentifier() throws TeamException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.IRemoteResource#getCreatorDisplayName()
	 */
	public String getCreatorDisplayName() throws TeamException {
		return null;
	}
}
