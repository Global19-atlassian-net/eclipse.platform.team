package org.eclipse.team.ui.synchronize;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.team.core.subscribers.TeamSubscriber;
import org.eclipse.team.internal.ui.IPreferenceIds;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.jobs.RefreshSubscriberInputJob;
import org.eclipse.team.internal.ui.synchronize.actions.RefreshAction;
import org.eclipse.team.internal.ui.synchronize.sets.SubscriberInput;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.part.IPageBookViewPage;

/**
 * A synchronize participant that displays synchronization information for local
 * resources that is managed via a {@link TeamSubscriber}.
 *
 * @since 3.0
 */
public abstract class TeamSubscriberParticipant extends AbstractSynchronizeParticipant {
	
	private SubscriberInput input;
	private int currentMode;
	private int currentLayout;
	
	/**
	 * Property constant indicating the mode of a page has changed. 
	 */
	public static final String P_SYNCVIEWPAGE_WORKINGSET = TeamUIPlugin.ID  + ".P_SYNCVIEWPAGE_WORKINGSET";	 //$NON-NLS-1$
	
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
	
	/**
	 * Property constant indicating the mode of a page has changed. 
	 */
	public static final String P_SYNCVIEWPAGE_LAYOUT = TeamUIPlugin.ID  + ".P_SYNCVIEWPAGE_LAYOUT";	 //$NON-NLS-1$
	
	/**
	 * View type constant (value 0) indicating that the synchronize view will be shown
	 * as a tree.
	 */
	public static final int TREE_LAYOUT = 0;
	
	/**
	 * View type constant (value 1) indicating that the synchronize view will be shown
	 * as a table.
	 */
	public static final int TABLE_LAYOUT = 1;
	
	public TeamSubscriberParticipant() {
		super();
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
	
	public void setLayout(int layout) {
		int oldLayout = currentLayout;
		currentLayout = layout;		
		firePropertyChange(this, P_SYNCVIEWPAGE_LAYOUT, new Integer(oldLayout), new Integer(layout));
	}
	
	public int getLayout() {
		return currentLayout;
	}
	
	public void setWorkingSet(IWorkingSet set) {
		SubscriberInput input = getInput();
		IWorkingSet oldSet = input.getWorkingSet();
		input.setWorkingSet(set);
		firePropertyChange(this, P_SYNCVIEWPAGE_WORKINGSET, oldSet, set);
	}
	
	public IWorkingSet getWorkingSet() {
		return getInput().getWorkingSet();
	}
	
	public void refreshWithRemote(IResource[] resources) {
		if((resources == null || resources.length == 0)) {
			RefreshAction.run(input.workingSetRoots(), input.getSubscriber());
		} else {
			RefreshAction.run(resources, input.getSubscriber());
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.AbstractSynchronizeViewPage#dispose()
	 */
	protected void dispose() {
		super.dispose();
		RefreshSubscriberInputJob refreshJob = TeamUIPlugin.getPlugin().getRefreshJob();
		refreshJob.removeSubscriberInput(input);		
		input.dispose();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.AbstractSynchronizeViewPage#init()
	 */
	protected void init() {
		super.init();
		RefreshSubscriberInputJob refreshJob = TeamUIPlugin.getPlugin().getRefreshJob();
		refreshJob.addSubscriberInput(input);
		
		if(TeamUIPlugin.getPlugin().getPreferenceStore().getBoolean(IPreferenceIds.SYNCVIEW_SCHEDULED_SYNC) && refreshJob.getState() == Job.NONE) {
			refreshJob.setReschedule(true);
			refreshJob.schedule(20000 /* 20 seconds */);
		}
	}
	
	/*
	 * For testing only!
	 */
	public SubscriberInput getInput() {
		return input;
	}
	
	protected void setSubscriber(TeamSubscriber subscriber) {
		this.input = new SubscriberInput(subscriber);
		this.currentMode = BOTH_MODE;
	}
}