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

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.ui.synchronize.viewers.*;

/**
 * An input that can be used with both {@link } and 
 * {@link }. The
 * job of this input is to create the logical model of the contents of the
 * sync set for displaying to the user. The created logical model must diff
 * nodes.
 * <p>
 * 1. First, prepareInput is called to initialize the model with the given sync
 * set. Building the model occurs in the ui thread.
 * 2. The input must react to changes in the sync set and adjust its diff node
 * model then update the viewer. In effect mediating between the sync set
 * changes and the model shown to the user. This happens in the ui thread.
 * </p>
 * NOT ON DEMAND - model is created then maintained!
 * @since 3.0
 */
public class HierarchicalModelProvider extends SynchronizeModelProvider {
		
	/**
	 * Create an input based on the provide sync set. The input is not initialized
	 * until <code>prepareInput</code> is called. 
	 * 
	 * @param set the sync set used as the basis for the model created by this input.
	 */
	public HierarchicalModelProvider(SyncInfoTree set) {
		super(set);
	}

	public ViewerSorter getViewerSorter() {
		return new SynchronizeModelElementSorter();
	}

	protected SyncInfoTree getSyncInfoTree() {
		return (SyncInfoTree)getSyncInfoSet();
	}

	/**
	 * Invoked by the <code>buildModelObject</code> method to create
	 * the childen of the given node. This method can be overriden
	 * by subclasses but subclasses should inv
	 * @param container
	 * @return
	 */
	protected IDiffElement[] createModelObjects(SynchronizeModelElement container) {
		IResource resource = null;
		if (container == getModelRoot()) {
			resource = ResourcesPlugin.getWorkspace().getRoot();
		} else {
			resource = container.getResource();
		}
		if(resource != null) {
			SyncInfoTree infoTree = getSyncInfoTree();
			IResource[] children = infoTree.members(resource);
			SynchronizeModelElement[] nodes = new SynchronizeModelElement[children.length];
			for (int i = 0; i < children.length; i++) {
				nodes[i] = createModelObject(container, children[i]);
			}
			return nodes;	
		}
		return new IDiffElement[0];
	}

	protected SynchronizeModelElement createModelObject(SynchronizeModelElement parent, IResource resource) {
		SyncInfo info = getSyncInfoTree().getSyncInfo(resource);
		SynchronizeModelElement newNode;
		if(info != null) {
			newNode = new SyncInfoModelElement(parent, info);
		} else {
			newNode = new UnchangedResourceModelElement(parent, resource);
		}
		addToViewer(newNode);
		return newNode;
	}

	/**
	 * Invokes <code>getModelObject(Object)</code> on an array of resources.
	 * @param resources
	 *            the resources
	 * @return the model objects for the resources
	 */
	protected Object[] getModelObjects(IResource[] resources) {
		Object[] result = new Object[resources.length];
		for (int i = 0; i < resources.length; i++) {
			result[i] = getModelObject(resources[i]);
		}
		return result;
	}

	/**
	 * Handle the change for the existing diff node. The diff node
	 * should be changed to have the given sync info
	 * @param diffNode the diff node to be changed
	 * @param info the new sync info for the diff node
	 */
	protected void handleChange(SynchronizeModelElement diffNode, SyncInfo info) {
		IResource local = info.getLocal();
		// TODO: Get any additional sync bits
		if(diffNode instanceof SyncInfoModelElement) {
			boolean wasConflict = isConflicting(diffNode);
			// The update preserves any of the additional sync info bits
			((SyncInfoModelElement)diffNode).update(info);
			boolean isConflict = isConflicting(diffNode);
			updateLabel(diffNode);
			if (wasConflict && !isConflict) {
				setParentConflict(diffNode, false);
			} else if (!wasConflict && isConflict) {
				setParentConflict(diffNode, true);
			}
		} else {
			removeFromViewer(local);
			addResources(new IResource[] {local});
		}
		// TODO: set any additional sync info bits
	}

	protected void addResources(IResource[] added) {
		for (int i = 0; i < added.length; i++) {
			IResource resource = added[i];
			SynchronizeModelElement node = getModelObject(resource);
			if (node != null) {
				// Somehow the node exists. Remove it and read it to ensure
				// what is shown matches the contents of the sync set
				removeFromViewer(resource);
			}
			// Build the sub-tree rooted at this node
			SynchronizeModelElement parent = getModelObject(resource.getParent());
			if (parent != null) {
				node = createModelObject(parent, resource);
				buildModelObjects(node);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#buildModelObjects(org.eclipse.team.ui.synchronize.viewers.SynchronizeModelElement)
	 */
	protected IDiffElement[] buildModelObjects(SynchronizeModelElement node) {
		IDiffElement[] children = createModelObjects(node);
		for (int i = 0; i < children.length; i++) {
			IDiffElement element = children[i];
			if (element instanceof SynchronizeModelElement) {
				buildModelObjects((SynchronizeModelElement) element);
			}
		}
		return children;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#doAdd(org.eclipse.team.ui.synchronize.viewers.SynchronizeModelElement, org.eclipse.team.ui.synchronize.viewers.SynchronizeModelElement)
	 */
	protected void doAdd(SynchronizeModelElement parent, SynchronizeModelElement element) {
		AbstractTreeViewer viewer = (AbstractTreeViewer)getViewer();
		viewer.add(parent, element);		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#doRemove(org.eclipse.team.ui.synchronize.viewers.SynchronizeModelElement)
	 */
	protected void doRemove(SynchronizeModelElement element) {
		AbstractTreeViewer viewer = (AbstractTreeViewer)getViewer();
		viewer.remove(element);		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#handleResourceAdditions(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
	 */
	protected void handleResourceAdditions(ISyncInfoTreeChangeEvent event) {
		IResource[] added = event.getAddedSubtreeRoots();
		addResources(added);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#handleResourceChanges(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
	 */
	protected void handleResourceChanges(ISyncInfoTreeChangeEvent event) {
		// Refresh the viewer for each changed resource
		SyncInfo[] infos = event.getChangedResources();
		for (int i = 0; i < infos.length; i++) {
			SyncInfo info = infos[i];
			IResource local = info.getLocal();
			SynchronizeModelElement diffNode = getModelObject(local);
			if (diffNode != null) {
				handleChange(diffNode, info);
			}
		}	
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#handleResourceRemovals(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
	 */
	protected void handleResourceRemovals(ISyncInfoTreeChangeEvent event) {
		// Remove the removed subtrees
		IResource[] removedRoots = event.getRemovedSubtreeRoots();
		for (int i = 0; i < removedRoots.length; i++) {
			removeFromViewer(removedRoots[i]);
		}
		// We have to look for folders that may no longer be in the set
		// (i.e. are in-sync) but still have descendants in the set
		IResource[] removedResources = event.getRemovedResources();
		for (int i = 0; i < removedResources.length; i++) {
			IResource resource = removedResources[i];
			if (resource.getType() != IResource.FILE) {
				SynchronizeModelElement node = getModelObject(resource);
				if (node != null) {
					removeFromViewer(resource);
					addResources(new IResource[] {resource});
				}
			}
		}
	}
}
