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
package org.eclipse.team.internal.ui.synchronize.actions;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipant;
import org.eclipse.ui.PlatformUI;

/**
 * Action that toggles pinned state of a participant
 */
public class PinParticipantAction extends Action {

	private ISynchronizeParticipant participant;

	public PinParticipantAction() {
		super();
		Utils.initAction(this, "action.pinParticipant.", Policy.getBundle()); //$NON-NLS-1$
	}

	public void setParticipant(ISynchronizeParticipant participant) {
		this.participant = participant;
		setEnabled(participant != null);
		updateState();
	}
	
	private void updateState() {
		setChecked(participant != null && participant.isPinned());
	}

	public void run() {
		if (participant != null) {
			try {
				PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor)
							throws InvocationTargetException, InterruptedException {
						participant.setPinned(!participant.isPinned());
						updateState();
					}
				});
			} catch (InvocationTargetException e) {
				Utils.handle(e);
			} catch (InterruptedException e) {
				// Cancelled. Just ignore
			}
		}
	}
}
