/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.synchronize;

import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.*;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.util.*;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.team.core.ITeamStatus;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.core.Assert;
import org.eclipse.team.internal.core.TeamPlugin;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.team.ui.synchronize.ISynchronizeModelElement;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;


/**
 * This class is reponsible for creating and maintaining a presentation model of 
 * {@link SynchronizeModelElement} elements that can be shown in a viewer. The model
 * is based on the synchronization information contained in the provided {@link SyncInfoSet}.
 */
public abstract class AbstractSynchronizeModelProvider implements ISynchronizeModelProvider, ISyncInfoSetChangeListener {
	
	/**
	 * Property constant for the expansion state for the elements displayed by the page. The
	 * expansion state is a List of resource paths.
	 */
	public static final String P_VIEWER_EXPANSION_STATE = TeamUIPlugin.ID  + ".P_VIEWER_EXPANSION_STATE"; //$NON-NLS-1$
	
	/**
	 * Property constant for the selection state for the elements displayed by the page. The
	 * selection state is a List of resource paths.
	 */
	public static final String P_VIEWER_SELECTION_STATE = TeamUIPlugin.ID  + ".P_VIEWER_SELECTION_STATE"; //$NON-NLS-1$
	
	private ISynchronizeModelElement root;
	
	private ISynchronizePageConfiguration configuration;
	
	private SyncInfoSet set;
	
	private SynchronizeModelUpdateHandler updateHandler;
	
	private boolean disposed = false;

    private SynchronizePageActionGroup actionGroup;

    private ListenerList listeners;
	
	/**
	 * Constructor for creating a sub-provider
	 * @param parentProvider the parent provider
	 * @param parentNode the root node of the model built by this provider
	 * @param configuration the sync page configuration
	 * @param set the sync info set from which the model is built
	 */
	protected AbstractSynchronizeModelProvider(AbstractSynchronizeModelProvider parentProvider, ISynchronizeModelElement parentNode, ISynchronizePageConfiguration configuration, SyncInfoSet set) {
		Assert.isNotNull(set);
		Assert.isNotNull(parentNode);
		this.root = parentNode;
		this.set = set;
		this.configuration = configuration;
		if (parentProvider == null) {
		    // The update handler will register for sync change events 
		    // with the sync set when the handler is activated
		    updateHandler = new SynchronizeModelUpdateHandler(this);
		} else {
		    // We will use the parent's update handler and register for changes with the given set
		    updateHandler = parentProvider.updateHandler;
		    set.addSyncSetChangedListener(this);
		}
	}
	
	/**
	 * Cosntructor for creating a root model provider.
	 * @param configuration the sync page configuration
	 * @param set the sync info set from which the model is built
	 */
	protected AbstractSynchronizeModelProvider(ISynchronizePageConfiguration configuration, SyncInfoSet set) {
		this(null, new UnchangedResourceModelElement(null, ResourcesPlugin.getWorkspace().getRoot()) {
			/* 
			 * Override to ensure that the diff viewer will appear in CompareEditorInputs
			 */
			public boolean hasChildren() {
				return true;
			}
		}, configuration, set);
		// Register the action group for this provider, since it is the root provider
		SynchronizePageActionGroup actionGroup = getActionGroup();
		if (actionGroup != null) {
		    configuration.addActionContribution(actionGroup);
		}
	}
	
	/**
	 * Return the action group for this provider or <code>null</code>
     * if there are no actions associated with this provider. The action
     * group will be registered with the configuration if this is
     * the root provider. If this provider is a sub-provider, it
     * is up to the parent provider to register the action group.
     * <p>
     * The action group for a provider is created by calling the
     * <code>createdActionGroup</code> method. If this method returns
     * a non-null group, it is cached so it can be disposed
     * when the provider is disposed.
     * @return the action group for this provider or <code>null</code>
     * if there are no actions associated with this provider
     */
    public final synchronized SynchronizePageActionGroup getActionGroup() {
        if (actionGroup == null) {
            actionGroup = createActionGroup();
        }
        return actionGroup;
    }

