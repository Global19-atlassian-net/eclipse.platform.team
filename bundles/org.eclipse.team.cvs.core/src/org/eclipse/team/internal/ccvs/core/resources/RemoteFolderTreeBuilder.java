package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.ccvs.core.CVSStatus;
import org.eclipse.team.ccvs.core.CVSTag;
import org.eclipse.team.ccvs.core.ICVSFile;
import org.eclipse.team.ccvs.core.ICVSFolder;
import org.eclipse.team.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.Update;
import org.eclipse.team.internal.ccvs.core.client.Command.GlobalOption;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.client.listeners.IStatusListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.IUpdateMessageListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.StatusListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.UpdateListener;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.connection.CVSServerException;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.omg.CORBA.UNKNOWN;

/*
 * This class is responsible for building a remote tree that shows the repository
 * state of a locally loaded folder tree.
 * 
 * It is used as follows
 * 
 * 		RemoteFolderTreeBuilder.buildRemoteTree(CVSRepositoryLocation, IManagedFolder, String, IProgressMonitor);
 * 
 * The provider IManagedFolder can be a local resource or a RemoteFolderTree that
 * that was previously built.
 */
public class RemoteFolderTreeBuilder {

	private Map fileDeltas;
	private List changedFiles;
	private Map remoteFolderTable;
	
	private ICVSFolder root;
	private RemoteFolderTree remoteRoot;
	private CVSRepositoryLocation repository;
	
	private CVSTag tag;
	
	private LocalOption[] updateLocalOptions;
	
	private boolean projectDoesNotExist = false;
	
	private static String UNKNOWN = ""; //$NON-NLS-1$
	private static String DELETED = "DELETED"; //$NON-NLS-1$
	private static String ADDED = "ADDED"; //$NON-NLS-1$
	private static String FOLDER = "FOLDER"; //$NON-NLS-1$
	
	private static Map EMPTY_MAP = new HashMap();
	
	static class DeltaNode {
		int syncState = Update.STATE_NONE;
		String name;
		String revision;
		
		DeltaNode(String name, String revision, int syncState) {
			this.name = name;
			this.revision = revision;
			this.syncState = syncState;			
		}
		
		String getName() {
			return name;
		}
		
		String getRevision() {
			return revision;
		}
		
		int getSyncState() {
			return syncState;
		}
	}
		
	
	private RemoteFolderTreeBuilder(CVSRepositoryLocation repository, ICVSFolder root, CVSTag tag) {
		this.repository = repository;
		this.root = root;
		this.tag = tag;
		this.fileDeltas = new HashMap();
		this.changedFiles = new ArrayList();
		this.remoteFolderTable = new HashMap();
		
		// Build the local options
		List localOptions = new ArrayList();
		localOptions.add(Update.RETRIEVE_ABSENT_DIRECTORIES);
		if (tag != null) {
			if (tag.getType() == CVSTag.HEAD) {
				localOptions.add(Update.CLEAR_STICKY);
			} else {
				localOptions.add(Update.makeTagOption(tag));
			}
		}
		updateLocalOptions = (LocalOption[])localOptions.toArray(new LocalOption[localOptions.size()]);
	}
	
	private LocalOption[] getOptionsWithoutTag() {
		// Build the local options
		List localOptions = new ArrayList();
		localOptions.add(Update.RETRIEVE_ABSENT_DIRECTORIES);
		return (LocalOption[])localOptions.toArray(new LocalOption[localOptions.size()]);
	}
	
	public static RemoteFolderTree buildBaseTree(CVSRepositoryLocation repository, ICVSFolder root, CVSTag tag, IProgressMonitor monitor) throws CVSException {
		RemoteFolderTreeBuilder builder = new RemoteFolderTreeBuilder(repository, root, tag);
 		return builder.buildBaseTree(null, root, monitor);
	}
	
	public static RemoteFolderTree buildRemoteTree(CVSRepositoryLocation repository, IContainer root, CVSTag tag, IProgressMonitor monitor) throws CVSException {
		return buildRemoteTree(repository, CVSWorkspaceRoot.getCVSFolderFor(root), tag, monitor);
	}
	
	public static RemoteFolderTree buildRemoteTree(CVSRepositoryLocation repository, ICVSFolder root, CVSTag tag, IProgressMonitor monitor) throws CVSException {
		RemoteFolderTreeBuilder builder = new RemoteFolderTreeBuilder(repository, root, tag);
 		return builder.buildTree(monitor);
	}
	
