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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.ccvs.ui.sync.ToolTipMessageDialog;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.sync.actions.SyncInfoDirectionFilter;
import org.eclipse.team.ui.sync.actions.SyncInfoFilter;
import org.eclipse.team.ui.sync.actions.SyncInfoSet;

public class SubscriberCommitAction extends CVSSubscriberAction {

	private String comment;

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.SubscriberAction#getSyncInfoFilter()
	 */
	protected SyncInfoFilter getSyncInfoFilter() {
		return new SyncInfoDirectionFilter(new int[] {SyncInfo.OUTGOING});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#getFilteredSyncInfoSet(org.eclipse.team.internal.ui.sync.views.SyncInfo[])
	 */
	protected SyncInfoSet getFilteredSyncInfoSet(SyncInfo[] selectedResources) {
		SyncInfoSet syncSet = super.getFilteredSyncInfoSet(selectedResources);
		if (!promptForConflictHandling(syncSet)) return null;
		try {
			if (!promptForUnaddedHandling(syncSet)) return null;
		} catch (CVSException e) {
			Utils.handle(e);
		}
		return syncSet;
	}
	
	protected boolean promptForConflictHandling(SyncInfoSet syncSet) {
		// If there is a conflict in the syncSet, remove from sync set.
		if (syncSet.hasConflicts() || syncSet.hasIncomingChanges()) {
			syncSet.removeConflictingNodes();
			syncSet.removeIncomingNodes();
		}
		return true;
	}

	private boolean promptForUnaddedHandling(SyncInfoSet syncSet) throws CVSException {
		if (syncSet.isEmpty()) return false;
		
		// accumulate any resources that are not under version control
		IResource[] unadded = getUnaddedResources(syncSet);
		
		// prompt to get comment and any resources to be added to version control
		RepositoryManager manager = CVSUIPlugin.getPlugin().getRepositoryManager();
		IResource[] toBeAdded = promptForResourcesToBeAdded(manager, unadded);
		if (toBeAdded == null) return false; // User cancelled.
		comment = promptForComment(manager, syncSet.getResources());
		if (comment == null) return false; // User cancelled.
		
		// remove unshared resources that were not selected by the user
		if (unadded != null && unadded.length > 0) {
			List resourcesToRemove = new ArrayList(unadded.length);
			for (int i = 0; i < unadded.length; i++) {
				IResource unaddedResource = unadded[i];
				boolean included = false;
				for (int j = 0; j < toBeAdded.length; j++) {
					IResource resourceToAdd = toBeAdded[j];
					if (unaddedResource.equals(resourceToAdd)) {
						included = true;
						break;
					}
				}
				if (!included)
					resourcesToRemove.add(unaddedResource);
			}
			syncSet.removeResources((IResource[]) resourcesToRemove.toArray(new IResource[resourcesToRemove.size()]));
		}
		return true;
	}
	
	private IResource[] getUnaddedResources(SyncInfoSet syncSet) throws CVSException {
		// TODO: Should only get outgoing additions (since conflicting additions 
		// could be considered to be under version control already)
		IResource[] resources = syncSet.getResources();
		List result = new ArrayList();
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (!isAdded(resource)) {
				result.add(resource);
			}
		}
		return (IResource[]) result.toArray(new IResource[result.size()]);
	}

	private boolean isAdded(IResource resource) throws CVSException {
		ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
		if (cvsResource.isFolder()) {
			return ((ICVSFolder)cvsResource).isCVSFolder();
		} else {
			return cvsResource.isManaged();
		}
	}

