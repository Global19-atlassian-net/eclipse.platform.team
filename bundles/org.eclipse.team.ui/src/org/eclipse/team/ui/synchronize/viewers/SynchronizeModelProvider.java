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
package org.eclipse.team.ui.synchronize.viewers;

import java.util.*;

import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Control;
import org.eclipse.team.core.ITeamStatus;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.core.Assert;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.ui.progress.UIJob;

/**
 * This class is reponsible for creating and maintaining a presentation model of 
 * {@link SynchronizeModelElement} elements that can be shown in a viewer. The model
 * is based on the synchronization information contained in the provided {@link SyncInfoSet}.
 * <p>
 * label updates (property propagation to parent nodes)
 * sync change listener (changes, additions, removals, reset)
 * batching busy updates
 * </p>
 * 
 * @see HierarchicalModelProvider
 * @see CompressedFoldersModelProvider
 * @since 3.0
 */
public abstract class SynchronizeModelProvider implements ISyncInfoSetChangeListener {

	// Flasg to indicate if tree control should be updated while
	// building the model.
	private boolean refreshViewer;
	
	protected Map resourceMap = Collections.synchronizedMap(new HashMap());
	
	protected SynchronizeModelElement root;
	
	// The viewer this input is being displayed in
	private StructuredViewer viewer;
	
	private Set pendingLabelUpdates = new HashSet();
	
	private LabelUpdateJob labelUpdater = new LabelUpdateJob();
	
	private IPropertyChangeListener listener = new IPropertyChangeListener() {
			public void propertyChange(final PropertyChangeEvent event) {
				if (event.getProperty() == SynchronizeModelElement.BUSY_PROPERTY) {
					labelUpdater.add(event.getSource(), ((Boolean)event.getNewValue()).booleanValue());
				}
			}
		};
	
	class LabelUpdateJob extends UIJob {
		public static final int BATCH_WAIT_INCREMENT = 100;
		Set nodes = new HashSet();
		public LabelUpdateJob() {
			super(Policy.bind("HierarchicalModelProvider.0")); //$NON-NLS-1$
			setSystem(true);
		}
		public IStatus runInUIThread(IProgressMonitor monitor) {
			Object[] updates;
			synchronized(nodes) {
				updates = nodes.toArray(new Object[nodes.size()]);
				nodes.clear();
			}
			if (canUpdateViewer()) {
				StructuredViewer tree = getViewer();
				tree.update(updates, null);
			}
			schedule(BATCH_WAIT_INCREMENT);
			return Status.OK_STATUS;
		}
		public void add(Object node, boolean isBusy) {
			synchronized(nodes) {
				nodes.add(node);
			}
			if (isBusy) {
				schedule(BATCH_WAIT_INCREMENT);
			} else {
				// Wait when unbusying to give the events a chance to propogate through
				// the collector
				schedule(BATCH_WAIT_INCREMENT * 10);
			}
		}
		public boolean shouldRun() {
			return !nodes.isEmpty();
		}
	}
	
	private SyncInfoSet set;
	
	/**
	 * Create an input based on the provide sync set. The input is not
	 * initialized until <code>prepareInput</code> is called.
	 * @param set
	 *            the sync set used as the basis for the model created by this
	 *            input.
	 */
	public SynchronizeModelProvider(SyncInfoSet set) {
		this(new UnchangedResourceModelElement(null, ResourcesPlugin.getWorkspace().getRoot()), set);
	}

	public SynchronizeModelProvider(SynchronizeModelElement parent, SyncInfoSet set) {
		Assert.isNotNull(set);
		Assert.isNotNull(parent);
		this.root = parent;
		this.set = set;
	}
	
	public SyncInfoSet getSyncInfoSet() {
		return set;
	}
	
	/**
	 * Return the <code>AbstractTreeViewer</code> asociated with this content
	 * provider or <code>null</code> if the viewer is not of the proper type.
	 * @return
	 */
	public StructuredViewer getViewer() {
		return viewer;
	}

	public void setViewer(StructuredViewer viewer) {
		Assert.isTrue(viewer instanceof AbstractTreeViewer);
		this.viewer = (AbstractTreeViewer) viewer;
	}

		/**
	 * Builds the viewer model based on the contents of the sync set.
	 */
	public SynchronizeModelElement prepareInput(IProgressMonitor monitor) {
		// Connect to the sync set which will register us as a listener and give us a reset event
		// in a background thread
		getSyncInfoSet().connect(this, monitor);
		return getModelRoot();
	}
	
