package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.ILocalSyncElement;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.core.sync.IRemoteSyncElement;
import org.eclipse.team.core.sync.RemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Update;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.MutableResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;

public class CVSRemoteSyncElement extends RemoteSyncElement {

	CVSLocalSyncElement localSync;
	IRemoteResource remote;
	boolean isThreeWay = true;

	public CVSRemoteSyncElement(boolean isThreeWay, IResource local, IRemoteResource base, IRemoteResource remote) {
		localSync = new CVSLocalSyncElement(local, base);
		this.remote = remote;	
		this.isThreeWay = isThreeWay;		
	}

	/*
	 * @see RemoteSyncElement#create(IResource, IRemoteResource, IRemoteResource)
	 */
	public IRemoteSyncElement create(boolean isThreeWay, IResource local, IRemoteResource base, IRemoteResource remote, Object data) {
		return new CVSRemoteSyncElement(isThreeWay, local, base, remote);
	}

	/*
	 * @see IRemoteSyncElement#getRemote()
	 */
	public IRemoteResource getRemote() {
		return remote;
	}

	/*
	 * @see LocalSyncElement#getData()
	 */
	protected Object getData() {
		return localSync.getData();
	}

	/*
	 * @see ILocalSyncElement#getLocal()
	 */
	public IResource getLocal() {
		return localSync.getLocal();
	}

	/*
	 * @see ILocalSyncElement#getBase()
	 */
	public IRemoteResource getBase() {
		return localSync.getBase();
	}

	/*
	 * @see ILocalSyncElement#isCheckedOut()
	 */
	public boolean isCheckedOut() {
		return localSync.isCheckedOut();
	}

	/*
	 * Local helper to indicate if the corresponding local resource has a base
	 */
	public boolean hasBase() {
		return getBase() != null;
	}
	
	/*
	 * @see ILocalSyncElement#hasRemote()
	 */
	public boolean hasRemote() {
		return remote != null;
	}
	
	/*
	 * @see LocalSyncElement#create(IResource, IRemoteResource, Object)
	 */
	public ILocalSyncElement create(IResource local, IRemoteResource base, Object data) {
		return localSync.create(local, base, data);
	}
	/*
	 * @see LocalSyncElement#isIgnored(IResource)
	 */
	protected boolean isIgnored(IResource resource) {
		return localSync.isIgnored(resource);
	}
	/*
	 * @see IRemoteSyncElement#ignoreBaseTree()
	 */
	public boolean isThreeWay() {
		return isThreeWay;
	}
	
	/*
	 * Update the sync info of the local resource in such a way that the local changes can be committed.
	 */
	public void makeOutgoing(IProgressMonitor monitor) throws TeamException {
		
		int syncKind = getSyncKind(GRANULARITY_TIMESTAMP, monitor);
		boolean incoming = (syncKind & DIRECTION_MASK) == INCOMING;
		boolean outgoing = (syncKind & DIRECTION_MASK) == OUTGOING;

		ICVSResource local = localSync.getCVSResource();
		RemoteResource remote = (RemoteResource)getRemote();
		ResourceSyncInfo origInfo = local.getSyncInfo();
		MutableResourceSyncInfo info = null;
		if(origInfo!=null) {
			info = origInfo.cloneMutable();			
		}
	
		if (outgoing) {
				// The sync info is alright, it's already outgoing!
				return;
		} else if (incoming) {
			// We have an incoming change, addition, or deletion that we want to ignore
			if (local.exists()) {
				// We could have an incoming change or deletion
				if (remote == null) {
					info.setAdded();
				} else {
					// Otherwise change the revision to the remote revision and dirty the file
					info.setRevision(remote.getSyncInfo().getRevision());
					info.setTimeStamp(null);
				}
			} else {
				// We have an incoming add, turn it around as an outgoing delete
				info = remote.getSyncInfo().cloneMutable();
				info.setDeleted(true);
			}
		} else if (local.exists()) {
			// We have a conflict and a local resource!
			if (hasRemote()) {
				if (hasBase()) {
					// We have a conflicting change, Update the local revision
					info.setRevision(remote.getSyncInfo().getRevision());
				} else {
					// We have conflictin additions.
					// We need to fetch the contents of the remote to get all the relevant information (timestamp, permissions)
					remote.getContents(Policy.monitorFor(monitor));
					info = remote.getSyncInfo().cloneMutable();
				}
			} else if (hasBase()) {
				// We have a remote deletion. Make the local an addition
				info.setAdded();
			} else {
				// There's a local, no base and no remote. We can't possible have a conflict!
				Assert.isTrue(false);
			} 
		} else {
			// We have a conflict and there is no local!
			if (hasRemote()) {
				// We have a local deletion that conflicts with remote changes.
				info.setRevision(remote.getSyncInfo().getRevision());
				info.setDeleted(true);
			} else {
				// We have conflicting deletions. Clear the sync info
				info = null;
				return;
			}
		}
		if(info!=null) {
			info.setTag(local.getParent().getFolderSyncInfo().getTag());
		}
		((ICVSFile)local).setSyncInfo(info, ICVSFile.UNKNOWN);
	}
	
