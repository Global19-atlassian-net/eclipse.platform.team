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
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.ICVSRunnable;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Util;

/**
 * Implements the ICVSFolder interface on top of an 
 * instance of the ICVSFolder interface
 * 
 * @see ICVSFolder
 */
class EclipseFolder extends EclipseResource implements ICVSFolder {

	protected EclipseFolder(IContainer container) {
		super(container);		
	}
	
	/**
	 * @see ICVSFolder#members(int)
	 */
	public ICVSResource[] members(int flags) throws CVSException {		
		final List result = new ArrayList();
		IResource[] resources = EclipseSynchronizer.getInstance().members((IContainer)resource);
		boolean includeFiles = (((flags & FILE_MEMBERS) != 0) || ((flags & (FILE_MEMBERS | FOLDER_MEMBERS)) == 0));
		boolean includeFolders = (((flags & FOLDER_MEMBERS) != 0) || ((flags & (FILE_MEMBERS | FOLDER_MEMBERS)) == 0));
		boolean includeManaged = (((flags & MANAGED_MEMBERS) != 0) || ((flags & (MANAGED_MEMBERS | UNMANAGED_MEMBERS | IGNORED_MEMBERS)) == 0));
		boolean includeUnmanaged = (((flags & UNMANAGED_MEMBERS) != 0) || ((flags & (MANAGED_MEMBERS | UNMANAGED_MEMBERS | IGNORED_MEMBERS)) == 0));
		boolean includeIgnored = ((flags & IGNORED_MEMBERS) != 0);
		boolean includeExisting = (((flags & EXISTING_MEMBERS) != 0) || ((flags & (EXISTING_MEMBERS | PHANTOM_MEMBERS)) == 0));
		boolean includePhantoms = (((flags & PHANTOM_MEMBERS) != 0) || ((flags & (EXISTING_MEMBERS | PHANTOM_MEMBERS)) == 0));
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
			if ((includeFiles && (resource.getType()==IResource.FILE)) 
					|| (includeFolders && (resource.getType()==IResource.FOLDER))) {
				boolean isManaged = cvsResource.isManaged();
				boolean isIgnored = cvsResource.isIgnored();
				if ((isManaged && includeManaged)|| (isIgnored && includeIgnored)
						|| ( ! isManaged && ! isIgnored && includeUnmanaged)) {
					boolean exists = cvsResource.exists();
					if ((includeExisting && exists) || (includePhantoms && !exists && isManaged)) {
						result.add(cvsResource);
					}
				}
						
			}		
		}	
		return (ICVSResource[]) result.toArray(new ICVSResource[result.size()]);
	}

	/**
	 * @see ICVSFolder#createFolder(String)
	 */
	public ICVSFolder getFolder(String name) throws CVSException {
		if ((CURRENT_LOCAL_FOLDER.equals(name)) || ((CURRENT_LOCAL_FOLDER + SEPARATOR).equals(name)))
			return this;
		IPath path = new Path(name);
		if(resource.getType()==IResource.ROOT && path.segmentCount()==1) {
			return new EclipseFolder(((IWorkspaceRoot)resource).getProject(name));
		} else {
			return new EclipseFolder(((IContainer)resource).getFolder(new Path(name)));
		}
	}

	/**
	 * @see ICVSFolder#createFile(String)
	 */
	public ICVSFile getFile(String name) throws CVSException {
		return new EclipseFile(((IContainer)resource).getFile(new Path(name)));
	}

	/**
	 * @see ICVSFolder#mkdir()
	 */
	public void mkdir() throws CVSException {
		try {
			if(resource.getType()==IResource.PROJECT) {
				IProject project = (IProject)resource;
				project.create(null);
				project.open(null);				
			} else {
				((IFolder)resource).create(false /*don't force*/, true /*make local*/, null);
				// We need to signal the creation to the synchronizer immediately because
				// we may do additional CVS operations on the folder before the next delta
				// occurs.
				EclipseSynchronizer.getInstance().created(getIResource());;
			}				
		} catch (CoreException e) {
			throw CVSException.wrapException(resource, Policy.bind("EclipseFolder_problem_creating", resource.getFullPath().toString(), e.getStatus().getMessage()), e); //$NON-NLS-1$
		} 
	}
		
	/**
	 * @see ICVSResource#isFolder()
	 */
	public boolean isFolder() {
		return true;
	}
		
	/**
	 * @see ICVSFolder#acceptChildren(ICVSResourceVisitor)
	 */
	public void acceptChildren(ICVSResourceVisitor visitor) throws CVSException {
		
		// Visit files and then folders
		ICVSResource[] subFiles = members(FILE_MEMBERS);
		for (int i=0; i<subFiles.length; i++) {
			subFiles[i].accept(visitor);
		}
		ICVSResource[] subFolders = members(FOLDER_MEMBERS);
		for (int i=0; i<subFolders.length; i++) {
			subFolders[i].accept(visitor);
		}
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

	/**
	 * @see ICVSResource#getRemoteLocation(ICVSFolder)
	 */
	public String getRemoteLocation(ICVSFolder stopSearching) throws CVSException {
				
		if (getFolderSyncInfo() != null) {
			return getFolderSyncInfo().getRemoteLocation();
		}			

		ICVSFolder parent = getParent();
		if(parent!=null && !equals(stopSearching)) {
			String parentLocation;
			parentLocation = parent.getRemoteLocation(stopSearching);
			if (parentLocation!=null) {
				return parentLocation + SEPARATOR + getName();
			}		
		}
		return null;
	}

	/*
	 * @see ICVSFolder#getFolderInfo()
	 */
	public FolderSyncInfo getFolderSyncInfo() throws CVSException {
		return EclipseSynchronizer.getInstance().getFolderSync((IContainer)resource);
	}

	/*
	 * @see ICVSFolder#setFolderInfo(FolderSyncInfo)
	 */
	public void setFolderSyncInfo(final FolderSyncInfo folderInfo) throws CVSException {
		// ignore folder sync on the root (i.e. CVSROOT/config/TopLevelAdmin=yes but we just ignore it)
		if (resource.getType() == IResource.ROOT) return;
		run(new ICVSRunnable() {
			public void run(IProgressMonitor monitor) throws CVSException {
				EclipseSynchronizer synchronizer = EclipseSynchronizer.getInstance();
				synchronizer.setFolderSync((IContainer)resource, folderInfo);
				// the server won't add directories as sync info, therefore it must be done when
				// a directory is shared with the repository.
				byte[] newSyncBytes = new ResourceSyncInfo(getName()).getBytes();
				byte[] oldSyncBytes = getSyncBytes();
				// only set the bytes if the new differes from the old.
				// this avoids unnecessary saving of sync files
				if (oldSyncBytes == null || ! Util.equals(newSyncBytes, oldSyncBytes))
					setSyncBytes(newSyncBytes);
			}
		}, null);

	}

	/*
	 * @see ICVSFolder#isCVSFolder()
	 */
	public boolean isCVSFolder() throws CVSException {
		return EclipseSynchronizer.getInstance().getFolderSync((IContainer)resource) != null;
	}

	/*
	 * @see ICVSResource#unmanage()
	 */
	public void unmanage(IProgressMonitor monitor) throws CVSException {
		run(new ICVSRunnable() {
			public void run(IProgressMonitor monitor) throws CVSException {
				monitor = Policy.monitorFor(monitor);
				monitor.beginTask(null, 100);
				recursiveUnmanage((IContainer) resource, Policy.subMonitorFor(monitor, 99));
				EclipseFolder.super.unmanage(Policy.subMonitorFor(monitor, 1));
				monitor.done();	
			}
		}, Policy.subMonitorFor(monitor, 99));
	}
	
	private static void recursiveUnmanage(IContainer container, IProgressMonitor monitor) throws CVSException {
		try {
			monitor.beginTask(null, 10);
			monitor.subTask(container.getFullPath().toOSString());
			EclipseSynchronizer.getInstance().deleteFolderSync(container);
	
			IResource[] members = container.members(true);
			for (int i = 0; i < members.length; i++) {
				monitor.worked(1);
				IResource resource = members[i];
				if (members[i].getType() != IResource.FILE) {
					recursiveUnmanage((IContainer) resource, monitor);
				}
			}
		} catch (CoreException e) {
		} finally {
			monitor.done();
		}
	}
	
	/*
	 * @see ICVSResource#isIgnored()
	 */
	public boolean isIgnored() throws CVSException {
		if(isCVSFolder()) {
			return false;
		}		
		return super.isIgnored();
	}
	
	/*
	 * @see ICVSFolder#getChild(String)
	 */
	public ICVSResource getChild(String namedPath) throws CVSException {
		IPath path = new Path(namedPath);
		if(path.segmentCount()==0) {
			 return this;
		}
		IResource child = ((IContainer)resource).findMember(path, true /* include phantoms */);
		if(child!=null) {
			if(child.getType()==IResource.FILE) {
				return new EclipseFile((IFile)child);
			} else {
				return new EclipseFolder((IContainer)child);
			}
		}
		return null;
	}
	
	/*
	 * @see ICVSFolder#run(ICVSRunnable, IProgressMonitor)
	 */
	public void run(final ICVSRunnable job, IProgressMonitor monitor) throws CVSException {
		EclipseSynchronizer.getInstance().run(getIResource(), job, monitor);
	}
		
	/**
	 * @see ICVSFolder#fetchChildren(IProgressMonitor)
	 */
	public ICVSResource[] fetchChildren(IProgressMonitor monitor) throws CVSException {
		return members(FILE_MEMBERS | FOLDER_MEMBERS);
	}
	/**
	 * @see org.eclipse.team.internal.ccvs.core.ICVSResource#delete()
	 */
	public void delete() throws CVSException {
		if (!exists()) return;
		if (isCVSFolder()) {
			EclipseSynchronizer.getInstance().prepareForDeletion((IContainer)getIResource());
		}
		super.delete();
	}
	
	/**
	 * Assumption this is only called from decorator and isIgnored() is purposely
	 * ommited here for performance reasons. 
	 */
	public boolean isModified(IProgressMonitor monitor) throws CVSException {
		try {
			monitor = Policy.monitorFor(monitor);
			monitor.beginTask(Policy.bind("EclipseFolder.isModifiedProgress", resource.getFullPath().toString()), 1000); //$NON-NLS-1$
			
			IContainer container = (IContainer)getIResource();
			
			// TODO: Added optimization to avoid loading sync info if possible
			// This will place a modified indicator on non-cvs folders
			// (i.e. the call to getModifiedState will cache a session property)
			int state = EclipseSynchronizer.getInstance().getModificationState(getIResource());
			
			boolean modified;
			if (state == ICVSFile.UNKNOWN) {
				
				if (!isCVSFolder()) {
					return container.exists();
				}
				
				// We have no cached info for the folder. We'll need to check directly,
				// caching as go. This will recursively determined the modified state
				// for all child resources until a modified child is found.
				modified = calculateAndSaveChildModificationStates(monitor);
				setModified(modified);
			} else {
				modified = (state == ICVSFile.DIRTY);
			}
			return modified;
		} finally {
			monitor.done();
		}
	}
	
	public void handleModification(boolean forAddition) throws CVSException {
		// For non-additions, we are only interested in sync info changes
		if (isIgnored() || !forAddition) return;

		// the folder is an addition.
		FolderSyncInfo info = getFolderSyncInfo();
		// if the folder has sync info, it was handled is setFolderInfo
		// otherwise, flush the ancestors to recalculate
		if (info == null) {
			setModified(true);
		}
	}
	
	/**
	 * Determines the modification state of the receiver by examining it's children.
	 * This method may result in modification state being cached with the children but
	 * does not cache it for the receiver.
	 */
	private boolean calculateAndSaveChildModificationStates(IProgressMonitor monitor) throws CVSException {
		IContainer container = (IContainer)getIResource();
		ICVSResource[] children = members(ALL_UNIGNORED_MEMBERS);

		for (int i = 0; i < children.length; i++) {
			ICVSResource resource = children[i];
			if (resource.isModified(null)) {
				// if a child resource is dirty consider the parent dirty as well, there
				// is no need to continue checking other siblings.
				return true;
			}
			monitor.worked(1);
		}
			
		return false;
	}
}
