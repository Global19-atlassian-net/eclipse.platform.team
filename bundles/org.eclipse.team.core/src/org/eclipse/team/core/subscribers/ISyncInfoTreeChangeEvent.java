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
package org.eclipse.team.core.subscribers;

import org.eclipse.core.resources.IResource;

/**
 * This is a change event that provides access to changes in subtrees
 * that contain the out-of-sync resources.
 */
public interface ISyncInfoTreeChangeEvent extends ISyncInfoSetChangeEvent {
	
	/**
	 * Returns the highest parent resources of all newly added elements available in this event
	 * by calling <code>getAddedResources()</code>. In other words, it returns the set of all
	 * parent containers that did not previously have descendants in the sync set but are direct
	 * children of containers that did previously have descescendants in the set. 
	 * <p>
	 * These roots are provided in order
	 * to allow listeners to optimize the reconciliation of hierachical views of 
	 * the <code>SyncInfoSet</code> contents. 
	 * 
	 * @return parents of all newly added elements  or an empty list if this event 
	 * doesn't contain added resources.
	 */
	public IResource[] getAddedSubtreeRoots();
	
	/**
	 * Returns the highest parent resources of all newly removed elements available in this event
	 * by calling <code>getRemovedResources()</code>. In other words, it returns the set of all
	 * parent containers that previously had descendants in the sync set but are direct
	 * children of containers that still have descescendants in the set. 
	 * <p>
	 * These roots are provided in order
	 * to allow listeners to optimize the reconciliation of hierachical views of 
	 * the <code>SyncInfoSet</code> contents. 
	 * 
	 * @return parents of all newly removed elements.  or an empty list if this event 
	 * doesn't contain added resources.
	 */
	public IResource[] getRemovedSubtreeRoots();
}
