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

import java.io.File;

import org.eclipse.core.resources.IFileModificationValidator;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;

/**
 * This example illustrates how to create a concrete implementation of a <code>RepositoryProvider</code>
 * that uses the file system to act as the repository. See the plugin.xml file for the xml required
 * to register this provider with the Team extension point <code>org.eclipse.team.core.repository</code>.
 * The plugin.xml file also contains examples of how to filter menu items using a repsitory provider's
 * ID.
 * 
 * <p>
 * This example provider illustrates the following:
 * <ol>
 * <li>simple working implementation of <code>RepositoyProvider</code>
 * <li>storage of a persistant property with the project (which provides the target location for the provider)
 * <li>access to an instance of <code>SimpleAccessOperations</code> for performing simple file operations
 * </ol>
 * 
 * <p>
 * Additional functionality that will be illustrated in the future include:
 * <ol>
 * <li>Validate Save/Validat Edit
 * <li>Move/Delete Hook
 * <li>Project Sets
 * <li>Use of the workspace synchronizer (ISynchronizer)
 * <li>Use of decorators
 * <li>combining streams and progress monitors to get responsive UI
 * </ol>
 * 
 */
public class FileSystemProvider extends RepositoryProvider {
	
	// The location of the folder on file system where the repository is stored.
	private IPath root;
	
	// The QualifiedName that is used to persist the location accross workspace as a persistant property on a resource
	private static QualifiedName FILESYSTEM_REPO_LOC = new QualifiedName(FileSystemPlugin.ID, "disk_location"); //$NON-NLS-1$

	/**
	 * Create a new FileSystemProvider.
	 */
	public FileSystemProvider() {
		super();
	}
	
	/**
	 * This method is invoked when the provider is mapped to a project.
	 * Although we have access to the project at this point (using 
	 * <code>getProject()</code>, we don't know the root location so
	 * there is nothing we can do yet.
	 * 
	 * @see org.eclipse.team.core.RepositoryProvider#configureProject()
	 */
	public void configureProject() throws CoreException {
	}

	/**
	 * This method is invoked when the provider is unmapped from its
	 * project.
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#deconfigure()
	 */
	public void deconfigure() throws CoreException {
		// Clear the persistant property containing the location
		getProject().setPersistentProperty(FILESYSTEM_REPO_LOC, null);
	}

	/**
	 * Return the provider ID as specified in the plugin.xml
	 * 
	 * @see RepositoryProvider#getID()
	 */
	public String getID() {
		return FileSystemPlugin.PROVIDER_ID;
	}
		
	/**
	 * Set the file system location for the provider. This mist be invoked after 
	 * the provider is mapped and configured but before the provider is used to 
	 * perform any operations.
	 * 
	 * @param location the path representing the location where the project contents will be stored.
	 * @throws TeamException
	 */
	public void setTargetLocation(String location) throws TeamException {
		
		// set the instance variable to the provided path
		root = new Path(location);
		
		// ensure that the location is a folder (if it exists)
		File file = new File(location);
		if (file.exists() && !file.isDirectory()) {
			throw new TeamException(Policy.bind("FileSystemProvider.mustBeFolder", location)); //$NON-NLS-1$
		}
		
		// record the location as a persistant property so it will be remembered across platform invokations
		try {
			getProject().setPersistentProperty(FILESYSTEM_REPO_LOC, location);
		} catch (CoreException e) {
			throw FileSystemPlugin.wrapException(e);
		}
	}
	
	/**
	 * Returns the folder in the file system to which the provider is connected.
	 * Return <code>null</code> if there is no location or there was a problem
	 * determining it.
	 * 
	 * @return IPath The path to the root of the repository.
	 */
	public IPath getRoot() {
		if (root == null) {
			try {
				String location = getProject().getPersistentProperty(FILESYSTEM_REPO_LOC);
				if (location == null) {
					return null;
				}
				root = new Path(location);
			} catch (CoreException e) {
				// log the problem and carry on
				FileSystemPlugin.log(e.getStatus());
				return null;
			}
		}
		return root;
	}

	/**
	 * Return an instance of <code>SimpleAccessOperations</code> that provides the
	 * operations for transfering data to and from the provider's location.
	 * Note: The interface <code>SimpleAccessOperations</code> is not part of the official
	 * Team API. We use it here for convenience.
	 * 
	 * @see org.eclipse.team.core.RepositoryProvider#getSimpleAccess()
	 */
	public SimpleAccessOperations getSimpleAccess() {
		return new FileSystemSimpleAccessOperations(this);
	}
	/**
	 * @see org.eclipse.team.core.RepositoryProvider#getFileModificationValidator()
	 */
	public IFileModificationValidator getFileModificationValidator() {
		return new FileModificationValidator(this);
	}

}
