package org.eclipse.team.internal.ccvs.ui.actions;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.ccvs.core.ICVSResource;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.RepositoryProviderType;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Command.KSubstOption;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.wizards.KSubstWizard;
import org.eclipse.team.ui.actions.TeamAction;

/**
 * TagAction tags the selected resources with a version tag specified by the user.
 */
public class SetKeywordSubstitutionAction extends TeamAction {
	private KSubstOption previousOption = null; // automatic

	/*
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		final IResource[] resources = getSelectedResources();
		KSubstWizard wizard = new KSubstWizard(resources, IResource.DEPTH_INFINITE, previousOption);
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.setMinimumPageSize(350, 250);
		dialog.open();
		previousOption = wizard.getKSubstOption();
	}
	
	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		IResource[] resources = getSelectedResources();
		if (resources.length == 0) return false;
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			// resource must be local
			if (! resource.isAccessible()) return false;
			// provider must be CVS
			RepositoryProvider provider = RepositoryProviderType.getProvider(resource.getProject(), CVSProviderPlugin.getTypeId());
			if (provider == null) return false;
			// resource must either be a project, or it must be managed
			if (resource.getType() != IResource.PROJECT) {
				ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
				if (! cvsResource.isManaged()) return false;
			}
		}
		return true;
	}
}
