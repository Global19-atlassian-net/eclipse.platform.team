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

import org.eclipse.compare.internal.INavigatable;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.internal.ui.synchronize.HierarchicalModelManager;
import org.eclipse.team.internal.ui.synchronize.SyncInfoModelElement;
import org.eclipse.team.internal.ui.synchronize.actions.ExpandAllAction;
import org.eclipse.team.internal.ui.synchronize.actions.NavigateAction;
import org.eclipse.ui.*;
import org.eclipse.ui.internal.dialogs.ContainerCheckedTreeViewer;

/**
 * A <code>TreeViewerAdvisor</code> that works with TreeViewers. Two default
 * tree viewers are provided that support navigation: <code>NavigableTreeViewer</code>
 * and <code>NavigableCheckboxTreeViewer</code>. 
 * <p>
 * Note that this advisor can be used with any tree viewer. By default it provides an
 * expand all action, double click behavior on containers, and navigation support for
 * tree viewers.
 * </p><p>
 * By default this advisor supports hierarchical models and honour the compressed
 * folder Team preference for showing the sync set as compressed folders. Subclasses
 * can provide their own presentation models.
 * <p>
 * @since 3.0
 */
public class TreeViewerAdvisor extends StructuredViewerAdvisor {
	
	private ExpandAllAction expandAllAction;
	private Action collapseAll;
	private NavigateAction gotoNext;
	private NavigateAction gotoPrevious;
	
	class NavigationActionGroup extends SynchronizePageActionGroup {
		public void initialize(ISynchronizePageConfiguration configuration) {
			super.initialize(configuration);
			final StructuredViewer viewer = getViewer();
			if (viewer instanceof AbstractTreeViewer) {
				
				expandAllAction = new ExpandAllAction((AbstractTreeViewer) viewer);
				Utils.initAction(expandAllAction, "action.expandAll."); //$NON-NLS-1$
				
				collapseAll = new Action() {
					public void run() {
						if (viewer == null || viewer.getControl().isDisposed() || !(viewer instanceof AbstractTreeViewer)) return;
						viewer.getControl().setRedraw(false);		
						((AbstractTreeViewer)viewer).collapseToLevel(viewer.getInput(), TreeViewer.ALL_LEVELS);
						viewer.getControl().setRedraw(true);
					}
				};
				Utils.initAction(collapseAll, "action.collapseAll."); //$NON-NLS-1$
				
				INavigatable nav = new INavigatable() {
					public boolean gotoDifference(boolean next) {
						return TreeViewerAdvisor.this.navigate(next);
					}
				};
				ISynchronizeParticipant participant = configuration.getParticipant();
				ISynchronizePageSite site = configuration.getSite();
				gotoNext = new NavigateAction(site, participant.getName(), nav, true /*next*/);		
				gotoPrevious = new NavigateAction(site, participant.getName(), nav, false /*previous*/);
			}
		}
		public void fillContextMenu(IMenuManager manager) {
			appendToGroup(manager, ISynchronizePageConfiguration.NAVIGATE_GROUP, expandAllAction);
		}
		public void fillActionBars(IActionBars actionBars) {
			IToolBarManager manager = actionBars.getToolBarManager();
			appendToGroup(manager, ISynchronizePageConfiguration.NAVIGATE_GROUP, gotoNext);
			appendToGroup(manager, ISynchronizePageConfiguration.NAVIGATE_GROUP, gotoPrevious);
			appendToGroup(manager, ISynchronizePageConfiguration.NAVIGATE_GROUP, collapseAll);
		}
	}
	
 	/**
	 * Interface used to implement navigation for tree viewers. This interface is used by
	 * {@link TreeViewerAdvisor#navigate(TreeViewer, boolean, boolean, boolean) to open 
	 * selections and navigate.
	 */
	public interface ITreeViewerAccessor {
		public void createChildren(TreeItem item);
		public void openSelection();
	}
	
