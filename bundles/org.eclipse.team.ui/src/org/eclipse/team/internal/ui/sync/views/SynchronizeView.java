/*************.******************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.sync.views;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.ITeamResourceChangeListener;
import org.eclipse.team.core.subscribers.TeamDelta;
import org.eclipse.team.core.subscribers.TeamSubscriber;
import org.eclipse.team.internal.core.Assert;
import org.eclipse.team.internal.ui.IPreferenceIds;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.internal.ui.actions.TeamAction;
import org.eclipse.team.internal.ui.jobs.JobBusyCursor;
import org.eclipse.team.internal.ui.jobs.RefreshSubscriberInputJob;
import org.eclipse.team.internal.ui.sync.actions.RefreshAction;
import org.eclipse.team.internal.ui.sync.actions.SyncViewerActions;
import org.eclipse.team.internal.ui.sync.sets.ISyncSetChangedListener;
import org.eclipse.team.internal.ui.sync.sets.SubscriberInput;
import org.eclipse.team.internal.ui.sync.sets.SyncSetChangedEvent;
import org.eclipse.team.ui.sync.ISynchronizeView;
import org.eclipse.team.ui.sync.actions.AndSyncInfoFilter;
import org.eclipse.team.ui.sync.actions.PseudoConflictFilter;
import org.eclipse.team.ui.sync.actions.SubscriberAction;
import org.eclipse.team.ui.sync.actions.SyncInfoChangeTypeFilter;
import org.eclipse.team.ui.sync.actions.SyncInfoDirectionFilter;
import org.eclipse.team.ui.sync.actions.SyncInfoFilter;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.navigator.ResourceSorter;

/**
 * The Team Synchronization view.
 */
public class SynchronizeView extends ViewPart implements ITeamResourceChangeListener, ISyncSetChangedListener, ISynchronizeView {
	
	// The property id for <code>getCurrentViewType</code>.
	public static final int PROP_VIEWTYPE = 1;
	
	/**
	  * View type constant (value 0) indicating that the synchronize view will be shown
	  * as a tree.
	  */
	public static final int TREE_VIEW = 0;

	/**
	 * View type constant (value 1) indicating that the synchronize view will be shown
	 * as a table.
	 */
	public static final int TABLE_VIEW = 1;
	
	 //This view's id. The same value as in the plugin.xml.
	 public static final String VIEW_ID = "org.eclipse.team.sync.views.SynchronizeView";  //$NON-NLS-1$
				
	// The viewer that is shown in the view. Currently this can be either a table or tree viewer.
	private StructuredViewer viewer;
	
	// Parent composite of this view. It is remembered so that we can dispose of its children when 
	// the viewer type is switched.
	private Composite composite = null;
	private StatisticsPanel statsPanel;
	private IMemento memento;

	// Viewer type constants
	private int currentViewType;
	
	// Cache for each subscriber registered with the view
	private Map subscriberInputs = new HashMap(1);
	
	// Remembering the current input and the previous.
	private SubscriberInput input = null;
	
	// A set of common actions. They are hooked to the active SubscriberInput and must 
	// be reset when the input changes.
	private SyncViewerActions actions;
	
	private JobBusyCursor busyCursor;

