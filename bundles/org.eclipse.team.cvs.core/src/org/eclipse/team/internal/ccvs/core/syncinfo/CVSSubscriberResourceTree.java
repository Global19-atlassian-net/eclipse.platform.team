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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.subscribers.helpers.*;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFile;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFolder;

/**
 * This RemoteSynchronizr uses a CVS Tag to fetch the remote tree
 */
public class CVSSubscriberResourceTree extends SyncBytesSubscriberResourceTree {

	public static final String SYNC_KEY_QUALIFIER = "org.eclipse.team.cvs"; //$NON-NLS-1$
	
	private CVSTag tag;
	private SynchronizationCache baseCache;
	
	public CVSSubscriberResourceTree(SynchronizationCache cache, CVSTag tag) {
		super(cache);
		this.tag = tag;
	}

	public CVSSubscriberResourceTree(String id, CVSTag tag) {
		this(
			new SynchronizationSyncBytesCache(new QualifiedName(CVSSubscriberResourceTree.SYNC_KEY_QUALIFIER, id)),
			tag);
	}

	public CVSSubscriberResourceTree(SynchronizationCache baseCache, SynchronizationCache cache, CVSTag tag) {
		this(new CVSDescendantSynchronizationCache(baseCache, cache), tag);
		this.baseCache = baseCache;
	}

	protected RefreshOperation getRefreshOperation() {
		return new CVSRefreshOperation(getSynchronizationCache(), baseCache, tag);
	}

	public ISubscriberResource getRemoteResource(IResource resource) throws TeamException {
		byte[] remoteBytes = getSyncBytes(resource);
		if (remoteBytes == null) {
			// There is no remote handle for this resource
			return null;
		} else {
			// TODO: This code assumes that the type of the remote resource
			// matches that of the local resource. This may not be true.
			if (resource.getType() == IResource.FILE) {
				byte[] parentBytes = getSyncBytes(resource.getParent());
				if (parentBytes == null) {
					CVSProviderPlugin.log(new CVSException( 
							Policy.bind("ResourceSynchronizer.missingParentBytesOnGet", getSyncName().toString(), resource.getFullPath().toString()))); //$NON-NLS-1$
					// Assume there is no remote and the problem is a programming error
					return null;
				}
				return RemoteFile.fromBytes(resource, remoteBytes, parentBytes);
			} else {
				return RemoteFolder.fromBytes(resource, remoteBytes);
			}
		}
	}

	private Object getSyncName() {
		SynchronizationCache cache = getSynchronizationCache();
		if (cache instanceof SynchronizationSyncBytesCache) {
			return ((SynchronizationSyncBytesCache)cache).getSyncName();
		}
		return cache.getClass().getName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.syncinfo.RemoteSynchronizer#setSyncBytes(org.eclipse.core.resources.IResource, byte[])
	 */
	public boolean setSyncBytes(IResource resource, byte[] bytes) throws TeamException {
		boolean changed = super.setSyncBytes(resource, bytes);
		if (resource.getType() == IResource.FILE && getSyncBytes(resource) != null && !parentHasSyncBytes(resource)) {
			// Log a warning if there is no sync bytes available for the resource's
			// parent but there is valid sync bytes for the child
			CVSProviderPlugin.log(new TeamException(Policy.bind("ResourceSynchronizer.missingParentBytesOnSet", getSyncName().toString(), resource.getFullPath().toString()))); //$NON-NLS-1$
		}
		return changed;
	}

	/**
	 * Indicates whether the parent of the given local resource has sync bytes for its
	 * corresponding remote resource. The parent bytes of a remote resource are required
	 * (by CVS) to create a handle to the remote resource.
	 */
	protected boolean parentHasSyncBytes(IResource resource) throws TeamException {
		if (resource.getType() == IResource.PROJECT) return true;
		return (getSyncBytes(resource.getParent()) != null);
	}

}
