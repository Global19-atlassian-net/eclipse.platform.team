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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.SyncInfoSet;
import org.eclipse.team.internal.ui.synchronize.SyncInfoModelElement;
import org.eclipse.team.ui.TeamOperation;

/**
 * A subscriber operation provides access to a {@link SyncInfoSet} containing
 * the selection from a {@link SynchronizeModelAction}.
 * When used in conjuntion with a {@link SynchronizeModelAction}, the selected
 * elements in the view will show busy indication if this 
 * operation is run as a job.
 * 
 * @see SyncInfoSet
 * @see SynchronizeModelAction
 * @since 3.0
 */
public abstract class SynchronizeModelOperation extends TeamOperation {
	
	private IDiffElement[] elements;
	
	/**
	 * Create an operation that will operate on the given diff elements
	 * that were obtained from a view populated by a 
	 * {@link org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider}.
	 * @param elements
	 */
	protected SynchronizeModelOperation(ISynchronizePageConfiguration configuration, IDiffElement[] elements) {
		super(configuration.getSite().getPart(), configuration.getRunnableContext());
		this.elements = elements;
	}

	/**
	 * Returns a sync info set that contains the {@link SyncInfo} for the
	 * elements of this operations.
	 */
	protected SyncInfoSet getSyncInfoSet() {
		return makeSyncInfoSetFromSelection(getSyncInfos());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.JobChangeAdapter#scheduled(org.eclipse.core.runtime.jobs.IJobChangeEvent)
	 */
	public void scheduled(IJobChangeEvent event) {
		super.scheduled(event);
		markBusy(elements, true);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.JobChangeAdapter#done(org.eclipse.core.runtime.jobs.IJobChangeEvent)
	 */
	public void done(IJobChangeEvent event) {
		markBusy(elements, false);
		super.done(event);
	}
	
	private void markBusy(IDiffElement[] elements, boolean isBusy) {
		for (int i = 0; i < elements.length; i++) {
			IDiffElement element = elements[i];
			if (element instanceof ISynchronizeModelElement) {
				((ISynchronizeModelElement)element).setPropertyToRoot(ISynchronizeModelElement.BUSY_PROPERTY, isBusy);
			}
		}
	}
	
	/*
	 * Return the selected SyncInfo for which this action is enabled.
	 * @return the selected SyncInfo for which this action is enabled.
	 */
	private SyncInfo[] getSyncInfos() {
		List filtered = new ArrayList();
		for (int i = 0; i < elements.length; i++) {
			IDiffElement e = elements[i];
			if (e instanceof SyncInfoModelElement) {
				filtered.add(((SyncInfoModelElement)e).getSyncInfo());
			}
		}
		return (SyncInfo[]) filtered.toArray(new SyncInfo[filtered.size()]);
	}
	
	/*
	 * Return a sync info set that contains the given sync info
	 */
	private SyncInfoSet makeSyncInfoSetFromSelection(SyncInfo[] infos) {
		return new SyncInfoSet(infos);		
	}
}
