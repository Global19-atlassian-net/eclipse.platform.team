package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFile;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.util.Assert;

/**
 * This is a visitor that is specially created for the add-command.<br>
 * It traverses the file-structure in the other direction, so that
 * all the parents are send until a parent is found that should allready
 * be known by the to the root are send.<br>
 * The visitor remembers the folders it has allready been to and does not
 * send them again (if possible). 
 */
class AddStructureVisitor extends AbstractStructureVisitor {
	private boolean forceSend = false;
	private Set visitedFolders = new HashSet();
	private ICVSFolder lastVisitedFolder;
	
	/**
	 * Constructor for AddStructureVisitor.
	 * @param requestSender
	 * @param mRoot
	 * @param monitor
	 */
	public AddStructureVisitor(Session session, IProgressMonitor monitor) {
		super(session, monitor);
	}

	/**
	 * @see ICVSResourceVisitor#visitFile(IManagedFile)
	 */
	public void visitFile(ICVSFile mFile) throws CVSException {
		
		if (!mFile.getParent().equals(lastVisitedFolder)) {
			forceSend = true;
			mFile.getParent().accept(this);
		}
		
		// We just send the fact, that the file is modified
		// not the data, we do not need it.
		session.sendIsModified(mFile.getName());
		
	}

	/**
	 * @see ICVSResourceVisitor#visitFolder(ICVSFolder)
	 */
	public void visitFolder(ICVSFolder mFolder) throws CVSException {
		
		Assert.isNotNull(mFolder);
		
		// Save the status wheter we want to send
		// this folder in every case
		boolean alreadyVisited;
		boolean forceSend = this.forceSend;
		this.forceSend = false;
		
		alreadyVisited = visitedFolders.contains(mFolder);
		
		if (!mFolder.equals(session.getLocalRoot()) && !alreadyVisited) {
			mFolder.getParent().accept(this);
		}
		
		if (forceSend || !alreadyVisited) {
			visitedFolders.add(mFolder);
			lastVisitedFolder = mFolder;
			sendFolder(mFolder,false,false);
		}
	}

}

