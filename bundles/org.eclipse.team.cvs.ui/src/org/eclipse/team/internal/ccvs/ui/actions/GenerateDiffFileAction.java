package org.eclipse.team.internal.ccvs.ui.actions;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.wizards.GenerateDiffFileWizard;
import org.eclipse.team.ui.actions.TeamAction;

/**
 * Action to generate a patch file using the CVS diff command.
 * 
 * NOTE: This is a temporary action and should eventually be replaced
 * by a create patch command in the compare viewer.
 */
public class GenerateDiffFileAction extends TeamAction {
	/**
	 * Makes sure that the projects of all selected resources are shared.
	 * Returns true if all resources are shared, and false otherwise.
	 */
	protected boolean checkSharing(IResource[] resources) {
		for (int i = 0; i < resources.length; i++) {
			CVSTeamProvider provider = (CVSTeamProvider)RepositoryProvider.getProvider(
						resources[i].getProject(), 
						CVSProviderPlugin.getTypeId());
			if (provider==null) {
				return false;
			}
		}
		return true;
	}
	
	/** (Non-javadoc)
	 * Method declared on IActionDelegate.
	 */
	public void run(IAction action) {
		final String title = Policy.bind("GenerateCVSDiff.title"); //$NON-NLS-1$
		final IResource[] resources = getSelectedResources();
		GenerateDiffFileWizard wizard = new GenerateDiffFileWizard(new StructuredSelection(resources), resources[0]);
		wizard.setWindowTitle(title);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.setMinimumPageSize(350, 250);
		dialog.open();
	}

	/** (Non-javadoc)
	 * Method declared on IActionDelegate.
	 */
	protected boolean isEnabled() throws TeamException {
		IResource[] resources = getSelectedResources();
		if(resources.length != 1) {
			return false;
		}
		if (!resources[0].isAccessible() || !checkSharing(new IResource[] {resources[0]})) return false;
		return true;
	}
}