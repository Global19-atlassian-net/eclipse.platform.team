/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.model.WorkbenchContentProvider;

/**
 * This class acts as an adaptable list that will return the resources in the
 * hierarchy indicated by their paths
 */
public class AdaptableHierarchicalResourceList extends AdaptableResourceList {

	/**
	 * Constructor for AdaptableHierarchicalResourceList.
	 * @param resources
	 */
	public AdaptableHierarchicalResourceList(IResource[] resources) {
		super(resources);
	}

	/**
	 * @see org.eclipse.ui.model.IWorkbenchAdapter#getChildren(java.lang.Object)
	 */
	public Object[] getChildren(Object o) {
		return getChildenFor(WorkbenchPlugin.getPluginWorkspace().getRoot());
	}

	private IResource[] getChildenFor(IContainer parent) {
		Set children = new HashSet();
		IPath parentPath = parent.getFullPath();
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			IPath resourcePath = resource.getFullPath();
			if (parent instanceof IWorkspaceRoot) {
				children.add(((IWorkspaceRoot)parent).getProject(resourcePath.segment(0)));
			} else if (parentPath.isPrefixOf(resourcePath)) {
				IPath parentRelativePath = resourcePath.removeFirstSegments(parentPath.segmentCount());
				if (parentRelativePath.segmentCount() == 1) {
					children.add(resource);
				} else if (parentRelativePath.segmentCount() > 1) {
					children.add(parent.getFolder(new Path(parentRelativePath.segment(0))));
				}
			}
		}
		return (IResource[]) children.toArray(new IResource[children.size()]);
	}
	
	/**
	 * Returns a content provider for <code>IResource</code>s that returns 
	 * only children of the given resource type.
	 */
	public ITreeContentProvider getTreeContentProvider() {
		return new WorkbenchContentProvider() {
			public Object[] getChildren(Object o) {
				if (o instanceof IContainer) {
					return getChildenFor((IContainer) o);
				} else {
					return super.getChildren(o);
				}
			}
		};
	}
	
	public void setResources(IResource[] resources) {
		this.resources = resources;
	}
}
