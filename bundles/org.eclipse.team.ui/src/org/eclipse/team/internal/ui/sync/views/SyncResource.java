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
package org.eclipse.team.internal.ui.sync.views;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.team.core.sync.SyncInfo;

/**
 * This is the UI model object representing a SyncInfo for a resource.
 * The main purpose of this class is to allow menu object contributions
 * to be applied to these resources.
 */
public class SyncResource implements IAdaptable {

	private SyncSet syncSet;
	private IResource resource;

	/**
	 * @param info
	 */
	public SyncResource(SyncSet syncSet, IResource resource) {
		this.syncSet = syncSet;
		this.resource = resource;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (adapter == IResource.class) {
			return getLocalResource();
		} else if (adapter == SyncInfo.class) {
			return getSyncInfo();
		}
		return null;
	}

	/**
	 * @return
	 */
	public IResource getLocalResource() {
		return resource;
	}

	/**
	 * @return
	 */
	public SyncInfo getSyncInfo() {
		return syncSet.getSyncInfo(resource);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object object) {
		if (object instanceof SyncResource) {
			SyncResource syncResource = (SyncResource) object;
			return getLocalResource().equals(syncResource.getLocalResource());
		}
		return super.equals(object);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getLocalResource().hashCode();
	}
}
