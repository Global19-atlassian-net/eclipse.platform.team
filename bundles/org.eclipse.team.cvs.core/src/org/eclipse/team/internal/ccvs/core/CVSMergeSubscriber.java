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

import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFile;
import org.eclipse.team.internal.ccvs.core.syncinfo.CVSSynchronizationCache;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Util;
import org.eclipse.team.internal.core.subscribers.caches.SynchronizationCache;
import org.eclipse.team.internal.core.subscribers.caches.SynchronizationSyncBytesCache;

/**
 * A CVSMergeSubscriber is responsible for maintaining the remote trees for a merge into
 * the workspace. The remote trees represent the CVS revisions of the start and end
 * points (version or branch) of the merge.
 * 
 * This subscriber stores the remote handles in the resource tree sync info slot. When
 * the merge is cancelled this sync info is cleared.
 * 
 * A merge can persist between workbench sessions and thus can be used as an
 * ongoing merge.
 * 
 * TODO: Is the merge subscriber interested in workspace sync info changes?
 * TODO: Do certain operations (e.g. replace with) invalidate a merge subscriber?
 * TODO: How to ensure that sync info is flushed when merge roots are deleted?
 */
public class CVSMergeSubscriber extends CVSSyncTreeSubscriber implements IResourceChangeListener, ISubscriberChangeListener {

	public static final String QUALIFIED_NAME = "org.eclipse.team.cvs.ui.cvsmerge-participant"; //$NON-NLS-1$
	private static final String UNIQUE_ID_PREFIX = "merge-"; //$NON-NLS-1$
	
	private CVSTag start, end;
	private List roots;
	private SynchronizationCache remoteSynchronizer;
	private SynchronizationSyncBytesCache mergedSynchronizer;
	private SynchronizationCache baseSynchronizer;
	
	public CVSMergeSubscriber(IResource[] roots, CVSTag start, CVSTag end) {		
		this(getUniqueId(), roots, start, end);
	}

	private static QualifiedName getUniqueId() {
		String uniqueId = Long.toString(System.currentTimeMillis());
		return new QualifiedName(QUALIFIED_NAME, "CVS" + UNIQUE_ID_PREFIX + uniqueId); //$NON-NLS-1$
	}
	
	public CVSMergeSubscriber(QualifiedName id, IResource[] roots, CVSTag start, CVSTag end) {		
		super(id, Policy.bind("CVSMergeSubscriber.2", start.getName(), end.getName()), Policy.bind("CVSMergeSubscriber.4")); //$NON-NLS-1$ //$NON-NLS-2$
		this.start = start;
		this.end = end;
		this.roots = new ArrayList(Arrays.asList(roots));
		initialize();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSWorkspaceSubscriber#initialize()
	 */
	private void initialize() {			
		QualifiedName id = getId();
		String syncKeyPrefix = id.getLocalName();
		remoteSynchronizer = new CVSSynchronizationCache(new QualifiedName(SYNC_KEY_QUALIFIER, syncKeyPrefix + end.getName()));
		baseSynchronizer = new CVSSynchronizationCache(new QualifiedName(SYNC_KEY_QUALIFIER, syncKeyPrefix + start.getName()));
		mergedSynchronizer = new SynchronizationSyncBytesCache(new QualifiedName(SYNC_KEY_QUALIFIER, syncKeyPrefix + "0merged")); //$NON-NLS-1$
		
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		CVSProviderPlugin.getPlugin().getCVSWorkspaceSubscriber().addListener(this);
	}

	protected SyncInfo getSyncInfo(IResource local, IRemoteResource base, IRemoteResource remote) throws TeamException {
		CVSMergeSyncInfo info = new CVSMergeSyncInfo(local, base, remote, this);
		info.init();
		return info;
	}

	public void merged(IResource[] resources) throws TeamException {
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			internalMerged(resource);
		}
		fireTeamResourceChange(SubscriberChangeEvent.asSyncChangedDeltas(this, resources));
	}
	
