/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.core.resources;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;

/**
 * The low level cache provides the sync info as bytes
 */
/*package*/ abstract class LowLevelSyncInfoCache {

	// the resources plugin synchronizer is used to cache and possibly persist. These
	// are keys for storing the sync info.
	/*package*/ static final QualifiedName FOLDER_SYNC_KEY = new QualifiedName(CVSProviderPlugin.ID, "folder-sync"); //$NON-NLS-1$
	/*package*/ static final QualifiedName RESOURCE_SYNC_KEY = new QualifiedName(CVSProviderPlugin.ID, "resource-sync"); //$NON-NLS-1$
	/*package*/ static final QualifiedName IGNORE_SYNC_KEY = new QualifiedName(CVSProviderPlugin.ID, "folder-ignore"); //$NON-NLS-1$

	/*package*/ static final QualifiedName IS_DIRTY = new QualifiedName(CVSProviderPlugin.ID, "is-dirty");
	/*package*/ static final QualifiedName CLEAN_UPDATE = new QualifiedName(CVSProviderPlugin.ID, "clean-update");
	/*package*/ static final QualifiedName DIRTY_COUNT = new QualifiedName(CVSProviderPlugin.ID, "dirty-count");
	/*package*/ static final QualifiedName DELETED_CHILDREN = new QualifiedName(CVSProviderPlugin.ID, "deleted");
	/*package*/ static final String IS_DIRTY_INDICATOR = "d";
	/*package*/ static final String NOT_DIRTY_INDICATOR = "c";
	/*package*/ static final String UPDATED_INDICATOR = "u";
	
	/*package*/ static final IStatus STATUS_OK = new Status(IStatus.OK, CVSProviderPlugin.ID, 0, Policy.bind("ok"), null); //$NON-NLS-1$
	
	/**
	 * If not already cached, loads and caches the folder sync for the container.
	 * Folder must exist and must not be the workspace root.
	 *
	 * @param container the container
	 * @return the folder sync info for the folder, or null if none.
	 */
	/*package*/ abstract FolderSyncInfo cacheFolderSync(IContainer container) throws CVSException;

	/**
	 * Returns the folder sync info for the container; null if none.
	 * Folder must exist and must not be the workspace root.
	 * The folder sync info for the container MUST ALREADY BE CACHED.
	 *
	 * @param container the container
	 * @return the folder sync info for the folder, or null if none.
	 * @see #cacheFolderSync
	 */
	/*package*/ abstract FolderSyncInfo getCachedFolderSync(IContainer container) throws CVSException;

	/**
	 * Sets the folder sync info for the container; if null, deletes it.
	 * Folder must exist and must not be the workspace root.
	 * The folder sync info for the container need not have previously been
	 * cached.
	 *
	 * @param container the container
	 * @param info the new folder sync info
	 */
	/*package*/ abstract void setCachedFolderSync(IContainer container, FolderSyncInfo info) throws CVSException;

	/**
	 * If not already cached, loads and caches the resource sync for the children of the container.
	 * Folder must exist and must not be the workspace root.
	 *
	 * @param container the container
	 */
	/*package*/ abstract void cacheResourceSyncForChildren(IContainer container) throws CVSException;

	/**
	 * Returns the resource sync info for all children of the container.
	 * Container must exist and must not be the workspace root.
	 * The resource sync info for the children of the container MUST ALREADY BE CACHED.
	 *
	 * @param container the container
	 * @return a collection of the resource sync info's for all children
	 * @see #cacheResourceSyncForChildren
	 */
	/*package*/ abstract byte[][] getCachedResourceSyncForChildren(IContainer container) throws CVSException;

	/**
	 * Sets the resource sync info for the resource; if null, deletes it. Parent
	 * must exist and must not be the workspace root. The resource sync info for
	 * the children of the parent container MUST ALREADY BE CACHED.
	 *
	 * @param resource the resource
	 * @param info the new resource sync info
	 * @see #cacheResourceSyncForChildren
	 */
	/*package*/ abstract void setCachedResourceSyncForChildren(IContainer container, byte[][] infos) throws CVSException;
	
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
	/*package*/ abstract IStatus commitCache(IProgressMonitor monitor);
	
	/*package*/ abstract String getDirtyIndicator(IResource resource) throws CVSException;
	
	/*package*/ abstract void setDirtyIndicator(IResource resource, String indicator) throws CVSException;
	
	/**
	 * Return the dirty count for the given folder. For existing folders, the
	 * dirty count may not have been calculated yet and this method will return
	 * -1 in that case.
	 */
	/*package*/ abstract int getCachedDirtyCount(IContainer container) throws CVSException;
	
	/**
	 * Set the dirty count for the given container to the given count.
	 *
	 * @param container
	 * @param count
	 * @throws CVSException
	 */
	/*package*/ abstract void setCachedDirtyCount(IContainer container, int count) throws CVSException;
	
	/*package*/ abstract void flushDirtyCache(IResource resource) throws CVSException;
	
	/*package*/ abstract boolean addDeletedChild(IContainer container, IFile file) throws CVSException;

	/*package*/ abstract boolean removeDeletedChild(IContainer container, IFile file) throws CVSException;
	
	/*package*/ abstract boolean isSyncInfoLoaded(IContainer parent) throws CVSException;
}
