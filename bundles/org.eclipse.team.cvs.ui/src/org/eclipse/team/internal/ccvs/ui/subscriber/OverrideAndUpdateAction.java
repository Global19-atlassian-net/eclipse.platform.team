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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.core.subscribers.FastSyncInfoFilter.SyncInfoDirectionFilter;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.operations.OverrideAndUpdateOperation;

/**
 * Runs an update command that will prompt the user for overwritting local
 * changes to files that have non-mergeable conflicts. All the prompting logic
 * is in the super class.
 */
public class OverrideAndUpdateAction extends CVSSubscriberAction {
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.SubscriberAction#getSyncInfoFilter()
	 */
	protected FastSyncInfoFilter getSyncInfoFilter() {
		return new SyncInfoDirectionFilter(new int[] {SyncInfo.CONFLICTING, SyncInfo.OUTGOING});
	}
	
	private FastSyncInfoFilter getConflictingAdditionFilter() {
		return new FastSyncInfoFilter.AndSyncInfoFilter(
			new FastSyncInfoFilter[] {
				new FastSyncInfoFilter.SyncInfoDirectionFilter(new int[] {SyncInfo.CONFLICTING}), 
				new FastSyncInfoFilter.SyncInfoChangeTypeFilter(new int[] {SyncInfo.ADDITION})
			});
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#run(org.eclipse.team.ui.sync.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void run(MutableSyncInfoSet syncSet, IProgressMonitor monitor) throws TeamException {
		try {
			if(promptForOverwrite(syncSet)) {
				SyncInfo[] conflicts = syncSet.getNodes(getConflictingAdditionFilter());
				List conflictingResources = new ArrayList();
				for (int i = 0; i < conflicts.length; i++) {
					SyncInfo info = conflicts[i];
					conflictingResources.add(info.getLocal());
				}
				new OverrideAndUpdateOperation(getShell(), syncSet.getResources(), (IResource[]) conflictingResources.toArray(new IResource[conflictingResources.size()]), null /* tag */, false /* recurse */).run(monitor);
			}
		} catch (InvocationTargetException e) {
			throw CVSException.wrapException(e);
		} catch (InterruptedException e) {
			Policy.cancelOperation();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#getJobName(org.eclipse.team.ui.synchronize.actions.SyncInfoSet)
	 */
	protected String getJobName(SyncInfoSet syncSet) {
		return Policy.bind("OverrideAndUpdateAction.jobName", new Integer(syncSet.size()).toString()); //$NON-NLS-1$
	}
}
