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
package org.eclipse.team.internal.core.subscribers.caches;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.core.Assert;

/**
 * A <code>ResourceVariantByteStore</code> that caches the variant bytes using 
 * the <code>org.eclipse.core.resources.ISynchronizer</code> so that
 * the tree is cached accross workbench invocations.
 */
public class PersistantResourceVariantByteStore extends ResourceVariantByteStore {

	private static final byte[] NO_REMOTE = new byte[0];
	
	private QualifiedName syncName;
	
	/**
	 * Create a persistant tree that uses the given qualified name
	 * as the key in the <code>org.eclipse.core.resources.ISynchronizer</code>.
	 * It must be unique and should use the plugin as the local name
	 * and a unique id within the plugin as the qualifier name.
	 * @param name the key used in the Core synchronizer
	 */
	public PersistantResourceVariantByteStore(QualifiedName name) {
		syncName = name;
		getSynchronizer().add(syncName);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#dispose()
	 */
	public void dispose() {
		getSynchronizer().remove(getSyncName());
	}

	/**
	 * Convenience method that returns the Core <code>ISynchronizer</code>.
	 */
	protected ISynchronizer getSynchronizer() {
		return ResourcesPlugin.getWorkspace().getSynchronizer();
	}

	/**
	 * Return the qualified name that uniquely identifies this tree.
	 * @return the qwualified name that uniquely identifies this tree.
	 */
	public QualifiedName getSyncName() {
		return syncName;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#getBytes(org.eclipse.core.resources.IResource)
	 */
	public byte[] getBytes(IResource resource) throws TeamException {
		byte[] syncBytes = internalGetSyncBytes(resource);
		if (syncBytes != null && equals(syncBytes, NO_REMOTE)) {
			// If it is known that there is no remote, return null
			return null;
		}
		return syncBytes;
	}

	private byte[] internalGetSyncBytes(IResource resource) throws TeamException {
		try {
			return getSynchronizer().getSyncInfo(getSyncName(), resource);
		} catch (CoreException e) {
			throw TeamException.asTeamException(e);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#setBytes(org.eclipse.core.resources.IResource, byte[])
	 */
	public boolean setBytes(IResource resource, byte[] bytes) throws TeamException {
		Assert.isNotNull(bytes);
		byte[] oldBytes = internalGetSyncBytes(resource);
		if (oldBytes != null && equals(oldBytes, bytes)) return false;
		try {
			getSynchronizer().setSyncInfo(getSyncName(), resource, bytes);
			return true;
		} catch (CoreException e) {
			throw TeamException.asTeamException(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#removeBytes(org.eclipse.core.resources.IResource, int)
	 */
	public boolean flushBytes(IResource resource, int depth) throws TeamException {
		if (resource.exists() || resource.isPhantom()) {
			try {
				if (depth != IResource.DEPTH_ZERO || internalGetSyncBytes(resource) != null) {
					getSynchronizer().flushSyncInfo(getSyncName(), resource, depth);
					return true;
				}
			} catch (CoreException e) {
				throw TeamException.asTeamException(e);
			}
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#isVariantKnown(org.eclipse.core.resources.IResource)
	 */
	public boolean isVariantKnown(IResource resource) throws TeamException {
		return internalGetSyncBytes(resource) != null;
	}
	
	/**
	 * This method should be invoked by a client to indicate that it is known that 
	 * there is no remote resource associated with the local resource. After this method
	 * is invoked, <code>isRemoteKnown(resource)</code> will return <code>true</code> and
	 * <code>getSyncBytes(resource)</code> will return <code>null</code>.
	 * @return <code>true</code> if this changes the remote sync bytes
	 */
	public boolean deleteBytes(IResource resource) throws TeamException {
		return setBytes(resource, NO_REMOTE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#members(org.eclipse.core.resources.IResource)
	 */
	public IResource[] members(IResource resource) throws TeamException {
		if(resource.getType() == IResource.FILE) {
			return new IResource[0];
		}	
		try {
			// Filter and return only resources that have sync bytes in the cache.
			IResource[] members = ((IContainer)resource).members(true /* include phantoms */);
			List filteredMembers = new ArrayList(members.length);
			for (int i = 0; i < members.length; i++) {
				IResource member = members[i];
				if(getBytes(member) != null) {
					filteredMembers.add(member);
				}
			}
			return (IResource[]) filteredMembers.toArray(new IResource[filteredMembers.size()]);
		} catch (CoreException e) {
			throw TeamException.asTeamException(e);
		}
	}

}