    /**
     * Create the action group for this provider. By default,
     * a <code>null</code> is returned. Subclasses may override.
     * @return the action group for this provider or <code>null</code>
     */
    protected SynchronizePageActionGroup createActionGroup() {
        return null;
    }
    
    /**
	 * Return the set that contains the elements this provider is using as
	 * a basis for creating a presentation model. This cannot be null.
	 * 
	 * @return the set that contains the elements this provider is
	 * using as a basis for creating a presentation model.
	 */
	public SyncInfoSet getSyncInfoSet() {
		return set;
	}
	
	/**
	 * Returns the input created by this provider or <code>null</code> if 
	 * {@link #prepareInput(IProgressMonitor)} hasn't been called on this object yet.
	 * 
	 * @return the input created by this provider.
	 */
	public ISynchronizeModelElement getModelRoot() {
		return root;
	}
	
	/**
	 * Return the page configuration for this provider.
	 * 
	 * @return the page configuration for this provider.
	 */
	public ISynchronizePageConfiguration getConfiguration() {
		return configuration;
	}
	
	/**
	 * Return the <code>AbstractTreeViewer</code> associated with this
	 * provider or <code>null</code> if the viewer is not of the proper type.
	 * @return the structured viewer that is displaying the model managed by this provider
	 */
	public StructuredViewer getViewer() {
		ISynchronizePage page = configuration.getPage();
		if (page == null) return null;
        Viewer viewer = page.getViewer();
		if (viewer instanceof AbstractTreeViewer) {
		    return (AbstractTreeViewer)viewer;
		}
		return null;
	}

	/**
	 * Builds the viewer model based on the contents of the sync set.
	 */
	public ISynchronizeModelElement prepareInput(IProgressMonitor monitor) {
		// Connect to the sync set which will register us as a listener and give us a reset event
		// in a background thread
	    if (isRootProvider()) {
	        updateHandler.connect(monitor);
	    } else {
	        getSyncInfoSet().connect(this, monitor);
	    }
		return getModelRoot();
	}
	
	/**
	 * Calculate the problem marker that should be shown on the given 
	 * element. The returned property can be either
	 * ISynchronizeModelElement.PROPAGATED_ERROR_MARKER_PROPERTY or
	 * ISynchronizeModelElement.PROPAGATED_WARNING_MARKER_PROPERTY.
	 * @param element a synchronize model element
	 * @return the marker property that should be displayed on the element
	 * or <code>null</code> if no marker should be displayed
	 */
	public String calculateProblemMarker(ISynchronizeModelElement element) {
		IResource resource = element.getResource();
		String property = null;
		if (resource != null && resource.exists()) {
			try {
				IMarker[] markers = resource.findMarkers(IMarker.PROBLEM, true, getLogicalModelDepth(resource));
				for (int i = 0; i < markers.length; i++) {
					IMarker marker = markers[i];
					try {
						Integer severity = (Integer) marker.getAttribute(IMarker.SEVERITY);
						if (severity != null) {
							if (severity.intValue() == IMarker.SEVERITY_ERROR) {
								property = ISynchronizeModelElement.PROPAGATED_ERROR_MARKER_PROPERTY;
								break;
							} else if (severity.intValue() == IMarker.SEVERITY_WARNING) {
								property = ISynchronizeModelElement.PROPAGATED_WARNING_MARKER_PROPERTY;
								// Keep going because there may be errors on other resources
							}
						}
					} catch (CoreException e) {
						if (!resource.exists()) {
							// The resource was deleted concurrently. Forget any previously found property
							property = null;
							break;
						}
						// If the marker exists, log the exception and continue.
						// Otherwise, just ignore the exception and keep going
						if (marker.exists()) {
							TeamPlugin.log(e);
						}
					}
				}
			} catch (CoreException e) {
				// If the resource exists (is accessible), log the exception and continue.
				// Otherwise, just ignore the exception
				if (resource.isAccessible() 
						&& e.getStatus().getCode() != IResourceStatus.RESOURCE_NOT_FOUND
						&& e.getStatus().getCode() != IResourceStatus.PROJECT_NOT_OPEN) {
					TeamPlugin.log(e);
				}
			}
		}
		return property;
	}
    
