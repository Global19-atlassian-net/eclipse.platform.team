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
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class RemoteFolderElement extends RemoteResourceElement {
	
	/**
	 * Overridden to append the version name to remote folders which
	 * have version tags and are top-level folders.
	 */
	public String getLabel(Object o) {
		if (!(o instanceof ICVSRemoteFolder)) return null;
		ICVSRemoteFolder folder = (ICVSRemoteFolder)o;
		CVSTag tag = folder.getTag();
		if (tag != null && tag.getType() != CVSTag.HEAD) {
			if (folder.getRemoteParent() == null) {
				return Policy.bind("RemoteFolderElement.nameAndTag", folder.getName(), tag.getName()); //$NON-NLS-1$
			}
		}
		return folder.getName();
	}
	public ImageDescriptor getImageDescriptor(Object object) {
		if (!(object instanceof ICVSRemoteFolder)) return null;
		ICVSRemoteFolder folder = (ICVSRemoteFolder) object;
		if (folder.isDefinedModule()) {
			return CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_MODULE);
		}
		return PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_FOLDER);
	}
	
	public boolean isDeferred() {
		return true;
	}
	
	public String getUniqueId() {
		return "org.eclipse.team.cvs.ui.remotefolderelement";
	}

	public Object[] fetchChildren(Object o, IProgressMonitor monitor) throws TeamException {
		if (!(o instanceof ICVSRemoteFolder)) return new Object[0];
		return ((ICVSRemoteFolder)o).members(monitor);
	}
}
