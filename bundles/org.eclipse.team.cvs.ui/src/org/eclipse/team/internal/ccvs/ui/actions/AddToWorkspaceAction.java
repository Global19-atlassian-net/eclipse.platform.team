package org.eclipse.team.internal.ccvs.ui.actions;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.client.Checkout;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.ui.actions.TeamAction;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

/**
 * Add some remote resources to the workspace. Current implementation:
 * -Works only for remote folders
 * -Does not prompt for project name; uses folder name instead
 */
public class AddToWorkspaceAction extends TeamAction {
	/**
	 * Returns the selected remote folders
	 */
	protected ICVSRemoteFolder[] getSelectedRemoteFolders() {
		ArrayList resources = null;
		if (!selection.isEmpty()) {
			resources = new ArrayList();
			Iterator elements = ((IStructuredSelection) selection).iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ICVSRemoteFolder) {
					resources.add(next);
					continue;
				}
				if (next instanceof IAdaptable) {
					IAdaptable a = (IAdaptable) next;
					Object adapter = a.getAdapter(ICVSRemoteFolder.class);
					if (adapter instanceof ICVSRemoteFolder) {
						resources.add(adapter);
						continue;
					}
				}
			}
		}
		if (resources != null && !resources.isEmpty()) {
			return (ICVSRemoteFolder[])resources.toArray(new ICVSRemoteFolder[resources.size()]);
		}
		return new ICVSRemoteFolder[0];
	}
	/*
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		run(new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
				try {
					ICVSRemoteFolder[] folders = getSelectedRemoteFolders();
					boolean yesToAll = false;
					String [] expansions = CVSProviderPlugin.getProvider().getExpansions(folders, Policy.subMonitorFor(monitor, 10));
					for (int i = 0; i < expansions.length; i++) {
						String name = new Path(expansions[i]).segment(0);
						IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
						if (!yesToAll) {
							switch (confirmOverwrite(project)) {
								// yes
								case 0:
									break;
								// yes to all
								case 1:
									yesToAll = true;
									break;
								// cancel
								default:
									return;
							}
						}
					}
					CVSProviderPlugin.getProvider().checkout(folders, null, Policy.subMonitorFor(monitor, 90));
				} catch (TeamException e) {
					throw new InvocationTargetException(e);
				}
			}
		}, Policy.bind("AddToWorkspaceAction.checkoutFailed"), this.PROGRESS_DIALOG);
	}
	private int confirmOverwrite(IProject project) {
		if (!project.exists()) return 0;
		final MessageDialog dialog = 
			new MessageDialog(shell, Policy.bind("AddToWorkspaceAction.confirmOverwrite"), null, Policy.bind("AddToWorkspaceAction.thisResourceExists", project.getName()), MessageDialog.QUESTION, 
				new String[] {
					IDialogConstants.YES_LABEL,
					IDialogConstants.YES_TO_ALL_LABEL, 
					IDialogConstants.CANCEL_LABEL}, 
				0);
		final int[] result = new int[1];
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				result[0] = dialog.open();
			}
		});
		return result[0];
	}
	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		ICVSRemoteFolder[] resources = getSelectedRemoteFolders();
		if (resources.length == 0) return false;
		for (int i = 0; i < resources.length; i++) {
			if (resources[i] instanceof ICVSRepositoryLocation) return false;
		}
		return true;
	}
}