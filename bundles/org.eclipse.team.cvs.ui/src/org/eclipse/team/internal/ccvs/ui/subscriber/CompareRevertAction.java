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
package org.eclipse.team.internal.ccvs.ui.subscriber;

import java.util.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * Action in compare editor that reverts the local contents to match the contents on the server.
 */
public class CompareRevertAction extends CVSSubscriberAction {
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#run(org.eclipse.team.core.subscribers.MutableSyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void run(MutableSyncInfoSet syncSet, IProgressMonitor monitor) throws TeamException {
		SyncInfo[] changed = syncSet.members();
		if (changed.length == 0) return;
		
		// The list of sync resources to be updated using "cvs update"
		List updateShallow = new ArrayList();
		// A list of sync resource folders which need to be created locally 
		// (incoming addition or previously pruned)
		Set parentCreationElements = new HashSet();
	
		for (int i = 0; i < changed.length; i++) {
			SyncInfo changedNode = changed[i];
			
			// Make sure that parent folders exist
			SyncInfo parent = getParent(changedNode);
			if (parent != null && isOutOfSync(parent)) {
				// We need to ensure that parents that are either incoming folder additions
				// or previously pruned folders are recreated.
				parentCreationElements.add(parent);
			}
			
			IResource resource = changedNode.getLocal();
			if (resource.getType() == IResource.FILE) {	
				if (changedNode.getLocal().exists()) {
					updateShallow.add(changedNode);
				} else if (changedNode.getRemote() != null) {
					updateShallow.add(changedNode);
				}
			} else {
				// Special handling for folders to support shallow operations on files
				// (i.e. folder operations are performed using the sync info already
				// contained in the sync info.
				if (isOutOfSync(changedNode)) {
					parentCreationElements.add(changedNode);
				}
			}

		}
		try {
			// Calculate the total amount of work needed
			int work = updateShallow.size() * 100;
			monitor.beginTask(null, work);

			if (parentCreationElements.size() > 0) {
				makeInSync((SyncInfo[]) parentCreationElements.toArray(new SyncInfo[parentCreationElements.size()]));				
			}		
			if (updateShallow.size() > 0) {
				runUpdate((SyncInfo[])updateShallow.toArray(new SyncInfo[updateShallow.size()]), Policy.subMonitorFor(monitor, updateShallow.size() * 100));
			}
		} finally {
			monitor.done();
		}
		return;
	}
	
	private void runUpdate(SyncInfo[] infos, IProgressMonitor monitor) throws TeamException {
		monitor.beginTask(null, 100 * infos.length);
		for (int i = 0; i < infos.length; i++) {
			SyncInfo info = infos[i];
			makeRemoteLocal(info, Policy.subMonitorFor(monitor, 100));
		}
		monitor.done();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#getJobName(org.eclipse.team.core.subscribers.SyncInfoSet)
	 */
	protected String getJobName(SyncInfoSet syncSet) {
		return "Reverting {0} resources" + new Integer(syncSet.size()).toString();

	}
}
