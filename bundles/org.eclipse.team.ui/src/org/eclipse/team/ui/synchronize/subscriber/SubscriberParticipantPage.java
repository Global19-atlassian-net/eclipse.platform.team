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
package org.eclipse.team.ui.synchronize.subscriber;

import org.eclipse.compare.internal.INavigatable;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.*;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.internal.ui.synchronize.ChangesSection;
import org.eclipse.team.internal.ui.synchronize.ConfigureRefreshScheduleDialog;
import org.eclipse.team.internal.ui.synchronize.actions.*;
import org.eclipse.team.ui.synchronize.ISynchronizeView;
import org.eclipse.team.ui.synchronize.viewers.DiffTreeViewerConfiguration;
import org.eclipse.ui.*;
import org.eclipse.ui.part.*;

/**
 * A synchronize view page that works with participants that are subclasses of 
 * {@link SubscriberParticipant}. It shows changes in the tree or table view
 * and supports navigation, opening, and filtering changes.
 * <p>
 * Clients can subclass to extend the label decoration or add action bar 
 * contributions. For more extensive modifications, clients should create
 * their own custom page.
 * </p> 
 * @since 3.0
 */
public class SubscriberParticipantPage implements IPageBookViewPage, IPropertyChangeListener, IAdaptable {
	// Parent composite of this view. It is remembered so that we can dispose of its children when 
	// the viewer type is switched.
	private Composite composite = null;
	private ChangesSection changesSection;
	private Viewer changesViewer;
	private boolean settingWorkingSet = false;
	
	private ISynchronizeView view;
	private SubscriberParticipant participant;
	private IPageSite site;
	
	// Toolbar and status line actions for this page, note that context menu actions shown in 
	// the changes viewer are contributed via the viewer and not the page.
	private NavigateAction gotoNext;
	private NavigateAction gotoPrevious;
	private Action configureSchedule;
	private SyncViewerShowPreferencesAction showPreferences;
	private TeamParticipantRefreshAction refreshAllAction;
	private Action collapseAll;
	private WorkingSetFilterActionGroup workingSetGroup;
	private StatusLineContributionGroup statusLine;
	private SubscriberPageDiffTreeViewerConfiguration configuration;
		
	/**
	 * Constructs a new SynchronizeView.
	 */
	public SubscriberParticipantPage(SubscriberParticipant page, ISynchronizeView view) {
		this.participant = page;
		this.view = view;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		composite = new Composite(parent, SWT.NONE); 
		//sc.setContent(composite);
		GridLayout gridLayout= new GridLayout();
		gridLayout.makeColumnsEqualWidth= false;
		gridLayout.marginWidth= 0;
		gridLayout.marginHeight = 0;
		gridLayout.verticalSpacing = 0;
		composite.setLayout(gridLayout);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.grabExcessVerticalSpace = true;
		composite.setLayoutData(data);
		
		// Create the changes section which, in turn, creates the changes viewer and its configuration
		this.changesSection = new ChangesSection(composite, this);
		this.changesViewer = createChangesViewer(changesSection.getComposite());
		changesSection.setViewer(changesViewer);
		
		// toolbar
		INavigatable nav = new INavigatable() {
			public boolean gotoDifference(boolean next) {
				return configuration.navigate(next);
			}
		};
		gotoNext = new NavigateAction(view, nav, true /*next*/);		
		gotoPrevious = new NavigateAction(view, nav, false /*previous*/);
		refreshAllAction = new TeamParticipantRefreshAction(getSite().getSelectionProvider(), getParticipant(), true /* refresh all */);
		refreshAllAction.setWorkbenchSite(view.getSite());
		collapseAll = new Action() {
			public void run() {
				if (changesViewer == null || !(changesViewer instanceof AbstractTreeViewer)) return;
				changesViewer.getControl().setRedraw(false);		
				((AbstractTreeViewer)changesViewer).collapseToLevel(changesViewer.getInput(), TreeViewer.ALL_LEVELS);
				changesViewer.getControl().setRedraw(true);
			}
		};
		Utils.initAction(collapseAll, "action.collapseAll."); //$NON-NLS-1$
		
		configureSchedule = new Action() {
			public void run() {
				ConfigureRefreshScheduleDialog d = new ConfigureRefreshScheduleDialog(
						getShell(), participant.getRefreshSchedule());
				d.setBlockOnOpen(false);
				d.open();
			}
		};
		Utils.initAction(configureSchedule, "action.configureSchedulel."); //$NON-NLS-1$
		
		// view menu
		workingSetGroup = new WorkingSetFilterActionGroup(getShell(), this, view, participant);		
		showPreferences = new SyncViewerShowPreferencesAction(getShell());		
		statusLine = new StatusLineContributionGroup(getShell(), getParticipant(), workingSetGroup);
		
		participant.addPropertyChangeListener(this);
		TeamUIPlugin.getPlugin().getPreferenceStore().addPropertyChangeListener(this);
		participant.setMode(participant.getMode());
	}
	
