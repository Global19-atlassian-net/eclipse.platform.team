package org.eclipse.team.internal.ccvs.ui.sync;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
 
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.ccvs.core.ICVSResource;
import org.eclipse.team.core.sync.IRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.ui.sync.ChangedTeamContainer;
import org.eclipse.team.ui.sync.ITeamNode;
import org.eclipse.team.ui.sync.SyncSet;
import org.eclipse.team.ui.sync.TeamFile;
import org.eclipse.team.ui.sync.UnchangedTeamContainer;

public class IgnoreAction extends Action {
	Shell shell;
	private CVSSyncCompareInput diffModel;
	private ISelectionProvider selectionProvider;

	public IgnoreAction(CVSSyncCompareInput model, ISelectionProvider sp, String label, Shell shell) {
		super(label);
		this.shell = shell;
		this.diffModel = model;
		this.selectionProvider = sp;
	}
	public void run() {
		IStructuredSelection selection = (IStructuredSelection)selectionProvider.getSelection();
		if (selection.isEmpty()) return;
		// Do the update
		Object first = selection.getFirstElement();
		ICVSResource cvsResource = null;
		if (first instanceof TeamFile) {
			IResource resource = ((TeamFile)first).getMergeResource().getResource();
			cvsResource = CVSWorkspaceRoot.getCVSFileFor((IFile) resource);
		} else if (first instanceof ChangedTeamContainer) {
			IResource resource = ((ChangedTeamContainer)first).getMergeResource().getResource();
			cvsResource = CVSWorkspaceRoot.getCVSFolderFor((IContainer) resource);
		}
		if (cvsResource != null) {
			try {
				cvsResource.setIgnored();
			} catch (CVSException e) {
				ErrorDialog.openError(shell, null, null, e.getStatus());
				return;
			}
			removeNodes(new SyncSet(selection).getChangedNodes());
			diffModel.updateView();
		}
	}
	/**
	 * Enabled if only one item is selected and it is an outgoing addition.
	 * 
	 * This may be a folder or a single file, which will be handled differently.
	 */
	protected boolean isEnabled(Object[] nodes) {
		if (nodes.length != 1) return false;
		if (!(nodes[0] instanceof ITeamNode)) return false;
		ITeamNode node = (ITeamNode)nodes[0];
		return node.getKind() == (ITeamNode.OUTGOING | IRemoteSyncElement.ADDITION);
	}
	public void update() {
		IStructuredSelection selection = (IStructuredSelection)selectionProvider.getSelection();
		setEnabled(isEnabled(selection.toArray()));
	}
	/**
	 * The given nodes have been synchronized.  Remove them from
	 * the sync set.
	 */
	private void removeNodes(final ITeamNode[] nodes) {
		// Update the model
		for (int i = 0; i < nodes.length; i++) {
			if (nodes[i].getClass() == UnchangedTeamContainer.class) {
				// Unchanged containers get removed automatically when all
				// children are removed
				continue;
			}
			if (nodes[i].getClass() == ChangedTeamContainer.class) {
				// If this node still has children, convert to an
				// unchanged container, then it will disappear when
				// all children have been removed.
				ChangedTeamContainer container = (ChangedTeamContainer)nodes[i];
				IDiffElement[] children = container.getChildren();
				if (children.length > 0) {
					IDiffContainer parent = container.getParent();
					UnchangedTeamContainer unchanged = new UnchangedTeamContainer(parent, container.getResource());
					for (int j = 0; j < children.length; j++) {
						unchanged.add(children[j]);
					}
					parent.removeToRoot(container);
					continue;
				}
				// No children, it will get removed below.
			}
			nodes[i].getParent().removeToRoot(nodes[i]);	
		}
	}
}
