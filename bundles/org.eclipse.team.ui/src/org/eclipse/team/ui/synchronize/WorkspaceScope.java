/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.ui.synchronize;

import org.eclipse.core.resources.IResource;

/**
 * A synchronize scope whose roots are the workspace.
 * <p>
 * Clients are not expected to subclass this class.
 * @since 3.0
 */
public class WorkspaceScope extends AbstractSynchronizeScope  {
	
	/**
	 * Create the resource scope that indicates that the subscriber roots should be used
	 */
	public WorkspaceScope() {
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.ScopableSubscriberParticipant.ISynchronizeScope#getName()
	 */
	public String getName() {
		return "Workspace";
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.ScopableSubscriberParticipant.ISynchronizeScope#getRoots()
	 */
	public IResource[] getRoots() {
		// Return null which indicates to use the subscriber roots
		return null;
	}
}