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
package org.eclipse.team.internal.ccvs.ui.operations;

import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.ICVSResource;

public abstract class RemoteOperation extends CVSOperation {

	private ICVSRemoteResource[] remoteResources;

	protected RemoteOperation(Shell shell, ICVSRemoteResource[] remoteResources) {
		super(shell);
		this.remoteResources =remoteResources;
	}

	protected ICVSRemoteResource[] getRemoteResources() {
		return remoteResources;
	}
	
	public ICVSResource[] getCVSResources() {
		ICVSResource[] cvsResources = new ICVSResource[remoteResources.length];
		for (int i = 0; i < remoteResources.length; i++) {
			cvsResources[i] = remoteResources[i];
		}
		return cvsResources;
	}

}
