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

import java.util.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.ui.synchronize.ISynchronizeModelElement;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;

/**
 * This class provides functionality for defining composite synchronize model
 * providers. A composite provider is one that breaks up the displayed
 * {@link SyncInfoSet} into subsets that may be didplayed using one
 * or more synchronize model providers.
 * 
 */
public abstract class CompositeModelProvider extends AbstractSynchronizeModelProvider {
    
    List providers = new ArrayList();
    Map resourceToElements = new HashMap(); // Map IResource to List of ISynchronizeModelElement
    Map elementToProvider = new HashMap(); // Map ISynchronizeModelElement -> AbstractSynchronizeModelProvider
	
    protected CompositeModelProvider(ISynchronizePageConfiguration configuration, SyncInfoSet set) {
        super(null, new UnchangedResourceModelElement(null, ResourcesPlugin.getWorkspace().getRoot()) {
			/* 
			 * Override to ensure that the diff viewer will appear in CompareEditorInputs
			 */
			public boolean hasChildren() {
				return true;
			}
		}, configuration, set);
    }
    
    /**
     * Add the provider to the list of providers.
     * @param provider the provider to be added
     */
    protected void addProvider(AbstractSynchronizeModelProvider provider) {
        providers.add(provider);
    }
    
    /**
     * Remove the provider from the list of providers.
     * @param provider the provider to be removed
     */
    protected void removeProvider(AbstractSynchronizeModelProvider provider) {
        providers.remove(provider);
        provider.dispose();
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.AbstractSynchronizeModelProvider#getProvider(org.eclipse.team.ui.synchronize.ISynchronizeModelElement)
     */
    protected AbstractSynchronizeModelProvider getProvider(ISynchronizeModelElement element) {
        return (AbstractSynchronizeModelProvider)elementToProvider.get(element);
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.AbstractSynchronizeModelProvider#getClosestExistingParents(org.eclipse.core.resources.IResource)
     */
    public ISynchronizeModelElement[] getClosestExistingParents(IResource resource) {
        AbstractSynchronizeModelProvider[] providers = getProviders();
        if (providers.length == 0) {
            return new ISynchronizeModelElement[0];
        }
        if (providers.length == 1) {
            return providers[0].getClosestExistingParents(resource);
        }
        List result = new ArrayList();
        for (int i = 0; i < providers.length; i++) {
            AbstractSynchronizeModelProvider provider = providers[i];
            ISynchronizeModelElement[] elements = provider.getClosestExistingParents(resource);
            for (int j = 0; j < elements.length; j++) {
                ISynchronizeModelElement element = elements[j];
                result.add(element);
            }
        }
        return (ISynchronizeModelElement[]) result.toArray(new ISynchronizeModelElement[result.size()]);
    }

    /**
     * Return all the sub-providers of this composite.
     * @return the sub-providers of this composite
     */
    protected AbstractSynchronizeModelProvider[] getProviders() {
        return (AbstractSynchronizeModelProvider[]) providers.toArray(new AbstractSynchronizeModelProvider[providers.size()]);
    }
    
    /**
     * Return the providers that are displaying the given resource.
     * @param resource the resource
     * @return the providers displaying the resource
     */
    protected AbstractSynchronizeModelProvider[] getProvidersContaining(IResource resource) {
        List elements = (List)resourceToElements.get(resource);
        if (elements == null || elements.isEmpty()) {
            return new AbstractSynchronizeModelProvider[0];
        }
        List result = new ArrayList();
        for (Iterator iter = elements.iterator(); iter.hasNext();) {
            ISynchronizeModelElement element = (ISynchronizeModelElement)iter.next();
            result.add(getProvider(element));
        }
        return (AbstractSynchronizeModelProvider[]) result.toArray(new AbstractSynchronizeModelProvider[result.size()]);
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.AbstractSynchronizeModelProvider#handleChanges(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
     */
    protected void handleChanges(ISyncInfoTreeChangeEvent event, IProgressMonitor monitor) {
        AbstractSynchronizeModelProvider[] providers = null;
        try {
            monitor.beginTask(null, 100);
            providers = beginInput();
            super.handleChanges(event, Policy.subMonitorFor(monitor, 50));
        } finally {
            endInput(providers, Policy.subMonitorFor(monitor, 50));
            monitor.done();
        }
    }

    /**
     * Begin inputing changes to the syncsets of the sub-providers
     */
    protected AbstractSynchronizeModelProvider[] beginInput() {
        AbstractSynchronizeModelProvider[] providers = getProviders();
        for (int i = 0; i < providers.length; i++) {
            AbstractSynchronizeModelProvider provider = providers[i];
            provider.getSyncInfoSet().beginInput();
        }
        return providers;
    }
    
    /**
     * End inputing to sub-provider sync sets
     */
    protected void endInput(AbstractSynchronizeModelProvider[] providers, IProgressMonitor monitor) {
        RuntimeException exception = null;
        for (int i = 0; i < providers.length; i++) {
            AbstractSynchronizeModelProvider provider = providers[i];
            try {
                provider.getSyncInfoSet().endInput(monitor);
            } catch (RuntimeException e) {
                // Remember the exception but continue so all locks are freed
                exception = e;
            }
        } 
        if (exception != null) {
            throw exception;
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.AbstractSynchronizeModelProvider#handleResourceAdditions(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
     */
    protected final void handleResourceAdditions(ISyncInfoTreeChangeEvent event) {
        handleAdditions(event.getAddedResources());
    }
    
    /**
     * Handle the resource additions by adding them to any existing
     * sub-providers or by creating addition sub-providers as needed.
     * @param resources
     */
    protected void handleAdditions(SyncInfo[] resources) {
        for (int i = 0; i < resources.length; i++) {
            SyncInfo info = resources[i];
            handleAddition(info);
        }
    }

    /**
     * Handle the addition of the given sync info to this provider
     * @param info the added sync info
     */
    protected abstract void handleAddition(SyncInfo info);

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.AbstractSynchronizeModelProvider#handleResourceChanges(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
     */
    protected final void handleResourceChanges(ISyncInfoTreeChangeEvent event) {
        SyncInfo[] infos = event.getChangedResources();
        for (int i = 0; i < infos.length; i++) {
            SyncInfo info = infos[i];
            handleChange(info);
        }
    }
    
    /**
     * The state of the sync info for a resource has changed. Propogate the
     * change to any sub-providers that contain the resource.
     * @param info the sync info for the resource whpose sync state has changed
     */
    protected void handleChange(SyncInfo info) {
        handleRemoval(info.getLocal());
        handleAddition(info);
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.AbstractSynchronizeModelProvider#handleResourceRemovals(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
     */
    protected final void handleResourceRemovals(ISyncInfoTreeChangeEvent event) {
        IResource[] resources = event.getRemovedResources();
        for (int i = 0; i < resources.length; i++) {
            IResource resource = resources[i];
            handleRemoval(resource);
        }
    }

    /**
     * Remove the resource from all providers that are displaying it
     * @param resource the resource to be removed
     */
    protected void handleRemoval(IResource resource) {
        AbstractSynchronizeModelProvider[] providers = getProvidersContaining(resource);
        for (int i = 0; i < providers.length; i++) {
            AbstractSynchronizeModelProvider provider = providers[i];
            removeFromProvider(resource, provider);
        }
    }
    
    /**
     * Remove the resource from the sync set of the given provider
     * unless the provider is this composite. Subclasses can 
     * override if they show resources directly.
     * @param resource the resource to be removed
     * @param provider the provider from which to remove the resource
     */
    protected void removeFromProvider(IResource resource, AbstractSynchronizeModelProvider provider) {
        if (provider != this) {
            provider.getSyncInfoSet().remove(resource);
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.AbstractSynchronizeModelProvider#nodeAdded(org.eclipse.team.ui.synchronize.ISynchronizeModelElement)
     */
	protected void nodeAdded(ISynchronizeModelElement node, AbstractSynchronizeModelProvider provider) {
		// Update the resource-to-element map and the element-to-provider map
		IResource r = node.getResource();
		if(r != null) {
			List elements = (List)resourceToElements.get(r);
			if(elements == null) {
				elements = new ArrayList(2);
				resourceToElements.put(r, elements);
			}
			elements.add(node);
		}
		elementToProvider.put(node, provider);
		super.nodeAdded(node, provider);
	}
	
	/* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.AbstractSynchronizeModelProvider#nodeRemoved(org.eclipse.team.ui.synchronize.ISynchronizeModelElement)
     */
    protected void nodeRemoved(ISynchronizeModelElement node, AbstractSynchronizeModelProvider provider) {
        // Update the resource-to-element map and the element-to-provider map
	    IResource r = node.getResource();
		if(r != null) {
			List elements = (List)resourceToElements.get(r);
			if(elements != null) {
				elements.remove(node);
				if (elements.isEmpty()) {
				    resourceToElements.remove(r);
				}
			}
		}
		elementToProvider.remove(node);
        super.nodeRemoved(node, provider);
    }
    
}
