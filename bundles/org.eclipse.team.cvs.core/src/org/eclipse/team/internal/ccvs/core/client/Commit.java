package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.util.Collection;
import java.util.Date;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.listeners.ICommandOutputListener;
import org.eclipse.team.internal.ccvs.core.syncinfo.MutableResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;

public class Commit extends Command {
	/*** Local options: specific to commit ***/
	// Forces a file to be committed even if it has not been modified; implies -l.
	// NOTE: This option is not fully supported -- a file will not be sent
	//       unless it is dirty.  The primary use is to resend a file that may
	//       or may not be changed (e.g. could depend on CR/LF translations, etc...)
	//       and force the server to create a new revision and reply Checked-in.
	public static final LocalOption FORCE = new LocalOption("-f"); //$NON-NLS-1$

	protected Commit() { }
	protected String getRequestId() {
		return "ci"; //$NON-NLS-1$
	}

	/**
	 * Send all files under the workingFolder as changed files to 
	 * the server.
	 */		
	protected ICVSResource[] sendLocalResourceState(Session session, GlobalOption[] globalOptions,
		LocalOption[] localOptions, ICVSResource[] resources, IProgressMonitor monitor)
		throws CVSException {			

		// Get the folders we want to work on
		checkResourcesManaged(resources);
		
		// Send all changed files to the server	
		ModifiedFileSender visitor = new ModifiedFileSender(session, monitor);
		visitor.visit(session, resources);
		
		// Send the changed files as arguments (because this is what other cvs clients do)
		ICVSFile[] changedFiles = visitor.getSentFiles();
		for (int i = 0; i < changedFiles.length; i++) {
			session.sendArgument(changedFiles[i].getRelativePath(session.getLocalRoot()));
		}
		return changedFiles;
	}
	
	/**
	 * On successful finish, prune empty directories if the -P or -D option was specified.
	 */
	protected IStatus commandFinished(Session session, GlobalOption[] globalOptions,
		LocalOption[] localOptions, ICVSResource[] resources, IProgressMonitor monitor,
		IStatus status) throws CVSException {
		// If we didn't succeed, don't do any post processing
		if (status.getCode() == CVSStatus.SERVER_ERROR) {
			return status;
		}

		// If pruning is enable, prune empty directories after a commit
		if (CVSProviderPlugin.getPlugin().getPruneEmptyDirectories()) { //$NON-NLS-1$
			new PruneFolderVisitor().visit(session, resources);
		}
		
		// Reset the timestamps of any committed files that are still dirty
		if (CVSProviderPlugin.getPlugin().getResetTimestampOfFalseChange()) {
			for (int i = 0; i < resources.length; i++) {
				ICVSResource resource = resources[i];
				if (!resource.isFolder()) {
					ICVSFile cvsFile = (ICVSFile)resources[i];
					if (cvsFile.exists() && cvsFile.isModified()) {
						status = mergeStatus(status, clearModifiedState(cvsFile));
					}
				}
			}
		}
		return status;
	}
	
	protected IStatus clearModifiedState(ICVSFile cvsFile) throws CVSException {
		ResourceSyncInfo info = cvsFile.getSyncInfo();
		if (info == null) {
			// There should be sync info. Log the problem
			return new Status(IStatus.WARNING, CVSProviderPlugin.ID, 0, Policy.bind("Commit.syncInfoMissing", cvsFile.getIResource().getFullPath().toString()), null); //$NON-NLS-1$
		}
		Date timeStamp = info.getTimeStamp();
		if (timeStamp == null) {
			// If the entry line has no timestamp, put the file timestamp in the entry line
			MutableResourceSyncInfo mutable = info.cloneMutable();
			mutable.setTimeStamp(cvsFile.getTimeStamp());
			// Setting the sync info to a change mutatble should trigger a check for modified 
			// see FileModificationManager and MutableResourceSyncInfo.
			cvsFile.setSyncInfo(mutable);
		} else {
			// reset the file timestamp to the one from the entry line
			cvsFile.setTimeStamp(timeStamp);
			// the file will be no longer modified
			CVSProviderPlugin.getPlugin().getFileModificationManager().setModified((IFile)cvsFile.getIResource(), false);
		}
		return new Status(IStatus.INFO, CVSProviderPlugin.ID, 0, Policy.bind("Commit.timestampReset", cvsFile.getIResource().getFullPath().toString()), null); //$NON-NLS-1$;
	}
	
	/**
	 * We do not want to send the arguments here, because we send
	 * them in sendRequestsToServer (special handling).
	 */
	protected void sendArguments(Session session, String[] arguments) throws CVSException {
	}
	
	public final IStatus execute(GlobalOption[] globalOptions, LocalOption[] localOptions, 
		ICVSResource[] arguments, Collection filesToCommitAsText,
		ICommandOutputListener listener, IProgressMonitor pm) throws CVSException {
		
		Session openSession = getOpenSession(arguments);
		openSession.setTextTransferOverride(filesToCommitAsText);
		try {
			return super.execute(globalOptions, localOptions, arguments, listener, pm);
		} finally {
			openSession.setTextTransferOverride(null);
		}
	}
}	