/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/

package org.eclipse.team.internal.ccvs.ui.sync;
 
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.CVSRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.IHelpContextIds;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.ReleaseCommentDialog;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.ui.sync.ChangedTeamContainer;
import org.eclipse.team.internal.ui.sync.ITeamNode;
import org.eclipse.team.internal.ui.sync.SyncSet;
import org.eclipse.team.internal.ui.sync.SyncView;
import org.eclipse.team.internal.ui.sync.TeamFile;

public class ForceCommitSyncAction extends MergeAction {
	public ForceCommitSyncAction(CVSSyncCompareInput model, ISelectionProvider sp, String label, Shell shell) {
		super(model, sp, label, shell);
	}

	protected SyncSet run(SyncSet syncSet, IProgressMonitor monitor) {
		boolean result = saveIfNecessary();
		if (!result) return null;
		
		// If there is a conflict in the syncSet, we need to prompt the user before proceeding.
		if (syncSet.hasConflicts() || syncSet.hasIncomingChanges()) {
			switch (promptForConflicts(syncSet)) {
				case 0:
					// Yes, synchronize conflicts as well
					break;
				case 1:
					// No, only synchronize non-conflicting changes.
					syncSet.removeConflictingNodes();
					syncSet.removeIncomingNodes();
					break;
				case 2:
				default:
					// Cancel
					return null;
			}	
		}
		ITeamNode[] changed = syncSet.getChangedNodes();
		if (changed.length == 0) {
			return syncSet;
		}
		
		// accumulate any resources that are not under version control
		IResource[] unadded = null;
		if (syncSet instanceof CVSSyncSet) {
			CVSSyncSet cvsSyncSet = (CVSSyncSet)syncSet;
			try {
				if (cvsSyncSet.hasNonAddedChanges()) {
					ITeamNode[] nodes = cvsSyncSet.getNonAddedNodes();
					unadded = new IResource[nodes.length];
					for (int i = 0; i < nodes.length; i++) {
						unadded[i] = nodes[i].getResource();
					}
				}
			} catch (CVSException e) {
				CVSUIPlugin.log(e.getStatus());
			}
		}
		
		// prompt to get comment and any resources to be added to version control
		RepositoryManager manager = CVSUIPlugin.getPlugin().getRepositoryManager();
		ReleaseCommentDialog dialog = promptForComment(manager, unadded);
		if (dialog == null) {
			// User cancelled.
			return null;
		}
		String comment = dialog.getComment();
		
		// remove unshared resources that were not selected by the user
		IResource[] toBeAdded = dialog.getResourcesToAdd();
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
			CVSSyncSet cvsSyncSet = (CVSSyncSet)syncSet;
			cvsSyncSet.removeNonAddedResources((IResource[]) resourcesToRemove.toArray(new IResource[resourcesToRemove.size()]));
		 	changed = syncSet.getChangedNodes();
			if (changed.length == 0) {
				return syncSet;
			}
		}
		
		List commits = new ArrayList();
		List additions = new ArrayList();
		List deletions = new ArrayList();
		List toMerge = new ArrayList();
		List incoming = new ArrayList();

		// A list of diff elements in the sync set which are incoming folder additions
		List parentCreationElements = new ArrayList();
		// A list of diff elements in the sync set which are folder conflicts
		List parentConflictElements = new ArrayList();
		