	private Shell getShell() {
		return view.getSite().getShell();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IViewPart#init(org.eclipse.ui.IViewSite, org.eclipse.ui.IMemento)
	 */
	public void init(IPageSite site) throws PartInitException {
		this.site = site;		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#setFocus()
	 */
	public void setFocus() {
		changesSection.setFocus();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	public void dispose() {
		changesSection.dispose();
		configuration.dispose();
	}

	/*
	 * This method enables "Show In" support for this view
	 * 
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class key) {
		if (key.equals(ISelectionProvider.class))
			return changesViewer;
		if (key == IShowInSource.class) {
			return new IShowInSource() {
				public ShowInContext getShowInContext() {					
					StructuredViewer v = (StructuredViewer)changesViewer;
					if (v == null) return null;
					ISelection s = v.getSelection();
					if (s instanceof IStructuredSelection) {
						Object[] resources = Utils.getResources(((IStructuredSelection)s).toArray());
						return new ShowInContext(null, new StructuredSelection(resources));
					}
					return null;
				}
			};
		}
		if (key == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { IPageLayout.ID_RES_NAV };
				}

			};
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#getControl()
	 */
	public Control getControl() {
		return composite;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#setActionBars(org.eclipse.ui.IActionBars)
	 */
	public void setActionBars(IActionBars actionBars) {
		if(actionBars != null) {
			IToolBarManager manager = actionBars.getToolBarManager();			
			
			// toolbar
			manager.add(refreshAllAction);
			manager.add(new Separator());	
			if(gotoNext != null) {
				manager.add(gotoNext);
				manager.add(gotoPrevious);
			}
			manager.add(collapseAll);
			manager.add(new Separator());

			// view menu
			IMenuManager menu = actionBars.getMenuManager();
			MenuManager layoutMenu = new MenuManager(Policy.bind("action.layout.label")); //$NON-NLS-1$		
			MenuManager comparisonCriteria = new MenuManager(Policy.bind("action.comparisonCriteria.label")); //$NON-NLS-1$
			//comparisonCriteriaGroup.addActionsToMenuMgr(comparisonCriteria);
			workingSetGroup.fillActionBars(actionBars);
			menu.add(new Separator());
			menu.add(new Separator());
			menu.add(new Separator("others"));
			menu.add(new Separator());
			menu.add(configureSchedule);
			menu.add(new Separator());
			menu.add(showPreferences);
			
			// status line
			statusLine.fillActionBars(actionBars);
		}		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPageBookViewPage#getSite()
	 */
	public IPageSite getSite() {
		return this.site;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		// Working set changed by user
		if(event.getProperty().equals(WorkingSetFilterActionGroup.CHANGE_WORKING_SET)) {
			if(settingWorkingSet) return;
			settingWorkingSet = true;
			participant.setWorkingSet((IWorkingSet)event.getNewValue());
			settingWorkingSet = false;
		// Working set changed programatically
		} else if(event.getProperty().equals(SubscriberParticipant.P_SYNCVIEWPAGE_WORKINGSET)) {
			if(settingWorkingSet) return;
			settingWorkingSet = true;
			Object newValue = event.getNewValue();
			if (newValue instanceof IWorkingSet) {	
				workingSetGroup.setWorkingSet((IWorkingSet)newValue);
			} else if (newValue == null) {
				workingSetGroup.setWorkingSet(null);
			}
			settingWorkingSet = false;
		// Change to showing of sync state in text labels preference
		} else if(event.getProperty().equals(IPreferenceIds.SYNCVIEW_VIEW_SYNCINFO_IN_LABEL)) {
			if(changesViewer instanceof StructuredViewer) {
				((StructuredViewer)changesViewer).refresh(true /* update labels */);
			}
		}
	}
	
	/**
	 * @return Returns the participant.
	 */
	public SubscriberParticipant getParticipant() {
		return participant;
	}
	
	/**
	 * @return Returns the view.
	 */
	public ISynchronizeView getSynchronizeView() {
		return view;
	}
	
	private Viewer createChangesViewer(Composite parent) {
		configuration = createSyncInfoSetCompareConfiguration();
		TreeViewer viewer = new DiffTreeViewerConfiguration.NavigableTreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		GridData data = new GridData(GridData.FILL_BOTH);
		viewer.getControl().setLayoutData(data);
		configuration.initializeViewer(viewer);
		getSite().setSelectionProvider(viewer);		
		return viewer;
	}

	public DiffTreeViewerConfiguration getViewerConfiguration() {
		return configuration;
	}
	
	public Viewer getViewer() {
		return changesViewer;
	}
	
	protected SubscriberPageDiffTreeViewerConfiguration createSyncInfoSetCompareConfiguration() {
		return new SubscriberPageDiffTreeViewerConfiguration(getSynchronizeView(), getParticipant());
	}
}