package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ICVSRunnable;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.syncinfo.BaserevInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.NotifyInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ReentrantLock;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.SyncFileWriter;

/**
 * A synchronizer is responsible for managing synchronization information for local
 * CVS resources.
 * 
 * @see ResourceSyncInfo
 * @see FolderSyncInfo
 */
public class EclipseSynchronizer {	
	protected static final String IS_DIRTY_INDICATOR = LowLevelSyncInfoCache.IS_DIRTY_INDICATOR;
	protected static final String NOT_DIRTY_INDICATOR = LowLevelSyncInfoCache.NOT_DIRTY_INDICATOR;
		
	// the cvs eclipse synchronizer is a singleton
	private static EclipseSynchronizer instance;
	
	// track resources that have changed in a given operation
	private ReentrantLock lock = new ReentrantLock();
	
	private Set changedResources = new HashSet();
	private Set changedFolders = new HashSet();
	
	private static IContainer cachedFolder;
	private static Map cachedResourceSyncInfos;
	private static boolean cacheDirty = false;
	
	private SessionPropertySyncInfoCache sessionPropertyCache = new SessionPropertySyncInfoCache();
	private SynchronizerSyncInfoCache synchronizerCache = new SynchronizerSyncInfoCache();
	
	/*
	 * Package private contructor to allow specialized subclass for handling folder deletions
	 */
	EclipseSynchronizer() {		
	}
	
	/**
	 * Returns the singleton instance of the synchronizer.
	 */
	public static EclipseSynchronizer getInstance() {		
		if(instance==null) {
			instance = new EclipseSynchronizer();
		}
		return instance;
	}
	
	public LowLevelSyncInfoCache getLowLevelCacheFor(IResource resource) {
		if (resource.isPhantom()) {
			return synchronizerCache;
		} else {
			return sessionPropertyCache;
		}
	}

	/**
	 * Sets the folder sync info for the specified folder.
	 * The folder must exist and must not be the workspace root.
	 * 
	 * @param folder the folder
	 * @param info the folder sync info, must not be null
	 * @see #getFolderSync, #deleteFolderSync
	 */
	public void setFolderSync(IContainer folder, FolderSyncInfo info) throws CVSException {
		Assert.isNotNull(info); // enforce the use of deleteFolderSync
		if (folder.getType() == IResource.ROOT || ! folder.exists()) {
			throw new CVSException(IStatus.ERROR, CVSException.UNABLE,
				Policy.bind("EclipseSynchronizer.ErrorSettingFolderSync", folder.getFullPath().toString())); //$NON-NLS-1$
		}
		try {
			beginOperation(null);
			// set folder sync and notify
			getLowLevelCacheFor(folder).setCachedFolderSync(folder, info);
			changedFolders.add(folder);
		} finally {
			endOperation(null);
		}
	}
	
	/**
	 * Gets the folder sync info for the specified folder.
	 * 
	 * @param folder the folder
	 * @return the folder sync info associated with the folder, or null if none.
	 * @see #setFolderSync, #deleteFolderSync
	 */
	public FolderSyncInfo getFolderSync(IContainer folder) throws CVSException {
		if (folder.getType() == IResource.ROOT || ! folder.exists()) return null;
		try {
			beginOperation(null);
			// cache folder sync and return it
			return getLowLevelCacheFor(folder).cacheFolderSync(folder);
		} finally {
			endOperation(null);
		}
	}	

	/**
	 * Deletes the folder sync for the specified folder and the resource sync
	 * for all of its children.  Does not recurse.
	 * 
	 * @param folder the folder
	 * @see #getFolderSync, #setFolderSync
	 */
	public void deleteFolderSync(IContainer folder) throws CVSException {
		if (folder.getType() == IResource.ROOT || ! folder.exists()) return;
		try {
			beginOperation(null);
			// delete folder sync
			getLowLevelCacheFor(folder).setCachedFolderSync(folder, null);
			changedFolders.add(folder);
			// iterate over all children with sync info and prepare notifications
			getLowLevelCacheFor(folder).cacheResourceSyncForChildren(folder);
			fastCacheResourceSyncForChildren(folder);
			for (Iterator iter = cachedResourceSyncInfos.values().iterator(); iter.hasNext();) {
				ResourceSyncInfo info = (ResourceSyncInfo) iter.next();
				IPath path = new Path(info.getName());
				if(info.isDirectory()) {
					changedResources.add(folder.getFolder(path));
				} else {
					changedResources.add(folder.getFile(path));
				}
			}
			// delete resource sync for all children
			getLowLevelCacheFor(folder).setCachedResourceSyncForChildren(folder, null);
		} finally {
			endOperation(null);
		}
	}

