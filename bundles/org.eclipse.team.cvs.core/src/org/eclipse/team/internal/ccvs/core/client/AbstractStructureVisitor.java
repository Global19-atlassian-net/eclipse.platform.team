package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.CVSEntryLineTag;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFile;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.resources.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;

/**
 * An ICVSResourceVisitor that is superclass to all ICVSResourceVisitor's used
 * by Command and it's subclasses.
 * Provides helper methods to send files and folders with modifications
 * to the server.
 */
abstract class AbstractStructureVisitor implements ICVSResourceVisitor {
	
	protected Session session;
	private ICVSFolder lastFolderSent;
	protected IProgressMonitor monitor;
	protected boolean sendQuestionable;
	protected boolean sendModifiedContents;

	public AbstractStructureVisitor(Session session, boolean sendQuestionable, boolean sendModifiedContents, IProgressMonitor monitor) {
		this.session = session;
		this.sendQuestionable = sendQuestionable;
		this.sendModifiedContents = sendModifiedContents;
		this.monitor = monitor;
	}

	/** 
	 * Helper method to indicate if a directory has already been sent to the server
	 */
	protected boolean isLastSent(ICVSFolder folder) {
		return folder.equals(lastFolderSent);
	}
	
	/** 
	 * Helper method to record if a directory has already been sent to the server
	 */
	protected void recordLastSent(ICVSFolder folder) {
		lastFolderSent = folder;
	}
	
	/** 
	 * Helper which indicates if a folder is an orphaned subtree. 
	 * That is, a directory which contains a CVS subdirectory but is
	 * not managed by its parent. The root directory of the session
	 * is not considered orphaned even if it is not managed by its
	 * parent.
	 */
	protected boolean isOrphanedSubtree(ICVSFolder mFolder) {
		return mFolder.isCVSFolder() && ! mFolder.isManaged() && ! mFolder.equals(session.getLocalRoot()) && mFolder.getParent().isCVSFolder();
	}
	
	/**
	 * Send the folder relative to the root to the server. Send all 
	 * appropiate modifier like Sticky, Questionable, Static-directory.
	 * <br>
	 * Folders will only be sent once.
	 */
	protected void sendFolder(ICVSFolder mFolder) throws CVSException {

		// Do not send the same folder twice
		if (isLastSent(mFolder)) return;

		String localPath = mFolder.getRelativePath(session.getLocalRoot());
		
		// Deal with questionable directories
		boolean isQuestionable = ! mFolder.isCVSFolder() || isOrphanedSubtree(mFolder);
		if (isQuestionable) {
			if (sendQuestionable) {
				// We need to make sure the parent folder was sent 
				sendFolder(mFolder.getParent());
				session.sendQuestionable(mFolder);
			}
			return;
		}

		// Send the directory to the server
		String remotePath = mFolder.getRemoteLocation(session.getLocalRoot());
		if (remotePath == null) {
			throw new CVSException("Unable to determine remote location for resource");
		}
		session.sendDirectory(localPath, remotePath);

		// Send any directory properties to the server
		FolderSyncInfo info = mFolder.getFolderSyncInfo();
		if (info != null) {

			if (info.getIsStatic()) {
				session.sendStaticDirectory();
			}

			CVSEntryLineTag tag = info.getTag();

			if (tag != null && tag.getType() != tag.HEAD) {
				session.sendSticky(tag.toEntryLineFormat(false));
			}
		}

		// Record that we sent this folder
		recordLastSent(mFolder);
	}

	/**
	 * Send the information about the file to the server.
	 * 
	 * If the file is modified, its contents are sent as well.
	 */
	protected void sendFile(ICVSFile mFile) throws CVSException {

		// Send the file's entry line to the server
		ResourceSyncInfo info = null;
		boolean isManaged = mFile.isManaged();
		if (isManaged) {
			info = mFile.getSyncInfo();
			session.sendEntry(info.getEntryLine(false, mFile.getTimeStamp()));
		} else {
			// If the file is not managed, send a questionable to the server if the file exists locally
			// A unmanaged, locally non-existant file results from the explicit use of the file name as a command argument
			if (sendQuestionable) {
				if (mFile.exists()) {
					session.sendQuestionable(mFile);
				}
				return;
			}
		}
		
		// If the file exists, send the appropriate indication to the server
		if (mFile.exists()) {
			if (mFile.isModified()) {
				boolean binary = (info != null) && ResourceSyncInfo.BINARY_TAG.equals(mFile.getSyncInfo().getKeywordMode());
				if (sendModifiedContents) {
					session.sendModified(mFile, binary, monitor);
				} else {
					session.sendIsModified(mFile, binary, monitor);
				}
			} else {
				session.sendUnchanged(mFile);
			}
		}
	}
	
	/**
	 * This method is used to visit a set of ICVSResources. Using it ensures
	 * that a common parent between the set of resources is only sent once
	 */
	public void visit(ICVSResource[] resources) throws CVSException {

		// Visit all the resources
		for (int i = 0; i < resources.length; i++) {
			resources[i].accept(this);
		}
	}
	
}