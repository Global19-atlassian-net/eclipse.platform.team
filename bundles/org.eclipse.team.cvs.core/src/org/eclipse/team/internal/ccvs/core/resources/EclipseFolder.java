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
package org.eclipse.team.internal.ccvs.core.resources;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.ICVSRunnable;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.MoveDeleteHook;

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
					if ((includeExisting && exists) || (includePhantoms && !exists)) {
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
				EclipseSynchronizer.getInstance().folderCreated((IFolder)resource);
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
	public void setFolderSyncInfo(FolderSyncInfo folderInfo) throws CVSException {
		EclipseSynchronizer.getInstance().setFolderSync((IContainer)resource, folderInfo);
		// the server won't add directories as sync info, therefore it must be done when
		// a directory is shared with the repository.
		setSyncInfo(new ResourceSyncInfo(getName()));
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
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask("", 100); //$NON-NLS-1$
			run(new ICVSRunnable() {
				public void run(IProgressMonitor monitor) throws CVSException {
					recursiveUnmanage((IContainer) resource, monitor);				
				}
			}, Policy.subMonitorFor(monitor, 99));
			// unmanaged from parent
			super.unmanage(Policy.subMonitorFor(monitor, 1));
		} finally {
			monitor.done();
		}
	}
	
	private static void recursiveUnmanage(IContainer container, IProgressMonitor monitor) throws CVSException {
		try {
			monitor.beginTask(null, 10);
			monitor.subTask(container.getFullPath().toOSString());
			EclipseSynchronizer.getInstance().deleteFolderSync(container);
			EclipseSynchronizer.getInstance().flushModificationCache(container);			
			IResource[] members = container.members(true);
			for (int i = 0; i < members.length; i++) {
				monitor.worked(1);
				IResource resource = members[i];
				if (members[i].getType() == IResource.FILE) {
					EclipseSynchronizer.getInstance().flushModificationCache((IFile)resource);
				} else {
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
		final CVSException[] error = new CVSException[1];
		// Remove the registered Move/Delete hook, assuming that the cvs runnable will keep sync info up-to-date
		final MoveDeleteHook hook = CVSTeamProvider.getRegisteredMoveDeleteHook();
		boolean oldSetting = hook.isWithinCVSOperation();
		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					try {
						hook.setWithinCVSOperation(true);
						EclipseSynchronizer.getInstance().run(job, monitor);
					} catch(CVSException e) {
						error[0] = e; 
					}
				}
			}, monitor);
		} catch(CoreException e) {
			throw CVSException.wrapException(e);
		} finally {
			hook.setWithinCVSOperation(oldSetting);
		}
		if(error[0]!=null) {
			throw error[0];
		}
	}
	
	/**
	 * Running with a flag of READ_ONLY will still ensure that only one thread
	 * is accessing the sync info but will not run inside a workspace runnable
	 * in order to avoid doing a build.
	 * 
	 * @see org.eclipse.team.internal.ccvs.core.ICVSFolder#run(ICVSRunnable, int, IProgressMonitor)
	 */
	public void run(final ICVSRunnable job, int flags, IProgressMonitor monitor) throws CVSException {
		if (flags == READ_ONLY)
			EclipseSynchronizer.getInstance().run(job, monitor);
		else
			run(job, monitor);
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
	 * Method adjustParentCount.
	 * @param file
	 * @param b
	 */
	protected void adjustModifiedCount(boolean modified) throws CVSException {
		if (EclipseSynchronizer.getInstance().adjustModifiedCount((IContainer)getIResource(), modified)) {
			((EclipseFolder)getParent()).adjustModifiedCount(modified);
		}
	}
	
	/*
	 * Flush all cached info for the container and it's ancestors
	 */
	protected void flushModificationCache() throws CVSException {
		EclipseSynchronizer.getInstance().flushModificationCache((IContainer)getIResource());
	}
	
	/*
	 * Either record the deletion (if modified is true) or remove the deletion if
	 * it is already recorded (if modified is false).
	 */
	protected void handleDeletion(IFile file, boolean modified) throws CVSException {
		boolean adjustParent;
		if (modified) {
			adjustParent = EclipseSynchronizer.getInstance().addDeletedChild((IContainer)getIResource(), file);
		} else {
			adjustParent = EclipseSynchronizer.getInstance().removeDeletedChild((IContainer)getIResource(), file);
		}
		if (adjustParent) {
			((EclipseFolder)getParent()).adjustModifiedCount(modified);
		}
	}
	
	public boolean isModified() throws CVSException {
		IContainer container = (IContainer)getIResource();
		Integer count = EclipseSynchronizer.getInstance().getDirtyCount(container);
		if (count == null) {
			String indicator = EclipseSynchronizer.getInstance().getDirtyIndicator(container);
			if (indicator == null) {
				// We have no cached info for the folder. We'll need to check directly,
				// caching as go.
				determineDirtyCount();
			} else {
				// the count has not been initialized yet
				if (indicator == EclipseSynchronizer.NOT_DIRTY_INDICATOR) {
					// the folder is not dirty so set the count to zero
					EclipseSynchronizer.getInstance().setDirtyCount(container, 0);
				} else {
					// The folder is dirty
					determineDirtyCount();
				}
				return indicator == EclipseSynchronizer.IS_DIRTY_INDICATOR;
			}
		} else {
			return count.intValue() > 0;
		}
		return false;
	}
	
	/**
	 * Method determineDirtyCount.
	 */
	private void determineDirtyCount() throws CVSException {
		IContainer container = (IContainer)getIResource();
		ICVSResource[] children = members(ALL_UNIGNORED_MEMBERS);
		int count = 0;
		for (int i = 0; i < children.length; i++) {
			ICVSResource resource = children[i];
			if (resource.isModified()) count++;
		}
		EclipseSynchronizer.getInstance().setDirtyCount(container, count);
	}
}