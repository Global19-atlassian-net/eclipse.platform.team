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
package org.eclipse.team.tests.ccvs.core.subscriber;

import java.util.*;

import junit.framework.AssertionFailedError;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.Subscriber;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.internal.ccvs.core.*;

/**
 * This class acts as the source for the sync info used by the subscriber tests.
 * The purpose is to allow the sync info to be obtained directly from the subscriber 
 * or through the sync set visible in the sync view.
 */
public class SyncInfoSource {

	protected static IProgressMonitor DEFAULT_MONITOR = new NullProgressMonitor();
	protected List mergeSubscribers = new ArrayList();
	protected List compareSubscribers = new ArrayList();
	
	public CVSMergeSubscriber createMergeSubscriber(IProject project, CVSTag root, CVSTag branch) {
		CVSMergeSubscriber subscriber = new CVSMergeSubscriber(new IResource[] { project }, root, branch);
		mergeSubscribers.add(subscriber);
		return subscriber;
	}
	
	public CVSCompareSubscriber createCompareSubscriber(IProject project, CVSTag tag) {
		CVSCompareSubscriber subscriber = new CVSCompareSubscriber(new IResource[] { project }, tag);
		compareSubscribers.add(subscriber);
		return subscriber;
	}
	
	/**
	 * Return the sync info for the given subscriber for the given resource.
	 */
	public SyncInfo getSyncInfo(Subscriber subscriber, IResource resource) throws TeamException {
		return subscriber.getSyncInfo(resource);
	}
	
	/**
	 * Refresh the subscriber for the given resource
	 */
	public void refresh(Subscriber subscriber, IResource resource) throws TeamException {
		subscriber.refresh(new IResource[] { resource}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
	}
	
	protected void assertProjectRemoved(Subscriber subscriber, IProject project) throws TeamException {
		IResource[] roots = subscriber.roots();
		for (int i = 0; i < roots.length; i++) {
			IResource resource = roots[i];
			if (resource.equals(project)) {
				throw new AssertionFailedError();
			}
		}
	}

	public void tearDown() {
		for (Iterator it = mergeSubscribers.iterator(); it.hasNext(); ) {
			CVSMergeSubscriber s = (CVSMergeSubscriber) it.next();
			s.cancel();
		}
	}

	/**
	 * Recalculate a sync info from scratch
	 */
	public void reset(Subscriber subscriber) throws TeamException {
		// Do nothing
		
	}
}
