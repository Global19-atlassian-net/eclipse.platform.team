package org.eclipse.team.internal.ccvs.core.util;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFile;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;

/**
 * Listen for the addition of orphaned subtrees as a result of a copy or move.
 * 
 * Listen in IResourceChangeEvent.PRE_AUTO_BUILD so that other interested parties 
 * (most notably, the file synchronizer) will receive up to date notifications
 */
public class OrphanedFolderListener extends ResourceDeltaVisitor {

	private void handleOrphanedSubtree(IResource resource) {
		if (resource.getType() == IResource.FOLDER) {
			try {
				ICVSFolder mFolder = (ICVSFolder)Session.getManagedResource(resource);
				if (mFolder.isCVSFolder() && ! mFolder.isManaged() && mFolder.getParent().isCVSFolder()) {
					mFolder.unmanage();
					CVSProviderPlugin.getSynchronizer().reload(resource.getLocation().toFile(), Policy.monitorFor(null));
				}
			} catch (CVSException e) {
				CVSProviderPlugin.log(e);
			}
		}
	}
	
	private void handleDeletedResource(IResource resource) {
		if (resource.getType() == IResource.FILE) {
			try {
				ICVSFile mFile = (ICVSFile)Session.getManagedResource(resource);
				if (mFile.isManaged()) {
					ResourceSyncInfo info = mFile.getSyncInfo();
					if (info.isAdded()) {
						mFile.unmanage();
					} else {
						mFile.setSyncInfo(new ResourceSyncInfo(info.getName(), info.DELETED_PREFIX + info.getRevision(), info.getTimeStamp(), info.getKeywordMode(), info.getTag(), info.getPermissions()));
					}
					CVSProviderPlugin.getSynchronizer().reload(resource.getLocation().toFile(), Policy.monitorFor(null));
				}
			} catch (CVSException e) {
				CVSProviderPlugin.log(e);
			}
		}
	}
	
	/*
	 * Handle the case where an added file has the same name as a "cvs removed" file
	 * by restoring the sync info to what it was before the delete
	 */
	private void handleReplacedDeletion(IResource resource) {
		if (resource.getType() == IResource.FILE) {
			try {
				ICVSFile mFile = (ICVSFile)Session.getManagedResource(resource);
				if (mFile.isManaged()) {
					ResourceSyncInfo info = mFile.getSyncInfo();
					if (info.isDeleted()) {
						mFile.setSyncInfo(new ResourceSyncInfo(info.getName(), info.getRevision(), info.getTimeStamp(), info.getKeywordMode(), info.getTag(), info.getPermissions()));
					}
					CVSProviderPlugin.getSynchronizer().reload(resource.getLocation().toFile(), Policy.monitorFor(null));
				}
			} catch (CVSException e) {
				CVSProviderPlugin.log(e);
			}
		}
	}
	
	/*
	 * @see ResourceDeltaVisitor#handleAdded(IResource[])
	 */
	protected void handleAdded(IResource[] resources) {
		for (int i = 0; i < resources.length; i++) {
			if (resources[i].getType() == IResource.FOLDER) {
				handleOrphanedSubtree(resources[i]);
			} else if (resources[i].getType() == IResource.FILE) {
				handleReplacedDeletion(resources[i]);
			}
		}
	}

	/*
	 * @see ResourceDeltaVisitor#handleRemoved(IResource[])
	 */
	protected void handleRemoved(IResource[] resources) {
		for (int i = 0; i < resources.length; i++) {
			handleDeletedResource(resources[i]);
		}
	}

	/*
	 * @see ResourceDeltaVisitor#handleChanged(IResource[])
	 */
	protected void handleChanged(IResource[] resources) {
	}

	/*
	 * @see ResourceDeltaVisitor#finished()
	 */
	protected void finished() {
	}
	
	protected int getEventMask() {
		return IResourceChangeEvent.PRE_AUTO_BUILD;
	}

}
