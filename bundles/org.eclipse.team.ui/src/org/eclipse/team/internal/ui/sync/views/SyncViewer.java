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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.ITeamResourceChangeListener;
import org.eclipse.team.core.sync.SyncTreeSubscriber;
import org.eclipse.team.core.sync.TeamDelta;
import org.eclipse.team.core.sync.TeamProvider;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.sync.actions.SyncViewerActions;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;

public class SyncViewer extends ViewPart implements ITeamResourceChangeListener {
	/*
	 * The viewer thst is shown in the view. Currently this can be
	 * either a table or tree viewer.
	 */
	private StructuredViewer viewer;
	
	/*
	 * Parent composite of this view. It is remembered so that we can
	 * dispose of its children when the viewer type is switched.
	 */
	private Composite composite = null;
	private IMemento memento;

	/*
	 * viewer type constants
	 */ 
	public static final int TREE_VIEW = 0;
	public static final int TABLE_VIEW = 1;
	
	/*
	 * Array of SubscriberInput objects. There is one of these for each subscriber
	 * registered with the sync view. 
	 */
	private Map subscriberInputs = new HashMap(1);
	private SubscriberInput input = null;
	
	/*
	 * A set of common actions. They are hooked to the active SubscriberInput and
	 * must be reset when the input changes.
	 */
	private SyncViewerActions actions;
			
	public SyncViewer() {
	}

	/**
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent) {
		TeamProvider.addListener(this);
		initializeActions();
		createViewer(parent, TABLE_VIEW);
		contributeToActionBars();
		this.composite = parent;
		
		SyncTreeSubscriber[] subscribers = TeamProvider.getSubscribers();
		for (int i = 0; i < subscribers.length; i++) {
			SyncTreeSubscriber subscriber = subscribers[i];
			addSubscriber(subscriber);
		}
	}

	public void switchViewerType(int viewerType) {
		if (composite == null || composite.isDisposed()) return;
		disposeChildren(composite);
		createViewer(composite, viewerType);
		composite.layout();
	}
	
	private void createViewer(Composite parent, int viewerType) {
		switch(viewerType) {
			case TREE_VIEW:
				createTreeViewerPartControl(parent); 
				break;
			case TABLE_VIEW:
				createTableViewerPartControl(parent); 
				break;
		}
		hookContextMenu();
		initializeListeners();
		
		if(input != null) {
			viewer.setInput(input.getSyncSet());
		}
	}

	protected ILabelProvider getLabelProvider() {
		return new DecoratingLabelProvider(
					new SyncViewerLabelProvider(),
					WorkbenchPlugin
						.getDefault()
						.getWorkbench()
						.getDecoratorManager()
						.getLabelDecorator());
	}

	private void createTreeViewerPartControl(Composite parent) {
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setContentProvider(new SyncSetTreeContentProvider());
		viewer.setLabelProvider(getLabelProvider());
		viewer.setSorter(new SyncViewerSorter());
	}
	
	private void createTableViewerPartControl(Composite parent) {
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
		TableViewer tableViewer = new TableViewer(table);
		
		// Create the table columns
		createColumns(table, layout, tableViewer);
		
		// Set the table contents
		viewer = tableViewer;
		viewer.setContentProvider(new SyncSetTableContentProvider());
		viewer.setLabelProvider(new SyncViewerLabelProvider());
		viewer.setSorter(new SyncViewerSorter());
	}
	
	/**
	 * Creates the columns for the sync viewer table.
	 */
	private void createColumns(Table table, TableLayout layout, TableViewer viewer) {
		//SelectionListener headerListener = getColumnListener(viewer);
		// revision
		TableColumn col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText("Resource"); //$NON-NLS-1$
		//col.addSelectionListener(headerListener);
		layout.addColumnData(new ColumnWeightData(30, true));
	
		// tags
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText("In Folder"); //$NON-NLS-1$
		//col.addSelectionListener(headerListener);
		layout.addColumnData(new ColumnWeightData(50, true));
	}
	
