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
package org.eclipse.team.examples.filesystem;

import java.io.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.*;
import org.eclipse.team.core.TeamException;

/**
 * Class represents a handle to a <code>java.io.File</code> that conforms to
 * the <code>org.eclipse.team.core.IResourceVariant</code> interface.
 */
public class FileSystemRemoteResource implements IAdaptable, IStorage {

	// the file object in which the data is stored on the disk
	private File ioFile;

	/**
	 * The constructor.
	 * @param path the full path of the resource on disk
	 */
	public FileSystemRemoteResource(IPath path) {
		this(new File(path.toOSString()));
	}

	/**
	 * Create a remote resource handle from the given java.io.file
	 * 
	 * @param ioFile the file
	 */
	FileSystemRemoteResource(File ioFile) {
		this.ioFile = ioFile;
	}

	/**
	 * Adapters are used to ensure that the right menus will appear in differnet views.
	 * 
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(Class)
	 */
	public Object getAdapter(Class adapter) {
		return Platform.getAdapterManager().getAdapter(this, adapter);
	}

	/**
	 * Returns an input stream containing the contents of the remote resource.
	 * The remote resource must be a file.
	 * 
	 * @see org.eclipse.team.core.sync.IResourceVariant#getContents(IProgressMonitor)
	 */
	public InputStream getContents(IProgressMonitor progress) throws TeamException {
		if (isContainer())
			throw new TeamException(Policy.bind("FileSystemRemoteResource.0")); //$NON-NLS-1$
		try {
			return new FileInputStream(ioFile);
		} catch (FileNotFoundException e) {
			throw FileSystemPlugin.wrapException(e);
		}
	}

	/**
	 * Return the modification timestamp of the remote resource.
	 * 
	 * @return long The date and time (in milliseconds) when the file was last changed on disk.
	 */
	public long getLastModified() {
		return ioFile.lastModified();
	}

	/**
	 * @see org.eclipse.team.core.sync.IResourceVariant#getName()
	 */
	public String getName() {
		return ioFile.getName();
	}

	/**
	 * @see org.eclipse.team.core.sync.IResourceVariant#isContainer()
	 */
	public boolean isContainer() {
		return ioFile.isDirectory();
	}

	/**
	 * Fetch the members of the remote resource. The remote resource must be a 
	 * container.
	 * 
	 * @see org.eclipse.team.core.sync.IResourceVariant#members(IProgressMonitor)
	 */
	public FileSystemRemoteResource[] members(IProgressMonitor progress) throws TeamException {
		// Make sure we have a container
		if (!isContainer())
			throw new TeamException(Policy.bind("RemoteResource.mustBeFolder", ioFile.getName())); //$NON-NLS-1$

		// convert the File children to remote resource children
		File[] members = ioFile.listFiles();
		FileSystemRemoteResource[] result = new FileSystemRemoteResource[members.length];
		for (int i = 0; i < members.length; i++) {
			result[i] = new FileSystemRemoteResource(members[i]);
		}
		return result;
	}

	/**
	 * copies a single specified file to a specified location on the filesystem.
	 * @param dest The location on the filesystem to which the file is to be copied
	 * @param src The source file
	 */
	static void copyFile(IPath dest, File src) {
		File target = new File(dest.append(src.getName()).toOSString());
		try {
			InputStream in = ((IFile) src).getContents();
			java.io.FileOutputStream out = new java.io.FileOutputStream(target);
			StreamUtil.pipe(in, out, target.length(), null, target.getName());
		} catch (FileNotFoundException e) {} catch (IOException e) {} catch (CoreException e) {}
	}
	/**
	 * Recursively copies an entire directory structure to a specified location on the filesystem
	 * @param dest The location on the filssystem to which the directory structure is to be written
	 * @param src The directory structure that is to be duplicated
	 */
	static void copyFolder(IPath dest, File src) {
		String children[] = src.list();
		File current;
		for (int i = 0; i < children.length; i++) {
			current = new File(children[i]);
			if (current.isFile())
				copyFile(dest.append(src.getName()), current);
			else if (current.isDirectory())
				copyFolder(dest.append(src.getName()), current);
		}
	}

	/**
	 * Creates a copy of the remote resource in the location specified
	 * @param location The destination for the copy of the remote resource
	 */
	public void copyOver(IPath location) {
		copyFolder(location, ioFile);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.IRemoteResource#getComment()
	 */
	public String getComment() throws TeamException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.IRemoteResource#getContentIdentifier()
	 */
	public String getContentIdentifier() throws TeamException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.IRemoteResource#getCreatorDisplayName()
	 */
	public String getCreatorDisplayName() throws TeamException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.sync.IRemoteResource#getBufferedStorage(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStorage getBufferedStorage(IProgressMonitor monitor) throws TeamException {
		// The contents are local so no caching is required
		return this;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.IStorage#getContents()
	 */
	public InputStream getContents() throws CoreException {
		return getContents(new NullProgressMonitor());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.IStorage#getFullPath()
	 */
	public IPath getFullPath() {
		return new Path(ioFile.getAbsolutePath());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.IStorage#isReadOnly()
	 */
	public boolean isReadOnly() {
		return true;
	}
}