	public Object getMapping(Object object) {
		return resourceMap.get(object);
	}
	
	/**
	 * Dispose of the builder
	 */
	public void dispose() {
		resourceMap.clear();
		getSyncInfoSet().removeSyncSetChangedListener(this);
	}
	
	/**
	 * Returns the input created by this controller or <code>null</code> if 
	 * {@link #prepareInput(IProgressMonitor)} hasn't been called on this object yet.
	 * @return
	 */
	public SynchronizeModelElement getModelRoot() {
		return root;
	}

	public abstract ViewerSorter getViewerSorter();

	/**
	 * Return the model object (i.e. an instance of <code>SyncInfoModelElement</code>
	 * or one of its subclasses) for the given IResource.
	 * @param resource
	 *            the resource
	 * @return the <code>SyncInfoModelElement</code> for the given resource
	 */
	protected SynchronizeModelElement getModelObject(IResource resource) {
		return (SynchronizeModelElement) resourceMap.get(resource);
	}

	public void syncInfoChanged(final ISyncInfoSetChangeEvent event, IProgressMonitor monitor) {
		if (! (event instanceof ISyncInfoTreeChangeEvent)) {
			reset();
		} else {
			final Control ctrl = getViewer().getControl();
			if (ctrl != null && !ctrl.isDisposed()) {
				ctrl.getDisplay().syncExec(new Runnable() {
					public void run() {
						if (!ctrl.isDisposed()) {
							BusyIndicator.showWhile(ctrl.getDisplay(), new Runnable() {
								public void run() {
									handleChanges((ISyncInfoTreeChangeEvent)event);
									getModelRoot().fireChanges();
								}
							});
						}
					}
				});
			}
		}
	}

	/**
	 * For each node create children based on the contents of
	 * @param node
	 * @return
	 */
	protected abstract IDiffElement[] buildModelObjects(SynchronizeModelElement node);

	protected abstract void doAdd(SynchronizeModelElement parent, SynchronizeModelElement element);
	
	protected abstract void doRemove(SynchronizeModelElement element);
	
	protected void associateDiffNode(SynchronizeModelElement node) {
		IResource resource = node.getResource();
		if(resource != null) {
			resourceMap.put(resource, node);
		}
	}

	protected void unassociateDiffNode(IResource resource) {
		resourceMap.remove(resource);
	}

	/**
	 * Handle the changes made to the viewer's <code>SyncInfoSet</code>.
	 * This method delegates the changes to the three methods <code>handleResourceChanges(ISyncInfoSetChangeEvent)</code>,
	 * <code>handleResourceRemovals(ISyncInfoSetChangeEvent)</code> and
	 * <code>handleResourceAdditions(ISyncInfoSetChangeEvent)</code>.
	 * @param event
	 *            the event containing the changed resourcses.
	 */
	protected void handleChanges(ISyncInfoTreeChangeEvent event) {
		StructuredViewer viewer = getViewer();
		try {			
			viewer.getControl().setRedraw(false);
			handleResourceChanges(event);
			handleResourceRemovals(event);
			handleResourceAdditions(event);
			firePendingLabelUpdates();
		} finally {
			viewer.getControl().setRedraw(true);
		}
	}

	/**
	 * Update the viewer for the sync set additions in the provided event. This
	 * method is invoked by <code>handleChanges(ISyncInfoSetChangeEvent)</code>.
	 * Subclasses may override.
	 * @param event
	 */
	protected abstract void handleResourceAdditions(ISyncInfoTreeChangeEvent event);

	/**
	 * Update the viewer for the sync set changes in the provided event. This
	 * method is invoked by <code>handleChanges(ISyncInfoSetChangeEvent)</code>.
	 * Subclasses may override.
	 * @param event
	 */
	protected abstract void handleResourceChanges(ISyncInfoTreeChangeEvent event);

	protected boolean isConflicting(SynchronizeModelElement diffNode) {
		return (diffNode.getKind() & SyncInfo.DIRECTION_MASK) == SyncInfo.CONFLICTING;
	}

	/**
	 * Update the viewer for the sync set removals in the provided event. This
	 * method is invoked by <code>handleChanges(ISyncInfoSetChangeEvent)</code>.
	 * Subclasses may override.
	 * @param event
	 */
	protected abstract void handleResourceRemovals(ISyncInfoTreeChangeEvent event);

