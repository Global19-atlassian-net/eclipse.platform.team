package org.eclipse.team.internal.ccvs.ui.model;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.ui.PlatformUI;

public class RemoteFileElement extends RemoteResourceElement {
	/**
	 * Initial implementation: return null;
	 */
	public Object[] internalGetChildren(Object o, IProgressMonitor monitor) {
		return new Object[0];
	}
	/**
	 * Initial implementation: return null.
	 */
	public ImageDescriptor getImageDescriptor(Object object) {
		if (!(object instanceof ICVSRemoteFile)) return null;
		return PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(((ICVSRemoteFile)object).getName());
	}
	/**
	 * Initial implementation: return the file's name and version
	 */
	public String getLabel(Object o) {
		if (!(o instanceof ICVSRemoteFile)) return null;
		ICVSRemoteFile file = (ICVSRemoteFile)o;
		try {
			return Policy.bind("nameAndRevision", file.getName(), file.getRevision()); //$NON-NLS-1$
		} catch (TeamException e) {
			handle(e);
			return null;
		}
	}
}