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
package org.eclipse.team.internal.core.subscribers;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.subscribers.ISyncInfoSetChangeListener;
import org.eclipse.team.core.subscribers.SyncInfoTree;
import org.eclipse.team.internal.core.Policy;

/**
 * The <code>SubscriberSyncInfoSet</code> is a <code>SyncInfoSet</code> that provides the ability to add,
 * remove and change <code>SyncInfo</code> and fires change event notifications to registered listeners. 
 * It also provides the ability
 * to batch changes in a single change notification as well as optimizations for sync info retrieval.
 * 
 * This class uses synchronized methods and synchronized blocks to protect internal data structures during both access
 * and modify operations and uses an <code>ILock</code> to make modification operations thread-safe. The events
 * are fired while this lock is held so clients responding to these events should not obtain their own internal locks
 * while processing change events.
 * 
 * TODO: Override modification methods to enforce use with handler
 * 
 */
public class SubscriberSyncInfoSet extends SyncInfoTree {
	
	protected SubscriberEventHandler handler;
	
	public SubscriberSyncInfoSet(SubscriberEventHandler handler) {
		this.handler = handler;
	}	

	public void run(final IWorkspaceRunnable runnable, IProgressMonitor monitor) throws CoreException {
		if (handler == null) {
			super.run(runnable, monitor);
		} else {
			handler.run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					// Perform a beginInput to ensure no modifications are performed on the set
					// while the runnable is being run in the background job
					beginInput();
					try {
						monitor.beginTask(null, 100);
						runnable.run(Policy.subMonitorFor(monitor, 95));
					} finally {
						endInput(Policy.subMonitorFor(monitor, 5));
						monitor.done();
					}
				}
			});
		}
	}

	/**
	 * Variation of connect that does not need progress and does not throw an exception.
	 * Progress ins provided by the background event handler and errors are passed through
	 * the chain to the view.
	 * @param listener
	 */
	public void connect(final ISyncInfoSetChangeListener listener) {
		if (handler == null) {
			// Should only use this connect if the set has a handler
			throw new UnsupportedOperationException();
		} else {
			handler.run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) {
					beginInput();
					try {
						monitor.beginTask(null, 100);
						addSyncSetChangedListener(listener);
						SyncSetChangedEvent event = new SyncSetChangedEvent(SubscriberSyncInfoSet.this);
						event.reset();
						listener.syncInfoChanged(event, Policy.subMonitorFor(monitor, 95));
					} finally {
						endInput(Policy.subMonitorFor(monitor, 5));
						monitor.done();
					}
				}
			});
		}
	}
	
}