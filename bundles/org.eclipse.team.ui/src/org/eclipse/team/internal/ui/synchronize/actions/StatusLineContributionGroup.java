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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.core.ITeamStatus;
import org.eclipse.team.core.subscribers.SubscriberSyncInfoCollector;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.team.ui.synchronize.subscriber.SubscriberParticipant;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.actions.ActionGroup;

public class StatusLineContributionGroup extends ActionGroup implements ISyncInfoSetChangeListener, IPropertyChangeListener {

	private static final String INCOMING_ID = TeamUIPlugin.ID + "org.eclipse.team.iu.statusline.incoming"; //$NON-NLS-1$
	private static final String OUTGOING_ID = TeamUIPlugin.ID + "org.eclipse.team.iu.statusline.outgoing"; //$NON-NLS-1$
	private static final String CONFLICTING_ID = TeamUIPlugin.ID + "org.eclipse.team.iu.statusline.conflicting"; //$NON-NLS-1$
	private static final String WORKINGSET_ID = TeamUIPlugin.ID + "org.eclipse.team.iu.statusline.workingset"; //$NON-NLS-1$
	private final static int WORKING_SET_FIELD_SIZE = 25;

	private StatusLineCLabelContribution incoming;
	private StatusLineCLabelContribution outgoing;
	private StatusLineCLabelContribution conflicting;
	private StatusLineCLabelContribution workingSet;
	
	private Image incomingImage = TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_DLG_SYNC_INCOMING).createImage();
	private Image outgoingImage = TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_DLG_SYNC_OUTGOING).createImage();
	private Image conflictingImage = TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_DLG_SYNC_CONFLICTING).createImage();
	
	private SubscriberSyncInfoCollector collector;
	private SubscriberParticipant participant;

	public StatusLineContributionGroup(final Shell shell, SubscriberParticipant participant, final WorkingSetFilterActionGroup setGroup) {
		super();
		this.participant = participant;
		this.collector = participant.getSubscriberSyncInfoCollector();
		this.incoming = createStatusLineContribution(INCOMING_ID, SubscriberParticipant.INCOMING_MODE, "0", incomingImage); //$NON-NLS-1$
		this.outgoing = createStatusLineContribution(OUTGOING_ID, SubscriberParticipant.OUTGOING_MODE, "0", outgoingImage); //$NON-NLS-1$
		this.conflicting = createStatusLineContribution(CONFLICTING_ID, SubscriberParticipant.CONFLICTING_MODE, "0", conflictingImage); //$NON-NLS-1$
		
		this.workingSet = new StatusLineCLabelContribution(WORKINGSET_ID, 25);
		this.workingSet.setTooltip(Policy.bind("StatisticsPanel.workingSetTooltip")); //$NON-NLS-1$
		updateWorkingSetText();

		this.workingSet.addListener(SWT.MouseDoubleClick, new Listener() {
			public void handleEvent(Event event) {
				new SelectWorkingSetAction(setGroup, shell).run();
			}
		});
		
		// Listen to changes to update the working set
		participant.addPropertyChangeListener(this);
		
		// Listen to changes to update the counts
		collector.getSyncInfoTree().addSyncSetChangedListener(this);
	}

	private void updateWorkingSetText() {
		IWorkingSet set = participant.getWorkingSet();
		if (set == null) {
			workingSet.setText(Policy.bind("StatisticsPanel.noWorkingSet")); //$NON-NLS-1$
		} else {
			String name = set.getName();
			if (name.length() > WORKING_SET_FIELD_SIZE) {
				name = name.substring(0, WORKING_SET_FIELD_SIZE - 3) + "..."; //$NON-NLS-1$
			}
			workingSet.setText(name);
		}
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
		collector.getSyncInfoTree().removeSyncSetChangedListener(this);
		participant.removePropertyChangeListener(this);
		incomingImage.dispose();
		outgoingImage.dispose();
		conflictingImage.dispose();
	}

	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(SubscriberParticipant.P_SYNCVIEWPAGE_WORKINGSET)) {
			updateWorkingSetText();
			updateCounts();
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.team.internal.ui.sync.sets.ISyncSetChangedListener#syncSetChanged(org.eclipse.team.internal.ui.sync.sets.SyncSetChangedEvent)
	 */
	public void syncInfoChanged(ISyncInfoSetChangeEvent event, IProgressMonitor monitor) {
		updateCounts();
	}

	private void updateCounts() {
		if (collector != null) {
			SyncInfoSet workspaceSetStats = collector.getSubscriberSyncInfoSet();
			SyncInfoSet workingSetSetStats = collector.getWorkingSetSyncInfoSet();

			final int workspaceConflicting = (int) workspaceSetStats.countFor(SyncInfo.CONFLICTING, SyncInfo.DIRECTION_MASK);
			final int workspaceOutgoing = (int) workspaceSetStats.countFor(SyncInfo.OUTGOING, SyncInfo.DIRECTION_MASK);
			final int workspaceIncoming = (int) workspaceSetStats.countFor(SyncInfo.INCOMING, SyncInfo.DIRECTION_MASK);
			final int workingSetConflicting = (int) workingSetSetStats.countFor(SyncInfo.CONFLICTING, SyncInfo.DIRECTION_MASK);
			final int workingSetOutgoing = (int) workingSetSetStats.countFor(SyncInfo.OUTGOING, SyncInfo.DIRECTION_MASK);
			final int workingSetIncoming = (int) workingSetSetStats.countFor(SyncInfo.INCOMING, SyncInfo.DIRECTION_MASK);

			TeamUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
				public void run() {
					IWorkingSet set = participant.getWorkingSet();
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
		mgr.add(workingSet);
		mgr.add(incoming);
		mgr.add(outgoing);
		mgr.add(conflicting);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.ISyncInfoSetChangeListener#syncInfoSetReset(org.eclipse.team.core.subscribers.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void syncInfoSetReset(SyncInfoSet set, IProgressMonitor monitor) {
		updateCounts();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.ISyncInfoSetChangeListener#syncInfoSetError(org.eclipse.team.core.subscribers.SyncInfoSet, org.eclipse.team.core.ITeamStatus[], org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void syncInfoSetErrors(SyncInfoSet set, ITeamStatus[] errors, IProgressMonitor monitor) {
		// Nothing to do for errors
	}
}