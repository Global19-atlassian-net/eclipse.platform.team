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
package org.eclipse.team.internal.ui.synchronize.actions;

import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.synchronize.sets.SyncInfoStatistics;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.actions.ActionGroup;

public class StatusLineContributionGroup extends ActionGroup implements ISyncSetChangedListener {

	private static final String INCOMING_ID = TeamUIPlugin.ID + "org.eclipse.team.iu.statusline.incoming"; //$NON-NLS-1$
	private static final String OUTGOING_ID = TeamUIPlugin.ID + "org.eclipse.team.iu.statusline.outgoing"; //$NON-NLS-1$
	private static final String CONFLICTING_ID = TeamUIPlugin.ID + "org.eclipse.team.iu.statusline.conflicting"; //$NON-NLS-1$

	private StatusLineCLabelContribution incoming;
	private StatusLineCLabelContribution outgoing;
	private StatusLineCLabelContribution conflicting;
	
	private Image incomingImage = TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_DLG_SYNC_INCOMING).createImage();
	private Image outgoingImage = TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_DLG_SYNC_OUTGOING).createImage();
	private Image conflictingImage = TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_DLG_SYNC_CONFLICTING).createImage();
	
	private ITeamSubscriberSyncInfoSets input;
	private TeamSubscriberParticipant participant;

	public StatusLineContributionGroup(final Shell shell, TeamSubscriberParticipant participant) {
		super();
		this.participant = participant;
		this.input = participant.getInput();
		this.incoming = createStatusLineContribution(INCOMING_ID, TeamSubscriberParticipant.INCOMING_MODE, "0", incomingImage); //$NON-NLS-1$
		this.outgoing = createStatusLineContribution(OUTGOING_ID, TeamSubscriberParticipant.OUTGOING_MODE, "0", outgoingImage); //$NON-NLS-1$
		this.conflicting = createStatusLineContribution(CONFLICTING_ID, TeamSubscriberParticipant.CONFLICTING_MODE, "0", conflictingImage); //$NON-NLS-1$
		participant.getInput().registerListeners(this);
	}

	private StatusLineCLabelContribution createStatusLineContribution(String id, final int mode, String label, Image image) {
		StatusLineCLabelContribution item = new StatusLineCLabelContribution(id, 15);
		item.addListener(SWT.MouseDown, new Listener() {
			public void handleEvent(Event event) {
				participant.setMode(mode);
			}
		});
		item.setText(label); //$NON-NLS-1$
		item.setImage(image);
		return item;
	}

	public void dispose() {
		input.deregisterListeners(this);
		incomingImage.dispose();
		outgoingImage.dispose();
		conflictingImage.dispose();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.team.internal.ui.sync.sets.ISyncSetChangedListener#syncSetChanged(org.eclipse.team.internal.ui.sync.sets.SyncSetChangedEvent)
	 */
	public void syncSetChanged(ISyncInfoSetChangeEvent event) {
		if (input != null) {
			SyncInfoStatistics workspaceSetStats = input.getSubscriberSyncSet().getStatistics();
			SyncInfoStatistics workingSetSetStats = input.getWorkingSetSyncSet().getStatistics();

			final int workspaceConflicting = (int) workspaceSetStats.countFor(SyncInfo.CONFLICTING, SyncInfo.DIRECTION_MASK);
			final int workspaceOutgoing = (int) workspaceSetStats.countFor(SyncInfo.OUTGOING, SyncInfo.DIRECTION_MASK);
			final int workspaceIncoming = (int) workspaceSetStats.countFor(SyncInfo.INCOMING, SyncInfo.DIRECTION_MASK);
			final int workingSetConflicting = (int) workingSetSetStats.countFor(SyncInfo.CONFLICTING, SyncInfo.DIRECTION_MASK);
			final int workingSetOutgoing = (int) workingSetSetStats.countFor(SyncInfo.OUTGOING, SyncInfo.DIRECTION_MASK);
			final int workingSetIncoming = (int) workingSetSetStats.countFor(SyncInfo.INCOMING, SyncInfo.DIRECTION_MASK);

			TeamUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
				public void run() {
					IWorkingSet set = input.getWorkingSet();
					if (set != null) {
						conflicting.setText(Policy.bind("StatisticsPanel.changeNumbers", new Integer(workingSetConflicting).toString(), new Integer(workspaceConflicting).toString())); //$NON-NLS-1$
						incoming.setText(Policy.bind("StatisticsPanel.changeNumbers", new Integer(workingSetIncoming).toString(), new Integer(workspaceIncoming).toString())); //$NON-NLS-1$
						outgoing.setText(Policy.bind("StatisticsPanel.changeNumbers", new Integer(workingSetOutgoing).toString(), new Integer(workspaceOutgoing).toString())); //$NON-NLS-1$

						conflicting.setTooltip(Policy.bind("StatisticsPanel.numbersWorkingSetTooltip", Policy.bind("StatisticsPanel.conflicting"), set.getName())); //$NON-NLS-1$ //$NON-NLS-2$
						outgoing.setTooltip(Policy.bind("StatisticsPanel.numbersWorkingSetTooltip", Policy.bind("StatisticsPanel.outgoing"), set.getName())); //$NON-NLS-1$ //$NON-NLS-2$
						incoming.setTooltip(Policy.bind("StatisticsPanel.numbersWorkingSetTooltip", Policy.bind("StatisticsPanel.incoming"), set.getName())); //$NON-NLS-1$ //$NON-NLS-2$

					} else {
						conflicting.setText(new Integer(workspaceConflicting).toString()); //$NON-NLS-1$
						incoming.setText(new Integer(workspaceIncoming).toString()); //$NON-NLS-1$
						outgoing.setText(new Integer(workspaceOutgoing).toString()); //$NON-NLS-1$

						conflicting.setTooltip(Policy.bind("StatisticsPanel.numbersTooltip", Policy.bind("StatisticsPanel.conflicting"))); //$NON-NLS-1$ //$NON-NLS-2$
						outgoing.setTooltip(Policy.bind("StatisticsPanel.numbersTooltip", Policy.bind("StatisticsPanel.outgoing"))); //$NON-NLS-1$ //$NON-NLS-2$
						incoming.setTooltip(Policy.bind("StatisticsPanel.numbersTooltip", Policy.bind("StatisticsPanel.incoming"))); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			});
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.actions.ActionGroup#fillActionBars(org.eclipse.ui.IActionBars)
	 */
	public void fillActionBars(IActionBars actionBars) {
		IStatusLineManager mgr = actionBars.getStatusLineManager();
		mgr.add(incoming);
		mgr.add(outgoing);
		mgr.add(conflicting);
	}
}