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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;

/**
 * The DateTagCategory is the parent of all the date tags in the repositories view.
 */
public class DateTagCategory extends TagCategory {

	public DateTagCategory(ICVSRepositoryLocation repository) {
		super(repository);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.model.TagCategory#getTags(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected CVSTag[] getTags(IProgressMonitor monitor) throws CVSException {
		return CVSUIPlugin.getPlugin().getRepositoryManager().getRepositoryRootFor(repository).getDateTags();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.model.IWorkbenchAdapter#getImageDescriptor(java.lang.Object)
	 */
	public ImageDescriptor getImageDescriptor(Object object) {
		// TODO Auto-generated method stub
		return CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_TAG);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.model.IWorkbenchAdapter#getLabel(java.lang.Object)
	 */
	public String getLabel(Object o) {
		return "Dates";
	}

}
