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
package org.eclipse.team.internal.core.subscribers;

import java.util.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.team.core.subscribers.*;

/**
 * This event keeps track of the changes in a sync set
 */
public class SyncSetChangedEvent implements ISyncInfoSetChangeEvent {
	
	private SyncInfoSet set;
	
	// List that accumulate changes
	// SyncInfo
	private Set changedResources = new HashSet();
	private Set removedResources = new HashSet();
	private Set addedResources = new HashSet();
	
	private boolean reset = false;

	public SyncSetChangedEvent(SyncInfoSet set) {
		super();
		this.set = set;
	}

	public void added(SyncInfo info) {
		if (removedResources.contains(info.getLocal())) {
			// A removal followed by an addition is treated as a change
			removedResources.remove(info.getLocal());
			changed(info);
		} else {
			addedResources.add(info);
		}
	}
	
	public void removed(IResource resource, SyncInfo info) {
		if (changedResources.contains(info)) {
			// No use in reporting the change since it has subsequently been removed
			changedResources.remove(info);
		} else if (addedResources.contains(info)) {
			// An addition followed by a removal can be dropped 
			addedResources.remove(info);
			return;
		}
		removedResources.add(resource);
	}
	
	public void changed(SyncInfo info) {
		changedResources.add(info);
	}
	
	public SyncInfo[] getAddedResources() {
		return (SyncInfo[]) addedResources.toArray(new SyncInfo[addedResources.size()]);
	}

	public SyncInfo[] getChangedResources() {
		return (SyncInfo[]) changedResources.toArray(new SyncInfo[changedResources.size()]);
	}

	public IResource[] getRemovedResources() {
		return (IResource[]) removedResources.toArray(new IResource[removedResources.size()]);
	}

	public SyncInfoSet getSet() {
		return set;
	}

	public void reset() {
		reset = true;
	}
	
	public boolean isReset() {
		return reset;
	}
	
	public boolean isEmpty() {
		return changedResources.isEmpty() && removedResources.isEmpty() && addedResources.isEmpty();
	}
}
