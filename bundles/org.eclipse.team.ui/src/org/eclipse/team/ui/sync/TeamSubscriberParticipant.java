package org.eclipse.team.ui.sync;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.team.core.subscribers.TeamSubscriber;
import org.eclipse.team.internal.ui.IPreferenceIds;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.jobs.RefreshSubscriberInputJob;
import org.eclipse.team.internal.ui.sync.pages.SubscriberSynchronizeViewPage;
import org.eclipse.team.internal.ui.sync.sets.SubscriberInput;
import org.eclipse.team.ui.sync.actions.RefreshAction;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.part.IPageBookViewPage;

public abstract class TeamSubscriberParticipant extends AbstractSynchronizeParticipant {
	
	private SubscriberInput input;
	private SubscriberSynchronizeViewPage page;
	private int currentMode;
	
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
	
	public static final String MB_MODESGROUP = TeamUIPlugin.ID + ".modes";
	
	public TeamSubscriberParticipant() {
		super();
	}
	
	protected void setSubscriber(TeamSubscriber subscriber) {
		this.input = new SubscriberInput(subscriber);
		this.currentMode = BOTH_MODE;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeParticipant#getUniqueId()
	 */
	public QualifiedName getUniqueId() {
		return input.getSubscriber().getId();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeViewPage#createPage(org.eclipse.team.ui.sync.ISynchronizeView)
	 */
	public IPageBookViewPage createPage(ISynchronizeView view) {
		this.page = new SubscriberSynchronizeViewPage(this, view, input);
		return page;
	}
	
	public SubscriberSynchronizeViewPage getPage() {
		return page;
	}
	
	public SubscriberInput getInput() {
		return input;
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
		firePropertyChange(this, P_SYNCVIEWPAGE_LAYOUT, new Integer(page.getLayout()), new Integer(layout));
	}
	
	public int getLayout() {
		return page.getLayout();		
	}
	
	public void setWorkingSet(IWorkingSet set) {
		firePropertyChange(this, P_SYNCVIEWPAGE_WORKINGSET, null, set);
	}
	
	public IWorkingSet getWorkingSet() {
		return getInput().getWorkingSet();
	}
	
	public void refreshWithRemote(IResource[] resources) {
		if(resources == null || resources.length == 0) {
			//page.getRefreshAction().run();
		} else {
			RefreshAction.run(resources, input.getSubscriber());
		}
	}
	
	public void setActionsBars(IActionBars actionBars, IToolBarManager detailsToolbar) {		
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
}
