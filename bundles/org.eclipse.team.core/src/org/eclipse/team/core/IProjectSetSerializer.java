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
package org.eclipse.team.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * IProjectSetSerializer manages the serializing and deserializing
 * of references to projects.  Given a project, it can produce a
 * UTF-8 encoded String which can be stored in a file.
 * Given this String, it can create in the workspace an IProject.
 */

public interface IProjectSetSerializer {
	
	/**
	 * For every IProject in providerProjects, return an opaque
	 * UTF-8 encoded String to act as a reference to that project.
	 * The format of the String is specific to the provider.
	 * The format of the String must be such that
	 * IProjectSetSerializer.addToWorskpace() will be able to
	 * consume it and recreate a corresponding project.
	 * @see IProjectSetSerializer.addToWorkspace()
	 * 
	 * @param providerProjects  an array of projects that the serializer should create
	 *   text references for
	 * @param context  a UI context object
	 * @return an array of serialized reference strings uniquely identifying the projects
	 */
	public String[] asReference(IProject[] providerProjects, Object context) throws TeamException;
	
	/**
	 * For every String in referenceStrings, create in the workspace a
	 * corresponding IProject.  Return an Array of the resulting IProjects.
	 * Result is unspecified in the case where an IProject of that name
	 * already exists. In the case of failure, a TeamException must be thrown.
	 * The opaque strings in referenceStrings are guaranteed to have been previously
	 * produced by IProjectSetSerializer.asReference().
	 * @see IProjectSetSerializer.asReference()
	 * 
	 * @param referenceStrings  an array of referene strings uniquely identifying the projects
	 * @param filename  the name of the file that the references were read from. This is included
	 *   in case the provider needs to deduce relative paths
	 * @param context  a UI context object
	 * @param monitor  a progress monitor
	 * @return an array of projects that were created
	 */
	public IProject[] addToWorkspace(String[] referenceStrings, String filename, Object context, IProgressMonitor monitor) throws TeamException;
}
