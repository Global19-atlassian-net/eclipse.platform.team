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

package org.eclipse.team.internal.ccvs.core.syncinfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.internal.jobs.JobManager;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.util.Assert;

/**
 * This RemoteSynchronizr uses a CVS Tag to fetch the remote tree
 */
public class RemoteTagSynchronizer extends CVSRemoteSynchronizer {

	private CVSTag tag;
	
	public RemoteTagSynchronizer(String id, CVSTag tag) {
		super(id);
		this.tag = tag;
	}

	public void collectChanges(IResource local, IRemoteResource remote, Collection changedResources, int depth, IProgressMonitor monitor) throws TeamException {
		byte[] remoteBytes = getRemoteSyncBytes(local, remote);
		boolean changed;
		if (remoteBytes == null) {
			changed = setRemoteDoesNotExist(local);
		} else {
			changed = setSyncBytes(local, remoteBytes);
		}
		if (changed) {
			changedResources.add(local);
		}
		if (depth == IResource.DEPTH_ZERO) return;
		Map children = mergedMembers(local, remote, monitor);	
		for (Iterator it = children.keySet().iterator(); it.hasNext();) {
			IResource localChild = (IResource) it.next();
			IRemoteResource remoteChild = (IRemoteResource)children.get(localChild);
			collectChanges(localChild, remoteChild, changedResources,
				depth == IResource.DEPTH_INFINITE ? IResource.DEPTH_INFINITE : IResource.DEPTH_ZERO, 
				monitor);
		}
		
		// Look for resources that have sync bytes but are not in the resources we care about
		IResource[] resources = getChildrenWithSyncBytes(local);
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (!children.containsKey(resource)) {
				// These sync bytes are stale. Purge them
				removeSyncBytes(resource, IResource.DEPTH_INFINITE);
				changedResources.add(resource);
			}
		}
	}
	
	protected Map mergedMembers(IResource local, IRemoteResource remote, IProgressMonitor progress) throws TeamException {
	
		// {IResource -> IRemoteResource}
		Map mergedResources = new HashMap();
		
		IRemoteResource[] remoteChildren = getRemoteChildren(remote, progress);
		
		IResource[] localChildren = getLocalChildren(local);		

		if (remoteChildren.length > 0 || localChildren.length > 0) {
			Set allSet = new HashSet(20);
			Map localSet = null;
			Map remoteSet = null;

			if (localChildren.length > 0) {
				localSet = new HashMap(10);
				for (int i = 0; i < localChildren.length; i++) {
					IResource localChild = localChildren[i];
					String name = localChild.getName();
					localSet.put(name, localChild);
					allSet.add(name);
				}
			}

			if (remoteChildren.length > 0) {
				remoteSet = new HashMap(10);
				for (int i = 0; i < remoteChildren.length; i++) {
					IRemoteResource remoteChild = remoteChildren[i];
					String name = remoteChild.getName();
					remoteSet.put(name, remoteChild);
					allSet.add(name);
				}
			}
		
			Iterator e = allSet.iterator();
			while (e.hasNext()) {
				String keyChildName = (String) e.next();

				if (progress != null) {
					if (progress.isCanceled()) {
						throw new OperationCanceledException();
					}
					// XXX show some progress?
				}

				IResource localChild =
					localSet != null ? (IResource) localSet.get(keyChildName) : null;

				IRemoteResource remoteChild =
					remoteSet != null ? (IRemoteResource) remoteSet.get(keyChildName) : null;
				
				if (localChild == null) {
					// there has to be a remote resource available if we got this far
					Assert.isTrue(remoteChild != null);
					boolean isContainer = remoteChild.isContainer();				
					localChild = getResourceChild(local /* parent */, keyChildName, isContainer);
				}
				mergedResources.put(localChild, remoteChild);				
			}
		}
		return mergedResources;
	}
	
	private IRemoteResource[] getRemoteChildren(IRemoteResource remote, IProgressMonitor progress) throws TeamException {
		return remote != null ? remote.members(progress) : new IRemoteResource[0];
	}

	private IResource[] getLocalChildren(IResource local) throws TeamException {
		IResource[] localChildren = null;			
		if( local.getType() != IResource.FILE && (local.exists() || local.isPhantom())) {
			// Include all non-ignored resources including outgoing deletions
			ICVSFolder cvsFolder = CVSWorkspaceRoot.getCVSFolderFor((IContainer)local);
			// Look inside existing folders and phantoms that are CVS folders
			if (local.exists() || cvsFolder.isCVSFolder()) {
				ICVSResource[] cvsChildren = cvsFolder.members(ICVSFolder.MANAGED_MEMBERS | ICVSFolder.UNMANAGED_MEMBERS);
				List resourceChildren = new ArrayList();
				for (int i = 0; i < cvsChildren.length; i++) {
					ICVSResource cvsResource = cvsChildren[i];
					resourceChildren.add(cvsResource.getIResource());
				}
				localChildren = (IResource[]) resourceChildren.toArray(new IResource[resourceChildren.size()]);
			}
		}
		if (localChildren == null) {
			localChildren = new IResource[0];
		}
		return localChildren;
	}
	
	private IResource[] getChildrenWithSyncBytes(IResource local) throws TeamException {			
		try {
			if (local.getType() != IResource.FILE && (local.exists() || local.isPhantom())) {
				IResource[] allChildren = ((IContainer)local).members(true /* include phantoms */);
				List childrenWithSyncBytes = new ArrayList();
				for (int i = 0; i < allChildren.length; i++) {
					IResource resource = allChildren[i];
					if (internalGetSyncBytes(resource) != null) {
						childrenWithSyncBytes.add(resource);
					}
				}
				return (IResource[]) childrenWithSyncBytes.toArray(
					new IResource[childrenWithSyncBytes.size()]);
			}
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
		return new IResource[0];
	}
	
	private byte[] internalGetSyncBytes(IResource resource) throws TeamException {
		return super.getSyncBytes(resource);
	}

	/*
	 * Returns a handle to a non-existing resource.
	 */
	private IResource getResourceChild(IResource parent, String childName, boolean isContainer) {
		if (parent.getType() == IResource.FILE) {
			return null;
		}
		if (isContainer) {
			return ((IContainer) parent).getFolder(new Path(childName));
		} else {
			return ((IContainer) parent).getFile(new Path(childName));
		}
	}

	public IResource[] refresh(IResource resource, int depth, boolean cacheFileContentsHint, IProgressMonitor monitor) throws TeamException {
		List changedResources = new ArrayList();
		ISchedulingRule rule = resource.getProject();
		monitor.beginTask(null, 100);
		try {
			// Get a scheduling rule on the project since CVS may obtain a lock higher then
			// the resource itself.
			JobManager.getInstance().beginRule(rule, monitor);
			if (!resource.getProject().isAccessible()) {
				// The project is closed so silently skip it
				return new IResource[0];
			}
			
			monitor.setTaskName(Policy.bind("RemoteTagSynchronizer.0", resource.getFullPath().makeRelative().toString())); //$NON-NLS-1$
			
			// build the remote tree only if an initial tree hasn't been provided
			IRemoteResource	tree = buildRemoteTree(resource, depth, cacheFileContentsHint, Policy.subMonitorFor(monitor, 70));
			
			// update the known remote handles 
			IProgressMonitor sub = Policy.infiniteSubMonitorFor(monitor, 30);
			try {
				sub.beginTask(null, 64);
				collectChanges(resource, tree, changedResources, depth, sub);
			} finally {
				sub.done();	 
			}
		} finally {
			JobManager.getInstance().endRule(rule);
			monitor.done();
		}
		IResource[] changes = (IResource[]) changedResources.toArray(new IResource[changedResources.size()]);
		return changes;
	}

	/**
	 * Build a remote tree for the given parameters.
	 */
	protected ICVSRemoteResource buildRemoteTree(IResource resource, int depth, boolean cacheFileContentsHint, IProgressMonitor monitor) throws TeamException {
		// TODO: we are currently ignoring the depth parameter because the build remote tree is
		// by default deep!
		return CVSWorkspaceRoot.getRemoteTree(resource, tag, cacheFileContentsHint, monitor);
	}

}
