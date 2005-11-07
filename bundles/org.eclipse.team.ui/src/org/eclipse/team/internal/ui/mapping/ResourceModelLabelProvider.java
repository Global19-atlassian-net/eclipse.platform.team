/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.mapping;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.ui.mapping.ISynchronizationContext;

/**
 * Resource label provider that can decorate using sync state.
 */
public class ResourceModelLabelProvider extends
		SynchronizationOperationLabelProvider {

	private ILabelProvider provider = new ResourceMappingLabelProvider();

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.SynchronizationOperationLabelProvider#getModelLabelProvider()
	 */
	protected ILabelProvider getModelLabelProvider() {
		return provider ;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.SynchronizationOperationLabelProvider#getSyncKind(java.lang.Object)
	 */
	protected int getSyncKind(Object element) {
		IResource resource = getResource(element);
		if (resource != null) {
			ISynchronizationContext context = getContext();
			if (context != null) {
				SyncInfo info = context.getSyncInfoTree().getSyncInfo(resource);
				if (info != null)
					return info.getKind();
			}
		}
			
		return SyncInfo.IN_SYNC;
	}

	private IResource getResource(Object element) {
		if (element instanceof IResource) {
			return (IResource) element;
		}
		if (element instanceof ResourceAndDepth) {
			return ((ResourceAndDepth) element).getResource();
			
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.navigator.IDescriptionProvider#getDescription(java.lang.Object)
	 */
	public String getDescription(Object anElement) {
		// TODO Auto-generated method stub
		return null;
	}

}
