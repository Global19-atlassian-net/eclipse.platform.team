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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.team.core.TeamException;

/**
 * A <code>ResourceVariantByteStore</code> that optimizes the memory footprint
 * of a remote resource variant tree by only storing those bytes that
 * differ from a base resource variant tree. This class should only be used 
 * for cases where the base and remote are on the same line-of-descent. 
 * For example, when the remote tree represents the current state of a branch
 * and the base represents the state of the same branch when the local workspace
 * as last refreshed.
 * <p>
 * This class also contains the logic that allows subclasses to determine if
 * bytes stored in the remote tree are on a different line-of-descent than the base.
 * This is necessary because it is possible for the base tree to change in ways that 
 * invalidate the stored remote variants. For example, if the local resources are moved
 * from the main trunck to a branch, any cached remote resource variants would be stale.

 */
public abstract class DescendantResourceVariantByteStore extends ResourceVariantByteStore {
	ResourceVariantByteStore baseCache, remoteCache;

	public DescendantResourceVariantByteStore(ResourceVariantByteStore baseCache, ResourceVariantByteStore remoteCache) {
		this.baseCache = baseCache;
		this.remoteCache = remoteCache;
	}
	
	/**
	 * This method will dispose the remote cache but not the base cache.
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#dispose()
	 */
	public void dispose() {
		remoteCache.dispose();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#getBytes(org.eclipse.core.resources.IResource)
	 */
	public byte[] getBytes(IResource resource) throws TeamException {
		byte[] remoteBytes = remoteCache.getBytes(resource);
		byte[] baseBytes = baseCache.getBytes(resource);
		if (baseBytes == null) {
			// There is no base so use the remote bytes
			return remoteBytes;
		}
		if (remoteBytes == null) {
			if (isVariantKnown(resource)) {
				// The remote is known to not exist
				// TODO: The check for NO_REMOTE does not take into consideration the line-of-descent
				return remoteBytes;
			} else {
				// The remote was either never queried or was the same as the base.
				// In either of these cases, the base bytes are used.
				return baseBytes;
			}
		}
		if (isDescendant(resource, baseBytes, remoteBytes)) {
			// Only use the remote bytes if they are later on the same line-of-descent as the base
			return remoteBytes;
		}
		// Use the base sbytes since the remote bytes must be stale (i.e. are
		// not on the same line-of-descent
		return baseBytes;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#setBytes(org.eclipse.core.resources.IResource, byte[])
	 */
	public boolean setBytes(IResource resource, byte[] bytes) throws TeamException {
		byte[] baseBytes = baseCache.getBytes(resource);
		if (baseBytes != null && equals(baseBytes, bytes)) {
			// Remove the existing bytes so the base will be used (thus saving space)
			return remoteCache.flushBytes(resource, IResource.DEPTH_ZERO);
		} else {
			return remoteCache.setBytes(resource, bytes);
		}	
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#removeBytes(org.eclipse.core.resources.IResource, int)
	 */
	public boolean flushBytes(IResource resource, int depth) throws TeamException {
		return remoteCache.flushBytes(resource, depth);
	}

	/**
	 * Return <code>true</code> if the variant associated with the given local 
	 * resource has been cached. This method is useful for those cases when
	 * there are no bytes for a resource variant and the client wants to
	 * know if this means that the remote does exist (i.e. this method returns
	 * <code>true</code>) or the remote has not been fetched (i.e. this method returns
	 * <code>false</code>).
	 * @param resource the local resource
	 * @return <code>true</code> if the variant associated with the given local 
	 * resource has been cached.
	 * @throws TeamException
	 */
	public abstract boolean isVariantKnown(IResource resource) throws TeamException;

	/**
	 * This method indicates whether the remote bytes are a later revision or version
	 * on the same line-of-descent as the base. A line of descent may be a branch or a fork
	 * (depending on the terminology used by the versioing server). If this method returns
	 * <code>false</code> then the remote bytes will be ignored by this tree.
	 * @param resource the local resource
	 * @param baseBytes the base bytes for the local resoource
	 * @param remoteBytes the remote bytes for the local resoource
	 * @return whether the remote bytes are later on the same line-of-descent as the base bytes
	 */
	protected abstract boolean isDescendant(IResource resource, byte[] baseBytes, byte[] remoteBytes) throws TeamException;
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#setVariantDoesNotExist(org.eclipse.core.resources.IResource)
	 */
	public boolean deleteBytes(IResource resource) throws TeamException {
		return remoteCache.deleteBytes(resource);
	}

	/**
	 * Return the base tree from which the remote is descendant.
	 * @return Returns the base tree.
	 */
	protected ResourceVariantByteStore getBaseTree() {
		return baseCache;
	}

	/**
	 * Return the remote tree which contains bytes only for the resource variants
	 * that differ from those in the base tree.
	 * @return Returns the remote tree.
	 */
	protected ResourceVariantByteStore getRemoteTree() {
		return remoteCache;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.core.subscribers.caches.ResourceVariantByteStore#members(org.eclipse.core.resources.IResource)
	 */
	public IResource[] members(IResource resource) throws TeamException {
		IResource[] remoteMembers = getRemoteTree().members(resource);
		IResource[] baseMembers = getBaseTree().members(resource);
		Set members = new HashSet();
		for (int i = 0; i < remoteMembers.length; i++) {
			members.add(remoteMembers[i]);
		}
		for (int i = 0; i < baseMembers.length; i++) {
			IResource member = baseMembers[i];
			// Add the base only inf the remote does not know about it
			// (i.e. hasn't marked it as deleted
			if (!isVariantKnown(member)) {
				members.add(member);
			}
		}
		return (IResource[]) members.toArray(new IResource[members.size()]);
	}

}