		for (int i = 0; i < changed.length; i++) {
			int kind = changed[i].getKind();
			IResource resource = changed[i].getResource();
			if (resource.getType() == resource.FILE) {
				commits.add(resource);
			}
			IDiffContainer parent = changed[i].getParent();
			if (parent != null) {
				int parentKind = changed[i].getParent().getKind();
				if (((parentKind & Differencer.CHANGE_TYPE_MASK) == Differencer.ADDITION) &&
					((parentKind & Differencer.DIRECTION_MASK) == ITeamNode.INCOMING)) {
					parentCreationElements.add(parent);
				} else if ((parentKind & Differencer.DIRECTION_MASK) == ITeamNode.CONFLICTING) {
					parentConflictElements.add(parent);
				}
			}
			switch (kind & Differencer.DIRECTION_MASK) {
				case ITeamNode.INCOMING:
					// Incoming change. Make it outgoing before committing.
					incoming.add(changed[i]);
					break;
				case ITeamNode.OUTGOING:
					switch (kind & Differencer.CHANGE_TYPE_MASK) {
						case Differencer.ADDITION:
							// Outgoing addition. 'add' it before committing.
							additions.add(resource);
							break;
						case Differencer.DELETION:
							// Outgoing deletion. 'delete' it before committing.
							deletions.add(resource);
							break;
						case Differencer.CHANGE:
							// Outgoing change. Just commit it.
							break;
					}
					break;
				case ITeamNode.CONFLICTING:
					if (changed[i] instanceof TeamFile) {
						toMerge.add(((TeamFile)changed[i]).getMergeResource().getSyncElement());
					}
					break;
			}
		}
		try {
			if (parentCreationElements.size() > 0) {
				// If a node has a parent that is an incoming folder creation, we have to 
				// create that folder locally and set its sync info before we can get the
				// node itself. We must do this for all incoming folder creations (recursively)
				// in the case where there are multiple levels of incoming folder creations.
				Iterator it = parentCreationElements.iterator();
				while (it.hasNext()) {
					makeInSync((IDiffElement)it.next());
				}				
			}
			if (parentConflictElements.size() > 0) {
				// If a node has a parent that is a folder conflict, that means that the folder
				// exists locally but has no sync info. In order to get the node, we have to 
				// create the sync info for the folder (and any applicable parents) before we
				// get the node itself.
				Iterator it = parentConflictElements.iterator();
				while (it.hasNext()) {
					makeInSync((IDiffElement)it.next());
				}				
			}

			// Handle any real incomming deletions by unmanaging them before adding
			Iterator it = incoming.iterator();
			Set incomingDeletions = new HashSet(incoming.size());
			while (it.hasNext()) {
				ITeamNode node = (ITeamNode)it.next();
				collectIncomingDeletions(node, incomingDeletions, monitor);
				if ((node instanceof TeamFile) && !additions.contains(node)) {
					CVSRemoteSyncElement element = (CVSRemoteSyncElement)((TeamFile)node).getMergeResource().getSyncElement();
					element.makeOutgoing(monitor);
				}
			}
			it = incomingDeletions.iterator();
			while (it.hasNext()) {
				ITeamNode node = (ITeamNode)it.next();
				CVSRemoteSyncElement syncElement;
				if (node instanceof TeamFile) {
					syncElement = (CVSRemoteSyncElement)((TeamFile)node).getMergeResource().getSyncElement();
				} else {
					syncElement = (CVSRemoteSyncElement)((ChangedTeamContainer)node).getMergeResource().getSyncElement();
				}
				additions.add(syncElement.getLocal());
				CVSWorkspaceRoot.getCVSResourceFor(syncElement.getLocal()).unmanage(null);
			}

			if (additions.size() != 0) {
				manager.add((IResource[])additions.toArray(new IResource[0]), monitor);
			}
			if (deletions.size() != 0) {
				manager.delete((IResource[])deletions.toArray(new IResource[0]), monitor);
			}
			if (toMerge.size() != 0) {
				manager.merged((IRemoteSyncElement[])toMerge.toArray(new IRemoteSyncElement[0]));
			}
			manager.commit((IResource[])commits.toArray(new IResource[commits.size()]), comment, monitor);
			
		} catch (final TeamException e) {
			handle(e);
			return null;
		}
		
