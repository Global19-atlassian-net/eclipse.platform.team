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
package org.eclipse.team.internal.ui.synchronize;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareUI;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.team.ui.synchronize.subscribers.*;

public class RefreshUserNotificationPolicyInModalDialog implements IRefreshSubscriberListener {

	private SubscriberParticipant participant;
	private ISynchronizePageConfiguration configuration;
	private Shell shell;

	public RefreshUserNotificationPolicyInModalDialog(Shell shell, ISynchronizePageConfiguration configuration, SubscriberParticipant participant) {
		this.configuration  = configuration;
		this.participant = participant;
		this.shell = shell;
	}

	public void refreshStarted(IRefreshEvent event) {
	}

	public Runnable refreshDone(final IRefreshEvent event) {
		//	Ensure that this event was generated for this participant
		if (event.getSubscriber() != participant.getSubscriber())
			return null;
		//	 If the event is for a cancelled operation, there's nothing to do
		int severity = event.getStatus().getSeverity();
		if(severity == Status.CANCEL || severity == Status.ERROR) 
			return null;
		
		return new Runnable() {
			public void run() {
				try {
					// If there are no changes
					if (event.getStatus().getCode() == IRefreshEvent.STATUS_NO_CHANGES) {
						MessageDialog.openInformation(shell, Policy.bind("OpenComparedDialog.noChangeTitle"), Policy.bind("OpenComparedDialog.noChangesMessage")); //$NON-NLS-1$ //$NON-NLS-2$
						return;
					}
					if (isSingleFileCompare(event.getResources())) {
						compareAndOpenEditors(event, participant);
					} else {
						compareAndOpenDialog(event, participant);
					}
				} finally {
					if (TeamUI.getSynchronizeManager().get(participant.getId(), participant.getSecondaryId()) == null) {
						participant.dispose();
					}
				}
			}
		};
	}

	protected boolean isSingleFileCompare(IResource[] resources) {
		return resources.length == 1 && resources[0].getType() == IResource.FILE;
	}

	protected void compareAndOpenEditors(IRefreshEvent event, SubscriberParticipant participant) {
		IResource[] resources = event.getResources();
		for (int i = 0; i < resources.length; i++) {
			SyncInfo info = participant.getSyncInfoSet().getSyncInfo(resources[i]);
			if (info != null) {
				SyncInfoCompareInput input = new SyncInfoCompareInput(event.getSubscriber().getName(), info);
				CompareUI.openCompareEditor(input);
			}
		}
	}

	protected void compareAndOpenDialog(final IRefreshEvent event, final SubscriberParticipant participant) {
		CompareConfiguration cc = new CompareConfiguration();
		ParticipantPageSaveablePart input = new ParticipantPageSaveablePart(Utils.getShell(null), cc, configuration, participant);
		ParticipantPageDialog dialog = new ParticipantPageDialog(shell, input, participant);
		dialog.setBlockOnOpen(true);
		dialog.open();
	}
}