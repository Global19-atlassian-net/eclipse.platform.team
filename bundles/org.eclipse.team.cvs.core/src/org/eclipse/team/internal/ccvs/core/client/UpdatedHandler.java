package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.util.Date;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.syncinfo.MutableResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;

/**
 * Handles any "Updated" and "Merged" responses
 * from the CVS server.
 * <p>
 * Suppose as a result of performing a command the CVS server responds
 * as follows:<br>
 * <pre>
 *   [...]
 *   Updated ???\n
 *   [...]
 * </pre>
 * Then 
 * </p>
 */

/**
 * Does get information about the file that is updated
 * and the file-content itself and puts it on the fileSystem.
 * 
 * The difference beetween the "Updated" and the "Merged" is, that
 * an "Merged" file is not going to be up-to-date after the operation.
 * 
 * Requiers a exisiting parent-folder.
 */
class UpdatedHandler extends ResponseHandler {
	
	private int handlerType;
	
	protected static final int HANDLE_UPDATED = ICVSFile.UPDATED;
	protected static final int HANDLE_MERGED = ICVSFile.MERGED;
	protected static final int HANDLE_UPDATE_EXISTING = ICVSFile.UPDATE_EXISTING;
	protected static final int HANDLE_CREATED = ICVSFile.CREATED;
	
	private static final String READ_ONLY_FLAG = "u=rw"; //$NON-NLS-1$
	
	public UpdatedHandler(int handlerType) {
		this.handlerType = handlerType;
	}
	
	public String getResponseID() {
		switch (handlerType) {
			case HANDLE_UPDATED: return "Updated"; //$NON-NLS-1$
			case HANDLE_MERGED: return "Merged"; //$NON-NLS-1$
			case HANDLE_UPDATE_EXISTING: return "Update-existing"; //$NON-NLS-1$
			case HANDLE_CREATED: return "Created"; //$NON-NLS-1$
		}
		return null;
	}

	public void handle(Session session, String localDir,
		IProgressMonitor monitor) throws CVSException {
		// read additional data for the response
		String repositoryFile = session.readLine();
		String entryLine = session.readLine();
		String permissionsLine = session.readLine();
		// temporary sync info for parsing the line received from the server
		ResourceSyncInfo info = new ResourceSyncInfo(entryLine, permissionsLine, null);

		// clear file update modifiers
		Date modTime = session.getModTime();
		session.setModTime(null);
		
		// Get the local file
		String fileName = repositoryFile.substring(repositoryFile.lastIndexOf("/") + 1); //$NON-NLS-1$
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
				Assert.isTrue(mParent.exists());
			}
		}
		ICVSFile mFile = mParent.getFile(fileName);
		
		boolean binary = info.getKeywordMode().isBinary();
		boolean readOnly = info.getPermissions().indexOf(READ_ONLY_FLAG) == -1;
		
		// The file may have been set as read-only by a previous checkout/update
		if (mFile.isReadOnly()) mFile.setReadOnly(false);
		try {
			session.receiveFile(mFile, binary, handlerType, monitor);
		} catch (CVSException e) {
			if (e.getStatus().getCode() == IResourceStatus.CASE_VARIANT_EXISTS) {
				// Record that we have a case collision and continue;
				session.addCaseCollision(new Path(localDir).append(fileName).toString(), Path.EMPTY.toString());
				return;
			} else {
				throw e;
			}
		}
		if (readOnly) mFile.setReadOnly(true);
		
		// Set the timestamp in the file and get it again so that we use the *real* timestamp
		// in the sync info. The os may not actually set the time we provided :)
		mFile.setTimeStamp(modTime);
		modTime = mFile.getTimeStamp();
		MutableResourceSyncInfo newInfoWithTimestamp = info.cloneMutable();
		newInfoWithTimestamp.setTimeStamp(modTime);
		if(handlerType==HANDLE_MERGED) {
			newInfoWithTimestamp.setMerged();
		} else if (!session.isIgnoringLocalChanges() && (handlerType==HANDLE_UPDATE_EXISTING || handlerType==HANDLE_CREATED)) {
			// both these cases result in an unmodified file.
			// reporting is handled by the FileModificationManager
			newInfoWithTimestamp.reported();
			CVSProviderPlugin.getPlugin().getFileModificationManager().updated(mFile);
		}
		mFile.setSyncInfo(newInfoWithTimestamp);
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

}