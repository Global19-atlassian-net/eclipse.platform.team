package org.eclipse.team.internal.ccvs.ui.sync;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.lang.reflect.InvocationTargetException;

import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.resources.CVSRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.ui.sync.ChangedTeamContainer;
import org.eclipse.team.ui.sync.ITeamNode;
import org.eclipse.team.ui.sync.SyncSet;
import org.eclipse.team.ui.sync.UnchangedTeamContainer;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

/**
 * Applies merge related actions to the selected ITeamNodes.
 */
abstract class MergeAction extends Action {
	public static final int CHECKIN = 0;
	public static final int GET = 1;
	public static final int DELETE_REMOTE = 2;
	public static final int DELETE_LOCAL = 3;

	private CVSSyncCompareInput diffModel;
	private ISelectionProvider selectionProvider;

	protected int syncMode;
	private Shell shell;
	
	/**
	 * Creates a MergeAction which works on selection and doesn't commit changes.
	 */
	public MergeAction(CVSSyncCompareInput model, ISelectionProvider sp, String label, Shell shell) {
		super(label);
		this.diffModel = model;
		this.selectionProvider = sp;
		this.shell = shell;
	}
	
	protected Shell getShell() {
		return shell;
	}
	
	protected CVSSyncCompareInput getDiffModel() {
		return diffModel;
	}
	
	/**
	 * Returns true if at least one node can perform the specified action.
	 */
	private boolean isEnabled(Object[] nodes) {
		for (int i = 0; i < nodes.length; i++) {
			if (nodes[i] instanceof ITeamNode) {
				ITeamNode node = (ITeamNode)nodes[i];
				if (isEnabled(node)) {
					return true;
				}
			} else {
				if (nodes[i] instanceof IDiffContainer)
					if (isEnabled(((IDiffContainer)nodes[i]).getChildren()))
						return true;
			}
		}
		return false;
	}

	protected abstract boolean isEnabled(ITeamNode node);
	
	/**
	 * Perform the sychronization operation.
	 */
	public void run() {
		ISelection s = selectionProvider.getSelection();
		if (!(s instanceof IStructuredSelection) || s.isEmpty()) {
			return;
		}
		final SyncSet set = new SyncSet((IStructuredSelection)s);
		removeNonApplicableNodes(set, syncMode);
		final SyncSet[] result = new SyncSet[1];
		WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				result[0] = MergeAction.this.run(set, monitor);
			}
		};
		try {
			run(op, Policy.bind("MergeAction.problemsDuringSync")); //$NON-NLS-1$
		} catch (InterruptedException e) {
		}
		if (result[0] != null) {
			// all returned nodes that have a changed sync kind are assumed
			// to have been operated on and will be removed from the diff tree.
			removeNodes(result[0].getChangedNodes());
			
			// any node that claims that it's IN_SYNC will be automatically 
			// filtered from the diff tree - see DiffElement.setKind().
			diffModel.updateView();
		}
	}
	
	protected abstract void removeNonApplicableNodes(SyncSet set, int syncMode);
	
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
					// Only convert to an UnchangedTeamContainer if the folder exists locally.
					// Otherwise, leave it as is.
					if (container.getResource().exists()) {
						UnchangedTeamContainer unchanged = new UnchangedTeamContainer(parent, container.getResource());
						for (int j = 0; j < children.length; j++) {
							unchanged.add(children[j]);
						}
						parent.removeToRoot(container);
					}
					continue;
				}
				// No children, it will get removed below.
			}
			nodes[i].getParent().removeToRoot(nodes[i]);	
		}
	}

	/**
	 * Updates the action with the latest selection, setting enablement
	 * as necessary.
	 */
	public void update(int syncMode) {
		this.syncMode = syncMode;
		IStructuredSelection selection = (IStructuredSelection)selectionProvider.getSelection();
		setEnabled(isEnabled(selection.toArray()));
	}
	
	/**
	 * Subclasses must implement this method, which performs action-specific code.
	 * 
	 * It may return the sync set which was passed in, or null.
	 */
	protected abstract SyncSet run(SyncSet syncSet, IProgressMonitor monitor);

	/**
	 * Helper method to run a runnable in a progress monitor dialog, and display any errors.
	 */
	protected void run(IRunnableWithProgress op, String problemMessage) throws InterruptedException {
		ProgressMonitorDialog dialog = new ProgressMonitorDialog(getShell());
		try {
			dialog.run(true, true, op);
		} catch (InvocationTargetException e) {
			Throwable throwable = e.getTargetException();
			IStatus error = null;
			if (throwable instanceof CoreException) {
				error = ((CoreException)throwable).getStatus();
			} else {
				error = new Status(IStatus.ERROR, CVSUIPlugin.ID, 1, Policy.bind("simpleInternal") , throwable); //$NON-NLS-1$
			}
			ErrorDialog.openError(shell, problemMessage, error.getMessage(), error);
			CVSUIPlugin.log(error);
		}
	}
	
	/**
	 * Helper method. Check if a save is necessary. If it is, prompt the user to save.
	 * Return true if all necessary saves have been performed, false otherwise.
	 */
	protected boolean saveIfNecessary() {
		return getDiffModel().saveIfNecessary();
	}		
	
	/**
	 * Answer true if the given diff element represents a locally deleted CVS folder.
	 * The sync state of locally deleted CVS folders is either outgoing deletion or
	 * conflicting change.
	 */
	protected boolean isLocallyDeletedFolder(IDiffElement element) {
		if ( ! (element.getType() == IDiffElement.FOLDER_TYPE)) return false;
		int kind = element.getKind();
		return (((kind & Differencer.CHANGE_TYPE_MASK) == Differencer.DELETION) &&
					((kind & Differencer.DIRECTION_MASK) == ITeamNode.OUTGOING))
				||	(((kind & Differencer.CHANGE_TYPE_MASK) == Differencer.CHANGE) &&
					((kind & Differencer.DIRECTION_MASK) == ITeamNode.CONFLICTING));
	}
	
	/** 
	 * Recreate any parents that are outgoing folder deletions
	 */
	protected void recreateLocallyDeletedFolder(IDiffElement element) throws TeamException {
		// Recursively make the parent element (and its parents) in sync.
		// Walk up and find the parents which need to be made in sync too. (For
		// each parent that doesn't already have sync info).
		if (element == null) return;
		if (element instanceof ChangedTeamContainer) {
			CVSRemoteSyncElement syncElement = (CVSRemoteSyncElement)((ChangedTeamContainer)element).getMergeResource().getSyncElement();
			// recreate the folder
			ICVSFolder cvsFolder = (ICVSFolder) CVSWorkspaceRoot.getCVSResourceFor(syncElement.getLocal());
			if (! cvsFolder.exists()) {
				recreateLocallyDeletedFolder(element.getParent());
				cvsFolder.mkdir();
				syncElement.makeInSync(Policy.monitorFor(null));
				((ChangedTeamContainer)element).makeInSync();
			}
		}
	}
}
