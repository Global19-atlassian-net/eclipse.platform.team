/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.core.resources;

import java.io.File;
import java.io.InputStream;
import java.util.Date;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.ILogEntry;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.syncinfo.NotifyInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;

/**
 * Represents handles to CVS resource on the local file system. Synchronization
 * information is taken from the CVS subdirectories. 
 */
public class EclipseFile extends EclipseResource implements ICVSFile {

	private static final String TEMP_FILE_EXTENSION = ".tmp";//$NON-NLS-1$
	private static final IPath PROJECT_META_DATA_PATH = new Path(".project");//$NON-NLS-1$
	
	/**
	 * Create a handle based on the given local resource.
	 */
	protected EclipseFile(IFile file) {
		super(file);
	}

	/*
	 * @see ICVSResource#delete()
	 */
	public void delete() throws CVSException {
		try {
			((IFile)resource).delete(false /*force*/, true /*keepHistory*/, null);
		} catch(CoreException e) {
			throw CVSException.wrapException(resource, Policy.bind("EclipseFile_Problem_deleting_resource", resource.getFullPath().toString(), e.getStatus().getMessage()), e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	public long getSize() {
		return getIOFile().length();	
	}

	public InputStream getContents() throws CVSException {
 		try {
			return getIFile().getContents();
		} catch (CoreException e) {
 			throw CVSException.wrapException(resource, Policy.bind("EclipseFile_Problem_accessing_resource", resource.getFullPath().toString(), e.getStatus().getMessage()), e); //$NON-NLS-1$ //$NON-NLS-2$
 		}
 	}
	
	/*
	 * @see ICVSFile#getTimeStamp()
	 */
	public Date getTimeStamp() {						
		return new Date((getIOFile().lastModified()/1000)*1000);
	}
 
	/*
	 * @see ICVSFile#setTimeStamp(String)
	 */
	public void setTimeStamp(Date date) throws CVSException {
		long time;
		if (date == null) {
			time = System.currentTimeMillis();
		} else {
			time = date.getTime();
		}
		getIOFile().setLastModified(time);
		try {
			// Needed for workaround to Platform Core Bug #
			resource.refreshLocal(IResource.DEPTH_ZERO, null);
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}		
	}

	/*
	 * @see ICVSResource#isFolder()
	 */
	public boolean isFolder() {
		return false;
	}
	
	/*
	 * @see ICVSFile#isModified()
	 */
	public boolean isModified() throws CVSException {
		ResourceSyncInfo info = getSyncInfo();
		if (!exists() || !isManaged(info)) {
			return true;
		} else {
			// consider a merged file as always modified.
			if(info.isMerged()) return true;
			return !getTimeStamp().equals(info.getTimeStamp());
		}
	}
	
	/*
	 * @see ICVSResource#accept(ICVSResourceVisitor)
	 */
	public void accept(ICVSResourceVisitor visitor) throws CVSException {
		visitor.visitFile(this);
	}

	/*
	 * This is to be used by the Copy handler. The filename of the form .#filename
	 */
	public void copyTo(String filename) throws CVSException {
		try {
			getIFile().copy(new Path(filename), true /*force*/, null);
		} catch(CoreException e) {
			throw new CVSException(e.getStatus());
		}
	}

	/*
	 * @see ICVSResource#getRemoteLocation()
	 */
	public String getRemoteLocation(ICVSFolder stopSearching) throws CVSException {
		return getParent().getRemoteLocation(stopSearching) + SEPARATOR + getName();
	}
		
	/*
	 * @see ICVSFile#setReadOnly()
	 */
	public void setContents(InputStream stream, int responseType, boolean keepLocalHistory, IProgressMonitor monitor) throws CVSException {
		try {
			IFile file = getIFile();
			if (PROJECT_META_DATA_PATH.equals(file.getFullPath().removeFirstSegments(1))) {
				responseType = UPDATED;
			}
			switch (responseType) {
				case UPDATED:
					if (resource.exists()) {
						file.setContents(stream, true /*force*/, true /*keep history*/, monitor);
						break;
					}
				case CREATED: // creating a new file so it should not exist locally
					file.create(stream, false /*force*/, monitor);
					break;
				case MERGED: // merging contents into a file that exists locally
					// Ensure we don't leave the file in a partially written state
					IFile tempFile = file.getParent().getFile(new Path(file.getName() + TEMP_FILE_EXTENSION));
					tempFile.create(stream, true /*force*/, monitor);
					file.delete(false, true, monitor);
					tempFile.move(new Path(file.getName()), true /*force*/, false /*history*/, monitor);
					break;
				case UPDATE_EXISTING: // creating a new file so it should exist locally
					file.setContents(stream, true /*force*/, true /*keep history*/, monitor);
					break;
			}
		} catch(CoreException e) {
			throw CVSException.wrapException(resource, Policy.bind("EclipseFile_Problem_writing_resource", e.getMessage(), e.getStatus().getMessage()), e); //$NON-NLS-1$
		}
	}
			
	/*
	 * @see ICVSFile#setReadOnly()
	 */
	public void setReadOnly(boolean readOnly) throws CVSException {
		getIFile().setReadOnly(readOnly);
	}

	/*
	 * @see ICVSFile#isReadOnly()
	 */
	public boolean isReadOnly() throws CVSException {
		return getIFile().isReadOnly();
	}
	
	/*
	 * Typecasting helper
	 */
	public IFile getIFile() {
		return (IFile)resource;
	}	
	
	/*
	 * To allow accessing size and timestamp for the underlying java.io.File
	 */
	private File getIOFile() {
		IPath location = resource.getLocation();
		if(location!=null) {
			return location.toFile();
		}
		return null;
	}
	/**
	 * @see ICVSFile#getLogEntries(IProgressMonitor)
	 */
	public ILogEntry[] getLogEntries(IProgressMonitor monitor)	throws TeamException {
		ResourceSyncInfo info = getSyncInfo();
		if(isManaged(info) && !info.isAdded()) {
			ICVSRemoteResource remoteFile = CVSWorkspaceRoot.getRemoteResourceFor(resource);
			return ((ICVSRemoteFile)remoteFile).getLogEntries(monitor);
		}
		return new ILogEntry[0];
	}
	/**
	 * @see org.eclipse.team.internal.ccvs.core.ICVSFile#setNotifyInfo(NotifyInfo)
	 */
	public void setNotifyInfo(NotifyInfo info) throws CVSException {
		if (isManaged()) {
			EclipseSynchronizer.getInstance().setNotifyInfo(resource, info);
			// On an edit, the base should be cached
			// On an unedit, the base should be restored and cleared
			// On a commit, the base should be cleared
		}
	}

	/**
	 * @see org.eclipse.team.internal.ccvs.core.ICVSFile#getNotifyInfo()
	 */
	public NotifyInfo getNotifyInfo() throws CVSException {
		if (isManaged()) {
			return EclipseSynchronizer.getInstance().getNotifyInfo(resource);		
		}
		return null;
	}

}