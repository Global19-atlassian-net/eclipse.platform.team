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
package org.eclipse.team.internal.ccvs.core.resources;


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
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSRunnable;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.syncinfo.BaserevInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.NotifyInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ReentrantLock;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ReentrantLock.IFlushOperation;
import org.eclipse.team.internal.ccvs.core.syncinfo.ReentrantLock.ThreadInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.FileNameMatcher;
import org.eclipse.team.internal.ccvs.core.util.ResourceStateChangeListeners;
import org.eclipse.team.internal.ccvs.core.util.SyncFileWriter;

/**
 * A synchronizer is responsible for managing synchronization information for local
 * CVS resources.
 * 
 * This class is thread safe but only allows one thread to modify the cache at a time. It
 * doesn't support fine grain locking on a resource basis. Lock ordering between the workspace
 * lock and the synchronizer lock is guaranteed to be deterministic. That is, the workspace
 * lock is *always* acquired before the synchronizer lock. This protects against possible
 * deadlock cases where the synchronizer lock is acquired before a workspace lock.
 * 
 * Special processing has been added for linked folders and their childen so
 * that their CVS meta files are never read or written.
 * 
 * IMPORTANT NOTICE: It is the reponsibility of the clients of EclipseSynchronizer
 * to ensure that they have wrapped operations that may modify the workspace in
 * an IWorkspaceRunnable. If this is not done, deltas may fore at inopertune times 
 * and corrupt the sync info. The wrapping could be done within the synchronizer 
 * itself but would require the creation of an inner class for each case that requires
 * it.
 * 
 * @see ResourceSyncInfo
 * @see FolderSyncInfo
 */
public class EclipseSynchronizer implements IFlushOperation {	
	private static final String IS_DIRTY_INDICATOR = SyncInfoCache.IS_DIRTY_INDICATOR;
	private static final String NOT_DIRTY_INDICATOR = SyncInfoCache.NOT_DIRTY_INDICATOR;
	private static final String RECOMPUTE_INDICATOR = SyncInfoCache.RECOMPUTE_INDICATOR; 
		
	// the cvs eclipse synchronizer is a singleton
	private static EclipseSynchronizer instance;
	
	// track resources that have changed in a given operation
	private ILock lock = Platform.getJobManager().newLock();
	private ReentrantLock resourceLock = new ReentrantLock();
	
	private SynchronizerSyncInfoCache synchronizerCache = new SynchronizerSyncInfoCache();
	private SessionPropertySyncInfoCache sessionPropertyCache = new SessionPropertySyncInfoCache(synchronizerCache);
	
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
	
	public SyncInfoCache getSyncInfoCacheFor(IResource resource) {
		if (resource.exists() && resource.isLocal(IResource.DEPTH_ZERO)) {
			return sessionPropertyCache;
		} else {
			return synchronizerCache;
		}
	}

	private boolean isValid(IResource resource) {
		return resource.exists() || resource.isPhantom();
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
		// ignore folder sync on the root (i.e. CVSROOT/config/TopLevelAdmin=yes but we just ignore it)
		if (folder.getType() == IResource.ROOT) return;
		if (!isValid(folder)) {
			throw new CVSException(IStatus.ERROR, CVSException.UNABLE,
				Policy.bind("EclipseSynchronizer.ErrorSettingFolderSync", folder.getFullPath().toString())); //$NON-NLS-1$
		}
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(folder, null);
			try {
				beginOperation();
				// get the old info
				FolderSyncInfo oldInfo = getFolderSync(folder);
				// set folder sync and notify
				getSyncInfoCacheFor(folder).setCachedFolderSync(folder, info, true);
				// if the sync info changed from null, we may need to adjust the ancestors
				if (oldInfo == null) {
					adjustDirtyStateRecursively(folder, RECOMPUTE_INDICATOR);
				}
				folderChanged(folder);
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, null);
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
		if (folder.getType() == IResource.ROOT || !isValid(folder)) return null;
		try {
			beginOperation();
			cacheFolderSync(folder);
			return getSyncInfoCacheFor(folder).getCachedFolderSync(folder);
		} finally {
			endOperation();
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
		if (folder.getType() == IResource.ROOT || !isValid(folder)) return;
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(folder, null);
			try {
				beginOperation();
				// iterate over all children with sync info and prepare notifications
				// this is done first since deleting the folder sync may remove a phantom
				cacheResourceSyncForChildren(folder, true /* can modify workspace */);
				IResource[] children = folder.members(true);
				for (int i = 0; i < children.length; i++) {
					IResource resource = children[i];
					resourceChanged(resource);
					// delete resource sync for all children
					getSyncInfoCacheFor(resource).setCachedSyncBytes(resource, null, true);
				}
				// delete folder sync
				getSyncInfoCacheFor(folder).setCachedFolderSync(folder, null, true);
				folderChanged(folder);
			} catch (CoreException e) {
				throw CVSException.wrapException(e);
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, null);
		}
	}

	private void folderChanged(IContainer folder) {
		resourceLock.folderChanged(folder);
	}

