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

import org.eclipse.compare.internal.INavigatable;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.internal.ui.synchronize.actions.*;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.team.ui.synchronize.subscribers.*;
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
public final class SubscriberParticipantPage extends Page implements ISynchronizePage, IPropertyChangeListener, IAdaptable {
	
	/** 
	 * Settings constant for section name (value <code>SubscriberParticipantPage</code>).
	 */
	private static final String STORE_SECTION_POSTFIX = "SubscriberParticipantPage"; //$NON-NLS-1$
	/** 
	 * Settings constant for sort order (value <code>SubscriberParticipantPage</code>).
	 */
	private static final String STORE_SORT_TYPE = "SubscriberParticipantPage.STORE_SORT_TYPE"; //$NON-NLS-1$
	/** 
	 * Settings constant for working set (value <code>SubscriberParticipantPage</code>).
	 */
	private static final String STORE_WORKING_SET = "SubscriberParticipantPage.STORE_WORKING_SET"; //$NON-NLS-1$
	/** 
	 * Settings constant for working set (value <code>SubscriberParticipantPage</code>).
	 */
	private static final String STORE_MODE = "SubscriberParticipantPage.STORE_MODE"; //$NON-NLS-1$
	
	private IDialogSettings settings;
	private SubscriberConfiguration configuration;
	
	// Parent composite of this view. It is remembered so that we can dispose of its children when 
	// the viewer type is switched.
	private Composite composite;
	private ChangesSection changesSection;
	private Viewer changesViewer;
	private boolean settingWorkingSet = false;
	private SubscriberParticipant participant;
	
	// Toolbar and status line actions for this page, note that context menu actions shown in 
	// the changes viewer are contributed via the viewer and not the page.
	private NavigateAction gotoNext;
	private NavigateAction gotoPrevious;
	private Action configureSchedule;
	private SyncViewerShowPreferencesAction showPreferences;
	private Action refreshAllAction;
	private Action collapseAll;
	private WorkingSetFilterActionGroup workingSetGroup;
	private StatusLineContributionGroup statusLine;
	private StructuredViewerAdvisor viewerAdvisor;
	private ISynchronizePageSite site;
		
	/**
	 * Constructs a new SynchronizeView.
	 */
	public SubscriberParticipantPage(SubscriberConfiguration configuration) {
		this.participant = (SubscriberParticipant)configuration.getParticipant();
		IDialogSettings viewsSettings = TeamUIPlugin.getPlugin().getDialogSettings();
		
		String key = Utils.getKey(participant.getId(), participant.getSecondaryId());
		settings = viewsSettings.getSection(key + STORE_SECTION_POSTFIX);
		if (settings == null) {
			settings = viewsSettings.addNewSection(key + STORE_SECTION_POSTFIX);
		}
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
		this.changesSection = new ChangesSection(composite, this, configuration);
		this.changesViewer = createChangesViewer(changesSection.getComposite());
		changesSection.setViewer(changesViewer);
		
		// toolbar
		INavigatable nav = new INavigatable() {
			public boolean gotoDifference(boolean next) {
				return viewerAdvisor.navigate(next);
			}
		};
		gotoNext = new NavigateAction(configuration.getPart(), configuration.getParticipant().getName(), nav, true /*next*/);		
		gotoPrevious = new NavigateAction(configuration.getPart(), configuration.getParticipant().getName(), nav, false /*previous*/);
		
		if(participant.doesSupportSynchronize()) {
			refreshAllAction = new Action() {
				public void run() {
					// Prime the refresh wizard with an appropriate initial selection
					final SubscriberRefreshWizard wizard = new SubscriberRefreshWizard(participant);
					IWorkingSet set = configuration.getWorkingSet();
					if(set != null) {
						int scopeHint = SubscriberRefreshWizard.SCOPE_WORKING_SET;
						wizard.setScopeHint(scopeHint);
					}					
					WizardDialog dialog = new WizardDialog(getShell(), wizard);
					dialog.open();
				}
			};
			Utils.initAction(refreshAllAction, "action.refreshWithRemote."); //$NON-NLS-1$
		}
		
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
		workingSetGroup = new WorkingSetFilterActionGroup(getShell(), participant.toString(), this, configuration.getWorkingSet());		
		showPreferences = new SyncViewerShowPreferencesAction(getShell());		
		statusLine = new StatusLineContributionGroup(getShell(), this, configuration, workingSetGroup);
		
		participant.addPropertyChangeListener(this);
		TeamUIPlugin.getPlugin().getPreferenceStore().addPropertyChangeListener(this);
	}
	
	private Shell getShell() {
		return getSynchronizePageSite().getShell();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeViewPage#init(org.eclipse.ui.IWorkbenchPart)
	 */
	public void init(ISynchronizePageSite site) {
		this.site = site;
		configuration.init(site);
	}
	
	public ISynchronizePageSite getSynchronizePageSite() {
		return site;
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
		statusLine.dispose();
		changesSection.dispose();
		composite.dispose();
		TeamUIPlugin.getPlugin().getPreferenceStore().removePropertyChangeListener(this);
		participant.removePropertyChangeListener(this);
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
			if(refreshAllAction != null) {
				manager.add(refreshAllAction);
			}
			manager.add(new Separator());	
			if(gotoNext != null) {
				manager.add(gotoNext);
				manager.add(gotoPrevious);
			}
			manager.add(collapseAll);
			manager.add(new Separator());

			// view menu
			IMenuManager menu = actionBars.getMenuManager();
			workingSetGroup.fillActionBars(actionBars);
			menu.add(new Separator());
			menu.add(new Separator());
			menu.add(new Separator("others")); //$NON-NLS-1$
			menu.add(new Separator());
			menu.add(configureSchedule);
			menu.add(new Separator());
			menu.add(showPreferences);
			
			// status line
			statusLine.fillActionBars(actionBars);
			
			// allow the advisor to contribute
			getViewerAdvisor().setActionBars(actionBars);
		}		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		// Working set changed by user
		if(event.getProperty().equals(WorkingSetFilterActionGroup.CHANGE_WORKING_SET)) {
			settingWorkingSet = true;
			configuration.setWorkingSet((IWorkingSet)event.getNewValue());
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
	
	private Viewer createChangesViewer(Composite parent) {
		TreeViewer viewer = new TreeViewerAdvisor.NavigableTreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		GridData data = new GridData(GridData.FILL_BOTH);
		viewer.getControl().setLayoutData(data);
		viewerAdvisor = new TreeViewerAdvisor(configuration.getParticipant().getId(), configuration.getPart().getSite(), getFilteredCollector().getSyncInfoTree());
		viewerAdvisor.initializeViewer(viewer);
		getSynchronizePageSite().setSelectionProvider(viewer);		
		return viewer;
	}
	
	public StructuredViewerAdvisor getViewerAdvisor() {
		return viewerAdvisor;
	}
	
	public Viewer getViewer() {
		return changesViewer;
	}

	public void setSelection(Object[] objects, boolean reveal) {
		getViewerAdvisor().setSelection(objects, reveal);
	}
}