/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.core.resources.IResource;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ui.synchronize.SubscriberParticipantWizard;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipantDescriptor;
import org.eclipse.team.ui.synchronize.SubscriberParticipant;

/**
 * This is the class registered with the org.eclipse.team.ui.synchronizeWizard
 */
public class CVSSynchronizeWizard extends SubscriberParticipantWizard {
	
	protected IResource[] getRootResources() {
		return CVSProviderPlugin.getPlugin().getCVSWorkspaceSubscriber().roots();
	}

	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.SubscriberParticipantWizard#getName()
	 */
	protected String getName() {
		ISynchronizeParticipantDescriptor desc = TeamUI.getSynchronizeManager().getParticipantDescriptor(WorkspaceSynchronizeParticipant.ID);
		if(desc != null) {
			return desc.getName();
		} else {
			return "Unknown";
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.SubscriberParticipantWizard#createParticipant(org.eclipse.core.resources.IResource[])
	 */
	protected SubscriberParticipant createParticipant(IResource[] resources) {
		// First check if there is an existing matching participant
		WorkspaceSynchronizeParticipant participant = (WorkspaceSynchronizeParticipant)SubscriberParticipant.getMatchingParticipant(WorkspaceSynchronizeParticipant.ID, resources);	
		// If there isn't, create one and add to the manager
		if (participant == null) {
			return new WorkspaceSynchronizeParticipant(resources);
		} else {
			return participant;
		}
	}
}
