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
package org.eclipse.team.internal.ui.synchronize;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.core.subscribers.TeamSubscriber;

public interface IRefreshEvent {	
	public static final int SCHEDULED_REFRESH = 1; 
	
	public static final int USER_REFRESH = 2;
	
	public int getRefreshType();
	
	public TeamSubscriber getSubscriber();
	
	public SyncInfo[] getChanges();
	
	public long getStartTime();
	
	public long getStopTime();
	
	public IStatus getStatus();
	
	public IResource[] getResources();
}