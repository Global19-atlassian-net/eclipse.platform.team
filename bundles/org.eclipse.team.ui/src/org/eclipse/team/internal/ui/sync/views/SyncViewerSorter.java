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
package org.eclipse.team.internal.ui.sync.views;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.team.core.subscribers.SyncInfo;

/**
 * This class sorts the model elements that appear in the SyncViewer
 */
public class SyncViewerSorter extends ViewerSorter {

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerSorter#category(java.lang.Object)
	 */
	public int category(Object element) {
		IResource resource = SyncSet.getIResource(element);
		if (element instanceof SyncInfo) {
			SyncInfo info = (SyncInfo)element;
			switch(resource.getType()) {
				case IResource.PROJECT: return 1;
				case IResource.FOLDER: return 2;
				case IResource.FILE: return 3;
			}
		} else {
			switch(resource.getType()) {
				case IResource.PROJECT: return 1;
				case IResource.FOLDER: return 2;
			}
		}
		return super.category(element);
	}

}
