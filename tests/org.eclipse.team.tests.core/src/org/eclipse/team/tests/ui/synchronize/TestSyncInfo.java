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
package org.eclipse.team.tests.ui.synchronize;

import org.eclipse.core.resources.IResource;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.SyncInfo;

public class TestSyncInfo extends SyncInfo {

	private int kind;
	
	public TestSyncInfo(IResource resource, int kind, TestSubscriber subscriber) throws TeamException {
		super(resource, null, null, subscriber.getResourceComparator());
		this.kind = kind;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.SyncInfo#calculateKind()
	 */
	protected int calculateKind() throws TeamException {
		return this.kind;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.SyncInfo#getKind()
	 */
	public int getKind() {
		return kind;
	}
}