	private void disposeChildren(Composite parent) {
		Control[] children = parent.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control control = children[i];
			control.dispose();
		}
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
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

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		actions.fillActionBars(bars);
	}

	/**
	 * Adds the listeners to the viewer.
	 * 
	 * @param viewer the viewer
	 * @since 2.0
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
//		viewer.getControl().addKeyListener(new KeyListener() {
//			public void keyPressed(KeyEvent event) {
//				handleKeyPressed(event);
//			}
//			public void keyReleased(KeyEvent event) {
//				handleKeyReleased(event);
//			}
//		});
	}
	
	/**
	 * Handles a selection changed event from the viewer.
	 * Updates the status line and the action bars, and links to editor (if option enabled).
	 * 
	 * @param event the selection event
	 * @since 2.0
	 */
	protected void handleSelectionChanged(SelectionChangedEvent event) {
		final IStructuredSelection sel = (IStructuredSelection) event.getSelection();
		updateStatusLine(sel);
		updateActionBars(sel);
// TODO: Need to decide if link to editor should be supported
//		dragDetected = false;
//		if (isLinkingEnabled()) {
//			getShell().getDisplay().asyncExec(new Runnable() {
//				public void run() {
//					if (dragDetected == false) {
//						// only synchronize with editor when the selection is not the result 
//						// of a drag. Fixes bug 22274.
//						linkToEditor(sel);
//					}
//				}
//			});
//		}
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
	
	private void initializeActions() {
		actions = new SyncViewerActions(this);
		actions.restore(memento);
	}
	
	private void showMessage(String message) {
		MessageDialog.openInformation(
			viewer.getControl().getShell(),
			"Sample View",
			message);
	}

	public void initializeSubscriberInput(final SubscriberInput input) {
		// Only initialize if we have a runnable context
		if (!hasRunnableContext()) return;
		this.input = input;
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				try {
					ActionContext context = new ActionContext(null);
					context.setInput(input);
					actions.setContext(context);
					input.prepareInput(monitor);
					Display.getDefault().asyncExec(new Runnable() {
								public void run() {
									viewer.setInput(input.getSyncSet());
								}
							});
				} catch (TeamException e) {
					throw new InvocationTargetException(e);
				}
			}
		});
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		// TODO: Broken on startup. Probably due to use of workbench progress
		if (viewer == null) return;
		viewer.getControl().setFocus();
	}

	public StructuredViewer getViewer() {
		return viewer;
	}
	
	private static void handle(Shell shell, Exception exception, String title, String message) {
		IStatus status = null;
		boolean log = false;
		boolean dialog = false;
		if (exception instanceof TeamException) {
			status = ((TeamException)exception).getStatus();
			log = false;
			dialog = true;
		} else if (exception instanceof InvocationTargetException) {
			Throwable t = ((InvocationTargetException)exception).getTargetException();
			if (t instanceof TeamException) {
				status = ((TeamException)t).getStatus();
				log = false;
				dialog = true;
			} else if (t instanceof CoreException) {
				status = ((CoreException)t).getStatus();
				log = true;
				dialog = true;
			} else if (t instanceof InterruptedException) {
				return;
			} else {
				status = new Status(IStatus.ERROR, TeamUIPlugin.ID, 1, "internal error", t); //$NON-NLS-1$
				log = true;
				dialog = true;
			}
		}
		if (status == null) return;
		if (!status.isOK()) {
			IStatus toShow = status;
			if (status.isMultiStatus()) {
				IStatus[] children = status.getChildren();
				if (children.length == 1) {
					toShow = children[0];
				}
			}
			if (title == null) {
				title = status.getMessage();
			}
			if (message == null) {
				message = status.getMessage();
			}
			if (dialog) {
				ErrorDialog.openError(shell, title, message, toShow);
			}
			if (log) {
				TeamUIPlugin.log(toShow);
			}
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	public void dispose() {
		super.dispose();
		TeamProvider.removeListener(this);
		for (Iterator it = subscriberInputs.values().iterator(); it.hasNext();) {
			SubscriberInput input = (SubscriberInput) it.next();
			input.dispose();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#setTitle(java.lang.String)
	 */
	public void setTitle(String title) {
		super.setTitle(title);
	}

	public void run(IRunnableWithProgress runnable) {
		try {
			getRunnableContext().run(true, true, runnable);
		} catch (InvocationTargetException e) {
			handle(getSite().getShell(), e, null, null);
		} catch (InterruptedException e) {
			// Nothing to be done
		}
	}
	
	/**
	 * Returns the runnableContext.
	 * @return IRunnableContext
	 */
	private IRunnableContext getRunnableContext() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	}
	
	private boolean hasRunnableContext() {
		return getRunnableContext() != null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IViewPart#init(org.eclipse.ui.IViewSite, org.eclipse.ui.IMemento)
	 */
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		this.memento = memento;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IViewPart#saveState(org.eclipse.ui.IMemento)
	 */
	public void saveState(IMemento memento) {
		super.saveState(memento);
		actions.save(memento);
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
				SyncTreeSubscriber s = delta.getSubscriber();
				addSubscriber(s);
			}
		}
	}

	private void addSubscriber(SyncTreeSubscriber s) {
		SubscriberInput si = new SubscriberInput(s);
		subscriberInputs.put(s.getId(), si);
		ActionContext context = new ActionContext(null);
		context.setInput(si);
		actions.addContext(context);
		
		// show the last registered subscriber in the view
		if (getInput() == null) {
			initializeSubscriberInput(si);
		}
	}
	
	public void collapseAll() {
		if (viewer == null || !(viewer instanceof AbstractTreeViewer)) return;
		viewer.getControl().setRedraw(false);		
		((AbstractTreeViewer)viewer).collapseToLevel(viewer.getInput(), TreeViewer.ALL_LEVELS);
		viewer.getControl().setRedraw(true);
	}
	
	public ISelection getSelection() {
		ISelection selection = getViewer().getSelection();
		if (! selection.isEmpty() && viewer instanceof AbstractTreeViewer) {
			// For a tree, selection should be deep and only include out-of-sync resources
			Object[] selected = ((IStructuredSelection)selection).toArray();
			Set result = new HashSet();
			for (int i = 0; i < selected.length; i++) {
				Object object = selected[i];
				if (object instanceof SyncResource) {
					SyncResource syncResource = (SyncResource) object;
					SyncResource[] infos = syncResource.getOutOfSyncDescendants();
					result.addAll(Arrays.asList(infos));
				}
			}
			selection = new StructuredSelection((Object[]) result.toArray(new Object[result.size()]));
		}
		return selection;
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
			actionContext.setSelection(selection);
			actions.updateActionBars();
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
				return "One item selected";
			} else {
				return resource.getFullPath().makeRelative().toString();
			}
		}
		if (selection.size() > 1) {
			return selection.size() + " items selected";
		}
		return ""; //$NON-NLS-1$
	}

	/**
	 * @param object
	 * @return
	 */
	private IResource getResource(Object object) {
		if (object instanceof IResource) {
			return (IResource)object;
		} else if (object instanceof IAdaptable) {
			return (IResource)((IAdaptable)object).getAdapter(IResource.class);
		}
		return null;
	}
}