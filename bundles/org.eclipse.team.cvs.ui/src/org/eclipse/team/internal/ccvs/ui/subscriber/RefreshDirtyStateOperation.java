/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.core.synchronize.SyncInfoFilter.ContentComparisonSyncInfoFilter;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.ui.IWorkbenchPart;

/**
 * Resets the dirty state of files whose contents match their base.
 */
public class RefreshDirtyStateOperation extends CVSSubscriberOperation {
	
	protected RefreshDirtyStateOperation(IWorkbenchPart part, IDiffElement[] elements) {
		super(part, elements);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberOperation#run(org.eclipse.team.core.synchronize.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void run(SyncInfoSet set, IProgressMonitor monitor) throws TeamException {
		// TODO Fetch the contents in a single connection
		final ContentComparisonSyncInfoFilter comparator = new SyncInfoFilter.ContentComparisonSyncInfoFilter(false);
		final SyncInfo[] infos = set.getSyncInfos();
		if (infos.length == 0) return;
		IProject project = infos[0].getLocal().getProject();
		ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor(project);
		folder.run(new ICVSRunnable() {
			public void run(IProgressMonitor monitor) throws CVSException {
				monitor.beginTask(null, infos.length * 100);
				for (int i = 0; i < infos.length; i++) {
					SyncInfo info = infos[i];
					IResource resource = info.getLocal();
					if (resource.getType() == IResource.FILE) {
						if (comparator.compareContents((IFile)resource, info.getBase(), Policy.subMonitorFor(monitor, 100))) {
							ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile)resource);
							cvsFile.checkedIn(null);
						}
					}
				}
				monitor.done();
			}
		}, monitor);

	}
}
