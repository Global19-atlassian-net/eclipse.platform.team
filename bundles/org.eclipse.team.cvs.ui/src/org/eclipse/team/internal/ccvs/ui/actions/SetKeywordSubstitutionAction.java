/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.actions;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.client.Command.KSubstOption;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.ResizableWizardDialog;
import org.eclipse.team.internal.ccvs.ui.wizards.KSubstWizard;

/**
 * TagAction tags the selected resources with a version tag specified by the user.
 */
public class SetKeywordSubstitutionAction extends WorkspaceAction {
	private KSubstOption previousOption = null; // automatic

	/*
	 * @see IActionDelegate#run(IAction)
	 */
	public void execute(IAction action) {
		final IResource[] resources = getSelectedResources();
		KSubstWizard wizard = new KSubstWizard(resources, IResource.DEPTH_INFINITE, previousOption);
		WizardDialog dialog = new ResizableWizardDialog(getShell(), wizard);
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
			RepositoryProvider provider = RepositoryProvider.getProvider(resource.getProject(), CVSProviderPlugin.getTypeId());
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
