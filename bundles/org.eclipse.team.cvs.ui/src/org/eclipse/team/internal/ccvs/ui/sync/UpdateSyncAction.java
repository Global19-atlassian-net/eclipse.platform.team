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
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Update;
import org.eclipse.team.internal.ccvs.core.resources.CVSRemoteSyncElement;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.IHelpContextIds;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.ui.sync.ITeamNode;
import org.eclipse.team.internal.ui.sync.SyncSet;
import org.eclipse.team.internal.ui.sync.TeamFile;

/**
 * UpdateSyncAction is run on a set of sync nodes when the "Update" menu item is performed
 * in the Synchronize view.
 * 
 * This class is also used as the super class of the merge update actions for regular and forced
 * update.
 */
public class UpdateSyncAction extends MergeAction {
	public static class ConfirmDialog extends MessageDialog {

		private boolean autoMerge = true;
		private Button radio1;
		private Button radio2;
		
		public ConfirmDialog(Shell parentShell) {
			super(
				parentShell, 
				Policy.bind("UpdateSyncAction.Conflicting_changes_found_1"),  //$NON-NLS-1$
				null,	// accept the default window icon
				Policy.bind("UpdateSyncAction.You_have_local_changes_you_are_about_to_overwrite_2"), //$NON-NLS-1$
				MessageDialog.QUESTION, 
				new String[] {IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL},
				0); 	// yes is the default
		}
		
		protected Control createCustomArea(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout());
			radio1 = new Button(composite, SWT.RADIO);
			radio1.addSelectionListener(selectionListener);
			
			radio1.setText(Policy.bind("UpdateSyncAction.Only_update_resources_that_can_be_automatically_merged_3")); //$NON-NLS-1$

			radio2 = new Button(composite, SWT.RADIO);
			radio2.addSelectionListener(selectionListener);

			radio2.setText(Policy.bind("UpdateSyncAction.Update_all_resources,_overwriting_local_changes_with_remote_contents_4")); //$NON-NLS-1$
			
			// set initial state
			radio1.setSelection(autoMerge);
			radio2.setSelection(!autoMerge);
			
			return composite;
		}
		