		return syncSet;
	}

	protected boolean isEnabled(ITeamNode node) {
		// The force commit action is enabled only for conflicting and incoming changes
		CVSSyncSet set = new CVSSyncSet(new StructuredSelection(node));
		if (syncMode == SyncView.SYNC_OUTGOING) {
			return (set.hasConflicts() && hasRealChanges(node, new int[] { ITeamNode.CONFLICTING }));
		} else {
			return ((set.hasIncomingChanges() || set.hasConflicts()) && hasRealChanges(node, new int[] { ITeamNode.CONFLICTING, ITeamNode.INCOMING }));
		}
	}	
	
	/**
	 * Prompts the user to determine how conflicting changes should be handled.
	 * Note: This method is designed to be overridden by test cases.
	 * @return 0 to sync conflicts, 1 to sync all non-conflicts, 2 to cancel
	 */
	protected int promptForConflicts(SyncSet syncSet) {
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
	 * Prompts the user to determine how unadded files should be handled.
	 * Note: This method is designed to be overridden by test cases.
	 * @return 0 to add unadded files, 1 to skip unadded, 2 to cancel
	 */
	protected int promptForAdditions(CVSSyncSet cvsSyncSet) {
		final int[] r = new int[1];
		getShell().getDisplay().syncExec(new Runnable() {
			public void run() {
				MessageDialog dialog = new MessageDialog(
					getShell(),
					Policy.bind("ForceCommitSyncAction.Outgoing_Changes_Not_Added_1"),  //$NON-NLS-1$
					null,
					Policy.bind("ForceCommitSyncAction.You_have_chosen_to_commit_new_resources_which_have_not_been_added_to_version_control._Do_you_wish_to_add_them_to_version_control_now__2"),  //$NON-NLS-1$
					MessageDialog.QUESTION, 
					new String[] {IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL}, 
					0);
				r[0] = dialog.open();
			}
		});
		return r[0];
	}
		
	/**
	 * Prompts the user for a release comment.
	 * Note: This method is designed to be overridden by test cases.
	 * @return the comment, or null to cancel
	 */
	protected ReleaseCommentDialog promptForComment(RepositoryManager manager, IResource[] unadded) {
		return manager.promptForComment(getShell(), unadded);
	}

	protected void removeNonApplicableNodes(SyncSet set, int syncMode) {
		set.removeOutgoingNodes();
		if (syncMode != SyncView.SYNC_BOTH) {
			set.removeIncomingNodes();
		}
	}
	
	/*
	 * Handle incoming folder deletion.
	 * 
	 * Special handling is required in the case were a folder has been deleted remotely
	 * (i.e using "rm -rf" on the server). 
	 * 
	 * We need to determine if there is a remote folder corresponding to this folder
	 * If there isn't, we need to unmanage the local resource and then add the folder
	 * Unfortunately, unmanaging may effect the state of the children which are also incoming deletions
	 */
	private void collectIncomingDeletions(ITeamNode node, Set additions, IProgressMonitor monitor) throws TeamException {
		if (isIncomingDeletion(node) && ! additions.contains(node) && ! existsRemotely(node, monitor)) {
			
			// Make sure that the parent is handled
			IDiffContainer parent = node.getParent();
			if (isIncomingDeletion((ITeamNode)parent)) {
				collectIncomingDeletions((ITeamNode)parent, additions, monitor);
			}
			
			// Add the node to the list
			additions.add(node);
		}
	}
	
	private boolean isIncomingDeletion(ITeamNode node) {
		return (node.getChangeDirection() == ITeamNode.INCOMING && node.getChangeType() == Differencer.DELETION);
	}
	
	/*
	 * For files, use the remote of the sync element to determine whether there is a remote or not.
	 * For folders, if there is no remote in the tree check remotely in case the folder was pruned
	 */
	private boolean existsRemotely(ITeamNode node, IProgressMonitor monitor) throws TeamException {
		
		CVSRemoteSyncElement syncElement;
		if (node instanceof TeamFile) {
			syncElement = (CVSRemoteSyncElement)((TeamFile)node).getMergeResource().getSyncElement();
		} else {
			syncElement = (CVSRemoteSyncElement)((ChangedTeamContainer)node).getMergeResource().getSyncElement();
		}
		if (syncElement.getRemote() != null) {
			return true;
		}
		if (syncElement.getLocal().getType() == IResource.FILE) {
			return false;
		}
		return CVSWorkspaceRoot.getRemoteResourceFor(syncElement.getLocal()).exists(monitor);
	}
	/**
	 * @see MergeAction#getHelpContextID()
	 */
	protected String getHelpContextID() {
		return IHelpContextIds.SYNC_FORCED_COMMIT_ACTION;
	}

	protected String getErrorTitle() {
		return Policy.bind("CommitAction.commitFailed"); //$NON-NLS-1$
	}
}
