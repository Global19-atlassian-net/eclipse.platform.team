package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
 
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFile;

/**
 * Visit the CVS file structure, only sending files if they are modified.
 */
public class ModifiedFileSender extends FileStructureVisitor {

	public ModifiedFileSender(Session session, IProgressMonitor monitor) {
		super(session, false, true, monitor);
	}
	
	/**
	 * Override sendFile to only send modified files
	 */
	protected void sendFile(ICVSFile mFile) throws CVSException {

		// Only send the file if its modified
		if (mFile.isModified()) {
			super.sendFile(mFile);
		}
	}
}
