package org.eclipse.team.ui.synchronize;

import java.util.Iterator;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
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
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.internal.ui.IPreferenceIds;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.internal.ui.jobs.JobBusyCursor;
import org.eclipse.team.internal.ui.synchronize.TeamSubscriberParticipantLabelProvider;
import org.eclipse.team.internal.ui.synchronize.actions.ComparisonCriteriaActionGroup;
import org.eclipse.team.internal.ui.synchronize.actions.INavigableControl;
import org.eclipse.team.internal.ui.synchronize.actions.NavigateAction;
import org.eclipse.team.internal.ui.synchronize.actions.OpenWithActionGroup;
import org.eclipse.team.internal.ui.synchronize.actions.RefactorActionGroup;
import org.eclipse.team.internal.ui.synchronize.actions.RefreshAction;
import org.eclipse.team.internal.ui.synchronize.actions.StatusLineContributionGroup;
import org.eclipse.team.internal.ui.synchronize.actions.SyncViewerShowPreferencesAction;
import org.eclipse.team.internal.ui.synchronize.actions.ToggleViewLayoutAction;
import org.eclipse.team.internal.ui.synchronize.actions.WorkingSetFilterActionGroup;
import org.eclipse.team.internal.ui.synchronize.sets.SubscriberInput;
import org.eclipse.team.internal.ui.synchronize.views.SyncSetContentProvider;
import org.eclipse.team.internal.ui.synchronize.views.SyncSetTableContentProvider;
import org.eclipse.team.internal.ui.synchronize.views.SyncTableViewer;
import org.eclipse.team.internal.ui.synchronize.views.SyncTreeViewer;
import org.eclipse.team.internal.ui.synchronize.views.SyncViewerSorter;
import org.eclipse.team.internal.ui.synchronize.views.SyncViewerTableSorter;
import org.eclipse.team.ui.synchronize.actions.SubscriberAction;
import org.eclipse.team.ui.synchronize.actions.SyncInfoFilter;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.IPageBookViewPage;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.views.navigator.ResourceSorter;

public class TeamSubscriberParticipantPage implements IPageBookViewPage, IPropertyChangeListener {
	// The viewer that is shown in the view. Currently this can be either a table or tree viewer.
	private StructuredViewer viewer;
	
	// Parent composite of this view. It is remembered so that we can dispose of its children when 
	// the viewer type is switched.
	private Composite composite = null;
	private boolean settingWorkingSet = false;
	
	// Viewer type constants
	private int layout;
	
	// Remembering the current input and the previous.
	private SubscriberInput input = null;
	
	// A set of common actions. They are hooked to the active SubscriberInput and must 
	// be reset when the input changes.
	// private SyncViewerActions actions;
	
	private JobBusyCursor busyCursor;
	private ISynchronizeView view;
	private TeamSubscriberParticipant participant;
	private IPageSite site;
	
	public final static int[] INCOMING_MODE_FILTER = new int[] {SyncInfo.CONFLICTING, SyncInfo.INCOMING};
	public final static int[] OUTGOING_MODE_FILTER = new int[] {SyncInfo.CONFLICTING, SyncInfo.OUTGOING};
	public final static int[] BOTH_MODE_FILTER = new int[] {SyncInfo.CONFLICTING, SyncInfo.INCOMING, SyncInfo.OUTGOING};
	public final static int[] CONFLICTING_MODE_FILTER = new int[] {SyncInfo.CONFLICTING};
	
	// Actions
	private OpenWithActionGroup openWithActions;
	private NavigateAction gotoNext;
	private NavigateAction gotoPrevious;
	private Action toggleLayoutTree;
	private Action toggleLayoutTable;
	private RefactorActionGroup refactorActions;
	private SyncViewerShowPreferencesAction showPreferences;
	private RefreshAction refreshAction;
	private ComparisonCriteriaActionGroup comparisonCriteria;
	private Action collapseAll;
	private Action expandAll;
	private WorkingSetFilterActionGroup workingSetGroup;
	private StatusLineContributionGroup statusLine;
	