	private RemoteFolderTree buildTree(IProgressMonitor monitor) throws CVSException {
		Session session = new Session(repository, root, false);
		session.open(monitor);
		try {
			fetchDelta(session, monitor);
			if (projectDoesNotExist) {
				return null;
			}
		} finally {
			session.close();
		}
		// FIXME: We need a second session because of the use of a different handle on the same remote resource
		// We didn't need one before!!! Perhaps we could support the changing of a sessions root as long as
		// the folder sync info is the same 
		remoteRoot =
			new RemoteFolderTree(null, root.getName(), repository,
				new Path(root.getFolderSyncInfo().getRepository()),
				tagForRemoteFolder(root, tag));
		session = new Session(repository, remoteRoot, false);
		session.open(monitor);
		try {
			buildRemoteTree(session, root, remoteRoot, Path.EMPTY, monitor);
			if (!changedFiles.isEmpty())
				fetchFileRevisions(session, (String[])changedFiles.toArray(new String[changedFiles.size()]), monitor);
			return remoteRoot;
		} finally {
			session.close();
		}
	}
	
	/*
	 * Build the base remote tree from the local tree.
	 * 
	 * The localPath is used to retrieve deltas from the recorded deltas
	 * 
	 */
	private RemoteFolderTree buildBaseTree(RemoteFolderTree parent, ICVSFolder local, IProgressMonitor monitor) throws CVSException {
		
		// Create a remote folder tree corresponding to the local resource
		RemoteFolderTree remote = new RemoteFolderTree(parent, local.getName(), repository, new Path(local.getFolderSyncInfo().getRepository()), local.getFolderSyncInfo().getTag());

		// Create a List to contain the created children
		List children = new ArrayList();
		
		// Build the child folders corresponding to local folders base
		ICVSFolder[] folders = local.getFolders();
		for (int i=0;i<folders.length;i++) {
			if (folders[i].isManaged() && folders[i].isCVSFolder()) {
				children.add(buildBaseTree(remote, folders[i], monitor));
			}
		}
		
		// Build the child files corresponding to local files base
		ICVSFile[] files = local.getFiles();
		for (int i=0;i<files.length;i++) {
			ICVSFile file = files[i];
			ResourceSyncInfo info = file.getSyncInfo();
			// if there is no sync info then there is no base
			if (info==null)
				continue;
			// There is no remote if the file was added
			if (info.isAdded())
				continue;
			// If the file was deleted locally, we need to generate a new sync info without the delete flag
			if (info.isDeleted())
				info = new ResourceSyncInfo(info.getName(), info.getRevision(), info.getTimeStamp(), info.getKeywordMode(), info.getTag(), info.getPermissions());
			children.add(new RemoteFile(remote, info));
		}

		// Add the children to the remote folder tree
		remote.setChildren((ICVSRemoteResource[])children.toArray(new ICVSRemoteResource[children.size()]));
		
		return remote;
	}
	
