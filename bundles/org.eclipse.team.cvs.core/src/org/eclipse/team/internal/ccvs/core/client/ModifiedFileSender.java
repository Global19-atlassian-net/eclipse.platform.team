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
package org.eclipse.team.internal.ccvs.core.client;

 
import java.util.HashSet;
import java.util.Set;

import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;

/**
 * Visit the CVS file structure, only sending files if they are modified.
 */
class ModifiedFileSender extends FileStructureVisitor {

	private final Set modifiedFiles;
	
	public ModifiedFileSender(Session session) {
		super(session, false, true);
		modifiedFiles = new HashSet();
	}
	
	/**
	 * Override sendFile to only send modified files
	 */
	protected void sendFile(ICVSFile mFile) throws CVSException {
		// Only send the file if its modified
		if (mFile.isManaged() && mFile.isModified(null)) {
			super.sendFile(mFile);
			modifiedFiles.add(mFile);
		}
	}
	
	protected String getSendFileTitleKey() {
		return null;
	}
	
	/**
	 * Return all the files that have been send to the server
	 */
	public ICVSFile[] getModifiedFiles() {
		return (ICVSFile[]) modifiedFiles.toArray(new ICVSFile[modifiedFiles.size()]);
	}
}