	/**
	 * Constructs a new SynchronizeView.
	 */
	public SynchronizeView() {
		currentViewType = getStore().getInt(IPreferenceIds.SYNCVIEW_VIEW_TYPE);
		if (currentViewType != TREE_VIEW) {
			currentViewType = TABLE_VIEW;
		}		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createPartControl(Composite parent) {		
		GridLayout gridLayout= new GridLayout();
		gridLayout.makeColumnsEqualWidth= false;
		gridLayout.marginWidth= 0;
		gridLayout.marginHeight = 0;
		parent.setLayout(gridLayout);
		
		// Create the busy cursor with no control to start with (createViewer will set it)
		busyCursor = new JobBusyCursor(null /* control */, SubscriberAction.SUBSCRIBER_JOB_TYPE);
		
		createViewer(parent);
		this.composite = parent;
				
		initializeActions();
		contributeToActionBars();
		
		// Register for addition/removal of subscribers
		TeamSubscriber.getSubscriberManager().addTeamResourceChangeListener(this);
		TeamSubscriber[] subscribers = TeamSubscriber.getSubscriberManager().getSubscribers();
		for (int i = 0; i < subscribers.length; i++) {
			TeamSubscriber subscriber = subscribers[i];
			addSubscriber(subscriber);
		}
		
		updateStatusPanel();
		updateTooltip();
		
		actions.setContext(null);
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IViewPart#init(org.eclipse.ui.IViewSite, org.eclipse.ui.IMemento)
	 */
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		this.memento = memento;
		
		RefreshSubscriberInputJob refreshJob = TeamUIPlugin.getPlugin().getRefreshJob();
		if(getStore().getBoolean(IPreferenceIds.SYNCVIEW_SCHEDULED_SYNC) && refreshJob.getState() == Job.NONE) {
			refreshJob.setReschedule(true);
			// start once the UI has started and stabilized
			refreshJob.schedule(20000 /* 20 seconds */);
		}
	}
	/*
	 * This method is synchronized to ensure that all internal state is not corrupted
	 */
	public synchronized void initializeSubscriberInput(final SubscriberInput input) {
		Assert.isNotNull(input);

		// listen to sync set changes in order to update state relating to the
		// size of the sync sets and update the title
		disconnectSubscriberInput();
		this.input = input;
		input.registerListeners(this);

		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				// create the viewer is it doesn't exist yet.
				if(viewer == null) {
					switchViewerType(currentViewType);
				}
				ActionContext context = new ActionContext(null);
				context.setInput(input);
				actions.setContext(context);
				viewer.setInput(input);
			
				RefreshSubscriberInputJob refreshJob = TeamUIPlugin.getPlugin().getRefreshJob();
				refreshJob.setSubscriberInput(input);
				updateStatusPanel();
				setTitle(Policy.bind("LiveSyncView.titleSubscriber", input.getSubscriber().getName())); //$NON-NLS-1$
			}
		});		
	}
	
	private void disconnectSubscriberInput() {
		if(input != null) {
			input.deregisterListeners(this);
			input = null;
		}
	}
	/**
	 * Toggles between label/tree/table viewers. 
	 */
	public void switchViewerType(int viewerType) {
		if(viewer == null || viewerType != currentViewType) {
			if (composite == null || composite.isDisposed()) return;
			IStructuredSelection oldSelection = null;
			if(viewer != null) {
				oldSelection = (IStructuredSelection)viewer.getSelection();
			}
			currentViewType = viewerType;
			getStore().setValue(IPreferenceIds.SYNCVIEW_VIEW_TYPE, currentViewType);
			disposeChildren(composite);
			createViewer(composite);
			composite.layout();
			if(oldSelection == null || oldSelection.size() == 0) {
				//gotoDifference(INavigableControl.NEXT);
			} else {
				viewer.setSelection(oldSelection, true);
			}
			fireSafePropertyChange(PROP_VIEWTYPE);
			updateStatusPanel();
		}
	}
	
	/**
	 * Adds the listeners to the viewer.
	 */
	protected void initializeListeners() {
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				handleSelectionChanged(event);
			}
		});
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				handleDoubleClick(event);
			}
		});
		viewer.addOpenListener(new IOpenListener() {
			public void open(OpenEvent event) {
				handleOpen(event);
			}
		});
	}	

	protected void initializeActions() {
		actions = new SyncViewerActions(this);
		actions.restore(memento);
	}
	protected void hookContextMenu() {
			if(viewer != null) {
				MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
				menuMgr.setRemoveAllWhenShown(true);
				menuMgr.addMenuListener(new IMenuListener() {
					public void menuAboutToShow(IMenuManager manager) {
						actions.fillContextMenu(manager);
					}
				});
				Menu menu = menuMgr.createContextMenu(viewer.getControl());
				viewer.getControl().setMenu(menu);
				getSite().registerContextMenu(menuMgr, viewer);
			}
		}	
	protected void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		actions.fillActionBars(bars);
	}
	
	protected void createViewer(Composite parent) {		
		if(input == null) {
			Label label = new Label(parent, SWT.WRAP);
			label.setText(Policy.bind("SynchronizeView.noSubscribersMessage")); //$NON-NLS-1$
		} else {
			statsPanel = new StatisticsPanel(parent);
			switch(currentViewType) {
				case TREE_VIEW:
					createTreeViewerPartControl(parent); 
					break;
				case TABLE_VIEW:
					createTableViewerPartControl(parent); 
					break;
			}
			viewer.setInput(input);
			viewer.getControl().setFocus();
			hookContextMenu();
			initializeListeners();
			busyCursor.setControl(viewer.getControl());
		}		
	}

	protected void createTreeViewerPartControl(Composite parent) {
		GridData data = new GridData(GridData.FILL_BOTH);
		viewer = new SyncTreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setLabelProvider(SyncViewerLabelProvider.getDecoratingLabelProvider());
		viewer.setSorter(new SyncViewerSorter(ResourceSorter.NAME));
		((TreeViewer)viewer).getTree().setLayoutData(data);
	}

	protected void createTableViewerPartControl(Composite parent) {
		// Create the table
		Table table = new Table(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridData data = new GridData(GridData.FILL_BOTH);
		table.setLayoutData(data);
		
		// Set the table layout
		TableLayout layout = new TableLayout();
		table.setLayout(layout);
		
		// Create the viewer
		TableViewer tableViewer = new SyncTableViewer(table);
		
		// Create the table columns
		createColumns(table, layout, tableViewer);
		
		// Set the table contents
		viewer = tableViewer;
		viewer.setContentProvider(new SyncSetTableContentProvider());
		viewer.setLabelProvider(new SyncViewerLabelProvider());
			
		viewer.setSorter(new SyncViewerTableSorter());
	}
	
	/**
	 * Creates the columns for the sync viewer table.
	 */
	protected void createColumns(Table table, TableLayout layout, TableViewer viewer) {
		SelectionListener headerListener = SyncViewerTableSorter.getColumnListener(viewer);
		// revision
		TableColumn col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText("Resource"); //$NON-NLS-1$
		col.addSelectionListener(headerListener);
		layout.addColumnData(new ColumnWeightData(30, true));
	
		// tags
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText("In Folder"); //$NON-NLS-1$
		col.addSelectionListener(headerListener);
		layout.addColumnData(new ColumnWeightData(50, true));
	}
	
	protected void disposeChildren(Composite parent) {
		// Null out the control of the busy cursor while we are switching viewers
		busyCursor.setControl(null);
		Control[] children = parent.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control control = children[i];
			control.dispose();
		}
	}
	
	/**
	 * Handles a selection changed event from the viewer. Updates the status line and the action 
	 * bars, and links to editor (if option enabled).
	 * 
	 * @param event the selection event
	 */
	protected void handleSelectionChanged(SelectionChangedEvent event) {
		final IStructuredSelection sel = (IStructuredSelection) event.getSelection();
		updateStatusLine(sel);
		updateActionBars(sel);
	}

	protected void handleOpen(OpenEvent event) {
		actions.open();
	}
	/**
	 * Handles a double-click event from the viewer.
	 * Expands or collapses a folder when double-clicked.
	 * 
	 * @param event the double-click event
	 * @since 2.0
	 */
	protected void handleDoubleClick(DoubleClickEvent event) {
		IStructuredSelection selection = (IStructuredSelection) event.getSelection();
		Object element = selection.getFirstElement();

		// Double-clicking should expand/collapse containers
		if (viewer instanceof TreeViewer) {
			TreeViewer tree = (TreeViewer)viewer;
			if (tree.isExpandable(element)) {
				tree.setExpandedState(element, !tree.getExpandedState(element));
			}
		}

	}
	
	public void activateSubscriber(TeamSubscriber subscriber) {
		SubscriberInput newInput = (SubscriberInput)subscriberInputs.get(subscriber.getId());
		if (newInput == null) {
			addSubscriber(subscriber);
		};
		if(! newInput.getSubscriber().getId().equals(getInput().getSubscriber().getId())) {
			initializeSubscriberInput(newInput);
		}
	}

	protected void updateStatusPanel() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				SubscriberInput input = getInput();
				if(input != null && statsPanel != null) {	
					statsPanel.update(new ViewStatusInformation(input));
				}
			}					 	
		});
	}

	protected void updateTooltip() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				SubscriberInput input = getInput();
				if(input != null) {	
					if(input.getWorkingSet() != null) {
						String tooltip = Policy.bind("LiveSyncView.titleTooltip", input.getWorkingSet().getName()); //$NON-NLS-1$
						setTitleToolTip(tooltip);					 	
					} else {
						setTitleToolTip(""); //$NON-NLS-1$
					}
				}
			}					 	
		});
	}
	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		if (viewer == null) return;
		viewer.getControl().setFocus();
	}

	public StructuredViewer getViewer() {
		return viewer;
	}
	
	private static void handle(Shell shell, Exception exception, String title, String message) {
		Utils.handleError(shell, exception, title, message);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	public void dispose() {
		super.dispose();
		
		// cancel and wait 
		RefreshSubscriberInputJob job = TeamUIPlugin.getPlugin().getRefreshJob();
		job.setRestartOnCancel(false);
		job.cancel();
		try {
			job.join();
		} catch (InterruptedException e) {
			// continue with shutdown
		}
		job.setSubscriberInput(null);
		
		// Cleanup the subscriber inputs
		TeamSubscriber.getSubscriberManager().removeTeamResourceChangeListener(this);
		for (Iterator it = subscriberInputs.values().iterator(); it.hasNext();) {
			SubscriberInput input = (SubscriberInput) it.next();
			input.dispose();
		}
		
		busyCursor.dispose();
	}

	public void run(IRunnableWithProgress runnable) {
		try {
			PlatformUI.getWorkbench().getActiveWorkbenchWindow().run(true, true, runnable);
		} catch (InvocationTargetException e) {
			handle(getSite().getShell(), e, null, null);
		} catch (InterruptedException e) {
			// Nothing to be done
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IViewPart#saveState(org.eclipse.ui.IMemento)
	 */
	public void saveState(IMemento memento) {
		super.saveState(memento);
		actions.save(memento);
	}
	
	public int getViewerType() {
		return currentViewType;
	}

	/*
	 * Return the current input for the view.
	 */
	public SubscriberInput getInput() {
		return input;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.ITeamResourceChangeListener#teamResourceChanged(org.eclipse.team.core.sync.TeamDelta[])
	 */
	public void teamResourceChanged(TeamDelta[] deltas) {
		for (int i = 0; i < deltas.length; i++) {
			TeamDelta delta = deltas[i];
			if(delta.getFlags() == TeamDelta.SUBSCRIBER_CREATED) {
				TeamSubscriber s = delta.getSubscriber();
				addSubscriber(s);
			}
		}
	}

	/*
	 * Add the subscriber to the view. This method does not activate
	 * the subscriber.
	 */
	synchronized private void addSubscriber(final TeamSubscriber s) {
		SubscriberInput si = new SubscriberInput(s);
		subscriberInputs.put(s.getId(), si);
		ActionContext context = new ActionContext(null);
		context.setInput(si);
		actions.addContext(context);
	}
	
	synchronized public void removeSubscriber(TeamSubscriber s) {
		// notify that context is changing
		SubscriberInput si = (SubscriberInput)subscriberInputs.get(s.getId());
		ActionContext context = new ActionContext(null);
		context.setInput(si);
		actions.removeContext(context);
		
		// dispose of the input
		si.dispose();
		
		// forget about this input
		subscriberInputs.remove(s.getId());
		
		// de-register the subscriber with the platform
		s.cancel();
		
		if (si == input) {
			if (subscriberInputs.isEmpty()) {
				disconnectSubscriberInput();
				if(viewer != null) {
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							if(viewer == null) return;
							ActionContext context = new ActionContext(null);
							context.setInput(null);
							actions.setContext(context);
							viewer.setInput(null);
							setTitle(Policy.bind("LiveSyncView.titleSubscriber")); //$NON-NLS-1$
						}
					});
				}
			} else {
				initializeSubscriberInput((SubscriberInput)subscriberInputs.values().iterator().next());
			}
		}
	}
	
	public void collapseAll() {
		if (viewer == null || !(viewer instanceof AbstractTreeViewer)) return;
		viewer.getControl().setRedraw(false);		
		((AbstractTreeViewer)viewer).collapseToLevel(viewer.getInput(), TreeViewer.ALL_LEVELS);
		viewer.getControl().setRedraw(true);
	}
	
	public ISelection getSelection() {
		return getViewer().getSelection();
	}
	
	/**
	 * This method enables "Show In" support for this view
	 * 
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class key) {
		if (key == IShowInSource.class) {
			return new IShowInSource() {
				public ShowInContext getShowInContext() {
					StructuredViewer v = getViewer();
					if (v == null) return null;
					return new ShowInContext(null, v.getSelection());
				}
			};
		}
		return super.getAdapter(key);
	}
	
	/**
	 * Updates the action bar actions.
	 * 
	 * @param selection the current selection
	 * @since 2.0
	 */
	protected void updateActionBars(IStructuredSelection selection) {
		if (actions != null) {
			ActionContext actionContext = actions.getContext();
			if(actionContext != null) {
				actionContext.setSelection(selection);
				actions.updateActionBars();
			}
		}
	}
	
	/**
	 * Updates the message shown in the status line.
	 *
	 * @param selection the current selection
	 */
	protected void updateStatusLine(IStructuredSelection selection) {
		String msg = getStatusLineMessage(selection);
		getViewSite().getActionBars().getStatusLineManager().setMessage(msg);
	}

	/**
	 * Returns the message to show in the status line.
	 *
	 * @param selection the current selection
	 * @return the status line message
	 * @since 2.0
	 */
	protected String getStatusLineMessage(IStructuredSelection selection) {
		if (selection.size() == 1) {
			IResource resource = getResource(selection.getFirstElement());
			if (resource == null) {
				return Policy.bind("SynchronizeView.12"); //$NON-NLS-1$
			} else {
				return resource.getFullPath().makeRelative().toString();
			}
		}
		if (selection.size() > 1) {
			return selection.size() + Policy.bind("SynchronizeView.13"); //$NON-NLS-1$
		}
		return ""; //$NON-NLS-1$
	}

	/**
	 * @param object
	 * @return
	 */
	private IResource getResource(Object object) {
		return (IResource)TeamAction.getAdapter(object, IResource.class);
	}
	
	/**
	 * Makes this view visible in the active page.
	 */
	public static SynchronizeView showInActivePage(IWorkbenchPage activePage, boolean allowSwitchingPerspectives) {
		IWorkbench workbench= TeamUIPlugin.getPlugin().getWorkbench();
		IWorkbenchWindow window= workbench.getActiveWorkbenchWindow();
		
		if(allowSwitchingPerspectives && ! TeamUIPlugin.getPlugin().getPreferenceStore().getString(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE).equals(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE_NONE)) {			
			try {
				String pId = TeamUIPlugin.getPlugin().getPreferenceStore().getString(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE);
				activePage = workbench.showPerspective(pId, window);
			} catch (WorkbenchException e) {
					Utils.handleError(window.getShell(), e, Policy.bind("SynchronizeView.14"), e.getMessage()); //$NON-NLS-1$
			}
		}
		try {
			if (activePage == null) {
				activePage = TeamUIPlugin.getActivePage();
				if (activePage == null) return null;
			}
			return (SynchronizeView)activePage.showView(VIEW_ID);
		} catch (PartInitException pe) {
			Utils.handleError(window.getShell(), pe, Policy.bind("SynchronizeView.16"), pe.getMessage()); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Update the title when either the subscriber or filter sync set changes.
	 */
	public void syncSetChanged(SyncSetChangedEvent event) {
		updateStatusPanel();
	}

	private void fireSafePropertyChange(final int property) {
		if(! composite.isDisposed()) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {		
					firePropertyChange(property);
				}
			});
		}
	}
	
	public void selectSubscriber(TeamSubscriber subscriber) {
		activateSubscriber(subscriber);
	}
	
	/**
	 * Refreshes the resources from the specified subscriber. The working set or filters applied
	 * to the sync view do not affect the sync.
	 */
	public void refreshWithRemote(TeamSubscriber subscriber, IResource[] resources) {
		QualifiedName id = subscriber.getId();
		if(subscriberInputs.containsKey(id)) {
			if(input != null && ! input.getSubscriber().getId().equals(id)) {
				initializeSubscriberInput((SubscriberInput)subscriberInputs.get(id));
			}
			RefreshAction.run(this, resources, subscriber);
		}		
	}

	/**
	 * Refreshes the resources in the current input for the given subscriber.
	 */	
	public void refreshWithRemote(TeamSubscriber subscriber) {
		QualifiedName id = subscriber.getId();
		if(input != null && subscriberInputs.containsKey(id)) {
			if(! input.getSubscriber().getId().equals(id)) {
				initializeSubscriberInput((SubscriberInput)subscriberInputs.get(id));
			}
			RefreshAction.run(this, input.workingSetRoots(), subscriber);
		}		
	}
	
	/**
	 * Refreshes the resources in the current input for the given subscriber.
	 */	
	public void refreshWithRemote() {
		if(input != null) {
			RefreshAction.run(this, input.workingSetRoots(), input.getSubscriber());
		}
	}
	
	public int getCurrentViewType() {
		return currentViewType;
	}

	public void selectAll() {
		if (getViewerType() == TABLE_VIEW) {
			TableViewer table = (TableViewer)getViewer();
			table.getTable().selectAll();
		} else {
			// Select All in a tree doesn't really work well
		}
	}
	
	private IPreferenceStore getStore() {
		return TeamUIPlugin.getPlugin().getPreferenceStore();
	}
	
	public SyncSetContentProvider getContentProvider() {
		return (SyncSetContentProvider)getViewer().getContentProvider();
	}

	public void setWorkingSet(IWorkingSet workingSet) {
		actions.setWorkingSet(workingSet);
	}

	public void workingSetChanged(IWorkingSet set) {
		input.setWorkingSet(set);
		updateTooltip();
	}
	/**
	 * Updates the filter applied to the active subscriber input and ensures that selection and expansions 
	 * is preserved when the filtered contents are shown. 
	 * @param filter
	 */
	public void updateInputFilter(int[] directions, int[] changeTypes) {			
		try {
			if(viewer instanceof INavigableControl) {
				((INavigableControl)viewer).preserveState(1);
			}
			input.setFilter(
							new AndSyncInfoFilter(
								new SyncInfoFilter[] {
									new SyncInfoDirectionFilter(directions), 
									new SyncInfoChangeTypeFilter(changeTypes),
									new PseudoConflictFilter()
								}), new NullProgressMonitor());
			if(viewer instanceof INavigableControl) {
				((INavigableControl)viewer).restoreState(1);
			}
		} catch (TeamException e) {
			Utils.handleError(getSite().getShell(), e, Policy.bind("SynchronizeView.16"), e.getMessage()); //$NON-NLS-1$
		}
	}
	
	/**
	 * Allows clients to change the active sync mode.
	 */
	public void setMode(int mode) {
		actions.setMode(mode);
	}
}