	/*
	 * Build the remote tree from the local tree and the recorded deltas.
	 * 
	 * The localPath is used to retrieve deltas from the recorded deltas
	 * 
	 */
	private void buildRemoteTree(Session session, ICVSFolder local, RemoteFolderTree remote, IPath localPath, IProgressMonitor monitor) throws CVSException {
		
		// Add the remote folder to the remote folder lookup table (used to update file revisions)
		remoteFolderTable.put(remote.getFolderSyncInfo().getRemoteLocation(), remote);
		
		// Create a map to contain the created children
		Map children = new HashMap();
		
		// If there's no corresponding local resource then we need to fetch its contents in order to populate the deltas
		if (local == null) {
			fetchNewDirectory(session, remote, localPath, monitor);
		}
		
		// Fetch the delta's for the folder
		Map deltas = (Map)fileDeltas.get(localPath);
		if (deltas == null)
			deltas = EMPTY_MAP;
		
		// If there is a local, use the local children to start buidling the remote children
		if (local != null) {
			// Build the child folders corresponding to local folders
			ICVSFolder[] folders = local.getFolders();
			for (int i=0;i<folders.length;i++) {
				DeltaNode d = (DeltaNode)deltas.get(folders[i].getName());
				if (folders[i].isCVSFolder() && ! isOrphanedSubtree(session, folders[i]) && (d==null || d.getRevision() != DELETED)) {
					children.put(folders[i].getName(), 
						new RemoteFolderTree(remote, folders[i].getName(), repository, 
							new Path(folders[i].getFolderSyncInfo().getRepository()), 
							tagForRemoteFolder(folders[i],tag)));
				}
			}
			// Build the child files corresponding to local files
			ICVSFile[] files = local.getFiles();
			for (int i=0;i<files.length;i++) {
				ICVSFile file = files[i];

				DeltaNode d = (DeltaNode)deltas.get(file.getName());
				ResourceSyncInfo info = file.getSyncInfo();
				// if there is no sync info then there isn't a remote file for this local file on the
				// server.
				if (info==null)
					continue;
				// There is no remote if the file was added and we didn't get a conflict (C) indicator from the server
				if (info.isAdded() && d==null)
					continue;
				// There is no remote if the file was deleted and we didn;t get a remove (R) indicator from the server
				if (info.isDeleted() && d==null)
					continue;
					
				int type = d==null ? Update.STATE_NONE : d.getSyncState();
				children.put(file.getName(), new RemoteFile(remote, type, info));
			}
		}
		
		// Build the children for new or out-of-date resources from the deltas
		Iterator i = deltas.keySet().iterator();
		while (i.hasNext()) {
			String name = (String)i.next();
			DeltaNode d = (DeltaNode)deltas.get(name);
			String revision = d.getRevision();
			if (revision == FOLDER) {
				// XXX should getRemotePath() return an IPath instead of a String?
				children.put(name, new RemoteFolderTree(remote, repository, 
					new Path(remote.getRepositoryRelativePath()).append(name), 
					tagForRemoteFolder(remote, tag)));
			} else if (revision == ADDED) {
				children.put(name, new RemoteFile(remote, d.getSyncState(), name, tagForRemoteFolder(remote, tag)));
			} else if (revision == UNKNOWN) {
				// The local resource is out of sync with the remote.
				// Create a RemoteFile associated with the tag so we are assured of getting the proper revision
				// (Note: this will replace the RemoteFile added from the local base)
				children.put(name, new RemoteFile(remote, d.getSyncState(), name, tagForRemoteFolder(remote, tag)));
			} else if (revision == DELETED) {
				// This should have been deleted while creating from the local resources.
				// If it wasn't, delete it now.
				if (children.containsKey(name))
					children.remove(name);
			} else {
				// We should never get here
			}
		}

		// Add the children to the remote folder tree
		remote.setChildren((ICVSRemoteResource[])children.values().toArray(new ICVSRemoteResource[children.size()]));
		
		// We have to delay building the child folders to support the proper fetching of new directories
		// due to the fact that the same CVS home directory (i.e. the same root directory) must
		// be used for all requests sent over the same connection
		Iterator childIterator = children.entrySet().iterator();
		List emptyChildren = new ArrayList();
		while (childIterator.hasNext()) {
			Map.Entry entry = (Map.Entry)childIterator.next();
			if (((RemoteResource)entry.getValue()).isFolder()) {
				RemoteFolderTree remoteFolder = (RemoteFolderTree)entry.getValue();
				String name = (String)entry.getKey();
				ICVSFolder localFolder;
				DeltaNode d = (DeltaNode)deltas.get(name);
				// for directories that are new on the server 
				if (d!=null && d.getRevision() == FOLDER)
					localFolder = null;
				else
					localFolder = local.getFolder(name);
				buildRemoteTree(session, localFolder, remoteFolder, localPath.append(name), monitor);
				// Record any children that are empty
				if (pruneEmptyDirectories() && remoteFolder.getChildren().length == 0) {
					// Prune if the local folder is also empty.
					if (localFolder == null || (localFolder.getFiles().length == 0 && localFolder.getFolders().length == 0))
						emptyChildren.add(remoteFolder);
					else {
						// Also prune if the tag we are fetching is not HEAD and differs from the tag of the local folder
						FolderSyncInfo info = localFolder.getFolderSyncInfo();
						if (tag != null && info != null && ! tag.equals(CVSTag.DEFAULT) && ! tag.equals(info.getTag()))
							emptyChildren.add(remoteFolder);
					}
				}
			}
		}
		
		// Prune any empty child folders
		if (pruneEmptyDirectories() && !emptyChildren.isEmpty()) {
			List newChildren = new ArrayList();
			newChildren.addAll(Arrays.asList(remote.getChildren()));
			newChildren.removeAll(emptyChildren);
			remote.setChildren((ICVSRemoteResource[])newChildren.toArray(new ICVSRemoteResource[newChildren.size()]));

		}
	}
	
