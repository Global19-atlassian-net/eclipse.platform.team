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


import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.Util;

/**
 * Handles server responses that arise as a result of issuing a request
 * (usually a command) to a CVS server.  The processing of each such
 * response is deferred to subclasses.
 */
public abstract class ResponseHandler {
	/**
	 * Returns the text string of the server response handled by this object.
	 * @return the id
	 */
	public abstract String getResponseID();

	/**
	 * Handles a server response.
	 * <p>
	 * Suppose as a result of performing a command the CVS server responds
	 * as follows:<br>
	 * <pre>
	 *   [...]
	 *   Clear-sticky myDirectory \n
	 *   /u/cvsroot/myDirectory \n
	 *   [...]
	 * </pre>
	 * Then the <code>handle</code> method of the <code>ResponseHandler</code>
	 * for <em>Clear-sticky</em> will be invoked with <code>argument</code>
	 * set to <em>"myDirectory"</em>.  It must then read the remaining
	 * response data from the connection (<em>"/u/cvsroot/myDirectory"</em>
	 * including the newline) and take any necessary action.
	 * </p><p>
	 * Note:  The type and quantity of additional data that must be read
	 * from the connection varies on a per-response basis.
	 * </p>
	 * @param session the Session used for CVS communication
	 * @param argument the argument supplied with the response
	 * @param monitor the progress monitor for the current CVS command
	 */
	public abstract void handle(Session session, String argument,
		IProgressMonitor monitor) throws CVSException;
	
	/**
	 * Creates a new CVS folder.
	 * @param localDir the local path of the folder relative to root
	 * @param repositoryDir the remote path of the folder relative to the repository
	 * @return the new folder
	 */
	protected static ICVSFolder createFolder(
			Session session,
			String localDir, 
			String repositoryDir) throws CVSException {
		
		ICVSFolder folder = session.getLocalRoot().getFolder(localDir);
		if (! folder.exists()) {
			try {
				folder.mkdir();
			} catch (CVSException original) {
				boolean caseInvariant = false;
				switch (original.getStatus().getCode()) {
					case IResourceStatus.CASE_VARIANT_EXISTS :
						// We will try to create the mapped child below.
						caseInvariant = true;
						break;

					case IResourceStatus.RESOURCE_NOT_FOUND :
						//	The parent of the folder doesn't exist. It could be due to case invariance.
						// Check if there is a case invariant mapping for the folder
						String actualLocalDir = session.getUniquePathForCaseSensitivePath(localDir, false);
						folder = session.getLocalRoot().getFolder(actualLocalDir);
						try {
							if (! folder.exists()) folder.mkdir();
							// We succeed in creating the child of a mapped parent
							// Since caseInvariant is false, we will fall through
						} catch (CVSException ex) {
							if (ex.getStatus().getCode() == IResourceStatus.CASE_VARIANT_EXISTS) {
								// We will try to create he mapped child below.
								caseInvariant = true;
							} else {
								// The attempt to get the mapped parent failed.
								// Throw the original exception
								throw original;
							}
						}
						break;
						
					case IResourceStatus.INVALID_VALUE :
						// the folder name is not supported by the platform. Try to compensate.
						String validLocalDir = session.getUniquePathForInvalidPath(localDir);
						folder = session.getLocalRoot().getFolder(validLocalDir);
						try {
							if (! folder.exists()) folder.mkdir();
							// We succeed in creating the child of a mapped parent
							// Since caseInvariant is false, we will fall through
						} catch (CVSException ex) {
							// The attempt to get a unique path failed.
							// Throw the original exception
							throw original;
						}
						break;
					default :
						throw original;
						
				}
				if (caseInvariant) {
					// Change the name (last segment) of the localDir to a unique name for the case invariant one
					String newlocalDir = session.getUniquePathForCaseSensitivePath(localDir, true);
					folder = session.getLocalRoot().getFolder(newlocalDir);
					if (! folder.exists()) folder.mkdir();
					// Signal to the session that there is a renamed folder.
					session.addCaseCollision(localDir, newlocalDir);
				}
			}
		}
		if (! folder.isCVSFolder()) {
			folder.setFolderSyncInfo(new FolderSyncInfo(
				Util.getRelativePath(session.getRepositoryRoot(), repositoryDir),
				session.getCVSRepositoryLocation().getLocation(),
				null, false));
		}
		return folder;
	}

	protected ICVSFolder getExistingFolder(Session session, String localDir)
		throws CVSException {
			ICVSFolder mParent = session.getLocalRoot().getFolder(localDir);
			if (! mParent.exists()) {
				// First, check if the parent is a phantom
				IContainer container = (IContainer)mParent.getIResource();
				if (container != null && container.isPhantom()) {
					// Create all the parents as need
					recreatePhatomFolders(mParent);
				} else {
					// It is possible that we have a case variant.
					localDir = session.getUniquePathForCaseSensitivePath(localDir, false);
					mParent = session.getLocalRoot().getFolder(localDir);
					if (!mParent.exists()) {
						// It is also possible that the path is invalid for this platform
						localDir = session.getUniquePathForInvalidPath(localDir);
						mParent = session.getLocalRoot().getFolder(localDir);
						Assert.isTrue(mParent.exists());
					}
				}
			}
			return mParent;
		}

	/**
	 * Method recreatePhatomFolders.
	 * @param mParent
	 */
	private void recreatePhatomFolders(ICVSFolder folder) throws CVSException {
		ICVSFolder parent = folder.getParent();
		if (!parent.exists()) {
			recreatePhatomFolders(parent);
		}
		folder.mkdir();
	}

	/**
	 * Return as instance that can be used by an open session. Subclasses that contain
	 * session related state must override this message to return a copy of themselves.
	 */
	/* package */ ResponseHandler getInstance() {
		return this;
	}
}

