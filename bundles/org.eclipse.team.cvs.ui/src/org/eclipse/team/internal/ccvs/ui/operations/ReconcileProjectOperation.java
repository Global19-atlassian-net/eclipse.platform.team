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
package org.eclipse.team.internal.ccvs.ui.operations;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.synchronize.SyncInfoFilter.ContentComparisonSyncInfoFilter;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.MutableResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * Reconcile an existing unshared local project with an existing remote folder.
 */
public class ReconcileProjectOperation extends ShareProjectOperation {

	private ICVSRemoteFolder folder;
	private ContentComparisonSyncInfoFilter contentCompare = new ContentComparisonSyncInfoFilter(false);
	private CVSWorkspaceSubscriber subscriber = CVSProviderPlugin.getPlugin().getCVSWorkspaceSubscriber();

	public ReconcileProjectOperation(Shell shell, IProject project, ICVSRemoteFolder folder) {
		super(shell, folder.getRepository(), project, folder.getRepositoryRelativePath());
		this.folder = folder;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CVSOperation#getTaskName()
	 */
	protected String getTaskName() {
		return Policy.bind("ReconcileProjectOperation.0", getProject().getName(), folder.getRepositoryRelativePath()); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ShareProjectOperation#createRemoteFolder(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected ICVSRemoteFolder createRemoteFolder(IProgressMonitor monitor) throws CVSException {
		// The folder already exists so just return the handle
		return folder;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ShareProjectOperation#mapProjectToRemoteFolder(org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void mapProjectToRemoteFolder(ICVSRemoteFolder remote, IProgressMonitor monitor) throws TeamException {
		// Map the project
		monitor.beginTask(null, 100);
		super.mapProjectToRemoteFolder(remote, Policy.subMonitorFor(monitor, 50));
		// Reconcile the sync info
		reconcileSyncInfo(Policy.subMonitorFor(monitor, 50));
		monitor.done();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CVSOperation#execute(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void reconcileSyncInfo(IProgressMonitor monitor) throws CVSException {
		try {
			monitor.beginTask(null, 300);
			// Fetch the entire remote tree
			ICVSRemoteFolder remote = CheckoutToRemoteFolderOperation.checkoutRemoteFolder(getPart(), folder, Policy.subMonitorFor(monitor, 100));
			// Traverse the tree and populate the workspace base or remote
			// with the sync info depending on file contents
			populateWorkspace(remote, Policy.subMonitorFor(monitor, 100));
			// Refresh the workspace subscriber to see conflicting additions
			subscriber.refresh(new IResource[] { getProject() }, IResource.DEPTH_INFINITE, Policy.subMonitorFor(monitor, 100));
		} catch (InvocationTargetException e) {
			throw CVSException.wrapException(e);
		} catch (TeamException e) {
			throw CVSException.wrapException(e);
		} catch (InterruptedException e) {
			throw new OperationCanceledException();
		} finally {
			monitor.done();
		}

	}

	private void populateWorkspace(final ICVSRemoteFolder remote, IProgressMonitor monitor) throws CVSException {
		CVSWorkspaceRoot.getCVSFolderFor(getProject()).run(new ICVSRunnable() {
			public void run(IProgressMonitor monitor) throws CVSException {
				populateWorkspace(getProject(), remote, monitor);
			}
		}, monitor);
		
	}

	/* private */ void populateWorkspace(IResource resource, ICVSRemoteResource remote, IProgressMonitor monitor) throws CVSException {
		try {
			if (resource.getType() == IResource.FILE) {
				if (remote.isContainer()) {
					CVSUIPlugin.log(IStatus.ERROR, Policy.bind("ReconcileProjectOperation.1", resource.getFullPath().toString(), remote.getRepositoryRelativePath()), null); //$NON-NLS-1$
				} else {
					IFile file = (IFile)resource;
					IResourceVariant variant = (IResourceVariant)remote;
					if (file.exists() 
							&& variant != null 
							&& contentCompare.compareContents(file, variant, monitor)) {
						// The contents are the same so populate the local workspace
						// with the remote sync info and make the file in-sync
						makeInSync(file, remote, monitor);
					} else {
						// Would like to put the bytes in the remote but this
						// is complicated due to subscriber events.
						// We'll refresh the subcriber at the end.
					}
				}
			} else {
				if (!remote.isContainer()) {
					CVSUIPlugin.log(IStatus.ERROR, Policy.bind("ReconcileProjectOperation.2", resource.getFullPath().toString(), remote.getRepositoryRelativePath()), null); //$NON-NLS-1$
				} else {
					// Map the local folder to the remote folder.
					// (Note that this will make phantoms for non-exisiting local folders)
					ICVSFolder folder = (ICVSFolder)CVSWorkspaceRoot.getCVSResourceFor(resource);
					folder.setFolderSyncInfo(((ICVSFolder)remote).getFolderSyncInfo());
					// Traverse the children of the remote
					// (The members were prefetched).
					ICVSRemoteResource[] members = remote.members(monitor);
					for (int i = 0; i < members.length; i++) {
						ICVSRemoteResource member = members[i];
						populateWorkspace(getLocalChild((IContainer)resource, member), member, monitor);
					}
				}
			}
		} catch (TeamException e) {
			throw CVSException.wrapException(e);
		}
	}

	/*
	 * Get the local resource handle for corresponding to the remote resource
	 */
	private IResource getLocalChild(IContainer parent, ICVSRemoteResource member) {
		IResource resource = parent.findMember(member.getName());
		if (resource == null) {
			if (member.isContainer()) {
				resource = parent.getFolder(new Path(member.getName()));
			} else {
				resource = parent.getFile(new Path(member.getName()));
			}
		}
		return resource;
	}

	/*
	 * Make the file in-sync with its corresponding remote.
	 */
	private void makeInSync(IFile file, ICVSRemoteResource remote, IProgressMonitor monitor) throws CVSException {
		ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor(file);
		ResourceSyncInfo info = remote.getSyncInfo();
		Date modTime = info.getTimeStamp();
		if (modTime != null) {
			cvsFile.setTimeStamp(modTime);
		}
		modTime = cvsFile.getTimeStamp();
		MutableResourceSyncInfo newInfoWithTimestamp = info.cloneMutable();
		newInfoWithTimestamp.setTimeStamp(modTime);
		cvsFile.setSyncInfo(newInfoWithTimestamp, ICVSFile.CLEAN);
	}

}