	/*
	 * Update the sync info of the local resource in such a way that the remote resource can be loaded 
	 * ignore any local changes. 
	 */
	public void makeIncoming(IProgressMonitor monitor) throws TeamException {
		// To make outgoing deletions incoming, the local will not exist but
		// it is still important to unmanage (e.g. delete all meta info) for the
		// deletion.
		CVSWorkspaceRoot.getCVSResourceFor(getLocal()).unmanage(null);
	}
	
	/*
	 * Load the resource and folder sync info into the local from the remote
	 * 
	 * This method can be used on incoming folder additions to set the folder sync info properly
	 * without hitting the server again. It also applies to conflicts that involves unmanaged
	 * local resources.
	 * 
	 * If the local folder is already managed and is a cvs folder, this operation
	 * will throw an exception if the mapping does not match that of the remote.
	 */
	 public void makeInSync(IProgressMonitor monitor) throws TeamException {
	 	
	 	// Only work on folders
	 	if (! isContainer()) return;
	 		
	 	int syncKind = getSyncKind(GRANULARITY_TIMESTAMP, monitor);
		boolean outgoing = (syncKind & DIRECTION_MASK) == OUTGOING;
		if (outgoing) return;
		
		ICVSFolder local = (ICVSFolder)localSync.getCVSResource();
		RemoteFolder remote = (RemoteFolder)getRemote();
		
		// The parent must be managed
		if (! local.getParent().isCVSFolder())
			return;
		
		// If the folder already has CVS info, check that the remote and local match
		if(local.isManaged() && local.isCVSFolder()) {
			// Verify that the root and repository are the same
			FolderSyncInfo remoteInfo = remote.getFolderSyncInfo();
			FolderSyncInfo localInfo = local.getFolderSyncInfo();
			if ( ! localInfo.getRoot().equals(remoteInfo.getRoot())) {
				throw new CVSException(Policy.bind("CVSRemoteSyncElement.rootDiffers", new Object[] {local.getName(), remoteInfo.getRoot(), localInfo.getRoot()}));//$NON-NLS-1$
			} else if ( ! localInfo.getRepository().equals(remoteInfo.getRepository())) {
				throw new CVSException(Policy.bind("CVSRemoteSyncElement.repositoryDiffers", new Object[] {local.getName(), remoteInfo.getRepository(), localInfo.getRepository()}));//$NON-NLS-1$
			}
			// The folders are in sync so just return
			return;
		}
		
		// Ensure that the folder exists locally
		if (! local.exists()) {
			local.mkdir();
		}
		
		// Since the parent is managed, this will also set the resource sync info. It is
		// impossible for an incoming folder addition to map to another location in the
		// repo, so we assume that using the parent's folder sync as a basis is safe.
		// It is also impossible for an incomming folder to be static.		
		FolderSyncInfo remoteInfo = remote.getFolderSyncInfo();
		FolderSyncInfo localInfo = local.getParent().getFolderSyncInfo();
		local.setFolderSyncInfo(new FolderSyncInfo(remoteInfo.getRepository(), remoteInfo.getRoot(), localInfo.getTag(), false));
	}
	/*
	 * Make all the folders that have both a remote and local in sync by copying
	 * the sync information from the remote to the local
	 */
	protected void makeFoldersInSync(IProgressMonitor progress) throws TeamException {
		FolderSyncInfo remoteInfo = ((RemoteFolder)getRemote()).getFolderSyncInfo();
		((ICVSFolder)localSync.getCVSResource()).setFolderSyncInfo(remoteInfo);
		ILocalSyncElement[] children = members(progress);
		for (int i = 0; i < children.length; i++) {
			CVSRemoteSyncElement child = (CVSRemoteSyncElement)children[i];
			if (child.isContainer() && child.getLocal().exists() && child.getRemote() != null) {
				child.makeFoldersInSync(progress);
			}
		}
	}
	/*
	 * @see ILocalSyncElement#getSyncKind(int, IProgressMonitor)
	 */
	public int getSyncKind(int granularity, IProgressMonitor progress) {
		
		// special handling for folders, the generic sync algorithm doesn't work well
		// with CVS because folders are not in namespaces (e.g. they exist in all versions
		// and branches).
		if(isContainer() && isThreeWay()) {
			int folderKind = IRemoteSyncElement.IN_SYNC;
			IResource local = getLocal();
			ICVSRemoteFolder remote = (ICVSRemoteFolder)getRemote();
			ICVSFolder cvsFolder = (ICVSFolder)localSync.getCVSResource();
			boolean isCVSFolder = false;
			try {
				isCVSFolder = cvsFolder.isCVSFolder();
			} catch (CVSException e) {
				// Assume the folder is not a CVS folder
			}
			if(!local.exists()) {
				if(remote != null) {
					if (isCVSFolder) {
						if (containsOutgoingDeletions(cvsFolder)) {
							// say the folder is in_sync even though it doesn't exist locally
							folderKind = IRemoteSyncElement.IN_SYNC;
						} else {
							folderKind = IRemoteSyncElement.INCOMING | IRemoteSyncElement.ADDITION;
						}
					} else {
						folderKind = IRemoteSyncElement.INCOMING | IRemoteSyncElement.ADDITION;
					}
				} else {
					// ignore conflicting deletion to keep phantom sync info
				}
			} else {
				if(remote == null) {
					if(isCVSFolder) {
						folderKind = IRemoteSyncElement.INCOMING | IRemoteSyncElement.DELETION;
					} else {
						folderKind = IRemoteSyncElement.OUTGOING | IRemoteSyncElement.ADDITION;
					}
				} else if(!isCVSFolder) {
					folderKind = IRemoteSyncElement.CONFLICTING | IRemoteSyncElement.ADDITION;
				} else {
					// folder exists both locally and remotely and are considered in sync, however 
					// we aren't checking the folder mappings to ensure that they are the same.
				}
			}
			return folderKind;
		}
		
		// 1. Run the generic sync calculation algorithm, then handle CVS specific
		// sync cases.
		int kind = super.getSyncKind(granularity, progress);
		
		// 2. Set the CVS specific sync type based on the workspace sync state provided
		// by the CVS server.
		if(remote!=null && (kind & IRemoteSyncElement.PSEUDO_CONFLICT) == 0) {
			int type = ((RemoteResource)remote).getWorkspaceSyncState();
			switch(type) {
				// the server compared both text files and decided that it cannot merge
				// them without line conflicts.
				case Update.STATE_CONFLICT: 
					return kind | ILocalSyncElement.MANUAL_CONFLICT;

				// the server compared both text files and decided that it can safely merge
				// them without line conflicts. 
				case Update.STATE_MERGEABLE_CONFLICT: 
					return kind | ILocalSyncElement.AUTOMERGE_CONFLICT;				
			}			
		}
		
		// 3. unmanage delete/delete conflicts and return that they are in sync
		kind = handleDeletionConflicts(kind);
		
		return kind;
	}