	private void resourceChanged(IResource resource) {
		resourceLock.resourceChanged(resource);
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
		if (parent == null || parent.getType() == IResource.ROOT || !isValid(parent)) {
			throw new CVSException(IStatus.ERROR, CVSException.UNABLE,
				Policy.bind("EclipseSynchronizer.ErrorSettingResourceSync", resource.getFullPath().toString())); //$NON-NLS-1$
		}
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(resource, null);
			try {
				beginOperation();
				// cache resource sync for siblings, set for self, then notify
				cacheResourceSyncForChildren(parent, true /* can modify workspace */);
				setCachedResourceSync(resource, info);
				resourceChanged(resource);		
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, null);
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
		byte[] info = getSyncBytes(resource);
		if (info == null) return null;
		return new ResourceSyncInfo(info);
	}

	/**
	 * Gets the resource sync info for the specified folder.
	 * 
	 * @param resource the resource
	 * @return the resource sync info associated with the resource, or null if none.
	 * @see #setResourceSync, #deleteResourceSync
	 */
	public byte[] getSyncBytes(IResource resource) throws CVSException {
		IContainer parent = resource.getParent();
		if (parent == null || parent.getType() == IResource.ROOT || !isValid(parent)) return null;
		try {
			beginOperation();
			// cache resource sync for siblings, then return for self
			try {
				cacheResourceSyncForChildren(parent, false /* cannot modify workspace */);
			} catch (CVSException e) {
				if (isCannotModifySynchronizer(e) || isResourceNotFound(e)) {
					// We will resort to loading the sync info for the requested resource from disk
					byte[] bytes =  getSyncBytesFromDisk(resource);
					if (!resource.exists() && bytes != null && !ResourceSyncInfo.isDeletion(bytes)) {
						bytes = ResourceSyncInfo.convertToDeletion(bytes);
					}
					return bytes;
				} else {
					throw e;
				}
			}
			return getCachedSyncBytes(resource);
		} finally {
			endOperation();
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
	public void setSyncBytes(IResource resource, byte[] syncBytes) throws CVSException {
		Assert.isNotNull(syncBytes); // enforce the use of deleteResourceSync
		IContainer parent = resource.getParent();
		if (parent == null || parent.getType() == IResource.ROOT || !isValid(parent)) {
			throw new CVSException(IStatus.ERROR, CVSException.UNABLE,
				Policy.bind("EclipseSynchronizer.ErrorSettingResourceSync", resource.getFullPath().toString())); //$NON-NLS-1$
		}
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(resource, null);
			try {
				beginOperation();
				// cache resource sync for siblings, set for self, then notify
				cacheResourceSyncForChildren(parent, true /* can modify workspace */);
				setCachedSyncBytes(resource, syncBytes);
				resourceChanged(resource);		
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, null);
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
		if (parent == null || parent.getType() == IResource.ROOT || !isValid(parent)) return;
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(resource, null);
			try {
				beginOperation();
				// cache resource sync for siblings, delete for self, then notify
				cacheResourceSyncForChildren(parent, true /* can modify workspace */);
				if (getCachedSyncBytes(resource) != null) { // avoid redundant notifications
					setCachedSyncBytes(resource, null);
					clearDirtyIndicator(resource);
					resourceChanged(resource);
				}
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, null);
		}
	}

	/**
	 * @param resource
	 */
	private void clearDirtyIndicator(IResource resource) throws CVSException {
		getSyncInfoCacheFor(resource).flushDirtyCache(resource);
		adjustDirtyStateRecursively(resource.getParent(), RECOMPUTE_INDICATOR);
	}

	/**
	 * Gets the array of ignore patterns for the specified folder.
	 * 
	 * @param folder the folder
	 * @return the patterns, or an empty array if none
	 * @see #addIgnored
	 */
	public boolean isIgnored(IResource resource) throws CVSException {
		if (resource.getType() == IResource.ROOT || 
		    resource.getType() == IResource.PROJECT || 
		    ! resource.exists()) {
			return false;
		} 
		try {
			beginOperation();
			FileNameMatcher matcher = cacheFolderIgnores(resource.getParent());
			return matcher.match(resource.getName());
		} finally {
			endOperation();
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
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(folder, null);
			try {
				beginOperation();
				String[] ignores = SyncFileWriter.readCVSIgnoreEntries(folder);
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
				ResourceStateChangeListeners.getListener().resourceSyncInfoChanged((IResource[])possibleIgnores.toArray(new IResource[possibleIgnores.size()]));
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, null);
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
		if (! isValid(folder)) return new IResource[0];
		try {				
			beginOperation();
			if (folder.getType() != IResource.ROOT) {
				// ensure that the sync info is cached so any required phantoms are created
				cacheResourceSyncForChildren(folder, false);
			}
		} catch (CVSException e) {
			if (!isCannotModifySynchronizer(e) && !isResourceNotFound(e)) {
				throw e;
			}
		} finally {
			endOperation();
		}
		try {
			return folder.members(true);
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
	}
	
	private boolean isCannotModifySynchronizer(CVSException e) {
		// IResourceStatus.WORKSPACE_LOCKED can occur if the resource sync is loaded 
		// during the POST_CHANGE delta phase.
		// CVSStatus.FAILED_TO_CACHE_SYNC_INFO can occur if the resource sync is loaded
		// when no scheduling rule is held.
		return (e.getStatus().getCode() == IResourceStatus.WORKSPACE_LOCKED 
				|| e.getStatus().getCode() == CVSStatus.FAILED_TO_CACHE_SYNC_INFO);
	}
	
	private boolean isResourceNotFound(CVSException e) {
		return e.getStatus().getCode() == IResourceStatus.RESOURCE_NOT_FOUND;
	}
	
	/**
	 * Begins a batch of operations in order to optimize sync file writing. 
	 * The provided scheduling rule indicates the resources
	 * that the resources affected by the operation while the returned scheduling rule
	 * is the rule obtained by the lock. It may differ from the provided rule as it must
	 * encompass any sync files that may change as a result of the operation.
	 */
	public ISchedulingRule beginBatching(ISchedulingRule resourceRule, IProgressMonitor monitor) {
		return resourceLock.acquire(resourceRule, this /* IFlushOperation */, monitor);
	}
	
	/**
	 * Ends a batch of operations. The provided rule must be the one that was returned
	 * by the corresponding call to beginBatching.
	 * <p>
	 * Progress cancellation is ignored while writting the cache to disk. This
	 * is to ensure cache to disk consistency.
	 * </p>
	 * 
	 * @param monitor the progress monitor, may be null
	 * @exception CVSException with a status with code <code>COMMITTING_SYNC_INFO_FAILED</code>
	 * if all the CVS sync information could not be written to disk.
	 */
	public void endBatching(ISchedulingRule rule, IProgressMonitor monitor) throws CVSException {
		resourceLock.release(rule, monitor);
	}
	
	/* (non-Javadoc)
	 * 
	 * Callback which is invoked when the batching resource lock is released 
	 * or when a flush is requested (see beginBatching(IResource)).
	 * 
	 * @see org.eclipse.team.internal.ccvs.core.syncinfo.ReentrantLock.IRunnableOnExit#run(org.eclipse.team.internal.ccvs.core.syncinfo.ReentrantLock.ThreadInfo, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void flush(final ThreadInfo info, IProgressMonitor monitor) throws CVSException {
		if (info != null && !info.isEmpty()) {
			try {
				beginOperation();
				ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
					public void run(IProgressMonitor pm) throws CoreException {
						IStatus status = commitCache(info, pm);
						if (!status.isOK()) {
							throw new CVSException(status);
						}
					}
				}, null, 0 /* no flags */, monitor);
			} catch (CoreException e) {
				throw CVSException.wrapException(e);
			} finally {
				endOperation();
			}
		}
	}
	
	/*
	 * Begin an access to the internal data structures of the synchronizer
	 */
	private void beginOperation() {
		lock.acquire();
	}
	
	/*
	 * End an access to the internal data structures of the synchronizer
	 */
	private void endOperation() {						
		lock.release();
	}
	
	/**
	 * Flush the sync information from the in-memory cache to disk and purge
	 * the entries from the cache.
	 * <p>
	 * Recursively flushes the sync information for all resources 
	 * below the root to disk and purges the entries from memory
	 * so that the next time it is accessed it will be retrieved from disk.
	 * May flush more sync information than strictly needed, but never less.
	 * </p>
	 * 
	 * @param root the root of the subtree to purge
	 * @param deep purge sync from child folders
	 * @param monitor the progress monitor, may be null
	 */
	public void flush(IContainer root, boolean deep, IProgressMonitor monitor) throws CVSException {
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 10);
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(root, Policy.subMonitorFor(monitor, 1));
			try {
				beginOperation();
				try {
					// Flush changes to disk
					resourceLock.flush(Policy.subMonitorFor(monitor, 8));
				} finally {
					// Purge the in-memory cache
					sessionPropertyCache.purgeCache(root, deep);
				}
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, Policy.subMonitorFor(monitor, 1));
			monitor.done();
		}
	}

	public void deconfigure(final IProject project, IProgressMonitor monitor) throws CVSException {
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 100);
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(project, Policy.subMonitorFor(monitor, 10));
			// Flush 
			flush(project, true /* deep */, Policy.subMonitorFor(monitor, 80));
				
			// forget about pruned folders however the top level pruned folder will have resource sync (e.g. 
			// a line in the Entry file). As a result the folder is managed but is not a CVS folder.
			synchronizerCache.purgeCache(project, true);
		} finally {
			if (rule != null) endBatching(rule, Policy.subMonitorFor(monitor, 10));
			monitor.done();
		}
	}
	
	/**
	 * Called to notify the synchronizer that meta files have changed on disk, outside 
	 * of the workbench. The cache will be flushed for this folder and it's immediate
	 * children and appropriate state change events are broadcasts to state change
	 * listeners.
	 */
	public void ignoreFilesChanged(IContainer[] roots) throws CVSException {
		for (int i = 0; i < roots.length; i++) {
			IContainer container = roots[i];
			ISchedulingRule rule = null;
			try {
				Set changed = new HashSet();
				rule = beginBatching(container, null);
				try {
					beginOperation();
					changed.addAll(Arrays.asList(
						sessionPropertyCache.purgeCache(container, false /*don't flush children*/)));
				} finally {
					endOperation();
				}
				if (!changed.isEmpty()) {
					ResourceStateChangeListeners.getListener().resourceSyncInfoChanged(
						(IResource[]) changed.toArray(new IResource[changed.size()]));
				}
			} finally {
				if (rule != null) endBatching(rule, null);
			}
		}
	}
	
	public void syncFilesChangedExternally(IContainer[] changedMetaFiles, IFile[] externalDeletions) throws CVSException {
		List changed = new ArrayList();
		for (int i = 0; i < changedMetaFiles.length; i++) {
			IContainer container = changedMetaFiles[i];
			if (!isWithinActiveOperationScope(container)) {
				changed.addAll(Arrays.asList(
					sessionPropertyCache.purgeCache(container, false /*don't flush children*/)));
			}
		}
		for (int i = 0; i < externalDeletions.length; i++) {
			IFile file = externalDeletions[i];
			if (!isWithinActiveOperationScope(file)) {
				sessionPropertyCache.purgeCache(file.getParent(), false /*don't flush children*/);
				changed.add(file);
			}
		}
		if (!changed.isEmpty()) {
			ResourceStateChangeListeners.getListener().externalSyncInfoChange(
					(IResource[]) changed.toArray(new IResource[changed.size()]));
		}
	}
	
	/*
	 * The resource is about to be deleted by the move delete hook.
	 * In all cases (except when the resource doesn't exist), this method 
	 * will indicate that the dirty state of the parent needs to be recomputed.
	 * For managed resources, it will move the cached sync info from the session
	 * property cache into the sycnrhonizer cache, purging the session cache.
	 * @param resource the resource about to be deleted.
	 * <p>
	 * Note taht this method is not recursive. Hence, for managed resources
	 * 
	 * @returns whether children need to be prepared
	 * @throws CVSException
	 */
	/* private */ boolean prepareForDeletion(IResource resource) throws CVSException {
		if (!resource.exists()) return false;
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(resource, null);
			try {
				beginOperation();
				// Flush the dirty info for the resource and it's ancestors.
				// Although we could be smarter, we need to do this because the
				// deletion may fail.
				adjustDirtyStateRecursively(resource, RECOMPUTE_INDICATOR);
				if (resource.getType() == IResource.FILE) {
					byte[] syncBytes = getSyncBytes(resource);
					if (syncBytes != null) {
						if (ResourceSyncInfo.isAddition(syncBytes)) {
							deleteResourceSync(resource);
						} else {
							syncBytes = convertToDeletion(syncBytes);
							synchronizerCache.setCachedSyncBytes(resource, syncBytes, true);
						}
						sessionPropertyCache.purgeResourceSyncCache(resource);
						resourceChanged(resource);
					}
					return false;
				} else {
					IContainer container = (IContainer)resource;
					if (container.getType() == IResource.PROJECT) {
						synchronizerCache.flush((IProject)container);
						return false;
					} else {
						// Move the folder sync info into phantom space
						FolderSyncInfo info = getFolderSync(container);
						if (info == null) return false;
						synchronizerCache.setCachedFolderSync(container, info, true);
						folderChanged(container);
						// move the resource sync as well
						byte[] syncBytes = getSyncBytes(resource);
						synchronizerCache.setCachedSyncBytes(resource, syncBytes, true);
						sessionPropertyCache.purgeResourceSyncCache(container);
						sessionPropertyCache.purgeCache(container, false);
						return true;
					}
				}
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, null);
		}
	}
	
	/**
	 * The resource has been deleted. Make sure any cached state is cleared.
	 * This is needed because the move/delete hook is not invoked in all situations
	 * (e.g. external deletion).
	 * 
	 * @param resource
	 * @throws CVSException
	 */
	protected void handleDeleted(IResource resource) throws CVSException {
		if (resource.exists()) return;
		try {
			beginOperation();
			adjustDirtyStateRecursively(resource, RECOMPUTE_INDICATOR);
		} finally {
			endOperation();
		}
	}
	
	/**
	 * Prepare for the deletion of the target resource from within 
	 * the move/delete hook. The method is invoked by both the 
	 * deleteFile/Folder methods and for the source resource
	 * of moveFile/Folder. This method will move the cached sync info
	 * into the phantom (ISynchronizer) cache so that outgoing deletions
	 * and known remote folders are preserved.
	 * 
	 * @param resource
	 * @param monitor
	 * @throws CVSException
	 */
	public void prepareForDeletion(IResource resource, IProgressMonitor monitor) throws CVSException {
		// Move sync info to phantom space for the resource and all it's children
		monitor = Policy.monitorFor(monitor);
		try {
			beginOperation();
			monitor.beginTask(null, 100);
			try {
				resource.accept(new IResourceVisitor() {
					public boolean visit(IResource innerResource) throws CoreException {
						try {
							return prepareForDeletion(innerResource);
						} catch (CVSException e) {
							CVSProviderPlugin.log(e);
							throw new CoreException(e.getStatus());
						}
					}
				});
			} catch (CoreException e) {
				throw CVSException.wrapException(e);
			}
		} finally {
			endOperation();
			monitor.done();
		}
	}
	
	/**
	 * If not already cached, loads and caches the resource sync for the children of the container.
	 * Folder must exist and must not be the workspace root.
	 *
	 * @param container the container
	 */
	private void cacheResourceSyncForChildren(IContainer container, boolean canModifyWorkspace) throws CVSException {
		// don't try to load if the information is already cached
		if (! getSyncInfoCacheFor(container).isResourceSyncInfoCached(container)) {
			// load the sync info from disk
			byte[][] infos;
			// do not load the sync info for resources that are linked
			if (isLinkedResource(container)) {
				infos = null;
			} else {
				infos = SyncFileWriter.readAllResourceSync(container);
			}
			if (infos != null) {
				for (int i = 0; i < infos.length; i++) {
					byte[] syncBytes = infos[i];
					IPath name = new Path(getName(syncBytes));
					IResource resource;
					if (isFolder(syncBytes)) {
						resource = container.getFolder(name);
					} else {
						resource = container.getFile(name);
					}
					getSyncInfoCacheFor(resource).setCachedSyncBytes(resource, syncBytes, canModifyWorkspace);
				}
			}
			getSyncInfoCacheFor(container).setResourceSyncInfoCached(container);
		}
	}
	
	/**
	 * If not already cached, loads and caches the folder sync for the
	 * container. Folder must exist and must not be the workspace root.
	 *
	 * @param container the container
	 */
	private void cacheFolderSync(IContainer container) throws CVSException {
		// don't try to load if the information is already cached
		if (! getSyncInfoCacheFor(container).isFolderSyncInfoCached(container)) {
			// load the sync info from disk
			FolderSyncInfo info;
			// do not load the sync info for resources that are linked
			if (isLinkedResource(container)) {
				info = null;
			} else {
				info = SyncFileWriter.readFolderSync(container);
			}
			getSyncInfoCacheFor(container).setCachedFolderSync(container, info, false);
		}
	}
	
	private boolean isLinkedResource(IResource resource) {
		return CVSWorkspaceRoot.isLinkedResource(resource);
	}

	/**
	 * Load the sync info for the given resource from disk
	 * @param resource
	 * @return byte[]
	 */
	private byte[] getSyncBytesFromDisk(IResource resource) throws CVSException {
		byte[][] infos = SyncFileWriter.readAllResourceSync(resource.getParent());
		if (infos == null) return null;
		for (int i = 0; i < infos.length; i++) {
			byte[] syncBytes = infos[i];
			if (resource.getName().equals(getName(syncBytes))) {
				return syncBytes;
			}
		}
		return null;
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
	/* internal use only */ IStatus commitCache(ThreadInfo threadInfo, IProgressMonitor monitor) {
		if (threadInfo.isEmpty()) {
			return SyncInfoCache.STATUS_OK;
		}
		List errors = new ArrayList();
		try {
			/*** prepare operation ***/
			// find parents of changed resources
			IResource[] changedResources = threadInfo.getChangedResources();
			IContainer[] changedFolders = threadInfo.getChangedFolders();
			Set dirtyParents = new HashSet();
			for (int i = 0; i < changedResources.length; i++) {
				IResource resource = changedResources[i];
				IContainer folder = resource.getParent();
				dirtyParents.add(folder);
			}
			
			monitor = Policy.monitorFor(monitor);
			int numDirty = dirtyParents.size();
			int numResources = changedFolders.length + numDirty;
			monitor.beginTask(null, numResources);
			if(monitor.isCanceled()) {
				monitor.subTask(Policy.bind("EclipseSynchronizer.UpdatingSyncEndOperationCancelled")); //$NON-NLS-1$
			} else {
				monitor.subTask(Policy.bind("EclipseSynchronizer.UpdatingSyncEndOperation")); //$NON-NLS-1$
			}
			
			/*** write sync info to disk ***/
			// folder sync info changes
			for (int i = 0; i < changedFolders.length; i++) {
				IContainer folder = changedFolders[i];
				if (folder.exists() && folder.getType() != IResource.ROOT) {
					try {
						FolderSyncInfo info = sessionPropertyCache.getCachedFolderSync(folder);
						// Do not write the folder sync for linked resources
						if (info == null) {
							// deleted folder sync info since we loaded it
							// (but don't overwrite the sync info for linked folders
							if (!isLinkedResource(folder))
								SyncFileWriter.deleteFolderSync(folder);
							dirtyParents.remove(folder);
						} else {
							// modified or created new folder sync info since we loaded it
							SyncFileWriter.writeFolderSync(folder, info);
						}
					} catch(CVSException e) {					
						try {
							sessionPropertyCache.purgeCache(folder, true /* deep */);
						} catch(CVSException pe) {
							errors.add(pe.getStatus());
						}
						errors.add(e.getStatus());
					}
				}
				monitor.worked(1);
			}

			// update progress for parents we will skip because they were deleted
			monitor.worked(numDirty - dirtyParents.size());

			// resource sync info changes
			for (Iterator it = dirtyParents.iterator(); it.hasNext();) {
				IContainer folder = (IContainer) it.next();
				if (folder.exists() && folder.getType() != IResource.ROOT) {
					// write sync info for all children in one go
					try {
						List infos = new ArrayList();
						IResource[] children = folder.members(true);
						for (int i = 0; i < children.length; i++) {
							IResource resource = children[i];
							byte[] syncBytes = getSyncBytes(resource);
							if (syncBytes != null) {
								infos.add(syncBytes);
							}
						}
						// do not overwrite the sync info for linked resources
						if (infos.size() > 0 || !isLinkedResource(folder))
							SyncFileWriter.writeAllResourceSync(folder,
								(byte[][]) infos.toArray(new byte[infos.size()][]));
					} catch(CVSException e) {
						try {
							sessionPropertyCache.purgeCache(folder, false /* depth 1 */);
						} catch(CVSException pe) {
							errors.add(pe.getStatus());
						}							
						errors.add(e.getStatus());
					} catch (CoreException e) {
						try {
							sessionPropertyCache.purgeCache(folder, false /* depth 1 */);
						} catch(CVSException pe) {
							errors.add(pe.getStatus());
						}							
						errors.add(e.getStatus());
					}
				}
				monitor.worked(1);
			}
			
			/*** broadcast events ***/
			monitor.subTask(Policy.bind("EclipseSynchronizer.NotifyingListeners")); //$NON-NLS-1$
			Set allChanges = new HashSet();
			allChanges.addAll(Arrays.asList(changedResources));
			allChanges.addAll(Arrays.asList(changedFolders));
			allChanges.addAll(dirtyParents);	
			IResource[] resources = (IResource[]) allChanges.toArray(
				new IResource[allChanges.size()]);
			broadcastResourceStateChanges(resources);
			if ( ! errors.isEmpty()) {
				MultiStatus status = new MultiStatus(CVSProviderPlugin.ID, 
											CVSStatus.COMMITTING_SYNC_INFO_FAILED, 
											Policy.bind("EclipseSynchronizer.ErrorCommitting"), //$NON-NLS-1$
											null);
				for (int i = 0; i < errors.size(); i++) {
					status.merge((IStatus)errors.get(i));
				}
				return status;
			}
			return SyncInfoCache.STATUS_OK;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Broadcasts the resource state changes for the given resources to CVS Provider Plugin
	 */
	void broadcastResourceStateChanges(IResource[] resources) {
		if (resources.length > 0) {
			ResourceStateChangeListeners.getListener().resourceSyncInfoChanged(resources);
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
	private byte[] getCachedSyncBytes(IResource resource) throws CVSException {
		return getSyncInfoCacheFor(resource).getCachedSyncBytes(resource);
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
	private void setCachedSyncBytes(IResource resource, byte[] syncBytes) throws CVSException {
		getSyncInfoCacheFor(resource).setCachedSyncBytes(resource, syncBytes, true);
		resourceChanged(resource);
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
		//todo
		byte[] syncBytes = null;
		if (info != null) syncBytes = info.getBytes();
		getSyncInfoCacheFor(resource).setCachedSyncBytes(resource, syncBytes, true);
	}
	
	/**
	 * If not already cached, loads and caches the folder ignores sync for the container.
	 * Folder must exist and must not be the workspace root.
	 * 
	 * @param container the container
	 * @return the folder ignore patterns, or an empty array if none
	 */
	private FileNameMatcher cacheFolderIgnores(IContainer container) throws CVSException {
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
	
	/*
	 * Recursively adds to the possibleIgnores list all children of the given 
	 * folder that can be ignored. This method may only be invoked when a 
	 * schedling rule for the given foldr is held and when the CVs sync lock is
	 * held.
	 * 
	 * @param folder the folder to be searched
	 * @param possibleIgnores the list of IResources that can be ignored
	 */
	private void accumulateNonManagedChildren(IContainer folder, List possibleIgnores) throws CVSException {
		try {
			cacheResourceSyncForChildren(folder, true /* can modify workspace */);
			IResource[] children = folder.members();
			List folders = new ArrayList();
			// deal with all files first and then folders to be otimized for caching scheme
			for (int i = 0; i < children.length; i++) {
				IResource child = children[i];
				if(getCachedSyncBytes(child)==null) {
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
	 * A value of null for info indicates that any entry for the given
	 * resource is to be removed from the Notify file.
	 * 
	 * @param resource
	 * @param info
	 */
	public void setNotifyInfo(IResource resource, NotifyInfo info) throws CVSException {
		NotifyInfo[] infos = SyncFileWriter.readAllNotifyInfo(resource.getParent());
		if (infos == null) {
			// if the file is empty and we are removing an entry, just return;
			if (info == null) return;
			infos = new NotifyInfo[] { info };
		} else {
			Map infoMap = new HashMap();
			for (int i = 0; i < infos.length; i++) {
				NotifyInfo notifyInfo = infos[i];
				infoMap.put(notifyInfo.getName(), notifyInfo);
			}
			if (info == null) {
				// if the info is null, remove the entry
				infoMap.remove(resource.getName());
			} else {
				// add the new entry to the list
				infoMap.put(info.getName(), info);
			}
			
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
			infoMap.put(notifyInfo.getName(), notifyInfo);
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
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 100);
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(file, Policy.subMonitorFor(monitor, 10));
			ResourceSyncInfo info = getResourceSync(file);
			// The file must exist remotely and locally
			if (info == null || info.isAdded() || info.isDeleted())
				return;
			SyncFileWriter.writeFileToBaseDirectory(file, Policy.subMonitorFor(monitor, 80));
			resourceChanged(file);
		} finally {
			if (rule != null) endBatching(rule, Policy.subMonitorFor(monitor, 10));
			monitor.done();
		}
	}
	
	public void restoreFileFromBaseDirectory(final IFile file, IProgressMonitor monitor) throws CVSException {
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 100);
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(file, Policy.subMonitorFor(monitor, 10));
			ResourceSyncInfo info = getResourceSync(file);
			// The file must exist remotely
			if (info == null || info.isAdded())
				return;
			SyncFileWriter.restoreFileFromBaseDirectory(file, Policy.subMonitorFor(monitor, 80));
			resourceChanged(file);
		} finally {
			if (rule != null) endBatching(rule, Policy.subMonitorFor(monitor, 10));
			monitor.done();
		}
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
			if (!getSyncInfoCacheFor(parent).isSyncInfoLoaded(parent)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Method ensureSyncInfoLoaded loads all the relevent sync info into the cache.
	 * This method can only be invoked when the workspace is open for modification.
	 * in other words it cannot be invoked from inside a POST_CHANGE delta listener.
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
			ISchedulingRule rule = null;
			try {
				rule = beginBatching(parent, null);
				try {
					beginOperation();
					cacheResourceSyncForChildren(parent, true /* can modify workspace */);
					cacheFolderSync(parent);
					cacheFolderIgnores(parent);
				} finally {
					endOperation();
				}
			} finally {
				if (rule != null) endBatching(rule, null);
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
						public boolean visit(IResource innerResource) throws CoreException {
							if (innerResource.getType() == IResource.FOLDER)
								folders.add(innerResource);
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
	
	/**
	 * Perform sync info batching within the context of the given resource
	 * scheduling rule while running the given ICVSRunnable.
	 * @param runnable
	 * @param monitor
	 * @throws CVSException
	 */
	public void run(ISchedulingRule resourceRule, ICVSRunnable runnable, IProgressMonitor monitor) throws CVSException {
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(null, 100);
		ISchedulingRule rule = beginBatching(resourceRule, Policy.subMonitorFor(monitor, 10));
		try {
			runnable.run(Policy.subMonitorFor(monitor, 80));
		} finally {
			if (rule != null) endBatching(rule, Policy.subMonitorFor(monitor, 10));
			monitor.done();
		}
	}
	
	/**
	 * This method is invoked from the IMoveDeleteHook to batch the resulting sync file
	 * changes.
	 */
	public void run(ICVSRunnable runnable, IProgressMonitor monitor) throws CVSException {
		// Use the root resource as the rule.
		// Note: This will not lock the workspace due to behavior in ReentrantLock
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		run(root, runnable, monitor);
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
	
	private void adjustDirtyStateRecursively(IResource resource, String indicator) throws CVSException {
		if (resource.getType() == IResource.ROOT) return;
		try {
			beginOperation();
			
			if (indicator == getDirtyIndicator(resource)) {
				return;
			} 					
			
			if (Policy.DEBUG_DIRTY_CACHING) {
				debug(resource, indicator, "adjusting dirty state"); //$NON-NLS-1$
			}

			getSyncInfoCacheFor(resource).setDirtyIndicator(resource, indicator);										

			IContainer parent = resource.getParent();
			if(indicator == NOT_DIRTY_INDICATOR) {
				adjustDirtyStateRecursively(parent, RECOMPUTE_INDICATOR);
			}
			
			if(indicator == RECOMPUTE_INDICATOR) {
				adjustDirtyStateRecursively(parent, RECOMPUTE_INDICATOR);
			} 
			
			if(indicator == IS_DIRTY_INDICATOR) {
				adjustDirtyStateRecursively(parent, indicator);
			} 
		} finally {
			endOperation();
		}
	}

	protected String getDirtyIndicator(IResource resource) throws CVSException {
		try {
			beginOperation();
			return getSyncInfoCacheFor(resource).getDirtyIndicator(resource);
		} finally {
			endOperation();
		}
	}
	
	/*
	 * Mark the given resource as either modified or clean using a persistant
	 * property. Do nothing if the modified state is already what we want.
	 * Return true if the modification state was changed.
	 */
	protected void setDirtyIndicator(IResource resource, boolean modified) throws CVSException {
		String indicator = modified ? IS_DIRTY_INDICATOR : NOT_DIRTY_INDICATOR;
		// set the dirty indicator and adjust the parent accordingly			
		adjustDirtyStateRecursively(resource, indicator);
	}

	/**
	 * Method getName.
	 * @param syncBytes
	 */
	private String getName(byte[] syncBytes) throws CVSException {
		return ResourceSyncInfo.getName(syncBytes);
	}
	
	/**
	 * Method isFolder.
	 * @param syncBytes
	 * @return boolean
	 */
	private boolean isFolder(byte[] syncBytes) {
		return ResourceSyncInfo.isFolder(syncBytes);
	}
		
	/**
	 * Method convertToDeletion.
	 * @param syncBytes
	 * @return byte[]
	 */
	private byte[] convertToDeletion(byte[] syncBytes) throws CVSException {
		return ResourceSyncInfo.convertToDeletion(syncBytes);
	}
	
	/**
	 * Method createdByMove clears any session properties on the file so it
	 * appears as an ADDED file.
	 * 
	 * @param destination
	 */
	public void createdByMove(IFile file) throws CVSException {
		deleteResourceSync(file);
	}

	static public void debug(IResource resource, String indicator, String string) {
		String di = EclipseSynchronizer.IS_DIRTY_INDICATOR;
		if(indicator == EclipseSynchronizer.IS_DIRTY_INDICATOR) {
			di = "dirty";	//$NON-NLS-1$
		} else if(indicator == EclipseSynchronizer.NOT_DIRTY_INDICATOR) {
			di = "clean";	//$NON-NLS-1$
		} else {
			di = "needs recomputing";	//$NON-NLS-1$
		} 
		System.out.println("["+string + ":" + di + "]  "  + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	static public void debug(IResource resource, boolean modified, String string) {
		debug(resource, modified ? IS_DIRTY_INDICATOR : NOT_DIRTY_INDICATOR, string);
	}
	
	/**
	 * @param file
	 * @return int
	 */
	public int getModificationState(IResource resource) throws CVSException {
		String indicator =  getDirtyIndicator(resource);
		if (Policy.DEBUG_DIRTY_CACHING) {
			debug(resource, indicator, "getModificationState"); //$NON-NLS-1$
		}
		if (indicator == null || indicator == RECOMPUTE_INDICATOR) {
			return ICVSFile.UNKNOWN;
		} else if (indicator == IS_DIRTY_INDICATOR) {
			return ICVSFile.DIRTY;
		} else if (indicator == NOT_DIRTY_INDICATOR) {
			return ICVSFile.CLEAN;
		} else {
			return ICVSFile.UNKNOWN;
		}
	}

	/**
	 * Return whether the resource is within the scope of a currently active
	 * CVS operation.
	 * @param resource
	 * @return
	 */
	public boolean isWithinActiveOperationScope(IResource resource) {
		return resourceLock.isWithinActiveOperationScope(resource);
	}
	
	public void setTimeStamp(IFile file, long time) throws CVSException {
		ISchedulingRule rule = null;
		try {
			rule = beginBatching(file, null);
			try {
				beginOperation();
				try {
					file.setLocalTimeStamp(time);
				} catch (CoreException e) {
					throw CVSException.wrapException(e);
				}
				resourceChanged(file);		
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, null);
		}
	}

	/**
	 * React to a resource that was just moved by the move/delete hook.
	 * @param resource the resource that was moved (at its new location)
	 */
	public void postMove(IResource resource) throws CVSException {
		try {
			beginOperation();
			if (resource.getType() == IResource.FILE) {
				// Purge any copied sync info so true sync info will 
				// be obtained from the synchronizer cache
				sessionPropertyCache.purgeResourceSyncCache(resource);
			} else {
				IContainer container = (IContainer)resource;
				// Purge any copied sync info
				sessionPropertyCache.purgeCache(container, true /* deep */);
				// Dirty all resources so old sync info will be rewritten to disk
				try {
					container.accept(new IResourceVisitor() {
						public boolean visit(IResource resource) throws CoreException {
							if (getSyncBytes(resource) != null) {
								resourceChanged(resource);
							}
							if (resource.getType() != IResource.FILE) {
								if (getFolderSync((IContainer)resource) != null) {
									folderChanged((IContainer)resource);
									return true;
								}
							}
							return false;
						}
					});
				} catch (CoreException e) {
					throw CVSException.wrapException(e);
				}
				// Flush the sync info to disk
				flush(container, true /* deep */, null);
			}
		} finally {
			endOperation();
		}
	}
	
	/**
	 * This method is to be invoked only from the move/delete hook. It's purpose
	 * is to obtain the sync look in order to prevent other threads from accessing
	 * sync info while the move/delete is taking place.
	 * @param runnable
	 * @param monitor
	 * @throws CVSException
	 */
	public void performMoveDelete(ICVSRunnable runnable, IProgressMonitor monitor) throws CVSException {
		ISchedulingRule rule = null;
		try {
			monitor.beginTask(null, 100);
			rule = beginBatching(null, null);
			try {
				beginOperation();
				runnable.run(Policy.subMonitorFor(monitor, 95));
			} finally {
				endOperation();
			}
		} finally {
			if (rule != null) endBatching(rule, Policy.subMonitorFor(monitor, 5));
			monitor.done();
		}
	}
}
