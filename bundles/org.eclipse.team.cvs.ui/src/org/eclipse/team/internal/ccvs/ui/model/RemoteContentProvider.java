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
package org.eclipse.team.internal.ccvs.ui.model;
 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.RemoteResource;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.model.WorkbenchContentProvider;

/**
 * Extension to the generic workbench content provider mechanism
 * to lazily determine whether an element has children.  That is,
 * children for an element aren't fetched until the user clicks
 * on the tree expansion box.
 */
public class RemoteContentProvider extends WorkbenchContentProvider {
	
	IWorkingSet workingSet;
	Map cache = new HashMap();
	
	/* (non-Javadoc)
	 * Method declared on WorkbenchContentProvider.
	 */
	public boolean hasChildren(Object element) {
		if (element == null) {
			return false;
		}
		// the + box will always appear, but then disappear
		// if not needed after you first click on it.
		if (element instanceof ICVSRemoteResource) {
			if (element instanceof ICVSRemoteFolder) {
				return ((ICVSRemoteFolder)element).isExpandable();
			}
			return ((ICVSRemoteResource)element).isContainer();
		} else if(element instanceof CVSResourceElement) {
			ICVSResource r = ((CVSResourceElement)element).getCVSResource();
			if(r instanceof RemoteResource) {
				return r.isFolder();
			}
		} else if(element instanceof VersionCategory) {
			return true;
		} else if(element instanceof BranchCategory) {
			return true;
		} else if(element instanceof ModulesCategory) {
			return true;
		} else if(element instanceof CVSTagElement) {
			return true;
		} else if(element instanceof RemoteModule) {
			return true;
		}
		return super.hasChildren(element);
	}
	
	/**
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
	 */
	public Object[] getChildren(Object parentElement) {
		IWorkbenchAdapter adapter = getAdapter(parentElement);
		if (adapter instanceof CVSModelElement) {
			CVSModelElement element = (CVSModelElement)adapter;
			if (element.isDeferred()) {
				List result = (List)cache.get(parentElement);
				if (result != null) return (Object[]) result.toArray(new Object[result.size()]);
				// otherwise, start the deferred fetch
				element.getChildrenDeferred(this, parentElement, workingSet);
				return new Object[0];
			} else {
				return ((CVSModelElement)adapter).getChildren(parentElement, workingSet);
			}
		}
		return super.getChildren(parentElement);
	}

	/**
	 * Sets the workingSet.
	 * @param workingSet The workingSet to set
	 */
	public void setWorkingSet(IWorkingSet workingSet) {
		this.workingSet = workingSet;
	}

	/**
	 * Returns the workingSet.
	 * @return IWorkingSet
	 */
	public IWorkingSet getWorkingSet() {
		return workingSet;
	}

	/**
	 * @param parent
	 * @param children
	 */
	protected void addChildren(Object parent, ICVSRemoteResource[] children) {
		List cachedChildren = (List)cache.get(parent);
		if (cachedChildren == null) {
			cachedChildren = new ArrayList();
			cache.put(parent, cachedChildren);
		}
		cachedChildren.addAll(Arrays.asList(children));
		viewer.getControl().getDisplay().asyncExec(new Runnable() {
			public void run() {
				viewer.refresh();
			}
		});
	}

	public void clearCache() {
		cache.clear();
	}
}
