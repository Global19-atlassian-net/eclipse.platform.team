/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.core.mapping;

import java.util.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.team.core.mapping.IResourceMappingScope;
import org.eclipse.team.core.mapping.PropertyChangeEvent;
import org.eclipse.team.core.mapping.provider.ScopeGenerator;

/**
 * Concrete implementation of the {@link IResourceMappingScope}
 * interface for use by clients.
 * <p>
 * This class is not intended to be subclasses by clients
 * 
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is a guarantee neither that this API will
 * work nor that it will remain the same. Please do not use this API without
 * consulting with the Platform/Team team.
 * </p>
 * 
 * @see org.eclipse.core.resources.mapping.ResourceMapping
 * 
 * @since 3.2
 */
public class ResourceMappingScope extends AbstractResourceMappingScope {
	
	private ResourceMapping[] inputMappings;
	private final Map mappingsToTraversals = new HashMap();
	private boolean hasAdditionalMappings;
	private boolean hasAdditionalResources;
	private final ScopeGenerator generator;
	private final CompoundResourceTraversal compoundTraversal = new CompoundResourceTraversal();
	
	public static ResourceTraversal[] combineTraversals(ResourceTraversal[] allTraversals) {
		Set zero = new HashSet();
		Set shallow = new HashSet();
		Set deep = new HashSet();
		for (int i = 0; i < allTraversals.length; i++) {
			ResourceTraversal traversal = allTraversals[i];
			switch (traversal.getDepth()) {
			case IResource.DEPTH_ZERO:
				zero.addAll(Arrays.asList(traversal.getResources()));
				break;
			case IResource.DEPTH_ONE:
				shallow.addAll(Arrays.asList(traversal.getResources()));
				break;
			case IResource.DEPTH_INFINITE:
				deep.addAll(Arrays.asList(traversal.getResources()));
				break;
			}
		}
		List result = new ArrayList();
		if (!zero.isEmpty()) {
			result.add(new ResourceTraversal((IResource[]) zero.toArray(new IResource[zero.size()]), IResource.DEPTH_ZERO, IResource.NONE));
		}
		if (!shallow.isEmpty()) {
			result.add(new ResourceTraversal((IResource[]) shallow.toArray(new IResource[shallow.size()]), IResource.DEPTH_ONE, IResource.NONE));
		}
		if (!deep.isEmpty()) {
			result.add(new ResourceTraversal((IResource[]) deep.toArray(new IResource[deep.size()]), IResource.DEPTH_INFINITE, IResource.NONE));
		}
		return (ResourceTraversal[]) result.toArray(new ResourceTraversal[result.size()]);
	}
	
	/**
	 * @param selectedMappings
	 */
	public ResourceMappingScope(ScopeGenerator generator, ResourceMapping[] selectedMappings) {
		this.generator = generator;
		inputMappings = selectedMappings;
	}
	
	/**
	 * Add the mapping and its traversals to the scope. This method should
	 * only be invoked during the scope building process.
	 * @param mapping the mapping being added to the scope
	 * @param traversals the traversals for that mapping
	 */
	public ResourceTraversal[] addMapping(ResourceMapping mapping, ResourceTraversal[] traversals) {
		ResourceTraversal[] newTraversals = compoundTraversal.getUncoveredTraversals(traversals);
		mappingsToTraversals.put(mapping, traversals);
		compoundTraversal.addTraversals(traversals);
		return newTraversals;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.IResourceMappingOperationScope#getInputMappings()
	 */
	public ResourceMapping[] getInputMappings() {
		return inputMappings;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.IResourceMappingOperationScope#getMappings()
	 */
	public ResourceMapping[] getMappings() {
		return (ResourceMapping[]) mappingsToTraversals.keySet().toArray(new ResourceMapping[mappingsToTraversals.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.IResourceMappingOperationScope#getTraversals()
	 */
	public ResourceTraversal[] getTraversals() {
		return compoundTraversal.asTraversals();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.IResourceMappingOperationScope#getTraversals(org.eclipse.core.resources.mapping.ResourceMapping)
	 */
	public ResourceTraversal[] getTraversals(ResourceMapping mapping) {
		return (ResourceTraversal[])mappingsToTraversals.get(mapping);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.IResourceMappingOperationScope#hasAdditionalMappings()
	 */
	public boolean hasAdditionalMappings() {
		return hasAdditionalMappings;
	}
	
	/**
	 * Set whether the scope has additional mappings to the input mappings.
	 * This method should only be invoked during the scope building process.
	 * @param hasAdditionalMappings whether the scope has additional mappings
	 */
	public void setHasAdditionalMappings(boolean hasAdditionalMappings) {
		this.hasAdditionalMappings = hasAdditionalMappings;
	}

	/**
	 * Set whether this scope has additional resources.
	 * This method should only be invoked during the scope building process.
	 * @param hasAdditionalResources whether the scope has additional resources
	 */
	public void setHasAdditionalResources(boolean hasAdditionalResources) {
		this.hasAdditionalResources = hasAdditionalResources;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.mapping.IResourceMappingScope#hasAdditonalResources()
	 */
	public boolean hasAdditonalResources() {
		return hasAdditionalResources;
	}

	public ScopeGenerator getGenerator() {
		return generator;
	}
	
	public void fireTraversalsChangedEvent(ResourceTraversal[] oldTraversals) {
		firePropertyChangedEvent(new PropertyChangeEvent(this, IResourceMappingScope.TRAVERSALS, oldTraversals, getTraversals()));
	}

	public CompoundResourceTraversal getCompoundTraversal() {
		return compoundTraversal;
	}

	public void fireMappingChangedEvent(ResourceMapping[] originalMappings) {
		firePropertyChangedEvent(new PropertyChangeEvent(this, IResourceMappingScope.MAPPINGS, originalMappings, getMappings()));
	}
}