	/*
	 * This method fetches the delta between the local state and the remote state of the resource tree
	 * and records the deltas in the fileDeltas instance variable
	 * 
	 * Returns the list of changed files
	 */
	private List fetchDelta(Session session, IProgressMonitor monitor) throws CVSException {
		
		// Create an listener that will accumulate new and removed files and folders
		final List newChildDirectories = new ArrayList();
		IUpdateMessageListener listener = new IUpdateMessageListener() {
			public void directoryInformation(IPath path, boolean newDirectory) {
				if (newDirectory) {
					// Record new directory with parent so it can be retrieved when building the parent
					recordDelta(path, FOLDER, Update.STATE_NONE);
					// Record new directory to be used as a parameter to fetch its contents
					newChildDirectories.add(path.toString());
				}
			}
			public void directoryDoesNotExist(IPath path) {
				// Record removed directory with parent so it can be removed when building the parent
				if (path.isEmpty()) {
					projectDoesNotExist = true;
				} else {
					recordDelta(path, DELETED, Update.STATE_NONE);
				}
			}
			public void fileInformation(int type, String filename) {
				// Cases that do not require action are:
				//	case 'A' :  = A locally added file that does not exists remotely
				//	case '?' :  = A local file that has not been added and does not exists remotely
				//  case 'M' :  = A locally modified file that has not been modified remotely
				switch(type) {
					case Update.STATE_MERGEABLE_CONFLICT :
					case Update.STATE_CONFLICT : 
								// We have an remote change to a modified local file
								// The change could be a local change conflicting with a remote deletion.
								// If so, the deltas may already have a DELETED for the file.
								// We shouldn't override this DELETED
								IPath filePath = new Path(filename);
								Map deltas = deltas = (Map)fileDeltas.get(filePath.removeLastSegments(1));
								DeltaNode d = deltas != null ? (DeltaNode)deltas.get(filePath.lastSegment()) : null;
								if ((d!=null) && (d.getRevision() == DELETED))
									break;
					case Update.STATE_DELETED : // We have a locally removed file that still exists remotely
					case Update.STATE_REMOTE_CHANGES : // We have an remote change to an unmodified local file
								changedFiles.add(filename);
								recordDelta(new Path(filename), UNKNOWN, type);
								break;
				}	
			}
			public void fileDoesNotExist(String filename) {
				recordDelta(new Path(filename), DELETED, Update.STATE_NONE);
			}
		};
		
		// Perform a "cvs -n update -d [-r tag] ." in order to get the
		// messages from the server that will indicate what has changed on the 
		// server.
		IStatus status = Command.UPDATE.execute(session,
			new GlobalOption[] { Command.DO_NOT_CHANGE },
			updateLocalOptions,
			new String[] { Session.CURRENT_LOCAL_FOLDER },
			new UpdateListener(listener),
			monitor);
		return changedFiles;
	}
	
	private void fetchNewDirectory(Session session, RemoteFolderTree newFolder, IPath localPath, IProgressMonitor monitor) throws CVSException {
		
		// Create an listener that will accumulate new files and folders
		IUpdateMessageListener listener = new IUpdateMessageListener() {
			public void directoryInformation(IPath path, boolean newDirectory) {
				if (newDirectory) {
					// Record new directory with parent so it can be retrieved when building the parent
					// NOTE: Check path prefix
					recordDelta(path, FOLDER, Update.STATE_NONE);
				}
			}
			public void directoryDoesNotExist(IPath path) {
			}
			public void fileInformation(int type, String filename) {
				// NOTE: Check path prefix
				changedFiles.add(filename);
				recordDelta(new Path(filename), ADDED, type);
			}
			public void fileDoesNotExist(String filename) {
			}
		};

		// NOTE: Should use the path relative to the remoteRoot
		IPath path = new Path(newFolder.getRepositoryRelativePath());
		IStatus status = Command.UPDATE.execute(session,
			new GlobalOption[] { Command.DO_NOT_CHANGE },
			updateLocalOptions,
			new String[] { localPath.toString() },
			new UpdateListener(listener),
			monitor); 
		if (status.getCode() == CVSStatus.SERVER_ERROR) {
			// FIXME: This should be refactored (maybe static methods on CVSException?)
			CVSServerException e = new CVSServerException(status);
			if ( ! e.isNoTagException() && e.containsErrors())
				throw e;
			// we now know that this is an exception caused by a cvs bug.
			// if the folder has no files in it (just subfolders) cvs does not respond with the subfolders...
			// workaround: retry the request with no tag to get the directory names (if any)
			Policy.checkCanceled(monitor);
			status = Command.UPDATE.execute(session,
				new GlobalOption[] { Command.DO_NOT_CHANGE },
				getOptionsWithoutTag(),
				new String[] { localPath.toString() },
				new UpdateListener(listener),
				monitor);
			if (status.getCode() == CVSStatus.SERVER_ERROR) {
				throw new CVSServerException(status);
			}
		}
	}
	
