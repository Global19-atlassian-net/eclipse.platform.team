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

import java.util.*;

import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.*;
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
import org.eclipse.team.ui.synchronize.viewers.ISynchronizeModelElement;
import org.eclipse.team.ui.synchronize.viewers.ISynchronizeModelProvider;
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
public abstract class SynchronizeModelProvider implements ISyncInfoSetChangeListener, ISynchronizeModelProvider, IResourceChangeListener {

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
		this(new UnchangedResourceModelElement(null, ResourcesPlugin.getWorkspace().getRoot()) {
			/* 
			 * Override to ensure that the diff viewer will appear in CompareEditorInputs
			 */
			public boolean hasChildren() {
				return true;
			}
		}, set);
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
	public ISynchronizeModelElement prepareInput(IProgressMonitor monitor) {
		// Connect to the sync set which will register us as a listener and give us a reset event
		// in a background thread
		getSyncInfoSet().connect(this, monitor);
		return getModelRoot();
	}
	
	/**
	 * The provider can try and return a mapping for the provided object. Providers often use mappings
	 * to store the source of a logical element they have created. For example, when displaying resource
	 * based logical elements, a provider will cache the resource -> element mapping for quick retrieval
	 * of the element when resource based changes are made.
	 * 
	 * @param object the object to query for a mapping
	 * @return an object created by this provider that would be shown in a viewer, or <code>null</code>
	 * if the provided object is not mapped by this provider.
	 */
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
	public ISynchronizeModelElement getModelRoot() {
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
	protected ISynchronizeModelElement getModelObject(IResource resource) {
		return (ISynchronizeModelElement) resourceMap.get(resource);
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
									ISynchronizeModelElement root = getModelRoot();
									if(root instanceof SynchronizeModelElement)
										((SynchronizeModelElement)root).fireChanges();
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
	protected abstract IDiffElement[] buildModelObjects(ISynchronizeModelElement node);

	protected abstract void doAdd(ISynchronizeModelElement parent, ISynchronizeModelElement element);
	
	protected abstract void doRemove(ISynchronizeModelElement element);
	
	protected void associateDiffNode(ISynchronizeModelElement node) {
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

	protected boolean isConflicting(ISynchronizeModelElement diffNode) {
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
				doRemove((ISynchronizeModelElement)elements[i]);
			}
			
			// Rebuild the model
			associateDiffNode(getModelRoot());
			buildModelObjects(getModelRoot());
			
			// Notify listeners that model has changed
			ISynchronizeModelElement root = getModelRoot();
			if(root instanceof SynchronizeModelElement) {
				((SynchronizeModelElement)root).fireChanges();
			}
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
		ISynchronizeModelElement node = getModelObject(resource);
		if (node == null) return;
		calculateProperties(node);
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
	protected void clearModelObjects(ISynchronizeModelElement node) {
		IDiffElement[] children = node.getChildren();
		for (int i = 0; i < children.length; i++) {
			IDiffElement element = children[i];
			if (element instanceof ISynchronizeModelElement) {
				clearModelObjects((ISynchronizeModelElement) element);
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
	
	protected void addToViewer(ISynchronizeModelElement node) {
		associateDiffNode(node);
		node.addPropertyChangeListener(listener);
		calculateProperties(node);
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
	protected void updateLabel(ISynchronizeModelElement diffNode) {
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

	protected void calculateProperties(ISynchronizeModelElement element) {
		element.setPropertyToRoot(ISynchronizeModelElement.PROPAGATED_CONFLICT_PROPERTY, isConflicting(element));
		IResource resource = element.getResource();
		if(resource != null) {
			try {
				boolean error = false;
				IMarker[] markers = resource.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
				for (int i = 0; i < markers.length; i++) {
					IMarker marker = markers[i];
					Integer severity = (Integer)marker.getAttribute(IMarker.SEVERITY);
					if(severity.intValue() == IMarker.SEVERITY_ERROR) {
						error = true; break;
					}
				}
				element.setPropertyToRoot(ISynchronizeModelElement.PROPAGATED_ERROR_MARKER_PROPERTY, error);
			} catch (CoreException e) {
				TeamUIPlugin.log(e);
			}
		}
		updateParentLabels(element);
	}
	
	protected void setPropertyToRoot(ISynchronizeModelElement diffNode, String propertyName, boolean value) {
		diffNode.setPropertyToRoot(propertyName, value);
		updateParentLabels(diffNode);
	}

	private void updateParentLabels(ISynchronizeModelElement diffNode) {
		updateLabel(diffNode);
		while (diffNode.getParent() != null) {
			diffNode = (ISynchronizeModelElement)diffNode.getParent();
			updateLabel(diffNode);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org.eclipse.core.resources.IResourceChangeEvent)
	 */
	public void resourceChanged(final IResourceChangeEvent event) {
		final Control ctrl = getViewer().getControl();
		if (ctrl != null && !ctrl.isDisposed()) {
			ctrl.getDisplay().syncExec(new Runnable() {
				public void run() {
					if (!ctrl.isDisposed()) {
						BusyIndicator.showWhile(ctrl.getDisplay(), new Runnable() {
							public void run() {
								String[] markerTypes = getMarkerTypes();
								boolean refreshNeeded = false;
								for (int idx = 0; idx < markerTypes.length; idx++) {
									IMarkerDelta[] markerDeltas = event.findMarkerDeltas(markerTypes[idx], true);
									List changes = new ArrayList(markerDeltas.length);
									for (int i = 0; idx < markerDeltas.length; idx++) {
										IMarkerDelta delta = markerDeltas[i];
										int kind = delta.getKind();
											ISynchronizeModelElement element = getModelObject(delta.getResource());
											if(element != null) {
												calculateProperties(element);
											}
									}
								}
								firePendingLabelUpdates();
							}
						});
					}
				}
			});
		}
	}
	
	
	protected String[] getMarkerTypes() {
		return new String[] {IMarker.PROBLEM};
	}
}