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
package org.eclipse.team.ui.synchronize.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.team.ui.synchronize.ISynchronizeView;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipant;

public class ShowSynchronizeParticipantAction extends Action {
	
	private ISynchronizeParticipant fPage;
	private ISynchronizeView fView;
	
	public void run() {
		if (!fPage.equals(fView.getParticipant())) {
			fView.display(fPage);
		}
	}
	
	/**
	 * Constructs an action to display the given synchronize participant in the
	 * synchronize view.
	 * 
	 * @param view the synchronize view in which the given page is contained
	 * @param participant the participant to show
	 */
	public ShowSynchronizeParticipantAction(ISynchronizeView view, ISynchronizeParticipant participant) {
		super(participant.getName(), Action.AS_RADIO_BUTTON);
		fPage = participant;
		fView = view;
		setImageDescriptor(participant.getImageDescriptor());
	}
}
