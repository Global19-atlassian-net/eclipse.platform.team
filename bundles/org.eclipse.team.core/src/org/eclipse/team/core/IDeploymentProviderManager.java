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
package org.eclipse.team.core;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;

/**
 * Manages deployment providers. Clients can programatically map and unmap deployment
 * providers to containers. 
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 * @see RepositoryProvider
 * @see DeploymentProvider
 * @since 3.0
 */
public interface IDeploymentProviderManager {
	/**
	 * Maps a container to the the given provider. Mappings are persisted across 
	 * workbench sessions.
	 * 
	 * @param container the container to be mapped to the given provider
	 * @param provider the provider to be mapped to the container
	 * @throws TeamException 
	 */
	public void map(IContainer container, DeploymentProvider provider) throws TeamException;
	
	/**
	 * Unmaps the given provider from the container.
	 *   
	 * @param container the container to be unmapped from the given provider
	 * @param provider the provider to unmap from the container
	 * @throws TeamException
	 */
	public void unmap(IContainer container, DeploymentProvider provider) throws TeamException;
	
	/**
	 * Returns the provider associated with the given resource. This loads the provider
	 * if not already loaded, and can be long running. To check the existance of
	 * a particular mapping call {@link #getMappedTo(IResource, String)} instead.
	 * 
	 * @param resource the resource to retreive the mapping
	 * @return the mapping for the resource, or <code>null</code> if a mapping doesn't
	 * exist for that resource.
	 */
	public DeploymentProvider getMapping(IResource resource);
	
	/**
	 * Returns <code>true</code> if the resource is mapped to the provider with
	 * the given id, and <code>false</code> otherwise. This method is fast running 
	 * and won't load the provider.
	 *  
	 * @param resource the resource for which to check the mapping
	 * @param id the id of the provider
	 * @return <code>true</code> if the resource is mapped to the provider with
	 * the given id, and <code>false</code> otherwise.
	 */
	public boolean getMappedTo(IResource resource, String id);
	
	/**
	 * Return an array of all the resource roots that are mapped
	 * to a deployment providers with the given
	 * id. If id is <code>null</code>, the roots of all deployment providers
	 * are returned.
	 * @param id a deployment provider id or <code>null</code>
	 * @return all roots that are mapped to deployment providers with
	 * the given id
	 */
	public IResource[] getDeploymentProviderRoots(String id);
}