		private SelectionListener selectionListener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Button button = (Button)e.widget;
				if (button.getSelection()) {
					autoMerge = (button == radio1);
				}
			}
		};
		
		public boolean getAutomerge() {
			return autoMerge;
		}
	}

	public UpdateSyncAction(CVSSyncCompareInput model, ISelectionProvider sp, String label, Shell shell) {
		super(model, sp, label, shell);
	}

	protected SyncSet run(final SyncSet syncSet, IProgressMonitor monitor) {
		boolean result = saveIfNecessary();
		if (!result) return null;
		
		// If there are conflicts or outgoing changes in the syncSet, we need to warn the user.
		boolean onlyUpdateAutomergeable = false;
		if (syncSet.hasConflicts() || syncSet.hasOutgoingChanges()) {
			if (syncSet.hasAutoMergeableConflicts()) {
				switch (promptForMergeableConflicts()) {
					case 0: // cancel
						return null;
					case 1: // only update auto-mergeable conflicts
						onlyUpdateAutomergeable = true;
						syncSet.removeNonMergeableNodes();
						break;
					case 2: // update all conflicts
						onlyUpdateAutomergeable = false;
						break;
				}				
			} else {
				if (! promptForConflicts()) return null;				
			}
		}
		
		ITeamNode[] changed = syncSet.getChangedNodes();
		if (changed.length == 0) {
			return syncSet;
		}
		
		List updateIgnoreLocalShallow = new ArrayList();
		List updateDeep = new ArrayList();
		List updateShallow = new ArrayList();

		// A list of diff elements in the sync set which are incoming folder additions
		Set parentCreationElements = new HashSet();
		// A list of diff elements in the sync set which are folder conflicts
		Set parentConflictElements = new HashSet();
		// A list of diff elements in the sync set which are outgoing folder deletions
		Set parentDeletionElements = new HashSet();
		// A list of the team nodes that we need to perform makeIncoming on
		List makeIncoming = new ArrayList();
		// A list of diff elements that are incoming deletions.
		// We do these first to avoid case conflicts
		List updateDeletions = new ArrayList();
		// A list of diff elements that need to be unmanaged and locally deleted
		List deletions = new ArrayList();
		
		for (int i = 0; i < changed.length; i++) {
			IDiffContainer parent = changed[i].getParent();
			if (parent != null) {
				int parentKind = changed[i].getParent().getKind();
				if (((parentKind & Differencer.CHANGE_TYPE_MASK) == Differencer.ADDITION) &&
					((parentKind & Differencer.DIRECTION_MASK) == ITeamNode.INCOMING)) {
					parentCreationElements.add(parent);
				} else if (isLocallyDeletedFolder(parent)) {
					parentDeletionElements.add(parent);
				} else if ((parentKind & Differencer.DIRECTION_MASK) == ITeamNode.CONFLICTING) {
					parentConflictElements.add(parent);
				}
			}
			ITeamNode changedNode = changed[i];
			IResource resource = changedNode.getResource();
			int kind = changedNode.getKind();
			switch (kind & Differencer.DIRECTION_MASK) {
				case ITeamNode.INCOMING:
					switch (kind & Differencer.CHANGE_TYPE_MASK) {
						case Differencer.ADDITION:
							updateIgnoreLocalShallow.add(changedNode);
							break;
						case Differencer.DELETION:
							updateDeletions.add(changedNode);
							break;
						case Differencer.CHANGE:
							updateDeep.add(changedNode);
							break;
					}
					break;
				case ITeamNode.OUTGOING:
					switch (kind & Differencer.CHANGE_TYPE_MASK) {
						case Differencer.ADDITION:
							// Unmanage the file if necessary and delete it.
							deletions.add(changedNode);
							break;
						case Differencer.DELETION:
							if (resource.getType() == IResource.FILE) {
								makeIncoming.add(changedNode);
								updateDeep.add(changedNode);
							}
							break;
						case Differencer.CHANGE:
							updateIgnoreLocalShallow.add(changedNode);
							break;
					}
					break;
				case ITeamNode.CONFLICTING:
					switch (kind & Differencer.CHANGE_TYPE_MASK) {
						case Differencer.ADDITION:
							if(changedNode instanceof IDiffContainer) {
								parentConflictElements.add(changedNode);
							} else {
								makeIncoming.add(changedNode);
								deletions.add(changedNode);
								updateIgnoreLocalShallow.add(changedNode);
							}
							break;
						case Differencer.DELETION:
							// Doesn't happen, these nodes don't appear in the tree.
							break;
						case Differencer.CHANGE:
							if (resource.getType() == IResource.FILE) {
								// Depends on the flag.
								if (onlyUpdateAutomergeable && (changedNode.getKind() & IRemoteSyncElement.AUTOMERGE_CONFLICT) != 0) {
									updateShallow.add(changedNode);
								} else {
									// Check to see if there is a remote
									if (((TeamFile)changedNode).getMergeResource().getSyncElement().getRemote() == null) {
										// If a locally modified file has no remote, "update -C" will fail.
										// We must unmanage and delete the file ourselves
										deletions.add(changedNode);
									} else {
										updateIgnoreLocalShallow.add(changedNode);
										// If the resource doesn't exist remotely, we must ensure the sync info will allow the above update.
										if (!resource.exists()) {
											makeIncoming.add(changedNode);
										}
									}
								}
							} else {
								// Conflicting change on a folder only occurs if the folder has been deleted locally
								// The folder should only be recreated if there were children in the changed set.
								// Such folders would have been added to the parentDeletionElements set above
							}
							break;
					}
					break;
			}
		}
		try {
			// Calculate the total amount of work needed
			int work = (makeIncoming.size() + (deletions.size() * 2) + updateDeletions.size() + updateShallow.size() + updateIgnoreLocalShallow.size() + updateDeep.size()) * 100;
			monitor.beginTask(null, work);

			RepositoryManager manager = CVSUIPlugin.getPlugin().getRepositoryManager();
			if (parentDeletionElements.size() > 0) {
				// If a node has a parent that is an outgoing folder deletion, we have to 
				// recreate that folder locally (it's sync info already exists locally). 
				// We must do this for all outgoing folder deletions (recursively)
				// in the case where there are multiple levels of outgoing folder deletions.
				Iterator it = parentDeletionElements.iterator();
				while (it.hasNext()) {
					recreateLocallyDeletedFolder((IDiffElement)it.next());
				}				
			}
			if (parentCreationElements.size() > 0) {
				// If a node has a parent that is an incoming folder creation, we have to 
				// create that folder locally and set its sync info before we can get the
				// node itself. We must do this for all incoming folder creations (recursively)
				// in the case where there are multiple levels of incoming folder creations.
				Iterator it = parentCreationElements.iterator();
				while (it.hasNext()) {
					IDiffElement element = (IDiffElement)it.next();
					makeInSync(element);
					// Remove the folder from the update shallow list since we have it locally now
					updateIgnoreLocalShallow.remove(element);
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
			// Make any outgoing changes or deletions into incoming changes before updating.
			Iterator it = makeIncoming.iterator();
			while (it.hasNext()) {
				ITeamNode node = (ITeamNode)it.next();
				CVSRemoteSyncElement element = CVSSyncCompareInput.getSyncElementFrom(node);
				element.makeIncoming(Policy.subMonitorFor(monitor, 100));
			}
			// Outgoing additions must be unmanaged (if necessary) and locally deleted.
			it = deletions.iterator();
			while (it.hasNext()) {
				ITeamNode node = (ITeamNode)it.next();
				CVSRemoteSyncElement element = CVSSyncCompareInput.getSyncElementFrom(node);
				element.makeIncoming(Policy.subMonitorFor(monitor, 100));
				deleteAndKeepHistory(element.getLocal(), Policy.subMonitorFor(monitor, 100));
			}
			
			if (updateDeletions.size() > 0) {
				runUpdateShallow((ITeamNode[])updateDeletions.toArray(new ITeamNode[updateDeletions.size()]), manager, Policy.subMonitorFor(monitor, 100));
			}			
			if (updateShallow.size() > 0) {
				runUpdateShallow((ITeamNode[])updateShallow.toArray(new ITeamNode[updateShallow.size()]), manager, Policy.subMonitorFor(monitor, 100));
			}
			if (updateIgnoreLocalShallow.size() > 0) {
				runUpdateIgnoreLocalShallow((ITeamNode[])updateIgnoreLocalShallow.toArray(new ITeamNode[updateIgnoreLocalShallow.size()]), manager, Policy.subMonitorFor(monitor, 100));
			}
			if (updateDeep.size() > 0) {
				runUpdateDeep((ITeamNode[])updateDeep.toArray(new ITeamNode[updateDeep.size()]), manager, Policy.subMonitorFor(monitor, 100));
			}
		} catch (final TeamException e) {
			handle(e);
			return null;
		} catch (final CoreException e) {
			handle(e);
			return null;
		} finally {
			monitor.done();
		}
		return syncSet;
	}

	/**
	 * Method deleteAndKeepHistory.
	 * @param iResource
	 * @param iProgressMonitor
	 */
	private void deleteAndKeepHistory(IResource resource, IProgressMonitor monitor) throws CoreException {
		if (resource.getType() == IResource.FILE)
			((IFile)resource).delete(false /* force */, true /* keep history */, monitor);
		else
			resource.delete(false /* force */, monitor);
	}

	
	protected void runUpdateDeep(ITeamNode[] nodes, RepositoryManager manager, IProgressMonitor monitor) throws TeamException {
		manager.update(getIResourcesFrom(nodes), Command.NO_LOCAL_OPTIONS, false, monitor);
	}
	
	protected void runUpdateIgnoreLocalShallow(ITeamNode[] nodes, RepositoryManager manager, IProgressMonitor monitor) throws TeamException {
		manager.update(getIResourcesFrom(nodes), new Command.LocalOption[] { Update.IGNORE_LOCAL_CHANGES, Command.DO_NOT_RECURSE }, false, monitor);
	}
	
	protected void runUpdateShallow(ITeamNode[] nodes, RepositoryManager manager, IProgressMonitor monitor) throws TeamException {
		manager.update(getIResourcesFrom(nodes), new Command.LocalOption[] { Command.DO_NOT_RECURSE }, false, monitor);
	}
		
	protected IResource[] getIResourcesFrom(ITeamNode[] nodes) {
		List resources = new ArrayList(nodes.length);
		for (int i = 0; i < nodes.length; i++) {
			resources.add(nodes[i].getResource());
		}
		return (IResource[]) resources.toArray(new IResource[resources.size()]);
	}
	
	protected boolean isEnabled(ITeamNode node) {
		// The update action is enabled only for non-conflicting incoming changes
		return new SyncSet(new StructuredSelection(node)).hasIncomingChanges();
	}
	
	/**
	 * Prompt for mergeable conflicts.
	 * Note: This method is designed to be overridden by test cases.
	 * @return 0 to cancel, 1 to only update mergeable conflicts, 2 to overwrite if unmergeable
	 */
	protected int promptForMergeableConflicts() {
		final boolean doAutomerge[] = new boolean[] {false};
		final int[] result = new int[] {Dialog.CANCEL};
		final Shell shell = getShell();
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				ConfirmDialog dialog = new ConfirmDialog(shell);
				result[0] = dialog.open();
				doAutomerge[0] = dialog.getAutomerge();
			}
		});
		if (result[0] == Dialog.CANCEL) return 0;
		return doAutomerge[0] ? 1 : 2;
	}
	
	/**
	 * Prompt for non-automergeable conflicts.
	 * Note: This method is designed to be overridden by test cases.
	 * @return false to cancel, true to overwrite local changes
	 */
	protected boolean promptForConflicts() {
		final boolean[] result = new boolean[] { false };
		final Shell shell = getShell();
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				result[0] = MessageDialog.openQuestion(shell, Policy.bind("UpdateSyncAction.Overwrite_local_changes__5"), Policy.bind("UpdateSyncAction.You_have_local_changes_you_are_about_to_overwrite._Do_you_wish_to_continue__6")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		});
		return result[0];
	}
	protected void removeNonApplicableNodes(SyncSet set, int syncMode) {
		set.removeConflictingNodes();
		set.removeOutgoingNodes();
	}
	/**
	 * @see MergeAction#getHelpContextID()
	 */
	protected String getHelpContextID() {
		return IHelpContextIds.SYNC_UPDATE_ACTION;
	}

	protected String getErrorTitle() {
		return Policy.bind("UpdateAction.update"); //$NON-NLS-1$
	}
}