	/**
	 * Constructs a new SynchronizeView.
	 */
	public TeamSubscriberParticipantPage(TeamSubscriberParticipant page, ISynchronizeView view, SubscriberInput input) {
		this.participant = page;
		this.view = view;
		this.input = input;
		layout = getStore().getInt(IPreferenceIds.SYNCVIEW_VIEW_TYPE);
		if (layout != TeamSubscriberParticipant.TREE_LAYOUT) {
			layout = TeamSubscriberParticipant.TABLE_LAYOUT;
		}		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		composite = new Composite(parent, SWT.NONE); 
		GridLayout gridLayout= new GridLayout();
		gridLayout.makeColumnsEqualWidth= false;
		gridLayout.marginWidth= 0;
		gridLayout.marginHeight = 0;
		gridLayout.verticalSpacing = 0;
		composite.setLayout(gridLayout);
		
		// Create the busy cursor with no control to start with (createViewer will set it)
		busyCursor = new JobBusyCursor(null /* control */, SubscriberAction.SUBSCRIBER_JOB_TYPE);
		createViewer(composite);
				
		// create actions
		openWithActions = new OpenWithActionGroup(view);
		refactorActions = new RefactorActionGroup(view);
		gotoNext = new NavigateAction(view, this, INavigableControl.NEXT);		
		gotoPrevious = new NavigateAction(view, this, INavigableControl.PREVIOUS);
		comparisonCriteria = new ComparisonCriteriaActionGroup(input);
		
		toggleLayoutTable = new ToggleViewLayoutAction(participant, TeamSubscriberParticipant.TABLE_LAYOUT);
		toggleLayoutTree = new ToggleViewLayoutAction(participant, TeamSubscriberParticipant.TREE_LAYOUT);
		workingSetGroup = new WorkingSetFilterActionGroup(getSite().getShell(), this, view, participant);
		
		showPreferences = new SyncViewerShowPreferencesAction(view.getSite().getShell());
		
		refreshAction = new RefreshAction(getSite().getPage(), input, true /* refresh all */);
		statusLine = new StatusLineContributionGroup(this.input);
		
		collapseAll = new Action() {
			public void run() {
				collapseAll();
			}
		};
		Utils.initAction(collapseAll, "action.collapseAll."); //$NON-NLS-1$
		
		expandAll = new Action() {
			public void run() {
				Viewer viewer = getViewer();
				ISelection selection = viewer.getSelection();
				if(viewer instanceof AbstractTreeViewer && ! selection.isEmpty()) {
					Iterator elements = ((IStructuredSelection)selection).iterator();
					while (elements.hasNext()) {
						Object next = elements.next();
						((AbstractTreeViewer) viewer).expandToLevel(next, AbstractTreeViewer.ALL_LEVELS);
					}
				}
			}
		};
		Utils.initAction(expandAll, "action.expandAll."); //$NON-NLS-1$
				
		participant.addPropertyChangeListener(this);
		updateMode(participant.getMode());		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IViewPart#init(org.eclipse.ui.IViewSite, org.eclipse.ui.IMemento)
	 */
	public void init(IPageSite site) throws PartInitException {
		this.site = site;		
	}
	
	private void hookContextMenu() {
		if(getViewer() != null) {
			MenuManager menuMgr = new MenuManager(participant.getId().getQualifier()); //$NON-NLS-1$
			menuMgr.setRemoveAllWhenShown(true);
			menuMgr.addMenuListener(new IMenuListener() {
				public void menuAboutToShow(IMenuManager manager) {
					setContextMenu(manager);
				}
			});
			Menu menu = menuMgr.createContextMenu(viewer.getControl());
			viewer.getControl().setMenu(menu);			
			getSite().registerContextMenu(participant.getId().getQualifier(), menuMgr, viewer);
		}
	}	

	protected void setContextMenu(IMenuManager manager) {
		openWithActions.fillContextMenu(manager);
		refactorActions.fillContextMenu(manager);
		manager.add(new Separator());
		manager.add(expandAll);
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}
	
	/**
	 * Toggles between label/tree/table viewers. 
	 */
	public void switchViewerType(int viewerType) {
		if(viewer == null || viewerType != layout) {
			if (composite == null || composite.isDisposed()) return;
			IStructuredSelection oldSelection = null;
			if(viewer != null) {
				oldSelection = (IStructuredSelection)viewer.getSelection();
			}
			layout = viewerType;
			getStore().setValue(IPreferenceIds.SYNCVIEW_VIEW_TYPE, layout);
			disposeChildren(composite);
			createViewer(composite);
			composite.layout();
			if(oldSelection == null || oldSelection.size() == 0) {
				//gotoDifference(INavigableControl.NEXT);
			} else {
				viewer.setSelection(oldSelection, true);
			}
		}
	}
	
	/**
	 * Adds the listeners to the viewer.
	 */
	protected void initializeListeners() {
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				;
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
	
	protected void createViewer(Composite parent) {				
		//tbMgr.createControl(parent);
		switch(layout) {
			case TeamSubscriberParticipant.TREE_LAYOUT:
				createTreeViewerPartControl(parent); 
				break;
			case TeamSubscriberParticipant.TABLE_LAYOUT:
				createTableViewerPartControl(parent); 
				break;
		}		
		viewer.setInput(input);
		viewer.getControl().setFocus();
		initializeListeners();
		hookContextMenu();
		getSite().setSelectionProvider(getViewer());
		busyCursor.setControl(viewer.getControl());
	}
	
	protected ILabelProvider getLabelProvider() {
		return new TeamSubscriberParticipantLabelProvider();		
	}
	
	protected void createTreeViewerPartControl(Composite parent) {
		GridData data = new GridData(GridData.FILL_BOTH);
		viewer = new SyncTreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setLabelProvider(getLabelProvider());
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
		viewer.setLabelProvider(getLabelProvider());		
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
		col.setText("Resource");
		col.addSelectionListener(headerListener);
		layout.addColumnData(new ColumnWeightData(30, true));
		
		// tags
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText("In Folder");
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
	}
	
	protected void handleOpen(OpenEvent event) {
		openWithActions.openInCompareEditor();
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
		
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#setFocus()
	 */
	public void setFocus() {
		if (viewer == null) return;
		viewer.getControl().setFocus();
	}
	
	public StructuredViewer getViewer() {
		return viewer;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	public void dispose() {
		busyCursor.dispose();
		statusLine.dispose();
	}
	
	/*
	 * Return the current input for the view.
	 */
	public SubscriberInput getInput() {
		return input;
	}
	
	public void collapseAll() {
		if (viewer == null || !(viewer instanceof AbstractTreeViewer)) return;
		viewer.getControl().setRedraw(false);		
		((AbstractTreeViewer)viewer).collapseToLevel(viewer.getInput(), TreeViewer.ALL_LEVELS);
		viewer.getControl().setRedraw(true);
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
		return null;
	}
	
	/**
	 * Updates the message shown in the status line.
	 *
	 * @param selection the current selection
	 */
	protected void updateStatusLine(IStructuredSelection selection) {
		String msg = getStatusLineMessage(selection);
		//getSite().getActionBars().getStatusLineManager().setMessage(msg);
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
	
	private IResource getResource(Object object) {
		return SyncSetContentProvider.getResource(object);
	}

	public void selectAll() {
		Viewer viewer = getViewer();
		if (viewer instanceof TableViewer) {
			TableViewer table = (TableViewer)viewer;
			table.getTable().selectAll();
		} else {
			// Select All in a tree doesn't really work well
		}
	}
	
	private IPreferenceStore getStore() {
		return TeamUIPlugin.getPlugin().getPreferenceStore();
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
			manager.add(refreshAction);
			manager.add(comparisonCriteria);
			manager.add(new Separator());		
			manager.add(gotoNext);
			manager.add(gotoPrevious);
			manager.add(collapseAll);
			manager.add(new Separator());

			// view menu
			updateViewMenu(actionBars);
			
			// status line
			statusLine.fillActionBars(actionBars);
		}		
	}

	protected void updateViewMenu(IActionBars actionBars) {
		IMenuManager menu = actionBars.getMenuManager();
		menu.removeAll();
		MenuManager layoutMenu = new MenuManager(Policy.bind("action.layout.label")); //$NON-NLS-1$		
		layoutMenu.add(toggleLayoutTable);
		layoutMenu.add(toggleLayoutTree);
		workingSetGroup.fillActionBars(actionBars);
		menu.add(layoutMenu);
		menu.add(new Separator());
		menu.add(showPreferences);
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
		// Layout change
		if(event.getProperty().equals(TeamSubscriberParticipant.P_SYNCVIEWPAGE_LAYOUT)) {
			switchViewerType(((Integer)event.getNewValue()).intValue());
		// Direction mode change
		} else if(event.getProperty().equals(TeamSubscriberParticipant.P_SYNCVIEWPAGE_MODE)) {
			updateMode(((Integer)event.getNewValue()).intValue());
		// Working set changed via menu selection - notify participant and
		// do all the real work when we get the next workset changed event
		} else if(event.getProperty().equals(WorkingSetFilterActionGroup.CHANGE_WORKING_SET)) {
			if(settingWorkingSet) return;
			participant.setWorkingSet((IWorkingSet)event.getNewValue());
		// Working set changed programatically
		} else if(event.getProperty().equals(TeamSubscriberParticipant.P_SYNCVIEWPAGE_WORKINGSET)) {
			settingWorkingSet = true;
			Object newValue = event.getNewValue();
			if (newValue instanceof IWorkingSet) {	
				workingSetGroup.setWorkingSet((IWorkingSet)newValue);
			} else if (newValue == null) {
				workingSetGroup.setWorkingSet(null);
			}
			settingWorkingSet = false;
		}
	}

	private void updateMode(int mode) {
		int[] modeFilter = BOTH_MODE_FILTER;
		switch(mode) {
			case TeamSubscriberParticipant.INCOMING_MODE:
				modeFilter = INCOMING_MODE_FILTER; break;
			case TeamSubscriberParticipant.OUTGOING_MODE:
				modeFilter = OUTGOING_MODE_FILTER; break;
			case TeamSubscriberParticipant.BOTH_MODE:
				modeFilter = BOTH_MODE_FILTER; break;
			case TeamSubscriberParticipant.CONFLICTING_MODE:
				modeFilter = CONFLICTING_MODE_FILTER; break;
		}
		try {
			input.setFilter(
					new SyncInfoFilter.AndSyncInfoFilter(
						new SyncInfoFilter[] {
						   new SyncInfoFilter.SyncInfoDirectionFilter(modeFilter), 
						   new SyncInfoFilter.SyncInfoChangeTypeFilter(new int[] {SyncInfo.ADDITION, SyncInfo.DELETION, SyncInfo.CHANGE}),
						   new SyncInfoFilter.PseudoConflictFilter()
			}), new NullProgressMonitor());
		} catch (TeamException e) {
			Utils.handleError(getSite().getShell(), e, Policy.bind("SynchronizeView.16"), e.getMessage()); //$NON-NLS-1$
		}
	}
	
	/**
	 * @return Returns the participant.
	 */
	public TeamSubscriberParticipant getParticipant() {
		return participant;
	}
}