package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.File;
import java.io.InputStream;
import java.util.Date;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.ccvs.core.ICVSFile;
import org.eclipse.team.ccvs.core.ICVSFolder;
import org.eclipse.team.ccvs.core.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;

/**
 * Represents handles to CVS resource on the local file system. Synchronization
 * information is taken from the CVS subdirectories. 
 */
class EclipseFile extends EclipseResource implements ICVSFile {

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

	public InputStream getInputStream() throws CVSException {
 		try {
			return getIFile().getContents();
		} catch (CoreException e) {
 			throw CVSException.wrapException(resource, Policy.bind("EclipseFile_Problem_accessing_resource", resource.getFullPath().toString(), e.getStatus().getMessage()), e); //$NON-NLS-1$ //$NON-NLS-2$
 		}
 	}
	
	/*
	 * @see ICVSFile#getTimeStamp()
	 */
	public long getTimeStamp() {						
		return getIOFile().lastModified();
	}
 
	/*
	 * @see ICVSFile#setTimeStamp(String)
	 */
	public void setTimeStamp(long date) throws CVSException {
		long timestamp = date;
		if (date==NULL_TIMESTAMP) {
			// get the current time
			timestamp = new Date().getTime();
		}
		getIOFile().setLastModified(timestamp);
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
	 * @see ICVSFile#isDirty()
	 */
	public boolean isDirty() throws CVSException {
		if (!exists() || !isManaged()) {
			return true;
		} else {
			ResourceSyncInfo info = getSyncInfo();
			if (info.isAdded()) return false;
			if (info.isDeleted()) return true;
			return getTimeStamp() != info.getTimeStamp();
		}
	}

	/*
	 * @see ICVSFile#isModified()
	 */
	public boolean isModified() throws CVSException {
		if (!exists() || !isManaged()) {
			return true;
		} else {
			ResourceSyncInfo info = getSyncInfo();
			return getTimeStamp() != info.getTimeStamp();
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
			if (responseType == CREATED || (responseType == UPDATED && ! resource.exists())) {
				try {
					file.create(stream, false /*force*/, null);
				} catch (CoreException e) {
					if (resource.exists()) {
						if (PROJECT_META_DATA_PATH.equals(file.getFullPath().removeFirstSegments(1))) {
							// Special handling for the .project meta-file
							file.setContents(stream, true /*force*/, true /*keep history*/, monitor);
							return;
						}
					}
					throw new CVSException(Policy.bind("EclipseFile_Problem_creating_resource", e.getMessage(), e.getStatus().getMessage()));  //$NON-NLS-1$
				}
			} else if(responseType == UPDATE_EXISTING) {
				file.setContents(stream, false /*force*/, keepLocalHistory /*keep history*/, monitor);
			} else {
				file.setContents(stream, false /*force*/, keepLocalHistory /*keep history*/, monitor);
				
	//			// Ensure we don't leave the file in a partially written state
	//			IFile tempFile = file.getParent().getFile(new Path(file.getName() + TEMP_FILE_EXTENSION));
	//			tempFile.create(new ByteArrayInputStream(toByteArray()), true /*force*/, null);
	//			file.delete(false, true, null);
	//			tempFile.move(new Path(file.getName()), true, true, null);
			}
		} catch(CoreException e) {
			throw new CVSException(Policy.bind("EclipseFile_Problem_writing_resource", e.getMessage(), e.getStatus().getMessage())); //$NON-NLS-1$
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
	private IFile getIFile() {
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
}