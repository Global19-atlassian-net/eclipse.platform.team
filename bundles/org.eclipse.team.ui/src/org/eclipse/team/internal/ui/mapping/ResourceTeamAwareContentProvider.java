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
package org.eclipse.team.internal.ui.mapping;

import java.util.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.mapping.*;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.team.ui.mapping.IResourceMappingScope;
import org.eclipse.ui.model.WorkbenchContentProvider;

/**
 * This content provider displays the mappings as a flat list 
 * of elements.
 * <p>
 * There are three use-cases we need to consider. The first is when there
 * are resource level mappings to be displayed. The second is when there
 * are mappings from a model provider that does not have a content provider
 * registered. The third is for the case where a resource mapping does not
 * have a model provider registered (this may be considered an error case).
 *
 */
public class ResourceTeamAwareContentProvider extends AbstractTeamAwareContentProvider {

	private WorkbenchContentProvider provider;

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.AbstractTeamAwareContentProvider#getDelegateContentProvider()
	 */
	protected ITreeContentProvider getDelegateContentProvider() {
		if (provider == null)
			provider = new WorkbenchContentProvider();
		return provider;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.AbstractTeamAwareContentProvider#getModelProviderId()
	 */
	protected String getModelProviderId() {
		return ModelProvider.RESOURCE_MODEL_PROVIDER_ID;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.AbstractTeamAwareContentProvider#getModelRoot()
	 */
	protected Object getModelRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.AbstractTeamAwareContentProvider#isInScope(java.lang.Object, java.lang.Object)
	 */
	protected boolean isInScope(Object parent, Object object) {
		if (object instanceof IResource) {
			IResource resource = (IResource) object;
			if (resource == null)
				return false;
			if (getScope().contains(resource))
				return true;
			if (hasChildrenInScope(object, resource)) {
				return true;
			}
		}
		return false;
	}
	
	private boolean hasChildrenInScope(Object object, IResource resource) {
		IResource[] roots = getScope().getRoots();
		for (int i = 0; i < roots.length; i++) {
			IResource root = roots[i];
			if (resource.getFullPath().isPrefixOf(root.getFullPath()))
				return true;
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.AbstractTeamAwareContentProvider#dispose()
	 */
	public void dispose() {
		provider.dispose();
		super.dispose();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.AbstractTeamAwareContentProvider#getTraversals(java.lang.Object)
	 */
	protected ResourceTraversal[] getTraversals(Object object) {
		IResourceMappingScope scope = getScope();
		// First see if the object is a root of the scope
		ResourceMapping mapping = scope.getMapping(object);
		if (mapping != null)
			return scope.getTraversals(mapping);
		// Next, check if the object is within the scope
		if (object instanceof IResource) {
			IResource resource = (IResource) object;
			if (scope.contains(resource)) {
				List result = new ArrayList();
				ResourceTraversal[] traversals = scope.getTraversals();
				for (int i = 0; i < traversals.length; i++) {
					ResourceTraversal traversal = traversals[i];
					if (traversal.contains(resource)) {
						boolean include = false;
						int depth = traversal.getDepth();
						if (depth == IResource.DEPTH_INFINITE) {
							include = true;
						} else {
							IResource[] roots = traversal.getResources();
							for (int j = 0; j < roots.length; j++) {
								IResource root = roots[j];
								if (root.equals(resource)) {
									include = true;
									break;
								}
								if (root.getFullPath().equals(resource.getFullPath().removeLastSegments(1)) && depth == IResource.DEPTH_ONE) {
									include = true;
									depth = IResource.DEPTH_ZERO;
									break;
								}
							}
						}
						if (include)
							result.add(new ResourceTraversal(new IResource[] { resource}, depth, IResource.NONE));
					}
				}
				return (ResourceTraversal[]) result.toArray(new ResourceTraversal[result.size()]);
			} else {
				// The resource is a parent of an in-scope resource
				IResource[] roots = scope.getRoots();
				List result = new ArrayList();
				for (int i = 0; i < roots.length; i++) {
					IResource root = roots[i];
					if (resource.getFullPath().isPrefixOf(root.getFullPath()));
					mapping = scope.getMapping(object);
					if (mapping != null) {
						ResourceTraversal[] traversals = scope.getTraversals(mapping);
						result.addAll(Arrays.asList(traversals));
					}
				}
				return (ResourceTraversal[]) result.toArray(new ResourceTraversal[result.size()]);
			}
		}
		return new ResourceTraversal[0];
	}

}