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
package org.eclipse.team.internal.ui.synchronize.views;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.core.subscribers.TeamSubscriber;
import org.eclipse.team.internal.ui.synchronize.sets.SubscriberInput;
import org.eclipse.team.internal.ui.synchronize.sets.SyncSet;
import org.eclipse.team.ui.synchronize.ITeamSubscriberParticipantNode;

/**
 * A SynchronizeViewNode instance appears in the Synchronize View. Actions contributed to
 * the view can use these instances to determine the set of resources
 * shown based on the current filter applied to the view.
 * <p>
 * @see org.eclipse.team.ui.sync.ISynchronizeViewNode
 */
public class SynchronizeViewNode implements IAdaptable, ITeamSubscriberParticipantNode {

	private IResource resource;
	private SubscriberInput input;

	/**
	 * Construct a SynchromizeViewNode
	 * @param input The SubscriberInput for the node.
	 * @param resource The resource for the node
	 */
	public SynchronizeViewNode(SubscriberInput input, IResource resource) {
		this.input = input;	
		this.resource = resource;
	}

	protected SyncSet getSyncSet() {
		return input.getFilteredSyncSet();
	}
	
	public SubscriberInput getSubscriberInput() {
		return input;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeViewNode#getTeamSubscriber()
	 */
	public TeamSubscriber getTeamSubscriber() {
		return input.getSubscriber();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (adapter == SyncInfo.class) {
			return getSyncInfo();
		} 
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeViewNode#getSyncInfo()
	 */
	public SyncInfo getSyncInfo() {
		return getSyncSet().getSyncInfo(resource);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeViewNode#getChildSyncInfos()
	 */
	public SyncInfo[] getChildSyncInfos() {
		return getSyncSet().getOutOfSyncDescendants(resource);
	}
	
	/**
	 * Return true if the receiver's TeamSubscriber and Resource are equal to that of object.
	 * @param object The object to test
	 * @return true has the same subsriber and resource
	 */
	public boolean equals(Object object) {
		if (object instanceof SynchronizeViewNode) {
			SynchronizeViewNode syncViewNode = (SynchronizeViewNode) object;
			return getTeamSubscriber().equals(syncViewNode.getTeamSubscriber()) && 
				getResource().equals(syncViewNode.getResource());
		}
		return super.equals(object);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getResource().hashCode() | getTeamSubscriber().hashCode();
	}

	/**
	 * @return IResource The receiver's resource
	 */
	public IResource getResource() {
		return resource;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "SynchronizeViewNode for " + getResource().getFullPath().toString(); //$NON-NLS-1$
	}
}