	private boolean isRemoved(IResource resource) throws CVSException {
		ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
		if (!cvsResource.isFolder()) {
			byte[] syncBytes =  ((ICVSFile)cvsResource).getSyncBytes();
			if (syncBytes == null) return true;
			return ResourceSyncInfo.isDeletion(syncBytes);
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#run(org.eclipse.team.ui.sync.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void run(SyncInfoSet syncSet, IProgressMonitor monitor) throws TeamException {
		
		final SyncInfo[] changed = syncSet.getSyncInfos();
		if (changed.length == 0) return;
		
		// A list of files to be committed
		final List commits = new ArrayList(); // of IResource
		// New resources that are not yet under CVS control and need a "cvs add"
		final List additions = new ArrayList(); // of IResource
		// Deleted resources that need a "cvs remove"
		final List deletions = new ArrayList(); // of IResource
		// A list of incoming or conflicting file changes to be made outgoing changes
		final List makeOutgoing = new ArrayList(); // of SyncInfo
		// A list of out-of-sync folders that must be made in-sync
		final List makeInSync = new ArrayList(); // of SyncInfo
		
		for (int i = 0; i < changed.length; i++) {
			SyncInfo changedNode = changed[i];
			int kind = changedNode.getKind();
			IResource resource = changedNode.getLocal();
			
			// Any parent folders should be made in-sync.
			// Steps will be taken after the commit to prune any empty folders
			SyncInfo parent = getParent(changedNode);
			if (parent != null) {
				if (isOutOfSync(parent)) {
					makeInSync.add(parent);
				}
			}
			
			if (resource.getType() == IResource.FILE) {
				// By default, all files are committed
				commits.add(resource);
				// Determine what other work needs to be done for the file
				switch (kind & SyncInfo.DIRECTION_MASK) {
					case SyncInfo.INCOMING:
						// Convert the incoming change to an outgoing change
						makeOutgoing.add(changedNode);
						break;
					case SyncInfo.OUTGOING:
						switch (kind & SyncInfo.CHANGE_MASK) {
							case SyncInfo.ADDITION:
								// Outgoing addition. 'add' it before committing.
								if (!isAdded(resource))
									additions.add(resource);
								break;
							case SyncInfo.DELETION:
								// Outgoing deletion. 'delete' it before committing.
								if (!isRemoved(resource))
									deletions.add(resource);
								break;
							case SyncInfo.CHANGE:
								// Outgoing change. Just commit it.
								break;
						}
						break;
					case SyncInfo.CONFLICTING:
						// Convert the conflicting change to an outgoing change
						makeOutgoing.add(changedNode);
						break;
				}
			} else {
				if (((kind & SyncInfo.DIRECTION_MASK) == SyncInfo.OUTGOING)
					&& ((kind & SyncInfo.CHANGE_MASK) == SyncInfo.ADDITION)) {
						// Outgoing folder additions must be added
						additions.add(changedNode.getLocal());
				} else if (isOutOfSync(changedNode)) {
					// otherwise, make any out-of-sync folders in-sync using the remote info
					makeInSync.add(changedNode);
				}
			}
		}
		try {
			// Calculate the total amount of work needed
			int work = (makeOutgoing.size() + additions.size() + deletions.size() + commits.size()) * 100;
			monitor.beginTask(null, work);
			
			if (makeInSync.size() > 0) {
				makeInSync((SyncInfo[]) makeInSync.toArray(new SyncInfo[makeInSync.size()]));			
			}

			if (makeOutgoing.size() > 0) {
				makeOutgoing((SyncInfo[]) makeOutgoing.toArray(new SyncInfo[makeInSync.size()]), Policy.subMonitorFor(monitor, makeOutgoing.size() * 100));			
			}

			RepositoryManager manager = CVSUIPlugin.getPlugin().getRepositoryManager();
			if (additions.size() != 0) {
				manager.add((IResource[])additions.toArray(new IResource[0]), Policy.subMonitorFor(monitor, additions.size() * 100));
			}
			if (deletions.size() != 0) {
				manager.delete((IResource[])deletions.toArray(new IResource[0]), Policy.subMonitorFor(monitor, deletions.size() * 100));
			}
			manager.commit((IResource[])commits.toArray(new IResource[commits.size()]), comment, Policy.subMonitorFor(monitor, commits.size() * 100));
			
		} catch (TeamException e) {
			throw CVSException.wrapException(e);
		}
	}	
	
	/**
	 * Prompts the user to determine how conflicting changes should be handled.
	 * Note: This method is designed to be overridden by test cases.
	 * @return 0 to sync conflicts, 1 to sync all non-conflicts, 2 to cancel
	 */
	protected int promptForConflicts(SyncInfoSet syncSet) {
		String[] buttons = new String[] {IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL};
		String question = Policy.bind("CommitSyncAction.questionRelease"); //$NON-NLS-1$
		String title = Policy.bind("CommitSyncAction.titleRelease"); //$NON-NLS-1$
		String[] tips = new String[] {
			Policy.bind("CommitSyncAction.releaseAll"), //$NON-NLS-1$
			Policy.bind("CommitSyncAction.releasePart"), //$NON-NLS-1$
			Policy.bind("CommitSyncAction.cancelRelease") //$NON-NLS-1$
		};
		Shell shell = getShell();
		final ToolTipMessageDialog dialog = new ToolTipMessageDialog(shell, title, null, question, MessageDialog.QUESTION, buttons, tips, 0);
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				dialog.open();
			}
		});
		return dialog.getReturnCode();
	}
		
	/**
	 * Prompts the user for a release comment.
	 * Note: This method is designed to be overridden by test cases.
	 * @return the comment, or null to cancel
	 */
	protected String promptForComment(RepositoryManager manager, IResource[] resourcesToCommit) {
		return manager.promptForComment(getShell(), resourcesToCommit);
	}

	protected IResource[] promptForResourcesToBeAdded(RepositoryManager manager, IResource[] unadded) {
		return manager.promptForResourcesToBeAdded(getShell(), unadded);
	}
	
	protected String getErrorTitle() {
		return Policy.bind("CommitAction.commitFailed"); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction#getJobName(org.eclipse.team.ui.sync.SyncInfoSet)
	 */
	protected String getJobName(SyncInfoSet syncSet) {
		return Policy.bind("CommitAction.jobName", new Integer(syncSet.size()).toString()); //$NON-NLS-1$
	}
}
