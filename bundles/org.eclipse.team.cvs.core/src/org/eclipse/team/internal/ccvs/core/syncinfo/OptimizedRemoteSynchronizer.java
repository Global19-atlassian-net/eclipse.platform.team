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
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.util.Util;

/**
 * The optimized remote synchronizer uses the base sync info when the remote
 * is unknown
 */
public class OptimizedRemoteSynchronizer extends RemoteSynchronizer {

	// The local synchronizer is used for cases where the remote is unknown
	private BaseSynchronizer baseSynchronizer = new BaseSynchronizer();
	
	/**
	 * @param id
	 */
	public OptimizedRemoteSynchronizer(String id) {
		super(id);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSynchronizer#getSyncBytes(org.eclipse.core.resources.IResource)
	 */
	public byte[] getSyncBytes(IResource resource) throws CVSException {
		byte[] bytes = super.getSyncBytes(resource);
		if (bytes == null) {
			// The remote was never known so use the base
			bytes = baseSynchronizer.getSyncBytes(resource);
		}
		return bytes;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.syncinfo.RemoteSynchronizer#setSyncBytes(org.eclipse.core.resources.IResource, byte[])
	 */
	public void setSyncBytes(IResource resource, byte[] bytes) throws CVSException {
		byte[] baseBytes = baseSynchronizer.getSyncBytes(resource);
		if (baseBytes != null && Util.equals(baseBytes, bytes)) {
			// Remove the existing bytes so the base will be used (thus saving space)
			removeSyncBytes(resource, IResource.DEPTH_ZERO);
		} else {
			super.setSyncBytes(resource, bytes);
		}
		
	}

}