	/**
	 * Return true if the provided phantom folder conyains any outgoing file deletions.
	 * We only need to detect if there are any files since a phantom folder can only
	 * contain outgoing filre deletions and other folder.
	 * 
	 * @param cvsFolder a phantom folder
	 * @return boolean
	 */
	private boolean containsOutgoingDeletions(ICVSFolder cvsFolder) {
		final boolean result[] = new boolean[] { false };
		try {
			cvsFolder.accept(new ICVSResourceVisitor() {
				public void visitFile(ICVSFile file) throws CVSException {
					// Do nothing. Files are handled below
				}
				public void visitFolder(ICVSFolder folder) throws CVSException {
					if (folder.members(ICVSFolder.FILE_MEMBERS).length > 0) {
						result[0] = true;
					} else {
						folder.acceptChildren(this);
					}
				}
			});
		} catch (CVSException e) {
			CVSProviderPlugin.log(e);
		}
		return result[0];
	}

	
	/*
	 * If the resource has a delete/delete conflict then ensure that the local is unmanaged so that the 
	 * sync info can be properly flushed.
	 */
	private int handleDeletionConflicts(int kind) {
		if(kind == (IRemoteSyncElement.CONFLICTING | IRemoteSyncElement.DELETION | IRemoteSyncElement.PSEUDO_CONFLICT)) {
			try {
				ICVSResource cvsResource = localSync.getCVSResource();
				if(!isContainer() && cvsResource.isManaged()) {
					cvsResource.unmanage(null);
				}
				return IRemoteSyncElement.IN_SYNC;
			} catch(CVSException e) {
				CVSProviderPlugin.log(e.getStatus());
				return IRemoteSyncElement.CONFLICTING | IRemoteSyncElement.DELETION;
			}
		}
		return kind;
	}
	
	/**
	 * @see RemoteSyncElement#timestampEquals(IRemoteResource, IRemoteResource)
	 */
	protected boolean timestampEquals(IRemoteResource e1, IRemoteResource e2) {
		if(e1.isContainer()) {
			if(e2.isContainer()) {
				return true;
			}
			return false;
		}
		return e1.equals(e2);
	}

	/**
	 * @see RemoteSyncElement#timestampEquals(IResource, IRemoteResource)
	 */
	protected boolean timestampEquals(IResource e1, IRemoteResource e2) {
		if(e1.getType() != IResource.FILE) {
			if(e2.isContainer()) {
				return true;
			}
			return false;
		}
		ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile)e1);
		try {
			byte[] syncBytes1 = cvsFile.getSyncBytes();
			byte[] syncBytes2 = ((ICVSRemoteFile)e2).getSyncBytes();
			
			if(syncBytes1 != null) {
				if(ResourceSyncInfo.isDeletion(syncBytes1) || ResourceSyncInfo.isMerge(syncBytes1) || cvsFile.isModified(null)) {
					return false;
				}
				return ResourceSyncInfo.getRevision(syncBytes1).equals(ResourceSyncInfo.getRevision(syncBytes2));
			}
			return false;
		} catch(CVSException e) {
			CVSProviderPlugin.log(e.getStatus());
			return false;
		}
	}
}