	private void internalMerged(IResource resource) throws TeamException {
		byte[] remoteBytes = remoteSynchronizer.getSyncBytes(resource);
		if (remoteBytes == null) {
			mergedSynchronizer.setRemoteDoesNotExist(resource);
		} else {
			mergedSynchronizer.setSyncBytes(resource, remoteBytes);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.TeamSubscriber#cancel()
	 */
	public void cancel() {	
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);		
		remoteSynchronizer.dispose();
		baseSynchronizer.dispose();
		mergedSynchronizer.dispose();		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.TeamSubscriber#roots()
	 */
	public IResource[] roots() {
		return (IResource[]) roots.toArray(new IResource[roots.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.TeamSubscriber#isSupervised(org.eclipse.core.resources.IResource)
	 */
	public boolean isSupervised(IResource resource) throws TeamException {
		return getBaseSynchronizationCache().getSyncBytes(resource) != null || getRemoteSynchronizationCache().getSyncBytes(resource) != null; 
	}

	public CVSTag getStartTag() {
		return start;
	}
	
	public CVSTag getEndTag() {
		return end;
	}

	/*
	 * What to do when a root resource for this merge changes?
	 * Deleted, Move, Copied
	 * Changed in a CVS way (tag changed, revision changed...)
	 * Contents changed by user
	 * @see IResourceChangeListener#resourceChanged(org.eclipse.core.resources.IResourceChangeEvent)
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			IResourceDelta delta = event.getDelta();
			if(delta != null) {
				delta.accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) throws CoreException {
					IResource resource = delta.getResource();
			
					if (resource.getType()==IResource.PROJECT) {
						IProject project = (IProject)resource;
						if (!project.isAccessible()) {
							return false;
						}
						if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
							return false;
						} 
						if (RepositoryProvider.getProvider(project, CVSProviderPlugin.getTypeId()) == null) {
							return false;
						}
					}
			
					if (roots.contains(resource)) {
						if (delta.getKind() == IResourceDelta.REMOVED || delta.getKind() == IResourceDelta.MOVED_TO) {
							cancel();
						}
						// stop visiting children
						return false;
					}
					// keep visiting children
					return true;
				}
			});
			}
		} catch (CoreException e) {
			CVSProviderPlugin.log(e.getStatus());
		}
	}

	/**
	 * Return whether the given resource has been merged with its 
	 * corresponding remote.
	 * @param resource tghe loca resource
	 * @return boolean
	 * @throws TeamException
	 */
	public boolean isMerged(IResource resource) throws TeamException {
		byte[] mergedBytes = mergedSynchronizer.getSyncBytes(resource);
		byte[] remoteBytes = remoteSynchronizer.getSyncBytes(resource);
		if (mergedBytes == null) {
			return (remoteBytes == null 
					&& mergedSynchronizer.isRemoteKnown(resource)
					&& remoteSynchronizer.isRemoteKnown(resource));
		}
		return Util.equals(mergedBytes, remoteBytes);
	}

	/* 
	 * Currently only the workspace subscriber knows when a project has been deconfigured. We will listen for these events
	 * and remove the root then forward to merge subscriber listeners.
	 * (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.ITeamResourceChangeListener#teamResourceChanged(org.eclipse.team.core.subscribers.TeamDelta[])
	 */
	public void subscriberResourceChanged(ISubscriberChangeEvent[] deltas) {		
		for (int i = 0; i < deltas.length; i++) {
			ISubscriberChangeEvent delta = deltas[i];
			switch(delta.getFlags()) {
				case ISubscriberChangeEvent.ROOT_REMOVED:
					IResource resource = delta.getResource();
					if(roots.remove(resource))	{
						fireTeamResourceChange(new ISubscriberChangeEvent[] {delta});
					}						
					break;
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getRemoteTag()
	 */
	protected CVSTag getRemoteTag() {
		return getEndTag();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getBaseTag()
	 */
	protected CVSTag getBaseTag() {
		return getStartTag();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getBaseSynchronizationCache()
	 */
	protected SynchronizationCache getBaseSynchronizationCache() {
		return baseSynchronizer;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getRemoteSynchronizationCache()
	 */
	protected SynchronizationCache getRemoteSynchronizationCache() {
		return remoteSynchronizer;
	}
	
	protected  boolean getCacheFileContentsHint() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#refreshBase(org.eclipse.core.resources.IResource[], int, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IResource[] refreshBase(IResource[] resources, int depth, IProgressMonitor monitor) throws TeamException {
		// Only refresh the base of a resource once as it should not change
		List unrefreshed = new ArrayList();
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (!baseSynchronizer.isRemoteKnown(resource)) {
				unrefreshed.add(resource);
			}
		}
		if (unrefreshed.isEmpty()) {
			monitor.done();
			return new IResource[0];
		}
		IResource[] refreshed = super.refreshBase((IResource[]) unrefreshed.toArray(new IResource[unrefreshed.size()]), depth, monitor);
		return refreshed;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#refreshRemote(org.eclipse.core.resources.IResource[], int, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IResource[] refreshRemote(IResource[] resources, int depth, IProgressMonitor monitor) throws TeamException {
		monitor.beginTask(null, 100);
		try {
			IResource[] refreshed = super.refreshRemote(resources, depth, Policy.subMonitorFor(monitor, 50));
			compareWithRemote(refreshed, Policy.subMonitorFor(monitor, 50));
			return refreshed;
		} finally {
			monitor.done();
		}
	}

	/*
	 * Mark as merged any local resources whose contents match that of the remote resource.
	 */
	private void compareWithRemote(IResource[] refreshed, IProgressMonitor monitor) throws CVSException, TeamException {
		// For any remote changes, if the revision differs from the local, compare the contents.
		if (refreshed.length == 0) return;
		SyncInfoFilter.ContentComparisonSyncInfoFilter contentFilter =
			new SyncInfoFilter.ContentComparisonSyncInfoFilter();
		monitor.beginTask(null, refreshed.length * 100);
		for (int i = 0; i < refreshed.length; i++) {
			IResource resource = refreshed[i];
			if (resource.getType() == IResource.FILE) {
				ICVSFile local = CVSWorkspaceRoot.getCVSFileFor((IFile)resource);
				byte[] localBytes = local.getSyncBytes();
				byte[] remoteBytes = remoteSynchronizer.getSyncBytes(resource);
				if (remoteBytes != null 
						&& localBytes != null
						&& local.exists()
						&& !ResourceSyncInfo.getRevision(remoteBytes).equals(ResourceSyncInfo.getRevision(localBytes))
						&& contentFilter.select(getSyncInfo(resource), Policy.subMonitorFor(monitor, 100))) {
					// The contents are equals so mark the file as merged
					internalMerged(resource);
				}
			}
		}
		monitor.done();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.utils.SyncTreeSubscriber#getBaseResource(org.eclipse.core.resources.IResource)
	 */
	public IRemoteResource getBaseResource(IResource resource) throws TeamException {
		// Use the merged bytes for the base if there are some
		byte[] mergedBytes = mergedSynchronizer.getSyncBytes(resource);
		if (mergedBytes != null) {
			byte[] parentBytes = baseSynchronizer.getSyncBytes(resource.getParent());
			if (parentBytes != null) {
				return RemoteFile.fromBytes(resource, mergedBytes, parentBytes);
			}
		}
		return super.getBaseResource(resource);
	}
}