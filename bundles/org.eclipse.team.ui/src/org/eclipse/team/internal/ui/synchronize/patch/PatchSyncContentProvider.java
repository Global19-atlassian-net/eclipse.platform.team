/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.synchronize.patch;

import org.eclipse.compare.internal.patch.PatchDiffNode;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.team.core.mapping.ISynchronizationContext;
import org.eclipse.team.core.mapping.ISynchronizationScope;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.mapping.SynchronizationResourceMappingContext;
import org.eclipse.team.ui.mapping.SynchronizationContentProvider;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;

public class PatchSyncContentProvider extends SynchronizationContentProvider {

	private PatchWorkbenchContentProvider delegate;

	public void init(ICommonContentExtensionSite site) {
		super.init(site);
		delegate = new PatchWorkbenchContentProvider(/*getPatcher()*/);
		// delegate.init(site);
	}

	public void dispose() {
		super.dispose();
		if (delegate != null)
			delegate.dispose();
	}

	protected ITreeContentProvider getDelegateContentProvider() {
		return delegate;
	}

	protected String getModelProviderId() {
		return PatchModelProvider.ID;
	}

	protected Object getModelRoot() {
		return PatchWorkspace.getInstance();
	}

	/*
	 * Copied from
	 * org.eclipse.team.examples.model.ui.mapping.ModelSyncContentProvider
	 * .getTraversals(ISynchronizationContext, Object)
	 */
	protected ResourceTraversal[] getTraversals(
			ISynchronizationContext context, Object object) {
		if (object instanceof IDiffElement) {
			ResourceMapping mapping = PatchModelProvider.getResourceMapping((IDiffElement) object);
			ResourceMappingContext rmc = new SynchronizationResourceMappingContext(
					context);
			try {
				return mapping.getTraversals(rmc, new NullProgressMonitor());
			} catch (CoreException e) {
				TeamUIPlugin.log(e);
			}
		}
		return new ResourceTraversal[0];
	}

	protected Object[] getChildrenInContext(ISynchronizationContext context,
			Object parent, Object[] children) {
		// TODO Auto-generated method stub
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < children.length; i++) {
			sb.append(children[i].toString()).append(","); //$NON-NLS-1$
		}
		System.out
				.println(">> [super] PatchSyncContentProvider.getChildrenInContext: context-> " + context + "; parent-> " + parent.toString() + "; children-> " + sb.toString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return super.getChildrenInContext(context, parent, children);
	}

	protected boolean isInScope(ISynchronizationScope scope, Object parent,
			Object element) {
		if (element instanceof PatchDiffNode) {
			final IResource resource = PatchModelProvider
					.getResource((PatchDiffNode) element);
			if (resource == null)
				return false;
			if (scope.contains(resource))
				return true;
		}
		return false;
	}
}