	/**
	 * A navigable checkboxec tree viewer that will work with the <code>navigate</code> method of
	 * this advisor.
	 */
	public static class NavigableCheckboxTreeViewer extends ContainerCheckedTreeViewer implements ITreeViewerAccessor {
		public NavigableCheckboxTreeViewer(Composite parent, int style) {
			super(parent, style);
		}

		public void createChildren(TreeItem item) {	
			super.createChildren(item);
		}

		public void openSelection() {
			fireOpen(new OpenEvent(this, getSelection()));
		}
	}
	
	/**
	 * A navigable tree viewer that will work with the <code>navigate</code> method of
	 * this advisor.
	 */
	public static class NavigableTreeViewer extends TreeViewer implements ITreeViewerAccessor {
		public NavigableTreeViewer(Composite parent, int style) {
			super(parent, style);
		}

		public void createChildren(TreeItem item) {	
			super.createChildren(item);
		}

		public void openSelection() {
			fireOpen(new OpenEvent(this, getSelection()));
		}
	}

	/**
	 * Create an advisor that will allow viewer contributions with the given <code>targetID</code>. This
	 * advisor will provide a presentation model based on the given sync info set. Note that it's important
	 * to call {@link #dispose()} when finished with an advisor.
	 * 
	 * @param targetID the targetID defined in the viewer contributions in a plugin.xml file.
	 * @param site the workbench site with which to register the menuId. Can be <code>null</code> in which
	 * case a site will be found using the default workbench page.
	 * @param set the set of <code>SyncInfo</code> objects that are to be shown to the user.
	 */
	public TreeViewerAdvisor(ISynchronizePageConfiguration configuration) {
		super(configuration);
		configuration.addActionContribution(new NavigationActionGroup());
		// Create the model manager. It hooks itself up
		new HierarchicalModelManager(this, configuration);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.StructuredViewerAdvisor#navigate(boolean)
	 */
	public boolean navigate(boolean next) {
		return TreeViewerAdvisor.navigate((TreeViewer)getViewer(), next, true, false);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.StructuredViewerAdvisor#initializeViewer(org.eclipse.jface.viewers.StructuredViewer)
	 */
	public boolean validateViewer(StructuredViewer viewer) {
		return viewer instanceof AbstractTreeViewer;
	}
	
	/**
	 * Handles a double-click event from the viewer. Expands or collapses a folder when double-clicked.
	 * 
	 * @param viewer the viewer
	 * @param event the double-click event
	 */
	protected boolean handleDoubleClick(StructuredViewer viewer, DoubleClickEvent event) {
		if (super.handleDoubleClick(viewer, event)) return true;
		IStructuredSelection selection = (IStructuredSelection) event.getSelection();
		Object element = selection.getFirstElement();
		AbstractTreeViewer treeViewer = (AbstractTreeViewer) getViewer();
		if(element != null) {
			if (treeViewer.getExpandedState(element)) {
				treeViewer.collapseToLevel(element, AbstractTreeViewer.ALL_LEVELS);
			} else {
				TreeViewerAdvisor.navigate((TreeViewer)getViewer(), true /* next */, false /* no-open */, true /* only-expand */);
			}
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.StructuredViewerAdvisor#initializeListeners(org.eclipse.jface.viewers.StructuredViewer)
	 */
	protected void initializeListeners(final StructuredViewer viewer) {
		super.initializeListeners(viewer);
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateStatusLine((IStructuredSelection) event.getSelection());
			}
		});
	}
	
	private void updateStatusLine(IStructuredSelection selection) {
		IWorkbenchSite ws = getConfiguration().getSite().getWorkbenchSite();
		if (ws != null && ws instanceof IViewSite) {
			String msg = getStatusLineMessage(selection);
			((IViewSite)ws).getActionBars().getStatusLineManager().setMessage(msg);
		}
	}
	
	private String getStatusLineMessage(IStructuredSelection selection) {
		if (selection.size() == 1) {
			Object first = selection.getFirstElement();
			if (first instanceof SyncInfoModelElement) {
				SyncInfoModelElement node = (SyncInfoModelElement) first;
				IResource resource = node.getResource();
				if (resource == null) {
					return node.getName();
				} else {
					return resource.getFullPath().makeRelative().toString();
				}
			}
		}
		if (selection.size() > 1) {
			return selection.size() + Policy.bind("SynchronizeView.13"); //$NON-NLS-1$
		}
		return ""; //$NON-NLS-1$
	}
	
	private static TreeItem findNextPrev(TreeViewer viewer, TreeItem item, boolean next) {
		if (item == null || !(viewer instanceof ITreeViewerAccessor))
			return null;
		TreeItem children[] = null;
		ITreeViewerAccessor treeAccessor = (ITreeViewerAccessor) viewer;
		if (!next) {
			TreeItem parent = item.getParentItem();
			if (parent != null)
				children = parent.getItems();
			else
				children = item.getParent().getItems();
			if (children != null && children.length > 0) {
				// goto previous child
				int index = 0;
				for (; index < children.length; index++)
					if (children[index] == item)
						break;
				if (index > 0) {
					item = children[index - 1];
					while (true) {
						treeAccessor.createChildren(item);
						int n = item.getItemCount();
						if (n <= 0)
							break;
						item.setExpanded(true);
						item = item.getItems()[n - 1];
					}
					// previous
					return item;
				}
			}
			// go up
			return parent;
		} else {
			item.setExpanded(true);
			treeAccessor.createChildren(item);
			if (item.getItemCount() > 0) {
				// has children: go down
				children = item.getItems();
				return children[0];
			}
			while (item != null) {
				children = null;
				TreeItem parent = item.getParentItem();
				if (parent != null)
					children = parent.getItems();
				else
					children = item.getParent().getItems();
				if (children != null && children.length > 0) {
					// goto next child
					int index = 0;
					for (; index < children.length; index++)
						if (children[index] == item)
							break;
					if (index < children.length - 1) {
						// next
						return children[index + 1];
					}
				}
				// go up
				item = parent;
			}
		}
		return item;
	}

	private static void setSelection(TreeViewer viewer, TreeItem ti, boolean fireOpen, boolean expandOnly) {
		if (ti != null) {
			Object data= ti.getData();
			if (data != null) {
				// Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
				ISelection selection = new StructuredSelection(data);
				if (expandOnly) {
					viewer.expandToLevel(data, 0);
				} else {
					viewer.setSelection(selection, true);
					ISelection currentSelection = viewer.getSelection();
					if (fireOpen && currentSelection != null && selection.equals(currentSelection)) {
						if (viewer instanceof ITreeViewerAccessor) {
							((ITreeViewerAccessor) viewer).openSelection();
						}
					}
				}
			}
		}
	}
	
	/**
	 * Selects the next (or previous) node of the current selection.
	 * If there is no current selection the first (last) node in the tree is selected.
	 * Wraps around at end or beginning.
	 * Clients may not override. 
	 *
	 * @param next if <code>true</code> the next node is selected, otherwise the previous node
	 * @return <code>true</code> if at end (or beginning)
	 */
	public static boolean navigate(TreeViewer viewer, boolean next, boolean fireOpen, boolean expandOnly) {
		Tree tree = viewer.getTree();
		if (tree == null)
			return false;
		TreeItem item = null;
		TreeItem children[] = tree.getSelection();
		if (children != null && children.length > 0)
			item = children[0];
		if (item == null) {
			children = tree.getItems();
			if (children != null && children.length > 0) {
				item = children[0];
				if (item != null && item.getItemCount() <= 0) {
					setSelection(viewer, item, fireOpen, expandOnly); // Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
					return false;
				}
			}
		}
		while (true) {
			item = findNextPrev(viewer, item, next);
			if (item == null)
				break;
			if (item.getItemCount() <= 0)
				break;
		}
		if (item != null) {
			setSelection(viewer, item, fireOpen, expandOnly); // Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
			return false;
		}
		return true;
	}
}