package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Date;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.ccvs.core.ICVSFile;
import org.eclipse.team.ccvs.core.ICVSFolder;
import org.eclipse.team.ccvs.core.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.EntryFileDateFormat;

/**
 * Represents handles to CVS resource on the local file system. Synchronization
 * information is taken from the CVS subdirectories. 
 */
class EclipseFile extends EclipseResource implements ICVSFile {

	/**
	 * Create a handle based on the given local resource.
	 */
	protected EclipseFile(IFile file) {
		super(file);
	}

	public long getSize() {
		return getIOFile().length();	
	}

	public InputStream getInputStream() throws CVSException {
 		try {
			return getIFile().getContents();
		} catch (CoreException e) {
 			throw CVSException.wrapException(e);
 		}
 	}
	
	public OutputStream getOutputStream() throws CVSException {
		return new ByteArrayOutputStream() {
			public void close() throws IOException {
				try {
					getIFile().setContents(new ByteArrayInputStream(toByteArray()), true /*force*/, true /*keep history*/, null);
					super.close();
				} catch(CoreException e) {
					throw new IOException("Error setting file contents: " + e.getMessage());
				}
			}
		};
	}
	
	/*
	 * @see ICVSFile#getTimeStamp()
	 */
	public String getTimeStamp() {						
		EntryFileDateFormat timestamp = new EntryFileDateFormat();		
		return timestamp.format(new Date(getIOFile().lastModified()));
	}
 
	/*
	 * @see ICVSFile#setTimeStamp(String)
	 */
	public void setTimeStamp(String date) throws CVSException {
		long millSec;		
		if (date==null) {
			// get the current time
			millSec = new Date().getTime();
		} else {
			try {
				EntryFileDateFormat timestamp = new EntryFileDateFormat();
				millSec = timestamp.toDate(date).getTime();
			} catch (ParseException e) {
				throw new CVSException(Policy.bind("LocalFile.invalidDateFormat", date), e); //$NON-NLS-1$
			}
		}		
		getIOFile().setLastModified(millSec);
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
			return !getTimeStamp().equals(info.getTimeStamp());
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
	public void moveTo(String filename) throws CVSException {
		try {
			getIFile().move(new Path(filename), true /*force*/, true /*keep history*/, null);
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
	 * @see ICVSResource#unmanage()
	 */
	public void unmanage() throws CVSException {
		CVSProviderPlugin.getSynchronizer().deleteResourceSync(getIOFile());
	}
	
	/*
	 * @see ICVSFile#setReadOnly()
	 */
	public void setReadOnly(boolean readOnly) throws CVSException {
		getIFile().setReadOnly(readOnly);
	}
	
	/*
	 * Typecasting helper
	 */
	private IFile getIFile() {
		return (IFile)resource;
	}
}