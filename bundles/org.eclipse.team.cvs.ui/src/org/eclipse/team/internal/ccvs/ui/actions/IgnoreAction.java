package org.eclipse.team.internal.ccvs.ui.actions;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
 
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.IgnoreResourcesDialog;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ui.actions.TeamAction;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

public class IgnoreAction extends CVSAction {
	
	protected boolean isEnabled() throws TeamException {
		IResource[] resources = getSelectedResources();
		if (resources.length == 0) return false;
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (resource.getType() == IResource.PROJECT) return false;
			ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
			if (cvsResource.isManaged()) return false;
			if (cvsResource.isIgnored()) return false;
		}
		return true;
	}
	
	protected void execute(final IAction action) {
		run(new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
				IResource[] resources = getSelectedResources();
				IgnoreResourcesDialog dialog = new IgnoreResourcesDialog(getShell(), resources);
				if (dialog.open() != IgnoreResourcesDialog.OK) return;
				
				try {
					for (int i = 0; i < resources.length; i++) {
						IResource resource = resources[i];
						String pattern = dialog.getIgnorePatternFor(resource);
						ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
						cvsResource.setIgnoredAs(pattern);
					}
					// fix the action enablement
					if (action != null) action.setEnabled(isEnabled());
				} catch (TeamException e) {
					//ErrorDialog.openError(getShell(), null, null, e.getStatus());
					handle(e, null, null);
					return;
				}
			}
		}, Policy.bind("IgnoreAction.ignore"), PROGRESS_BUSYCURSOR); //$NON-NLS-1$
	}
}
