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

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.core.subscribers.caches.SynchronizationCache;
import org.eclipse.team.internal.ccvs.core.resources.EclipseSynchronizer;


public class CVSBaseSynchronizationCache extends SynchronizationCache {
	public void dispose() {
		// Do nothing
	}
	public byte[] getSyncBytes(IResource resource) throws TeamException {
		if (resource.getType() == IResource.FILE) {
			// For a file, return the entry line
			byte[] bytes =  EclipseSynchronizer.getInstance().getSyncBytes(resource);
			if (bytes != null) {
				// Use the base sync info (i.e. no deletion or addition)
				if (ResourceSyncInfo.isDeletion(bytes)) {
					bytes = ResourceSyncInfo.convertFromDeletion(bytes);
				} else if (ResourceSyncInfo.isAddition(bytes)) {
					bytes = null;
				}
			}
			return bytes;
		} else {
			// For a folder, return the folder sync info bytes
			FolderSyncInfo info = EclipseSynchronizer.getInstance().getFolderSync((IContainer)resource);
			if (info == null) return null;
			return info.getBytes();
		}
	}
	public boolean isRemoteKnown(IResource resource) throws TeamException {
		return getSyncBytes(resource) != null;
	}
	public boolean removeSyncBytes(IResource resource, int depth) throws TeamException {
		throw new UnsupportedOperationException();
	}
	public boolean setSyncBytes(IResource resource, byte[] bytes) throws TeamException {
		throw new UnsupportedOperationException();
	}
	public boolean setRemoteDoesNotExist(IResource resource) throws TeamException {
		throw new UnsupportedOperationException();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.utils.SynchronizationCache#members(org.eclipse.core.resources.IResource)
	 */
	public IResource[] members(IResource resource) throws TeamException {
		if(resource.getType() == IResource.FILE) {
			return new IResource[0];
		}	
		return EclipseSynchronizer.getInstance().members((IContainer)resource);
	}
}