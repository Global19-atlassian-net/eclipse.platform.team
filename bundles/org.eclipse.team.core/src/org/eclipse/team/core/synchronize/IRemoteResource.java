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
package org.eclipse.team.core.synchronize;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;

/**
 * This interface is used by <code>SyncInfo</code> instances
 * to provide access to the base and remote resources that correspond to 
 * a local resource.
 * 
 * @see SyncInfo
 * @since 3.0
 */
public interface IRemoteResource {
	
	/**
	 * Answers the name of the remote resource. The name may be
	 * displayed to the user.
	 * 
	 * @return name of the subscriber resource.
	 */
	public String getName();
	
	/**
	 * Answers if the remote resource may have children.
	 * 
	 * @return <code>true</code> if the remote element may have children and 
	 * <code>false</code> otherwise.
	 */
	public boolean isContainer();
	
	/**
	 * Return an instance of IStorage or <code>null</code> if the remote resource
	 * does not have contents (i.e. is a folder). Since the <code>ISorage#getContents()</code>
	 * method does not accept an <code>IProgressMonitor</code>, this method must ensure that the contents
	 * access by the resulting <code>IStorage</code> is cached locally (hence the <code>IProgressMonitor</code> 
	 * argument to this method). Implementations of this method should
	 * ensure that the resulting <code>IStorage</code> is accessing locally cached contents and is not
	 * contacting the server.
	 * @return an <code>IStorage</code> that provides access to the contents of 
	 * the remote resource or <code>null</code> if the remote resource is a container.
	 */
	public IStorage getStorage(IProgressMonitor monitor) throws TeamException;
	
	/**
	 * Return a content identifier that is used to differentiate versions
	 * or revisions of the same resource.
	 * 
	 * @return a String that identifies the version of the subscriber resource
	 * @throws TeamException
	 */
	public String getContentIdentifier();
	
	/**
	 * Returns whether the remote resource is equal to the provided object.
	 * @param object the object to be compared
	 * @return whether the object is equal to the remote resource
	 */
	public boolean equals(Object object);

}
