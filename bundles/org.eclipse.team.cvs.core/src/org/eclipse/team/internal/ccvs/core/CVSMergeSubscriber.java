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
import org.eclipse.team.internal.ccvs.core.syncinfo.CVSRemoteSynchronizer;
import org.eclipse.team.internal.ccvs.core.syncinfo.RemoteTagSynchronizer;

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
public class CVSMergeSubscriber extends CVSSyncTreeSubscriber implements IResourceChangeListener, ITeamResourceChangeListener {

	public static final String QUALIFIED_NAME = "org.eclipse.team.cvs.ui.cvsmerge-participant"; //$NON-NLS-1$
	private static final String UNIQUE_ID_PREFIX = "merge-"; //$NON-NLS-1$
	
	private CVSTag start, end;
	private List roots;
	private RemoteTagSynchronizer remoteSynchronizer;
	private SynchronizationSyncBytesCache mergedSynchronizer;
	private RemoteTagSynchronizer baseSynchronizer;

	private static final byte[] NO_REMOTE = new byte[0];
	

	protected IResource[] refreshRemote(IResource resource, int depth, IProgressMonitor monitor) throws TeamException {
		IResource[] remoteChanges = super.refreshRemote(resource, depth, monitor);
		adjustMergedResources(remoteChanges);
		return remoteChanges;
	}

	private void adjustMergedResources(IResource[] remoteChanges) throws TeamException {
		for (int i = 0; i < remoteChanges.length; i++) {
			IResource resource = remoteChanges[i];
			mergedSynchronizer.removeSyncBytes(resource, IResource.DEPTH_ZERO);			
		}	
	}

	private static QualifiedName getUniqueId() {
		String uniqueId = Long.toString(System.currentTimeMillis());
		return new QualifiedName(QUALIFIED_NAME, "CVS" + UNIQUE_ID_PREFIX + uniqueId); //$NON-NLS-1$
	}
	
	public CVSMergeSubscriber(IResource[] roots, CVSTag start, CVSTag end) {		
		this(getUniqueId(), roots, start, end);
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
		remoteSynchronizer = new RemoteTagSynchronizer(syncKeyPrefix + end.getName(), end);
		baseSynchronizer = new RemoteTagSynchronizer(syncKeyPrefix + start.getName(), start);
		mergedSynchronizer = new SynchronizationSyncBytesCache(new QualifiedName(CVSRemoteSynchronizer.SYNC_KEY_QUALIFIER, syncKeyPrefix + "0merged")); //$NON-NLS-1$
		
		try {
			setCurrentComparisonCriteria(ContentComparisonCriteria.ID_IGNORE_WS);
		} catch (TeamException e) {
			// use the default but log an exception because the content comparison should
			// always be available.
			CVSProviderPlugin.log(e);
		}
		
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		CVSProviderPlugin.getPlugin().getCVSWorkspaceSubscriber().addListener(this);
	}

	protected SyncInfo getSyncInfo(IResource local, ISubscriberResource base, ISubscriberResource remote, IProgressMonitor monitor) throws TeamException {
		return new CVSMergeSyncInfo(local, base, remote, this, monitor);
	}

	public void merged(IResource[] resources) throws TeamException {
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			byte[] remoteBytes = remoteSynchronizer.getSyncBytes(resource);
			if (remoteBytes == null) {
				// If there is no remote, use a place holder to indicate the resouce was merged
				remoteBytes = NO_REMOTE;
			}
			mergedSynchronizer.setSyncBytes(resource, remoteBytes);
		}
		fireTeamResourceChange(TeamDelta.asSyncChangedDeltas(this, resources));
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
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getRemoteSynchronizer()
	 */
	protected SubscriberResourceTree getRemoteSynchronizer() {
		return remoteSynchronizer;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getBaseSynchronizer()
	 */
	protected SubscriberResourceTree getBaseSynchronizer() {
		return baseSynchronizer;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.TeamSubscriber#isSupervised(org.eclipse.core.resources.IResource)
	 */
	public boolean isSupervised(IResource resource) throws TeamException {
		return getBaseSynchronizer().hasRemote(resource) || getRemoteSynchronizer().hasRemote(resource); 
	}

	public CVSTag getStartTag() {
		return start;
	}
	
	public CVSTag getEndTag() {
		return end;
	}
	
	public boolean isReleaseSupported() {
		// you can't release changes to a merge
		return false;
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

	public boolean isMerged(IResource resource) throws TeamException {
		return (mergedSynchronizer.getSyncBytes(resource) != null ||
				mergedSynchronizer.isRemoteKnown(resource));
	}

	/* 
	 * Currently only the workspace subscriber knows when a project has been deconfigured. We will listen for these events
	 * and remove the root then forward to merge subscriber listeners.
	 * (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.ITeamResourceChangeListener#teamResourceChanged(org.eclipse.team.core.subscribers.TeamDelta[])
	 */
	public void teamResourceChanged(TeamDelta[] deltas) {		
		for (int i = 0; i < deltas.length; i++) {
			TeamDelta delta = deltas[i];
			switch(delta.getFlags()) {
				case TeamDelta.PROVIDER_DECONFIGURED:
					IResource resource = delta.getResource();
					if(roots.remove(resource))	{
						fireTeamResourceChange(new TeamDelta[] {delta});
					}						
					break;
			}
		}
	}		
}