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
package org.eclipse.team.internal.ccvs.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.RemoteSynchronizer;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.core.subscribers.TeamDelta;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.OptimizedRemoteSynchronizer;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.ResourceStateChangeListeners;
import org.eclipse.team.internal.ccvs.core.util.Util;

/**
 * CVSWorkspaceSubscriber
 */
public class CVSWorkspaceSubscriber extends CVSSyncTreeSubscriber implements IResourceStateChangeListener {
	
	private OptimizedRemoteSynchronizer remoteSynchronizer;
	
	// qualified name for remote sync info
	private static final String REMOTE_RESOURCE_KEY = "remote-resource-key"; //$NON-NLS-1$

	CVSWorkspaceSubscriber(QualifiedName id, String name, String description) {
		super(id, name, description);
		
		// install sync info participant
		remoteSynchronizer = new OptimizedRemoteSynchronizer(REMOTE_RESOURCE_KEY);
		
		ResourceStateChangeListeners.getListener().addResourceStateChangeListener(this); 
	}

	/* 
	 * Return the list of projects shared with a CVS team provider.
	 * 
	 * [Issue : this will have to change when folders can be shared with
	 * a team provider instead of the current project restriction]
	 * (non-Javadoc)
	 * @see org.eclipse.team.core.sync.ISyncTreeSubscriber#roots()
	 */
	public IResource[] roots() {
		List result = new ArrayList();
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < projects.length; i++) {
			IProject project = projects[i];
			if(project.isOpen()) {
				RepositoryProvider provider = RepositoryProvider.getProvider(project, CVSProviderPlugin.getTypeId());
				if(provider != null) {
					result.add(project);
				}
			}
		}
		return (IProject[]) result.toArray(new IProject[result.size()]);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceSyncInfoChanged(org.eclipse.core.resources.IResource[])
	 */
	public void resourceSyncInfoChanged(IResource[] changedResources) {
		internalResourceSyncInfoChanged(changedResources, true); 
	}

	private void internalResourceSyncInfoChanged(IResource[] changedResources, boolean canModifyWorkspace) {
		// IMPORTANT NOTE: This will throw exceptions if performed during the POST_CHANGE delta phase!!!
		for (int i = 0; i < changedResources.length; i++) {
			IResource resource = changedResources[i];
			try {
				if (resource.getType() == IResource.FILE
						&& (resource.exists() || resource.isPhantom())) {
					byte[] remoteBytes = remoteSynchronizer.getSyncBytes(resource);
					if (remoteBytes == null) {
						if (remoteSynchronizer.isRemoteKnown(resource)) {
							// The remote is known not to exist. If the local resource is
							// managed then this information is stale
							if (getBaseSynchronizer().hasRemote(resource)) {
								if (canModifyWorkspace) {
									remoteSynchronizer.removeSyncBytes(resource, IResource.DEPTH_ZERO);
								} else {
									signalStaleSyncBytes(resource);
								}
							}
						}
					} else {
						byte[] localBytes = remoteSynchronizer.getBaseSynchronizer().getSyncBytes(resource);
						if (localBytes == null || !isLaterRevision(remoteBytes, localBytes)) {
							if (canModifyWorkspace) {
								remoteSynchronizer.removeSyncBytes(resource, IResource.DEPTH_ZERO);
							} else {
								signalStaleSyncBytes(resource);
							}
						}
					}
				} else if (resource.getType() == IResource.FOLDER) {
					// If the base has sync info for the folder, purge the remote bytes
					if (getBaseSynchronizer().hasRemote(resource) && canModifyWorkspace) {
						remoteSynchronizer.removeSyncBytes(resource, IResource.DEPTH_ZERO);
					}
				}
			} catch (TeamException e) {
				CVSProviderPlugin.log(e);
			}
		}		
		
		fireTeamResourceChange(TeamDelta.asSyncChangedDeltas(this, changedResources));
	}

	/**
	 * Return <code>true</code> if the remoteBytes represents a later revision on the same
	 * branch as localBytes. Return <code>false</code> if remoteBytes is the same or an earlier 
	 * revision or if the bytes are on a separate branch (or tag)
	 * @param remoteBytes
	 * @param localBytes
	 * @return
	 */
	private boolean isLaterRevision(byte[] remoteBytes, byte[] localBytes) {
		try {
			// If the two byte arrays are the same, then the remote isn't a later revision
			if (remoteBytes == localBytes) return false;
			//	If the tags differ, then the remote isn't a later revision
			byte[] remoteTag = ResourceSyncInfo.getTagBytes(remoteBytes);
			byte[] localTag = ResourceSyncInfo.getTagBytes(localBytes);
			if (!Util.equals(remoteTag, localTag)) return false;
			// If the revisions are the same, the remote isn't later
			String remoteRevision = ResourceSyncInfo.getRevision(remoteBytes);
			String localRevision = ResourceSyncInfo.getRevision(localBytes);
			if (remoteRevision.equals(localRevision)) return false;
			return isLaterRevision(remoteRevision, localRevision);
		} catch (CVSException e) {
			CVSProviderPlugin.log(e);
			return false;
		}
	}