	protected void reset() {
		try {
			refreshViewer = false;
			
			// Clear existing model, but keep the root node
			resourceMap.clear();
			clearModelObjects(getModelRoot());
			// remove all from tree viewer
			IDiffElement[] elements = getModelRoot().getChildren();
			for (int i = 0; i < elements.length; i++) {
				doRemove((SynchronizeModelElement)elements[i]);
			}
			
			// Rebuild the model
			associateDiffNode(getModelRoot());
			buildModelObjects(getModelRoot());
			
			// Notify listeners that model has changed
			getModelRoot().fireChanges();
		} finally {
			refreshViewer = true;
		}
		TeamUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
			public void run() {
				StructuredViewer viewer = getViewer();
				if (viewer != null && !viewer.getControl().isDisposed()) {
					viewer.refresh();
				}
			}
		});
	}

	/**
	 * Remove any traces of the resource and any of it's descendants in the
	 * hiearchy defined by the content provider from the content provider and
	 * the viewer it is associated with.
	 * @param resource
	 */
	protected void removeFromViewer(IResource resource) {
		SynchronizeModelElement node = getModelObject(resource);
		if (node == null) return;
		if (isConflicting(node)) {
			setParentConflict(node, false);
		}
		clearModelObjects(node);
		if (canUpdateViewer()) {
			doRemove(node);
		}
	}

	/**
	 * Clear the model objects from the diff tree, cleaning up any cached state
	 * (such as resource to model object map). This method recurses deeply on
	 * the tree to allow the cleanup of any cached state for the children as
	 * well.
	 * @param node
	 *            the root node
	 */
	protected void clearModelObjects(SynchronizeModelElement node) {
		IDiffElement[] children = node.getChildren();
		for (int i = 0; i < children.length; i++) {
			IDiffElement element = children[i];
			if (element instanceof SynchronizeModelElement) {
				clearModelObjects((SynchronizeModelElement) element);
			}
		}
		IResource resource = node.getResource();
		if (resource != null) {
			unassociateDiffNode(resource);
		}
		IDiffContainer parent = node.getParent();
		if (parent != null) {
			parent.removeToRoot(node);
		}
	}
	
	protected void addToViewer(SynchronizeModelElement node) {
		associateDiffNode(node);
		node.addPropertyChangeListener(listener);
		if (isConflicting(node)) {
			setParentConflict(node, true);
		}
		if (canUpdateViewer()) {
			doAdd((SynchronizeModelElement)node.getParent(), node);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.synchronize.ISyncInfoSetChangeListener#syncInfoSetReset(org.eclipse.team.core.synchronize.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void syncInfoSetReset(SyncInfoSet set, IProgressMonitor monitor) {
		reset();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.synchronize.ISyncInfoSetChangeListener#syncInfoSetErrors(org.eclipse.team.core.synchronize.SyncInfoSet, org.eclipse.team.core.ITeamStatus[], org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void syncInfoSetErrors(SyncInfoSet set, ITeamStatus[] errors, IProgressMonitor monitor) {
		// When errors occur we currently don't process them. It may be possible to decorate
		// elements in the model with errors, but currently we prefer to let ignore and except
		// another listener to display them.
	}

	/**
	 * Update the label of the given diff node. Diff nodes
	 * are accumulated and updated in a single call.
	 * @param diffNode the diff node to be updated
	 */
	protected void updateLabel(SynchronizeModelElement diffNode) {
		pendingLabelUpdates.add(diffNode);
	}
	
	/**
	 * @param tree
	 * @return
	 */
	private boolean canUpdateViewer() {
		return refreshViewer && getViewer() != null;
	}
	
	/**
	 * Forces the viewer to update the labels for parents whose children have
	 * changed during this round of sync set changes.
	 */
	protected void firePendingLabelUpdates() {
		try {
			if (canUpdateViewer()) {
				StructuredViewer tree = getViewer();
				tree.update(pendingLabelUpdates.toArray(new Object[pendingLabelUpdates.size()]), null);
			}
		} finally {
			pendingLabelUpdates.clear();
		}
	}

	protected void setParentConflict(SynchronizeModelElement diffNode, boolean value) {
		diffNode.setPropertyToRoot(SynchronizeModelElement.PROPAGATED_CONFLICT_PROPERTY, value);
		updateParentLabels(diffNode);
	}

	private void updateParentLabels(SynchronizeModelElement diffNode) {
		updateLabel(diffNode);
		while (diffNode.getParent() != null) {
			diffNode = (SynchronizeModelElement)diffNode.getParent();
			updateLabel(diffNode);
		}
	}
}