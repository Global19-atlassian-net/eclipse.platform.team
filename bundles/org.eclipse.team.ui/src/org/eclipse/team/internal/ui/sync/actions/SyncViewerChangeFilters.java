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
package org.eclipse.team.internal.ui.sync.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.team.core.sync.SyncInfo;
import org.eclipse.team.internal.ui.sync.views.ChangeFiltersContentProvider;
import org.eclipse.team.internal.ui.sync.views.FilterSyncViewerAction;
import org.eclipse.team.internal.ui.sync.views.SyncViewer;
import org.eclipse.ui.IMemento;

/**
 * This class provides a set of actions that support sync set filtering by 
 * change type.
 */
public class SyncViewerChangeFilters extends SyncViewerActionGroup {
	
	private static final String MEMENTO_KEY_PREFIX = "SyncViewerChangeFilters";
	
	// array of actions for filtering bt change type (additions, deletions and changes)
	private ChangeFilterAction[] actions;
	
	private FilterSyncViewerAction filterAction;
	
	/**
	 * Action for filtering by change type.
	 */
	class ChangeFilterAction extends Action {
		// The SyncInfo change constant associated with the change type
		private int changeFilter;
		public ChangeFilterAction(String title, int changeFilter) {
			super(title);
			this.changeFilter = changeFilter;
		}
		public void run() {
			SyncViewerChangeFilters.this.updateSyncView();
		}
		/**
		 * @return
		 */
		public int getChangeFilter() {
			return changeFilter;
		}
	}
	
	protected SyncViewerChangeFilters(SyncViewer viewer) {
		super(viewer);
		initializeActions();
	}
	
	/**
	 * 
	 */
	private void initializeActions() {
		ChangeFilterAction additions = new ChangeFilterAction("Show Additions", SyncInfo.ADDITION);
		additions.setChecked(true);
		ChangeFilterAction deletions = new ChangeFilterAction("Show Deletions", SyncInfo.DELETION);
		deletions.setChecked(true);
		ChangeFilterAction changes = new ChangeFilterAction("Show Changes", SyncInfo.CHANGE);
		changes.setChecked(true);
		actions = new ChangeFilterAction[] { additions, deletions, changes };
		
		filterAction = new FilterSyncViewerAction(getSyncView(), this);
	}

	/**
	 * Forward the selected change filters to the sync view.
	 * @param changeType
	 */
	protected void updateSyncView() {
		getSyncView().updateFilters();
	}

	/**
	 * Get the current set of active change filters
	 */
	public int[] getChangeFilters() {
		// Determine how many change types are checked
		int count = 0;
		for (int i = 0; i < actions.length; i++) {
			ChangeFilterAction action = actions[i];
			if (action.isChecked()) {
				count++;
			}
		}
		// Create an array of checked change types
		int[] changeFilters = new int[count];
		count = 0;
		for (int i = 0; i < actions.length; i++) {
			ChangeFilterAction action = actions[i];
			if (action.isChecked()) {
				changeFilters[count++] = action.getChangeFilter();
			}
		}
		return changeFilters;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.actions.ActionGroup#fillContextMenu(org.eclipse.jface.action.IMenuManager)
	 */
	public void fillContextMenu(IMenuManager menu) {
		super.fillContextMenu(menu);
		menu.add(filterAction);
	}

	/**
	 * Return all the actions for filtering by change type
	 * @return
	 */
	public Action[] getFilters() {
		return actions;
	}

	/**
	 * Return all the active change types
	 * @return
	 */
	public Action[] getActiveFilters() {
		List result = new ArrayList();
		for (int i = 0; i < actions.length; i++) {
			Action action = actions[i];
			if (action.isChecked()) {
				result.add(action);
			}
		}
		return (Action[]) result.toArray(new Action[result.size()]);
	}

	/**
	 * Change the active change types to those in the provided array
	 * @param results
	 */
	public void setActiveFilters(Object[] results) {
		for (int i = 0; i < actions.length; i++) {
			Action action = actions[i];
			boolean active = false;
			for (int j = 0; j < results.length; j++) {
				Object object = results[j];
				if (object == action) {
					active = true;
					break;
				}
			}
			action.setChecked(active);
		}
	}

	/**
	 * 
	 */
	public ILabelProvider getLabelProvider() {
		return new LabelProvider() {
			public String getText(Object element) {
				if (element instanceof Action) {
					return ((Action)element).getText();
				}
				return super.getText(element);
			}
		};
	}

	/**
	 * 
	 */
	public ChangeFiltersContentProvider getContentProvider() {
		return new ChangeFiltersContentProvider(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ccvs.syncviews.actions.SyncViewerActionGroup#restore(org.eclipse.ui.IMemento)
	 */
	public void restore(IMemento memento) {
		super.restore(memento);
		// Uncheck everything
		setActiveFilters(new ChangeFilterAction[0]);
		// Check those in the memento
		Integer i;
		int count = 0;
		do {
			i = memento.getInteger(MEMENTO_KEY_PREFIX + "." + count);
			if (i != null) {
				count++;
				actions[i.intValue()].setChecked(true);
			}
		} while (i != null);
		
		// Make sure at least one is checked
		if (count == 0) {
			setActiveFilters(actions);
		}
	}

	/* (non-Javadoc)
	 * 
	 * Save each active change filter as follows:
	 * 
	 *    MEMENTO_KEY_PREFIX.0 = ?
	 *    MEMENTO_KEY_PREFIX.1 = ?
	 * 
	 * @see org.eclipse.team.ccvs.syncviews.actions.SyncViewerActionGroup#save(org.eclipse.ui.IMemento)
	 */
	public void save(IMemento memento) {
		super.save(memento);
		int count = 0;
		for (int i = 0; i < actions.length; i++) {
			Action action = actions[i];
			if (action.isChecked()) {
				memento.putInteger(MEMENTO_KEY_PREFIX + "." + count++, i);
			}
		}
	}
}
