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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.core.subscribers.SyncInfoFilter.SyncInfoDirectionFilter;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * This action marks the local resource as merged by updating the base
 * resource revision to match the remote resource revision
 */
public class SubscriberConfirmMergedAction extends CVSSubscriberAction {

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.SubscriberAction#getSyncInfoFilter()
	 */
	protected SyncInfoFilter getSyncInfoFilter() {
		return new SyncInfoDirectionFilter(new int[] {SyncInfo.CONFLICTING});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#run(org.eclipse.team.ui.sync.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void run(MutableSyncInfoSet syncSet, IProgressMonitor monitor) throws CVSException {
		SyncInfo[] syncResources = syncSet.members();
		monitor.beginTask(null, 100 * syncResources.length);
		try {
			for (int i = 0; i < syncResources.length; i++) {
				SyncInfo info = syncResources[i];
				if (!makeOutgoing(info, Policy.subMonitorFor(monitor, 100))) {
					// Failure was logged in makeOutgoing
				}
			}
		} catch (TeamException e) {
			handle(e);
		} finally {
			monitor.done();
		}
	}

	private boolean makeOutgoing(SyncInfo info, IProgressMonitor monitor) throws CVSException, TeamException {
		monitor.beginTask(null, 100);
		try {
			CVSSyncInfo cvsInfo = getCVSSyncInfo(info);
			if (cvsInfo == null) {
				CVSUIPlugin.log(IStatus.ERROR, Policy.bind("SubscriberConfirmMergedAction.0", cvsInfo.getLocal().getFullPath().toString()), null); //$NON-NLS-1$
				return false;
			}
			// Make sure the parent is managed
			ICVSFolder parent = CVSWorkspaceRoot.getCVSFolderFor(cvsInfo.getLocal().getParent());
			if (!parent.isCVSFolder()) {
				// the parents must be made outgoing before the child can
				SyncInfo parentInfo = cvsInfo.getSubscriber().getSyncInfo(parent.getIResource());
				if (!makeOutgoing(parentInfo, Policy.subMonitorFor(monitor, 20))) {
					return false;
				}
			}
			IStatus status = cvsInfo.makeOutgoing(Policy.subMonitorFor(monitor, 80));
			if (status.getSeverity() == IStatus.ERROR) {
				logError(status);
				return false;
			}
			return true;
		} finally {
			monitor.done();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#getJobName(org.eclipse.team.ui.synchronize.actions.SyncInfoSet)
	 */
	protected String getJobName(SyncInfoSet syncSet) {
		return Policy.bind("SubscriberConfirmMergedAction.jobName", new Integer(syncSet.size()).toString()); //$NON-NLS-1$
	}
}