	/**
	 * Return the logical model depth used for marker propogation
	 * @param resource the resoure
	 * @return the depth the resources should be traversed
	 */
	protected int getLogicalModelDepth(IResource resource) {
		return IResource.DEPTH_INFINITE;
	}
	
	/**
	 * Update the label of the given diff node. The label for nodes queued 
	 * using this method will not be updated until <code>firePendingLabelUpdates</code>
	 * is called.
	 * @param diffNode the diff node to be updated
	 */
	protected void queueForLabelUpdate(ISynchronizeModelElement diffNode) {
		updateHandler.queueForLabelUpdate(diffNode);
	}
    
    /**
     * Throw away any old state associated with this provider and
     * rebuild the model from scratch.
     */
	protected void reset() {
		// save expansion state
		if(hasViewerState()) {
			saveViewerState();
		}
		
		// Clear existing model, but keep the root node
		clearModelObjects(getModelRoot());
		
		// Rebuild the model
		buildModelObjects(getModelRoot());
		
		// Notify listeners that model has changed
		ISynchronizeModelElement root = getModelRoot();
		if(root instanceof SynchronizeModelElement) {
			((SynchronizeModelElement)root).fireChanges();
		}
		TeamUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
			public void run() {
				StructuredViewer viewer = getViewer();
				if (viewer != null && !viewer.getControl().isDisposed()) {
					viewer.refresh();
					//	restore expansion state
					restoreViewerState();
				}
			}
		});
	}
	
	/**
	 * For each node create children based on the contents of
	 * @param node
	 * @return
	 */
	protected abstract IDiffElement[] buildModelObjects(ISynchronizeModelElement node);
	
	/**
     * @return
     */
    protected boolean hasViewerState() {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * 
     */
	protected void saveViewerState() {
        // TODO Auto-generated method stub
        
    }

    /**
     * 
     */
    protected void restoreViewerState() {
        // TODO Auto-generated method stub
        
    }

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.ISynchronizeModelProvider#saveState()
	 */
	public void saveState() {
		saveViewerState();
	}
	
    /**
	 * Method invoked when a sync element is added or removed or its state changes.
	 * This method can be invoked from the UI thread or a background thread.
	 * @param element synchronize element
	 * @param clear <code>true</code> if the conflict bit of the element was cleared 
	 * (i.e. the element has been deleted)
	 */
	protected void propogateConflictState(ISynchronizeModelElement element, boolean clear) {
		boolean isConflict = clear ? false : isConflicting(element);
		boolean wasConflict = element.getProperty(ISynchronizeModelElement.PROPAGATED_CONFLICT_PROPERTY);
		// Only propogate and update parent labels if the state of the element has changed
		if (isConflict != wasConflict) {
			element.setPropertyToRoot(ISynchronizeModelElement.PROPAGATED_CONFLICT_PROPERTY, isConflict);
			updateHandler.updateParentLabels(element);
		}
	}
	
	/**
	 * Return whether the given model element represets a conflict.
	 * @param element the element being tested
	 * @return
	 */
	protected boolean isConflicting(ISynchronizeModelElement element) {
		return (element.getKind() & SyncInfo.DIRECTION_MASK) == SyncInfo.CONFLICTING;
	}
	
	/**
	 * Dispose of the provider
	 */
	public void dispose() {
	    // Only dispose the update handler if it is
	    // directly associated with this provider
	    if (isRootProvider()) {
	        updateHandler.dispose();
	    } else {
	        set.removeSyncSetChangedListener(this);
	    }
	    if (actionGroup != null) {
	        Utils.syncExec(new Runnable() {
                public void run() {
                    actionGroup.dispose();
                }
            }, getViewer());
	    }
		this.disposed = true;
	}
	
    private boolean isRootProvider() {
        return updateHandler.getProvider() == this;
    }

    /**
	 * Return whether this provide has been disposed.
     * @return whether this provide has been disposed
     */
	public boolean isDisposed() {
        return disposed;
    }

    /**
     * Return the closest parent elements that represents a model element that
     * could contains the given resource. Multiple elements need only be returned
     * if two or more logical views are being shown and each view has an element
     * that could contain the resource.
     * @param resource the resource
     * @return one or more lowest level parents that could contain the resource
     */
    public abstract ISynchronizeModelElement[] getClosestExistingParents(IResource resource);
    
	/**
	 * Handle the changes made to the viewer's <code>SyncInfoSet</code>.
	 * This method delegates the changes to the three methods <code>handleResourceChanges(ISyncInfoSetChangeEvent)</code>,
	 * <code>handleResourceRemovals(ISyncInfoSetChangeEvent)</code> and
	 * <code>handleResourceAdditions(ISyncInfoSetChangeEvent)</code>.
	 * @param event
	 *            the event containing the changed resourcses.
	 */
	protected void handleChanges(ISyncInfoTreeChangeEvent event, IProgressMonitor monitor) {
		handleResourceChanges(event);
		handleResourceRemovals(event);
		handleResourceAdditions(event);
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

	/**
	 * Update the viewer for the sync set removals in the provided event. This
	 * method is invoked by <code>handleChanges(ISyncInfoSetChangeEvent)</code>.
	 * Subclasses may override.
	 * @param event
	 */
	protected abstract void handleResourceRemovals(ISyncInfoTreeChangeEvent event);
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.synchronize.ISyncInfoSetChangeListener#syncInfoChanged(org.eclipse.team.core.synchronize.ISyncInfoSetChangeEvent, org.eclipse.core.runtime.IProgressMonitor)
	 */
    public void syncInfoChanged(ISyncInfoSetChangeEvent event, IProgressMonitor monitor) {
		if (! (event instanceof ISyncInfoTreeChangeEvent)) {
			reset();
		} else {
		    handleChanges((ISyncInfoTreeChangeEvent)event, monitor);
		}
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.team.core.synchronize.ISyncInfoSetChangeListener#syncInfoSetErrors(org.eclipse.team.core.synchronize.SyncInfoSet, org.eclipse.team.core.ITeamStatus[], org.eclipse.core.runtime.IProgressMonitor)
     */
    public void syncInfoSetErrors(SyncInfoSet set, ITeamStatus[] errors, IProgressMonitor monitor) {
        // Not handled

    }
    
    /* (non-Javadoc)
     * @see org.eclipse.team.core.synchronize.ISyncInfoSetChangeListener#syncInfoSetReset(org.eclipse.team.core.synchronize.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
     */
    public void syncInfoSetReset(SyncInfoSet set, IProgressMonitor monitor) {
        reset();
    }
    
	protected void addToViewer(ISynchronizeModelElement node) {
	    updateHandler.nodeAdded(node, this);
		propogateConflictState(node, false);
		// Set the marker property on this node.
		// There is no need to propogate this to the parents 
		// as they will be displaying the proper marker already
		String property = calculateProblemMarker(node);
		if (property != null) {
			node.setProperty(property, true);
		}
		if (Utils.canUpdateViewer(getViewer())) {
			doAdd((SynchronizeModelElement)node.getParent(), node);
		}
	}
	
	/**
	 * Remove any traces of the model element and any of it's descendants in the
	 * hiearchy defined by the content provider from the content provider and
	 * the viewer it is associated with.
	 * @param node the model element to remove
	 */
	protected void removeFromViewer(ISynchronizeModelElement node) {
		propogateConflictState(node, true /* clear the conflict */);
		clearModelObjects(node);
		if (Utils.canUpdateViewer(getViewer())) {
			doRemove(node);
		}
		updateHandler.nodeRemoved(node, this);
	}
	
	/**
	 * Clear the model objects from the diff tree, cleaning up any cached state
	 * (such as resource to model object map). This method recurses deeply on
	 * the tree to allow the cleanup of any cached state for the children as
	 * well.
	 * @param node the root node
	 */
	protected void clearModelObjects(ISynchronizeModelElement node) {
		IDiffElement[] children = node.getChildren();
		for (int i = 0; i < children.length; i++) {
			IDiffElement element = children[i];
			if (element instanceof ISynchronizeModelElement) {
			    ISynchronizeModelElement sme = (ISynchronizeModelElement) element;
                ISynchronizeModelProvider provider = getProvider(sme);
                if (provider != null && provider instanceof AbstractSynchronizeModelProvider) {
                    ((AbstractSynchronizeModelProvider)provider).clearModelObjects(sme);
                } else {
                    clearModelObjects(sme);
                }
			}
		}
		IDiffContainer parent = node.getParent();
		if (parent != null) {
			parent.removeToRoot(node);
		}
	}
	
	/**
	 * Return the provider that created and manages the given
	 * model element. The default is to return the receiver.
	 * Subclasses may override.
     * @param element the synchronizew model element
     * @return the provider that created the element
     */
    protected ISynchronizeModelProvider getProvider(ISynchronizeModelElement element) {
        return this;
    }

    /**
     * Add the element to the viewer.
     * @param parent the parent of the element which is already added to the viewer
     * @param element the element to be added to the viewer
     */
	protected void doAdd(ISynchronizeModelElement parent, ISynchronizeModelElement element) {
		AbstractTreeViewer viewer = (AbstractTreeViewer)getViewer();
		viewer.add(parent, element);		
	}
	
	/**
	 * Remove the element from the viewer
	 * @param element the element to be removed
	 */
	protected void doRemove(ISynchronizeModelElement element) {
		AbstractTreeViewer viewer = (AbstractTreeViewer)getViewer();
		viewer.remove(element);		
	}
	
	/**
	 * This is a callback from the model update handler that gets invoked 
	 * when a node is added to the viewer. It is only invoked for the
	 * root level model provider.
	 * @param node
	 * @param provider the provider that added the node
	 */
	protected void nodeAdded(ISynchronizeModelElement node, AbstractSynchronizeModelProvider provider) {
	    // Default is to do nothing
	}
	
	/**
	 * This is a callback from the model update handler that gets invoked 
	 * when a node is removed from the viewer. It is only invoked for the
	 * root level model provider.
	 * @param node
	 */
	protected void nodeRemoved(ISynchronizeModelElement node, AbstractSynchronizeModelProvider provider) {
	    // Default is to do nothing
	}
	
    public void addPropertyChangeListener(IPropertyChangeListener listener) {
        synchronized (this) {
            if (listeners == null) {
                listeners = new ListenerList();
            }
            listeners.add(listener);
        }

    }
    public void removePropertyChangeListener(IPropertyChangeListener listener) {
        if (listeners != null) {
            synchronized (this) {
                listeners.remove(listener);
                if (listeners.isEmpty()) {
                    listeners = null;
                }
            }
        }
    }
    
	protected void firePropertyChange(String key, Object oldValue, Object newValue) {
		Object[] allListeners;
		synchronized(this) {
		    allListeners = listeners.getListeners();
		}
		final PropertyChangeEvent event = new PropertyChangeEvent(this, key, oldValue, newValue);
		for (int i = 0; i < allListeners.length; i++) {
			final IPropertyChangeListener listener = (IPropertyChangeListener)allListeners[i];
			Platform.run(new ISafeRunnable() {
				public void handleException(Throwable exception) {
					// Error is logged by platform
				}
				public void run() throws Exception {
					listener.propertyChange(event);
				}
			});
		}
	}
}