	/**
	 * Sets the resource sync info for the specified resource.
	 * The parent folder must exist and must not be the workspace root.
	 * 
	 * @param resource the resource
	 * @param info the resource sync info, must not be null
	 * @see #getResourceSync, #deleteResourceSync
	 */
	public void setResourceSync(IResource resource, ResourceSyncInfo info) throws CVSException {
		Assert.isNotNull(info); // enforce the use of deleteResourceSync
		IContainer parent = resource.getParent();
		if (parent == null || ! parent.exists() || parent.getType() == IResource.ROOT) {
			throw new CVSException(IStatus.ERROR, CVSException.UNABLE,
				Policy.bind("EclipseSynchronizer.ErrorSettingResourceSync", resource.getFullPath().toString())); //$NON-NLS-1$
		}
		try {
			beginOperation(null);
			// cache resource sync for siblings, set for self, then notify
			getLowLevelCacheFor(parent).cacheResourceSyncForChildren(parent);
			setCachedResourceSync(resource, info);
			changedResources.add(resource);		
		} finally {
			endOperation(null);
		}
	}
	
	/**
	 * Gets the resource sync info for the specified folder.
	 * 
	 * @param resource the resource
	 * @return the resource sync info associated with the resource, or null if none.
	 * @see #setResourceSync, #deleteResourceSync
	 */
	public ResourceSyncInfo getResourceSync(IResource resource) throws CVSException {
		IContainer parent = resource.getParent();
		if (parent == null || ! parent.exists() || parent.getType() == IResource.ROOT) return null;
		try {
			beginOperation(null);
			// cache resource sync for siblings, then return for self
			getLowLevelCacheFor(parent).cacheResourceSyncForChildren(parent);
			return getCachedResourceSync(resource);
		} finally {
			endOperation(null);
		}
	}

	/**
	 * Deletes the resource sync info for the specified resource, if it exists.
	 * 
	 * @param resource the resource
	 * @see #getResourceSync, #setResourceSync
	 */
	public void deleteResourceSync(IResource resource) throws CVSException {
		IContainer parent = resource.getParent();
		if (parent == null || ! parent.exists() || parent.getType() == IResource.ROOT) return;
		try {
			beginOperation(null);
			// cache resource sync for siblings, delete for self, then notify
			getLowLevelCacheFor(parent).cacheResourceSyncForChildren(parent);
			if (getCachedResourceSync(resource) != null) { // avoid redundant notifications
				setCachedResourceSync(resource, null);
				changedResources.add(resource);
			}
		} finally {
			endOperation(null);
		}
	}

	/**
	 * Gets the array of ignore patterns for the specified folder.
	 * 
	 * @param folder the folder
	 * @return the patterns, or an empty array if none
	 * @see #addIgnored
	 */
	public String[] getIgnored(IContainer folder) throws CVSException {
		if (folder.getType() == IResource.ROOT || ! folder.exists()) return SessionPropertySyncInfoCache.NULL_IGNORES;
		try {
			beginOperation(null);
			return cacheFolderIgnores(folder);
		} finally {
			endOperation(null);
		}
	}
	
	/**
	 * Adds a pattern to the set of ignores for the specified folder.
	 * 
	 * @param folder the folder
	 * @param pattern the pattern
	 */
	public void addIgnored(IContainer folder, String pattern) throws CVSException {
		if (folder.getType() == IResource.ROOT || ! folder.exists()) {
			throw new CVSException(IStatus.ERROR, CVSException.UNABLE,
				Policy.bind("EclipseSynchronizer.ErrorSettingIgnorePattern", folder.getFullPath().toString())); //$NON-NLS-1$
		}
		try {
			beginOperation(null);
			String[] ignores = cacheFolderIgnores(folder);
			if (ignores != null) {
				// verify that the pattern has not already been added
				for (int i = 0; i < ignores.length; i++) {
					if (ignores[i].equals(pattern)) return;
				}
				// add the pattern
				String[] oldIgnores = ignores;
				ignores = new String[oldIgnores.length + 1];
				System.arraycopy(oldIgnores, 0, ignores, 0, oldIgnores.length);
				ignores[oldIgnores.length] = pattern;
			} else {
				ignores = new String[] { pattern };
			}
			setCachedFolderIgnores(folder, ignores);
			SyncFileWriter.writeCVSIgnoreEntries(folder, ignores);
			// broadcast changes to unmanaged children - they are the only candidates for being ignored
			List possibleIgnores = new ArrayList();
			accumulateNonManagedChildren(folder, possibleIgnores);
			CVSProviderPlugin.broadcastSyncInfoChanges((IResource[])possibleIgnores.toArray(new IResource[possibleIgnores.size()]));
		} finally {
			endOperation(null);
		}
	}
	
