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
package org.eclipse.team.internal.ccvs.ui.actions;
 
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.subscriber.CompareWithLatestParticipant;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipant;
import org.eclipse.ui.PlatformUI;

/**
 * This action shows the CVS workspace participant into a model dialog. For single file
 * selection, the compare editor is shown instead.
 * 
 * @since 3.0
 */
public class CompareWithRemoteAction extends WorkspaceAction {
	
	public void execute(IAction action) throws InvocationTargetException, InterruptedException {
		final IResource[] resources = getSelectedResources();		
		// Show the 3-way comparison in a model dialog
		CompareWithLatestParticipant participant = new CompareWithLatestParticipant(resources);
		String taskName = Policy.bind("Participant.comparingDetail", participant.getName(), Utils.stripAmpersand(calculateActionTagValue())); //$NON-NLS-1$
		IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();
		boolean showInSyncView = store.getBoolean(ICVSUIConstants.PREF_SHOW_COMPARE_MERGE_IN_SYNCVIEW);
		if(showInSyncView) {
			TeamUI.getSynchronizeManager().addSynchronizeParticipants(new ISynchronizeParticipant[] {participant});
			participant.refresh(resources, Policy.bind("Participant.comparing"), taskName, null); //$NON-NLS-1$
		} else {
			ISynchronizePageConfiguration configuration = participant.createPageConfiguration();
			configuration.setProperty(ISynchronizePageConfiguration.P_TOOLBAR_MENU, new String[] { 
					ISynchronizePageConfiguration.NAVIGATE_GROUP, 
					ISynchronizePageConfiguration.MODE_GROUP, 
					ISynchronizePageConfiguration.LAYOUT_GROUP });
			configuration.setWorkingSet(PlatformUI.getWorkbench().getWorkingSetManager().createWorkingSet("", resources)); //$NON-NLS-1$
			participant.refreshInDialog(getShell(), resources, Policy.bind("Participant.comparing"), taskName, configuration, null); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	/*
	 * Update the text label for the action based on the tags in the selection.
	 * @see TeamAction#setActionEnablement(org.eclipse.jface.action.IAction)
	 */
	protected void setActionEnablement(IAction action) {
		super.setActionEnablement(action);
		action.setText(calculateActionTagValue());
	}
	
	/**
	 * Enable for resources that are managed (using super) or whose parent is a CVS folder.
	 * 
	 * @see org.eclipse.team.internal.ccvs.ui.actions.WorkspaceAction#isEnabledForCVSResource(org.eclipse.team.internal.ccvs.core.ICVSResource)
	 */
	protected boolean isEnabledForCVSResource(ICVSResource cvsResource) throws CVSException {
		return super.isEnabledForCVSResource(cvsResource) || cvsResource.getParent().isCVSFolder();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.actions.WorkspaceAction#isEnabledForNonExistantResources()
	 */
	protected boolean isEnabledForNonExistantResources() {
		return true;
	}
}