	/**
	 * Return true if the remoteRevision represents a later revision than the local revision
	 * on the same branch.
	 * @param remoteRevision
	 * @param localRevision
	 * @return
	 */
	private boolean isLaterRevision(String remoteRevision, String localRevision) {
		int localDigits[] = Util.convertToDigits(localRevision);
		if (localDigits.length == 0) return false;
		int remoteDigits[] = Util.convertToDigits(remoteRevision);
		if (remoteDigits.length == 0) return false;
		if (localDigits.length > remoteDigits.length) {
			// If there are more digits in the local revision then there is
			// no way that the remote is later on the same branch
			return false;
		}
		// For the remote to be later, at least one of the remote digits must
		// be larger or, if all the remote and local digits are equals, there
		// must be more remote digits
		for (int i = 0; i < localDigits.length; i++) {
			int localDigit = localDigits[i];
			int remoteDigit = remoteDigits[i];
			if (remoteDigit > localDigit) return true;
			if (remoteDigit < localDigit) return false;
		}
		// All the leading digits are equals so the remote is later if it is longer
		return remoteDigits.length > localDigits.length;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#externalSyncInfoChange(org.eclipse.core.resources.IResource[])
	 */
	public void externalSyncInfoChange(IResource[] changedResources) {
		internalResourceSyncInfoChanged(changedResources, false);
	}
	
	private void signalStaleSyncBytes(IResource resource) {
		// TODO: it would be nice to be able to start a refresh job
		// but this needs refactoring
		// The following is a temporary measure (see bug 43774)
		CVSProviderPlugin.log(new CVSStatus(IStatus.WARNING, "The incoming changes of CVS Workspace subscriber in the Synchronize view may be stale. Perform a Refresh with Remote on resource " + resource.getFullPath().toString()));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceModified(org.eclipse.core.resources.IResource[])
	 */
	public void resourceModified(IResource[] changedResources) {
		// This is only ever called from a delta POST_CHANGE
		// which causes problems since the workspace tree is closed
		// for modification and we flush the sync info in resourceSyncInfoChanged
		
		// Since the listeners of the Subscriber will also listen to deltas
		// we don't need to propogate this.
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#projectConfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectConfigured(IProject project) {
		TeamDelta delta = new TeamDelta(this, TeamDelta.PROVIDER_CONFIGURED, project);
		fireTeamResourceChange(new TeamDelta[] {delta});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#projectDeconfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectDeconfigured(IProject project) {
		try {
			remoteSynchronizer.removeSyncBytes(project, IResource.DEPTH_INFINITE);
		} catch (TeamException e) {
			CVSProviderPlugin.log(e);
		}
		TeamDelta delta = new TeamDelta(this, TeamDelta.PROVIDER_DECONFIGURED, project);
		fireTeamResourceChange(new TeamDelta[] {delta});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getRemoteSynchronizer()
	 */
	protected RemoteSynchronizer getRemoteSynchronizer() {
		return remoteSynchronizer;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getBaseSynchronizer()
	 */
	protected RemoteSynchronizer getBaseSynchronizer() {
		return remoteSynchronizer.getBaseSynchronizer();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.TeamSubscriber#getAllOutOfSync(org.eclipse.core.resources.IResource[], int, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public SyncInfo[] getAllOutOfSync(IResource[] resources, final int depth, IProgressMonitor monitor) throws TeamException {
		monitor.beginTask(null, resources.length * 100);
		final List result = new ArrayList();
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			final IProgressMonitor infinite = Policy.infiniteSubMonitorFor(monitor, 100);
			try {
				// We need to do a scheduling rule on the project to
				// avoid overly desctructive operations from occuring 
				// while we gather sync info
				infinite.beginTask(null, 512);
				Platform.getJobManager().beginRule(resource, Policy.subMonitorFor(infinite, 1));
				resource.accept(new IResourceVisitor() {
					public boolean visit(IResource innerResource) throws CoreException {
						try {
							if (isOutOfSync(innerResource, infinite)) {
								SyncInfo info = getSyncInfo(innerResource, infinite);
								if (info != null && info.getKind() != 0) {
									result.add(info);
								}
							}
							return true;
						} catch (TeamException e) {
							// TODO:See bug 42795
							throw new CoreException(e.getStatus());
						}
					}
				}, depth, true /* include phantoms */);
			} catch (CoreException e) {
				throw CVSException.wrapException(e);
			} finally {
				Platform.getJobManager().endRule(resource);
				infinite.done();
			}
		}
		monitor.done();
		return (SyncInfo[]) result.toArray(new SyncInfo[result.size()]);
	}
	
	/* internal use only */ boolean isOutOfSync(IResource resource, IProgressMonitor monitor) throws TeamException {
		return (hasIncomingChange(resource) || hasOutgoingChange(CVSWorkspaceRoot.getCVSResourceFor(resource), monitor));
	}
	
	private boolean hasOutgoingChange(ICVSResource resource, IProgressMonitor monitor) throws CVSException {
		if (resource.isFolder()) {
			// A folder is an outgoing change if it is not a CVS folder and not ignored
			ICVSFolder folder = (ICVSFolder)resource;
			// OPTIMIZE: The following checks load the CVS folder information
			if (folder.getParent().isModified(monitor)) {
				return !folder.isCVSFolder() && !folder.isIgnored();
			}
		} else {
			// A file is an outgoing change if it is modified
			ICVSFile file = (ICVSFile)resource;
			// The parent caches the dirty state so we only need to check
			// the file if the parent is dirty
			// OPTIMIZE: Unfortunately, the modified check on the parent still loads
			// the CVS folder information so not much is gained
			if (file.getParent().isModified(monitor)) {
				return file.isModified(monitor);
			}
		}
		return false;
	}
	
	private boolean hasIncomingChange(IResource resource) throws TeamException {
		return remoteSynchronizer.isRemoteKnown(resource);
	}
}
