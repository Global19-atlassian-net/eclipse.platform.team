package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFile;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.util.Assert;

/**
 * Handles a "Removed" response from the CVS server.
 * <p>
 * Suppose as a result of performing a command the CVS server responds
 * as follows:<br>
 * <pre>
 *   [...]
 *   Removed ??? \n
 *   [...]
 * </pre>
 * Then 
 * </p>
 */

/**
 * It removes the file from both the entries of the parent-folder
 * and from the local filesystem.
 */
class RemovedHandler extends ResponseHandler {
	public String getResponseID() {
		return "Removed";
	}

	public void handle(Session session, String localDir,
		IProgressMonitor monitor) throws CVSException {
		// read additional data for the response
		String repositoryFile = session.readLine();

		// Get the local file		
		String fileName = repositoryFile.substring(repositoryFile.lastIndexOf("/") + 1);
		ICVSFolder mParent = session.getLocalRoot().getFolder(localDir);
		ICVSFile mFile = mParent.getFile(fileName);

		Assert.isTrue(mFile.exists() && mFile.isManaged());
		
		// delete then unmanage the file
		mFile.delete();
		mFile.unmanage();
	}
}