	// Get the file revisions for the given filenames
	private void fetchFileRevisions(Session session, String[] fileNames, IProgressMonitor monitor) throws CVSException {
		
		// Create a listener for receiving the revision info
		final int[] count = new int[] {0};
		final Map revisions = new HashMap();
		IStatusListener listener = new IStatusListener() {
			public void fileStatus(IPath path, String remoteRevision) {
				try {
					updateRevision(path, remoteRevision);
					count[0]++;
				} catch (CVSException e) {
					// The count will be off which will trigger another exception
					CVSProviderPlugin.log(e);
				}
			}
		};
			
		// Perform a "cvs status..." with a custom message handler
		IStatus status = Command.STATUS.execute(session,
			Command.NO_GLOBAL_OPTIONS,
			Command.NO_LOCAL_OPTIONS,
			fileNames,
			new StatusListener(listener),
			monitor);
		if (status.getCode() == CVSStatus.SERVER_ERROR) {
			throw new CVSServerException(status);
		}
		
		// XXX we can't make this check because it may be valid to call this method
		// without any file names (e.g. fileNames array empty) which would run the
		// status on all files.
		//if (count[0] != fileNames.length)
		//	throw new CVSException(Policy.bind("RemoteFolder.errorFetchingRevisions"));
	}

	private boolean pruneEmptyDirectories() {
		return CVSProviderPlugin.getPlugin().getPruneEmptyDirectories();
	}
	/*
	 * Record the deltas in a double map where the outer key is the parent directory
	 * and the inner key is the file name. The value is the revision of the file or
	 * DELETED (file or folder). New folders have a revision of FOLDER.
	 * 
	 * A revison of UNKNOWN indicates that the revision has not been fetched
	 * from the repository yet.
	 */
	private void recordDelta(IPath path, String revision, int syncState) {
		IPath parent = path.removeLastSegments(1);
		Map deltas = (Map)fileDeltas.get(parent);
		if (deltas == null) {
			deltas = new HashMap();
			fileDeltas.put(parent, deltas);
		}
		String name = path.lastSegment();
		deltas.put(name, new DeltaNode(name, revision, syncState));
	}
	
	private void updateRevision(IPath path, String revision) throws CVSException {
		RemoteFolderTree folder = (RemoteFolderTree)remoteFolderTable.get(path.removeLastSegments(1).toString());
		((RemoteFile)folder.getFile(path.lastSegment())).setRevision(revision);
	}
	
	/*
	 * Return the tag that should be associated with a remote folder.
	 * 
	 * This method is used to ensure that new directories contain the tag
	 * derived from the parant local folder when appropriate. For instance,
	 * 
	 * The tag should be the provided tag. However, if tag is null, the 
	 * tag for the folder should be derived from the provided reference folder
	 * which could be the local resource corresponding to the remote or the parent
	 * of the remote.
	 */
	private CVSTag tagForRemoteFolder(ICVSFolder folder, CVSTag tag) throws CVSException {
		return tag == null ? folder.getFolderSyncInfo().getTag() : tag;
	}
	
	private boolean isOrphanedSubtree(Session session, ICVSFolder mFolder) {
		return mFolder.isCVSFolder() && ! mFolder.isManaged() && ! mFolder.equals(session.getLocalRoot()) && mFolder.getParent().isCVSFolder();
	}
}