	/**
	 * Returns the members of this folder including deleted resources with sync info,
	 * but excluding special resources such as CVS subdirectories.
	 *
	 * @param folder the container to list
	 * @return the array of members
	 */
	public IResource[] members(IContainer folder) throws CVSException {
		if (! folder.exists()) return new IResource[0];
		try {				
			beginOperation(null);
			if (folder.getType() == IResource.ROOT) return folder.members();
			getLowLevelCacheFor(folder).cacheResourceSyncForChildren(folder);
			fastCacheResourceSyncForChildren(folder);
			// add all children with or without sync info
			Set childResources = new HashSet();
			for (Iterator iter = cachedResourceSyncInfos.values().iterator(); iter.hasNext();) {
				ResourceSyncInfo info = (ResourceSyncInfo) iter.next();
				IPath path = new Path(info.getName());
				if(info.isDirectory()) {
					childResources.add(folder.getFolder(path));
				} else {
					childResources.add(folder.getFile(path));
				}
			}
			childResources.addAll(Arrays.asList(folder.members()));
			return (IResource[])childResources.toArray(new IResource[childResources.size()]);
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		} finally {
			endOperation(null);
		}
	}
	
	/**
	 * Begins a batch of operations.
	 * 
	 * @param monitor the progress monitor, may be null
	 */
	public void beginOperation(IProgressMonitor monitor) throws CVSException {
		lock.acquire();

		if (lock.getNestingCount() == 1) {
			prepareCache(monitor);
		}		
	}
	
	/**
	 * Ends a batch of operations.  Pending changes are committed only when
	 * the number of calls to endOperation() balances those to beginOperation().
	 * <p>
	 * Progress cancellation is ignored while writting the cache to disk. This
	 * is to ensure cache to disk consistency.
	 * </p>
	 * 
	 * @param monitor the progress monitor, may be null
	 * @exception CVSException with a status with code <code>COMMITTING_SYNC_INFO_FAILED</code>
	 * if all the CVS sync information could not be written to disk.
	 */
	public void endOperation(IProgressMonitor monitor) throws CVSException {		
		try {
			IStatus status = LowLevelSyncInfoCache.STATUS_OK;
			if (lock.getNestingCount() == 1) {
				status = commitCache(monitor);
			}
			if (!status.isOK()) {
				throw new CVSException(status);
			}
		} finally {
			lock.release();
		}
	}
	
