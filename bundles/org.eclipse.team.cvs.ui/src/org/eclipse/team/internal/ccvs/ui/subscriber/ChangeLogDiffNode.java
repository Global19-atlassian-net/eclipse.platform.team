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
package org.eclipse.team.internal.ccvs.ui.subscriber;

import java.text.DateFormat;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.team.internal.ccvs.core.ILogEntry;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ui.synchronize.SynchronizeModelElement;
import org.eclipse.team.ui.synchronize.ISynchronizeModelElement;

public class ChangeLogDiffNode extends SynchronizeModelElement {

	private ILogEntry logEntry;

	public ChangeLogDiffNode(ISynchronizeModelElement parent, ILogEntry logEntry) {
		super(parent);
		this.logEntry = logEntry;
	}

	public ILogEntry getComment() {
		return logEntry;
	}
	
	public boolean equals(Object other) {
		if(other == this) return true;
		if(! (other instanceof ChangeLogDiffNode)) return false;
		return ((ChangeLogDiffNode)other).getComment().equals(getComment());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.model.IWorkbenchAdapter#getImageDescriptor(java.lang.Object)
	 */
	public ImageDescriptor getImageDescriptor(Object object) {
		return CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_DATE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.compare.structuremergeviewer.DiffNode#getName()
	 */
	public String getName() {
		String date = DateFormat.getDateTimeInstance().format(logEntry.getDate());
		return date + ": " + logEntry.getComment() + " (" + logEntry.getAuthor() +")";
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.SyncInfoModelElement#toString()
	 */
	public String toString() {
		return getName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelElement#getResource()
	 */
	public IResource getResource() {
		return null;
	}
}
