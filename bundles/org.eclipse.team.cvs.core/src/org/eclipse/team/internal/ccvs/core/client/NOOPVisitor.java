/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.core.client;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.syncinfo.NotifyInfo;

/**
 * Send the contents of the CVS/Notify files to the server
 */
public class NOOPVisitor extends AbstractStructureVisitor {

	public NOOPVisitor(Session session, IProgressMonitor monitor) {
		// Only send non-empty folders
		super(session, false, false, monitor);
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.core.ICVSResourceVisitor#visitFile(ICVSFile)
	 */
	public void visitFile(ICVSFile file) throws CVSException {
		sendPendingNotification(file);
	}

	/**
	 * @see org.eclipse.team.internal.ccvs.core.ICVSResourceVisitor#visitFolder(ICVSFolder)
	 */
	public void visitFolder(ICVSFolder folder) throws CVSException {
		if (folder.isCVSFolder()) {
			folder.acceptChildren(this);
		}
	}

}