	/**
	 * Flushes unwritten sync information to disk.
	 * <p>
	 * Recursively commits unwritten sync information for all resources 
	 * below the root, and optionally purges the cached data from memory
	 * so that the next time it is accessed it will be retrieved from disk.
	 * May flush more sync information than strictly needed, but never less.
	 * </p>
	 * <p>
	 * Will throw a CVS Exception with a status with code = CVSStatus.DELETION_FAILED 
	 * if the flush could not perform CVS folder deletions. In this case, all other
	 * aspects of the operation succeeded.
	 * </p>
	 * 
	 * @param root the root of the subtree to flush
	 * @param purgeCache if true, purges the cache from memory as well
	 * @param deep purge sync from child folders
	 * @param monitor the progress monitor, may be null
	 */
	public void flush(IContainer root, boolean purgeCache, boolean deep, IProgressMonitor monitor) throws CVSException {
		// flush unwritten sync info to disk
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 10);
		try {
			beginOperation(Policy.subMonitorFor(monitor, 1));
			
			IStatus status = commitCache(Policy.subMonitorFor(monitor, 7));
			
			// purge from memory too if we were asked to
			if (purgeCache) sessionPropertyCache.purgeCache(root, deep);
	
			// prepare for the operation again if we cut the last one short
			prepareCache(Policy.subMonitorFor(monitor, 1));
			
			if (!status.isOK()) {
				throw new CVSException(status);
			}
		} finally {
			endOperation(Policy.subMonitorFor(monitor, 1));
			monitor.done();
		}
	}
	
	/**
	 * Called to notify the synchronizer that meta files have changed on disk, outside 
	 * of the workbench. The cache will be flushed for this folder and it's immediate
	 * children and appropriate state change events are broadcasts to state change
	 * listeners.
	 */
	public void syncFilesChanged(IContainer[] roots) throws CVSException {
		try {
			for (int i = 0; i < roots.length; i++) {
				IContainer root = roots[i];
				flush(root, true, false /*don't flush children*/, null);
				List changedPeers = new ArrayList();
				changedPeers.add(root);
				changedPeers.addAll(Arrays.asList(root.members()));
				IResource[] resources = (IResource[]) changedPeers.toArray(new IResource[changedPeers.size()]);
				CVSProviderPlugin.broadcastSyncInfoChanges(resources);
				CVSProviderPlugin.getPlugin().getFileModificationManager().syncInfoChanged(resources);
			}
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
	}
	
	/**
	 * The folder is about to be deleted (including its CVS subfolder).
	 * Take any appropriate action to remember the CVS information.
	 */
	public void prepareForDeletion(IContainer container) throws CVSException {
		try {
			beginOperation(null);
			purgeFastCache();
			if (container.getType() == IResource.PROJECT) {
				synchronizerCache.flush((IProject)container);
			} else {
				// Move the folder sync info into phantom space
				FolderSyncInfo info = getFolderSync(container);
				if (info == null) return;
				synchronizerCache.setCachedFolderSync(container, info);
				synchronizerCache.setCachedResourceSyncForChildren(container, sessionPropertyCache.getCachedResourceSyncForChildren(container));
				changedFolders.add(container);
				// todo
				// Move the dirty count into phantom space
				int dirtyCount = getDirtyCount(container);
				if (dirtyCount != -1) {
					synchronizerCache.setCachedDirtyCount(container, dirtyCount);
				}
			}
		} finally {
			endOperation(null);
		}
	}
	
	/**
	 * Notify the receiver that a folder has been created.
	 * Any existing phantom sync info will be moved
	 *
	 * @param folder the folder that has been created
	 */
	public void folderCreated(IFolder folder) throws CVSException {
		try {
			// set the dirty count using what was cached in the phantom it
			beginOperation(null);
			FolderSyncInfo folderInfo = synchronizerCache.getCachedFolderSync(folder);
			if (folderInfo != null) {
				byte[][] infos = synchronizerCache.getCachedResourceSyncForChildren(folder);
				if (folder.getFolder(SyncFileWriter.CVS_DIRNAME).exists()) {
					// There is already a CVS subdirectory which indicates that
					// either the folder was recreated by an external tool or that
					// a folder with CVS information was copied from another location.
					// To know the difference, we need to compare the folder sync info.
					// If they are mapped to the same root and repository then just
					// purge the phantom info. Otherwise, keep the original sync info.

					// flush the phantom info so we can get what is on disk.
					synchronizerCache.flush(folder);

					// Get the new folder sync info
					FolderSyncInfo newFolderInfo = getFolderSync(folder);
					if (newFolderInfo.getRoot().equals(folderInfo.getRoot())
						&& newFolderInfo.getRepository().equals(folderInfo.getRepository())) {
							// The folder is the same so use what is on disk
							return;
					}

					// The folder is mapped to a different location.
					// Purge new resource sync before restoring from phantom
					ICVSFolder cvsFolder = CVSWorkspaceRoot.getCVSFolderFor(folder);
					ICVSResource[] children = cvsFolder.members(ICVSFolder.MANAGED_MEMBERS);
					for (int i = 0; i < children.length; i++) {
						ICVSResource resource = children[i];
						deleteResourceSync(resource.getIResource());
					}
				}

				// set the sync info using what was cached in the phantom
				setFolderSync(folder, folderInfo);
				sessionPropertyCache.setCachedResourceSyncForChildren(folder, infos);
			}
		} finally {
			try {
				endOperation(null);
			} finally {
				synchronizerCache.flush(folder);
			}
		}
	}
	
	/**
	 * Prepares the cache for a series of operations.
	 *
	 * @param monitor the progress monitor, may be null
	 */
	private void prepareCache(IProgressMonitor monitor) throws CVSException {
	}
	
	/**
	 * Commits the cache after a series of operations.
	 * 
	 * Will return STATUS_OK unless there were problems writting sync 
	 * information to disk. If an error occurs a multistatus is returned
	 * with the list of reasons for the failures. Failures are recovered,
	 * and all changed resources are given a chance to be written to disk.
	 * 
	 * @param monitor the progress monitor, may be null
	 */
	private IStatus commitCache(IProgressMonitor monitor) {
		// write the fast cache to the low level cache
		IStatus status = LowLevelSyncInfoCache.STATUS_OK;
		try {
			purgeFastCache();
		} catch (CVSException e) {
			status = e.getStatus();
		}
		
		// Commit the session property cache to disk.
		status = mergeStatus(sessionPropertyCache.commitCache(monitor), status);

		/*** broadcast events ***/
		changedResources.addAll(changedFolders);				
		IResource[] resources = (IResource[]) changedResources.toArray(
			new IResource[changedResources.size()]);
		broadcastResourceStateChanges(resources);
		changedResources.clear();
		changedFolders.clear();
		return status;
	}
	
	/**
	 * Method mergeStatus.
	 * @param iStatus
	 * @param status
	 * @return IStatus
	 */
	private IStatus mergeStatus(IStatus status1, IStatus status2) {
		if (status1.isOK()) return status2;
		if (status2.isOK()) return status1;
		if (status1.isMultiStatus()) {
			((MultiStatus)status1).merge(status2);
			return status1;
		}
		if (status2.isMultiStatus()) {
			((MultiStatus)status2).merge(status1);
			return status2;
		}
		return new MultiStatus(CVSProviderPlugin.ID,
								CVSStatus.COMMITTING_SYNC_INFO_FAILED,
								new IStatus[] { status1, status2 },
								Policy.bind("EclipseSynchronizer.ErrorCommitting"), //$NON-NLS-1$
								null);
	}
	
	/**
	 * Broadcasts the resource state changes for the given resources to CVS Provider Plugin
	 */
	void broadcastResourceStateChanges(IResource[] resources) {
		if (resources.length > 0) {
			CVSProviderPlugin.broadcastSyncInfoChanges(resources);
		}
	}
	
	/**
	 * Returns the resource sync info for the resource; null if none.
	 * Parent must exist and must not be the workspace root.
	 * The resource sync info for the children of the parent container MUST ALREADY BE CACHED.
	 * 
	 * @param resource the resource
	 * @return the resource sync info for the resource, or null
	 * @see #cacheResourceSyncForChildren
	 */
	private ResourceSyncInfo getCachedResourceSync(IResource resource) throws CVSException {
		fastCacheResourceSyncForChildren(resource.getParent());
		return (ResourceSyncInfo) cachedResourceSyncInfos.get(resource.getName());
	}

	private void fastCacheResourceSyncForChildren(IContainer parent) throws CVSException {
		if (!parent.equals(cachedFolder)) {
			purgeFastCache();
			byte[][] infos = getLowLevelCacheFor(parent).getCachedResourceSyncForChildren(parent);
			if (infos == null) {
				// There should be sync info but it was missing. Report the error
				throw new CVSException(Policy.bind("EclipseSynchronizer.folderSyncInfoMissing", parent.getFullPath().toString())); //$NON-NLS-1$
			}
			HashMap children = new HashMap();
			for (int i = 0; i < infos.length; i++) {
				ResourceSyncInfo info = new ResourceSyncInfo(infos[i]);
				children.put(info.getName(), info);
			}
			cachedResourceSyncInfos = children;
			cachedFolder = parent;
		}
	}
	
	/**
	 * Method purgeCurrentFolderCache.
	 */
	private void purgeFastCache() throws CVSException {
		try {
			beginOperation(null);
			if (cacheDirty) {
				byte[][] newInfos = null;
				if (!cachedResourceSyncInfos.isEmpty()) {
					newInfos = new byte[cachedResourceSyncInfos.size()][];
					int i = 0;
					for (Iterator iter = cachedResourceSyncInfos.values().iterator(); iter.hasNext();) {
						ResourceSyncInfo info = (ResourceSyncInfo) iter.next();
						newInfos[i++] = info.getBytes();
					}
				}
				getLowLevelCacheFor(cachedFolder).setCachedResourceSyncForChildren(cachedFolder, newInfos);
			}
			cacheDirty = false;
			cachedFolder = null;
			cachedResourceSyncInfos = null;
		} finally {
			endOperation(null);
		}
	}

	/**
 	 * Sets the resource sync info for the resource; if null, deletes it. Parent
 	 * must exist and must not be the workspace root. The resource sync info for
 	 * the children of the parent container MUST ALREADY BE CACHED.
	 * 
	 * @param resource the resource
	 * @param info the new resource sync info
	 * @see #cacheResourceSyncForChildren
	 */
	private void setCachedResourceSync(IResource resource, ResourceSyncInfo info) throws CVSException {
		// Get the old info to trigger caching for the parent
		fastCacheResourceSyncForChildren(resource.getParent());
		Assert.isNotNull(cachedResourceSyncInfos);
		Assert.isTrue(resource.getParent().equals(cachedFolder));
		if (info == null) {
			cachedResourceSyncInfos.remove(resource.getName());
		} else {
			cachedResourceSyncInfos.put(resource.getName(), info);
		}
		cacheDirty = true;
	}
	
	/**
	 * If not already cached, loads and caches the folder ignores sync for the container.
	 * Folder must exist and must not be the workspace root.
	 * 
	 * @param container the container
	 * @return the folder ignore patterns, or an empty array if none
	 */
	private String[] cacheFolderIgnores(IContainer container) throws CVSException {
		return sessionPropertyCache.cacheFolderIgnores(container);
	}
	
	/**
	 * Sets the array of folder ignore patterns for the container, must not be null.
	 * Folder must exist and must not be the workspace root.
	 * 
	 * @param container the container
	 * @param ignores the array of ignore patterns
	 */
	private void setCachedFolderIgnores(IContainer container, String[] ignores) throws CVSException {
		sessionPropertyCache.setCachedFolderIgnores(container, ignores);
	}
	
	/**
	 * Recursively adds to the possibleIgnores list all children of the given 
	 * folder that can be ignored.
	 * 
	 * @param folder the folder to be searched
	 * @param possibleIgnores the list of IResources that can be ignored
	 */
	private void accumulateNonManagedChildren(IContainer folder, List possibleIgnores) throws CVSException {
		try {
			getLowLevelCacheFor(folder).cacheResourceSyncForChildren(folder);
			IResource[] children = folder.members();
			List folders = new ArrayList();
			// deal with all files first and then folders to be otimized for caching scheme
			for (int i = 0; i < children.length; i++) {
				IResource child = children[i];
				if(getCachedResourceSync(child)==null) {
					possibleIgnores.add(child);
				}
				if(child.getType()!=IResource.FILE) {
					folders.add(child);
				}
			}
			for (Iterator iter = folders.iterator(); iter.hasNext();) {
				IContainer child = (IContainer) iter.next();
				accumulateNonManagedChildren(child, possibleIgnores);
			}
		} catch(CoreException e) {
			throw CVSException.wrapException(e);
		}
	}
	
	/**
	 * Add the entry to the CVS/Notify file. We are not initially concerned with efficiency
	 * since edit/unedit are typically issued on a small set of files.
	 * 
	 * XXX If there was a previous notify entry for the resource, it is replaced. This is
	 * probably not the proper behavior (see EclipseFile).
	 * 
	 * @param resource
	 * @param info
	 */
	public void setNotifyInfo(IResource resource, NotifyInfo info) throws CVSException {
		NotifyInfo[] infos = SyncFileWriter.readAllNotifyInfo(resource.getParent());
		if (infos == null) {
			infos = new NotifyInfo[] { info };
		} else {
			Map infoMap = new HashMap();
			for (int i = 0; i < infos.length; i++) {
				NotifyInfo notifyInfo = infos[i];
				infoMap.put(infos[i].getName(), infos[i]);
			}
			infoMap.put(info.getName(), info);
			NotifyInfo[] newInfos = new NotifyInfo[infoMap.size()];
			int i = 0;
			for (Iterator iter = infoMap.values().iterator(); iter.hasNext();) {
				newInfos[i++] = (NotifyInfo) iter.next();
			}
			infos = newInfos;
		}
		SyncFileWriter.writeAllNotifyInfo(resource.getParent(), infos);
	}

	/**
	 * Method getNotifyInfo.
	 * @param resource
	 * @return NotifyInfo
	 */
	public NotifyInfo getNotifyInfo(IResource resource) throws CVSException {
		NotifyInfo[] infos = SyncFileWriter.readAllNotifyInfo(resource.getParent());
		if (infos == null) return null;
		for (int i = 0; i < infos.length; i++) {
			NotifyInfo notifyInfo = infos[i];
			if (notifyInfo.getName().equals(resource.getName())) {
				return notifyInfo;
			}
		}
		return null;
	}

	/**
	 * Method deleteNotifyInfo.
	 * @param resource
	 */
	public void deleteNotifyInfo(IResource resource) throws CVSException {
		NotifyInfo[] infos = SyncFileWriter.readAllNotifyInfo(resource.getParent());
		if (infos == null) return;
		Map infoMap = new HashMap();
		for (int i = 0; i < infos.length; i++) {
			NotifyInfo notifyInfo = infos[i];
			infoMap.put(infos[i].getName(), infos[i]);
		}
		infoMap.remove(resource.getName());
		NotifyInfo[] newInfos = new NotifyInfo[infoMap.size()];
		int i = 0;
		for (Iterator iter = infoMap.values().iterator(); iter.hasNext();) {
			newInfos[i++] = (NotifyInfo) iter.next();
		}
		SyncFileWriter.writeAllNotifyInfo(resource.getParent(), newInfos);
	}
	
	/**
	 * Add the entry to the CVS/Baserev file. We are not initially concerned
	 * with efficiency since edit/unedit are typically issued on a small set of
	 * files.
	 *
	 * XXX If there was a previous notify entry for the resource, it is replaced. This is
	 * probably not the proper behavior (see EclipseFile).
	 *
	 * @param resource
	 * @param info
	 */
	public void setBaserevInfo(IResource resource, BaserevInfo info) throws CVSException {
		BaserevInfo[] infos = SyncFileWriter.readAllBaserevInfo(resource.getParent());
		if (infos == null) {
			infos = new BaserevInfo[] { info };
		} else {
			Map infoMap = new HashMap();
			for (int i = 0; i < infos.length; i++) {
				infoMap.put(infos[i].getName(), infos[i]);
			}
			infoMap.put(info.getName(), info);
			BaserevInfo[] newInfos = new BaserevInfo[infoMap.size()];
			int i = 0;
			for (Iterator iter = infoMap.values().iterator(); iter.hasNext();) {
				newInfos[i++] = (BaserevInfo) iter.next();
			}
			infos = newInfos;
		}
		SyncFileWriter.writeAllBaserevInfo(resource.getParent(), infos);
	}

	/**
	 * Method getBaserevInfo.
	 * @param resource
	 * @return BaserevInfo
	 */
	public BaserevInfo getBaserevInfo(IResource resource) throws CVSException {
		BaserevInfo[] infos = SyncFileWriter.readAllBaserevInfo(resource.getParent());
		if (infos == null) return null;
		for (int i = 0; i < infos.length; i++) {
			BaserevInfo info = infos[i];
			if (info.getName().equals(resource.getName())) {
				return info;
			}
		}
		return null;
	}
			
	/**
	 * Method deleteNotifyInfo.
	 * @param resource
	 */
	public void deleteBaserevInfo(IResource resource) throws CVSException {
		BaserevInfo[] infos = SyncFileWriter.readAllBaserevInfo(resource.getParent());
		if (infos == null) return;
		Map infoMap = new HashMap();
		for (int i = 0; i < infos.length; i++) {
			infoMap.put(infos[i].getName(), infos[i]);
		}
		infoMap.remove(resource.getName());
		BaserevInfo[] newInfos = new BaserevInfo[infoMap.size()];
		int i = 0;
		for (Iterator iter = infoMap.values().iterator(); iter.hasNext();) {
			newInfos[i++] = (BaserevInfo) iter.next();
		}
		SyncFileWriter.writeAllBaserevInfo(resource.getParent(), newInfos);
	}

	public void copyFileToBaseDirectory(final IFile file, IProgressMonitor monitor) throws CVSException {
		run(new ICVSRunnable() {
			public void run(IProgressMonitor monitor) throws CVSException {
				ResourceSyncInfo info = getResourceSync(file);
				// The file must exist remotely and locally
				if (info == null || info.isAdded() || info.isDeleted())
					return;
				SyncFileWriter.writeFileToBaseDirectory(file, monitor);
				changedResources.add(file);
			}
		}, monitor);
	}
	
	public void restoreFileFromBaseDirectory(final IFile file, IProgressMonitor monitor) throws CVSException {
		run(new ICVSRunnable() {
			public void run(IProgressMonitor monitor) throws CVSException {
				ResourceSyncInfo info = getResourceSync(file);
				// The file must exist remotely
				if (info == null || info.isAdded())
					return;
				SyncFileWriter.restoreFileFromBaseDirectory(file, monitor);
				changedResources.add(file);
			}
		}, monitor);
	}
	
	public void deleteFileFromBaseDirectory(final IFile file, IProgressMonitor monitor) throws CVSException {
		ResourceSyncInfo info = getResourceSync(file);
		// The file must exist remotely
		if (info == null || info.isAdded())
			return;
		SyncFileWriter.deleteFileFromBaseDirectory(file, monitor);
	}
	
	/**
	 * Method isSyncInfoLoaded returns true if all the sync info for the
	 * provided resources is loaded into the internal cache.
	 * 
	 * @param resources
	 * @param i
	 * @return boolean
	 */
	public boolean isSyncInfoLoaded(IResource[] resources, int depth) throws CVSException {
		// get the folders involved
		IContainer[] folders = getParentFolders(resources, depth);
		// for all folders that have a CVS folder, ensure the sync info is cached
		for (int i = 0; i < folders.length; i++) {
			IContainer parent = folders[i];
			if (!getLowLevelCacheFor(parent).isSyncInfoLoaded(parent)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Method ensureSyncInfoLoaded loads all the relevent sync info into the cache
	 * @param resources
	 * @param i
	 * @return Object
	 */
	public void ensureSyncInfoLoaded(IResource[] resources, int depth) throws CVSException {
		// get the folders involved
		IContainer[] folders = getParentFolders(resources, depth);
		// Cache the sync info for all the folders
		for (int i = 0; i < folders.length; i++) {
			IContainer parent = folders[i];
			try {
				beginOperation(null);
				getLowLevelCacheFor(parent).cacheResourceSyncForChildren(parent);
				getLowLevelCacheFor(parent).cacheFolderSync(parent);
				cacheFolderIgnores(parent);
			} finally {
				endOperation(null);
			}
		}
	}

	/*
	 * Collect the projects and parent folders of the resources since 
	 * thats were the sync info is kept.
	 */
	private IContainer[] getParentFolders(IResource[] resources, int depth) throws CVSException {
		final Set folders = new HashSet();
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			folders.add(resource.getProject());
			if (resource.getType() != IResource.PROJECT) {
				folders.add(resource.getParent());
			}
			// use the depth to gather child folders when appropriate
			if (depth != IResource.DEPTH_ZERO) {
				try {
					resource.accept(new IResourceVisitor() {
						public boolean visit(IResource resource) throws CoreException {
							if (resource.getType() == IResource.FOLDER)
								folders.add(resource);
							// let the depth determine who we visit
							return true;
						}
					}, depth, false);
				} catch (CoreException e) {
					throw CVSException.wrapException(e);
				}
			}
		}
		return (IContainer[]) folders.toArray(new IContainer[folders.size()]);
	}
	
	public void run(ICVSRunnable job, IProgressMonitor monitor) throws CVSException {
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 100);
		try {
			beginOperation(Policy.subMonitorFor(monitor, 5));
			job.run(Policy.subMonitorFor(monitor, 60));
		} finally {
			endOperation(Policy.subMonitorFor(monitor, 35));
			monitor.done();
		}
	}
	
	/**
	 * Method isEdited returns true if a "cvs edit" was performed on the given
	 * file and no commit or unedit has yet been performed.
	 * @param iResource
	 * @return boolean
	 */
	public boolean isEdited(IFile resource) throws CVSException {
		return SyncFileWriter.isEdited(resource);
	}
	
	protected void setDirtyIndicator(IResource resource, String indicator) throws CVSException {
		getLowLevelCacheFor(resource).setDirtyIndicator(resource, indicator);
	}
	
	protected String getDirtyIndicator(IResource resource) throws CVSException {
		return getLowLevelCacheFor(resource).getDirtyIndicator(resource);
	}
	
	/*
	 * Return the dirty count for the given folder. For existing folders, the
	 * dirty count may not have been calculated yet and this method will return
	 * null in that case. For phantom folders, the dirty count is calculated if
	 * it does not exist yet.
	 */
	protected int getDirtyCount(IContainer container) throws CVSException {
		try {
			beginOperation(null);
			return getLowLevelCacheFor(container).getCachedDirtyCount(container);
		} finally {
		  endOperation(null);
	   }
	}
	protected void setDirtyCount(IContainer container, int count) throws CVSException {
		try {
			beginOperation(null);
			getLowLevelCacheFor(container).setCachedDirtyCount(container, count);
		} finally {
		   endOperation(null);
	   	}
	}
	
	/*
	 * Mark the given resource as either modified or clean using a persistant
	 * property. Do nothing if the modified state is already what we want.
	 * Return true if the modification state was changed.
	 */
	protected boolean setModified(IResource container, boolean modified) throws CVSException {
		String indicator = modified ? IS_DIRTY_INDICATOR : NOT_DIRTY_INDICATOR;
		// if it's already set, no need to set the property or adjust the parents count
		if (indicator.equals(getDirtyIndicator(container))) return false;
		// set the dirty indicator and adjust the parent accordingly
		setDirtyIndicator(container, indicator);
		return true;
	}
	
	/*
	 * Adjust the modified count for the given container and return true if the
	 * parent should be adjusted
	 */
	protected boolean adjustModifiedCount(IContainer container, boolean dirty) throws CVSException {
		if (container.getType() == IResource.ROOT) return false;
		int count = getDirtyCount(container);
		boolean updateParent = false;
		if (count == -1) {
			// The number of dirty children has not been tallied for this parent.
			// (i.e. no one has queried this folder yet)
			if (dirty) {
				// Make sure the parent and it's ansecestors
				// are still marked as dirty (if they aren't already)
				String indicator = getDirtyIndicator(container);
				if (indicator == null) {
					// The dirty state for the folder has never been cached
					// or the cache was flushed due to an error of some sort.
					// Let the next dirtyness query invoke the caching
				} else if (indicator.equals(NOT_DIRTY_INDICATOR)) {
					setModified(container, true);
					updateParent = true;
				}
			} else {
				// Let the initial query of dirtyness determine if the persistent
				// property is still acurate.
			}
		} else {
			if (dirty) {
				count++;
				if (count == 1) {
					setModified(container, true);
					updateParent = true;
				}
			} else {
				Assert.isTrue(count > 0);
				count--;
				if (count == 0) {
					setModified(container, false);
					updateParent = true;
				}
			}
			setDirtyCount(container, count);
		}
		return updateParent;
	}
	
	/*
	 * Add the deleted child and return true if it didn't exist before
	 */
	protected boolean addDeletedChild(IContainer container, IFile file) throws CVSException {
		try {
			beginOperation(null);
			getLowLevelCacheFor(container).addDeletedChild(container, file);
			return true;
		} finally {
			endOperation(null);
		}
	}
	
	protected boolean removeDeletedChild(IContainer container, IFile file) throws CVSException {
		try {
			beginOperation(null);
			getLowLevelCacheFor(container).removeDeletedChild(container, file);
			return true;
		} finally {
			endOperation(null);
		}
	}

	protected void setDeletedChildren(IContainer parent, Set deletedFiles) throws CVSException {
		if (!parent.exists()) return;
		sessionPropertyCache.setDeletedChildren(parent, deletedFiles);
	}
	
	protected void flushModificationCache(IResource resource, int depth) throws CVSException {
		if (resource.getType() == IResource.ROOT) return;
		try {
			final CVSException[] exception = new CVSException[] { null };
			beginOperation(null);
			resource.accept(new IResourceVisitor() {
				public boolean visit(IResource resource) throws CoreException {
					try {
						getLowLevelCacheFor(resource).flushDirtyCache(resource);
					} catch (CVSException e) {
						exception[0] = e;
					}
					return true;
				}
			}, depth, true);
			if (exception[0] != null) {
				throw exception[0];
			}
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		} finally {
			endOperation(null);
		}
	}
	
	/**
	 * Method updated flags the objetc as having been modfied by the updated
	 * handler. This flag is read during the resource delta to determine whether
	 * the modification made the file dirty or not.
	 *
	 * @param mFile
	 */
	public void markFileAsUpdated(IFile file) throws CVSException {
		sessionPropertyCache.markFileAsUpdated(file);
	}
	
	protected boolean contentsChangedByUpdate(IFile file) throws CVSException {
		return sessionPropertyCache.contentsChangedByUpdate(file);
	}
}
