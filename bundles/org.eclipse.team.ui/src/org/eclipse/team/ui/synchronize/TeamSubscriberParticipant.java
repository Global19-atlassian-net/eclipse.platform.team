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
package org.eclipse.team.ui.synchronize;

import org.eclipse.core.resources.IResource;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.team.core.subscribers.TeamSubscriber;
import org.eclipse.team.internal.ui.IPreferenceIds;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.synchronize.TeamSubscriberParticipantComposite;
import org.eclipse.team.internal.ui.synchronize.actions.RefreshAction;
import org.eclipse.team.internal.ui.synchronize.sets.SubscriberInput;
import org.eclipse.team.ui.controls.IControlFactory;
import org.eclipse.ui.*;
import org.eclipse.ui.part.IPageBookViewPage;

/**
 * A synchronize participant that displays synchronization information for local
 * resources that are managed via a {@link TeamSubscriber}.
 *
 * @since 3.0
 */
public abstract class TeamSubscriberParticipant extends AbstractSynchronizeParticipant {
	
	private SubscriberInput input;
	private RefreshSchedule refreshSchedule;
	
	private int currentMode;
	
	private IWorkingSet workingSet;
	
	/**
	 * Key for settings in memento
	 */
	private static final String CTX_SUBSCRIBER_PARTICIPANT_SETTINGS = TeamUIPlugin.ID + ".TEAMSUBSRCIBERSETTINGS"; //$NON-NLS-1$
	
	/**
	 * Key for schedule in memento
	 */
	private static final String CTX_SUBSCRIBER_SCHEDULE_SETTINGS = TeamUIPlugin.ID + ".TEAMSUBSRCIBER_REFRESHSCHEDULE"; //$NON-NLS-1$
	
	/**
	 * Property constant indicating the mode of a page has changed. 
	 */
	public static final String P_SYNCVIEWPAGE_WORKINGSET = TeamUIPlugin.ID  + ".P_SYNCVIEWPAGE_WORKINGSET";	 //$NON-NLS-1$
	
	/**
	 * Property constant indicating the schedule of a page has changed. 
	 */
	public static final String P_SYNCVIEWPAGE_SCHEDULE = TeamUIPlugin.ID  + ".P_SYNCVIEWPAGE_SCHEDULE";	 //$NON-NLS-1$
	
	/**
	 * Property constant indicating the mode of a page has changed. 
	 */
	public static final String P_SYNCVIEWPAGE_MODE = TeamUIPlugin.ID  + ".P_SYNCVIEWPAGE_MODE";	 //$NON-NLS-1$
		
	/**
	 * Modes are direction filters for the view
	 */
	public final static int INCOMING_MODE = 0x1;
	public final static int OUTGOING_MODE = 0x2;
	public final static int BOTH_MODE = 0x4;
	public final static int CONFLICTING_MODE = 0x8;
	public final static int ALL_MODES = INCOMING_MODE | OUTGOING_MODE | CONFLICTING_MODE | BOTH_MODE;
	
	public TeamSubscriberParticipant() {
		super();
		refreshSchedule = new RefreshSchedule(this);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeViewPage#createPage(org.eclipse.team.ui.sync.ISynchronizeView)
	 */
	public IPageBookViewPage createPage(ISynchronizeView view) {
		return new TeamSubscriberParticipantPage(this, view, input);
	}
	
	public void setMode(int mode) {
		int oldMode = getMode();
		currentMode = mode;
		TeamUIPlugin.getPlugin().getPreferenceStore().setValue(IPreferenceIds.SYNCVIEW_SELECTED_MODE, mode);
		firePropertyChange(this, P_SYNCVIEWPAGE_MODE, new Integer(oldMode), new Integer(mode));
	}
	
	public int getMode() {
		return currentMode;
	}
	
	public void setRefreshSchedule(RefreshSchedule schedule) {
		this.refreshSchedule = schedule;
		firePropertyChange(this, P_SYNCVIEWPAGE_SCHEDULE, null, schedule);
	}
	
	public RefreshSchedule getRefreshSchedule() {
		return refreshSchedule;
	}
	
	public void setWorkingSet(IWorkingSet set) {
		ITeamSubscriberSyncInfoSets input = getInput();
		IWorkingSet oldSet = null;
		if(input != null) {
			oldSet = input.getWorkingSet();
			input.setWorkingSet(set);
			workingSet = null;
		} else {
			workingSet = set;
		}
		firePropertyChange(this, P_SYNCVIEWPAGE_WORKINGSET, oldSet, set);
	}
	
	public IWorkingSet getWorkingSet() {
		ITeamSubscriberSyncInfoSets input = getInput();
		if(input != null) {
			return getInput().getWorkingSet();
		} else {
			return workingSet;
		}
	}
	
	public void refreshWithRemote(IResource[] resources) {
		if((resources == null || resources.length == 0)) {
			RefreshAction.run(input.workingSetRoots(), this);
		} else {
			RefreshAction.run(resources, this);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.AbstractSynchronizeViewPage#dispose()
	 */
	public void dispose() {
		refreshSchedule.dispose();
		removePropertyChangeListener(input);
		input.dispose();
	}
	
	/*
	 * For testing only!
	 */
	public ITeamSubscriberSyncInfoSets getInput() {
		return input;
	}
	
	protected void setSubscriber(TeamSubscriber subscriber) {
		this.input = new SubscriberInput(this, subscriber);
		addPropertyChangeListener(input);
		if(workingSet != null) {
			setWorkingSet(workingSet);
		}
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#init(org.eclipse.ui.IMemento)
	 */
	public void init(IMemento memento) throws PartInitException {
		if(memento != null) {
			IMemento settings = memento.getChild(CTX_SUBSCRIBER_PARTICIPANT_SETTINGS);
			if(settings != null) {
				String set = settings.getString(P_SYNCVIEWPAGE_WORKINGSET);
				String mode = settings.getString(P_SYNCVIEWPAGE_MODE);
				RefreshSchedule schedule = RefreshSchedule.init(settings.getChild(CTX_SUBSCRIBER_SCHEDULE_SETTINGS), this);
				setRefreshSchedule(schedule);
				
				if(set != null) {
					IWorkingSet workingSet = PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSet(set);
					if(workingSet != null) {
						setWorkingSet(workingSet);
					}
				}
				setMode(Integer.parseInt(mode));
			}
		} else {
			setMode(BOTH_MODE);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#saveState(org.eclipse.ui.IMemento)
	 */
	public void saveState(IMemento memento) {
		IMemento settings = memento.createChild(CTX_SUBSCRIBER_PARTICIPANT_SETTINGS);
		IWorkingSet set = getWorkingSet();
		if(set != null) {
			settings.putString(P_SYNCVIEWPAGE_WORKINGSET, getWorkingSet().getName());
		}
		settings.putString(P_SYNCVIEWPAGE_MODE, Integer.toString(getMode()));
		refreshSchedule.saveState(settings.createChild(CTX_SUBSCRIBER_SCHEDULE_SETTINGS));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#createOverviewPage(org.eclipse.swt.widgets.Composite, org.eclipse.team.ui.synchronize.ISynchronizeView)
	 */
	public Composite createOverviewComposite(Composite parent, IControlFactory factory, ISynchronizeView view) {
		return new TeamSubscriberParticipantComposite(parent, true, factory, this, view);
	}
}