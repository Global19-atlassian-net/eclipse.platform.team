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
package org.eclipse.team.internal.core.subscribers;

import java.util.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.subscribers.BackgroundEventHandler.Event;
import org.eclipse.team.internal.core.Policy;

/**
 * This handler collects changes and removals to resources and calculates their
 * synchronization state in a background job. The result is fed input the SyncSetInput.
 * 
 * Exceptions that occur when the job is processing the events are collected and
 * returned as part of the Job's status.
 * 
 * OPTIMIZATION: look into provinding events with multiple resources instead of
 * one.
 */
public class SubscriberEventHandler extends BackgroundEventHandler {
	// The set that receives notification when the resource synchronization state
	// has been calculated by the job.
	private SyncSetInputFromSubscriber set;

	// Changes accumulated by the event handler
	private List resultCache = new ArrayList();
	
	/**
	 * Internal resource synchronization event. Can contain a result.
	 */
	class SubscriberEvent extends Event{
		static final int REMOVAL = 1;
		static final int CHANGE = 2;
		static final int INITIALIZE = 3;
		SyncInfo result;

		SubscriberEvent(IResource resource, int type, int depth) {
			super(resource, type, depth);
		}
		public SubscriberEvent(
			IResource resource,
			int type,
			int depth,
			SyncInfo result) {
				this(resource, type, depth);
				this.result = result;
		}
		public SyncInfo getResult() {
			return result;
		}
		protected String getTypeString() {
			switch (getType()) {
				case REMOVAL :
					return "REMOVAL"; //$NON-NLS-1$
				case CHANGE :
					return "CHANGE"; //$NON-NLS-1$
				case INITIALIZE :
					return "INITIALIZE"; //$NON-NLS-1$
				default :
					return "INVALID"; //$NON-NLS-1$
			}
		}
	}
	
	/**
	 * Create a handler. This will initialize all resources for the subscriber associated with
	 * the set.
	 * @param set the subscriber set to feed changes into
	 */
	public SubscriberEventHandler(SyncSetInputFromSubscriber set) {
		this.set = set;
		reset(SubscriberEvent.INITIALIZE);
	}
	
	/**
	 * Schedule the job or process the events now.
	 */
	public void schedule() {
		JobStatusHandler.schedule(getEventHandlerJob(), TeamSubscriber.SUBSCRIBER_JOB_TYPE);
	}
	
	/**
	 * Initialize all resources for the subscriber associated with the set. This will basically recalculate
	 * all synchronization information for the subscriber.
	 * @param resource
	 * @param depth
	 */
	public void initialize() {
		reset(SubscriberEvent.CHANGE);
	}
	
	/**
	 * Called by a client to indicate that a resource has changed and its synchronization state
	 * should be recalculated.  
	 * @param resource the changed resource
	 * @param depth the depth of the change calculation
	 */
	public void change(IResource resource, int depth) {
		queueEvent(new SubscriberEvent(resource, SubscriberEvent.CHANGE, depth));
	}
	
	/**
	 * Called by a client to indicate that a resource has been removed and should be removed. The
	 * removal will propagate to the set.
	 * @param resource the resource that was removed
	 */
	public void remove(IResource resource) {
		queueEvent(
			new SubscriberEvent(resource, SubscriberEvent.REMOVAL, IResource.DEPTH_INFINITE));
	}
	
	/**
	 * Collect the calculated synchronization information for the given resource at the given depth. The
	 * results are added to the provided list.
	 */
	private void collect(
		IResource resource,
		int depth,
		IProgressMonitor monitor,
		List results)
		throws TeamException {
		
		if (resource.getType() != IResource.FILE
			&& depth != IResource.DEPTH_ZERO) {
			IResource[] members =
				set.getSubscriber().members(resource);
			for (int i = 0; i < members.length; i++) {
				collect(
					members[i],
					depth == IResource.DEPTH_INFINITE
						? IResource.DEPTH_INFINITE
						: IResource.DEPTH_ZERO,
					monitor,
					results);
			}
		}

		monitor.subTask(Policy.bind("SubscriberEventHandler.2", resource.getFullPath().toString())); //$NON-NLS-1$
		SyncInfo info = set.getSubscriber().getSyncInfo(resource);
		// resource is no longer under the subscriber control
		if (info == null) {
			results.add(
				new SubscriberEvent(resource, SubscriberEvent.REMOVAL, IResource.DEPTH_ZERO));
		} else {
			results.add(
				new SubscriberEvent(resource, SubscriberEvent.CHANGE, IResource.DEPTH_ZERO, info));
		}
		monitor.worked(1);
	}
	
	/**
	 * Called to initialize to calculate the synchronization information using the optimized subscriber method. For
	 * subscribers that don't support the optimization, all resources in the subscriber are manually re-calculated. 
	 * @param resources the resources to check
	 * @param depth the depth
	 * @param monitor
	 * @return Event[] the change events
	 * @throws TeamException
	 */
	private SubscriberEvent[] getAllOutOfSync(
		IResource[] resources,
		int depth,
		IProgressMonitor monitor)
		throws TeamException {
		
		monitor.beginTask(null, 100);
		try {
			SyncInfo[] infos =
				set.getSubscriber().getAllOutOfSync(resources, depth, Policy.subMonitorFor(monitor, 50));
	
			// The subscriber hasn't cached out-of-sync resources. We will have to
			// traverse all resources and calculate their state. 
			if (infos == null) {
				List events = new ArrayList();
				IProgressMonitor subMonitor = Policy.infiniteSubMonitorFor(monitor, 50);
				subMonitor.beginTask(null, resources.length);
				for (int i = 0; i < resources.length; i++) {
					collect(
						resources[i],
						IResource.DEPTH_INFINITE,
						subMonitor,
						events);
				}
				return (SubscriberEvent[]) events.toArray(new SubscriberEvent[events.size()]);
				// The subscriber has returned the list of out-of-sync resources.
			} else {
				SubscriberEvent[] events = new SubscriberEvent[infos.length];
				for (int i = 0; i < infos.length; i++) {
					SyncInfo info = infos[i];
					events[i] =
						new SubscriberEvent(info.getLocal(), SubscriberEvent.CHANGE, depth, info);
				}
				return events;
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Feed the given events to the set. The appropriate method on the set is called
	 * for each event type. 
	 * @param events
	 */
	private void dispatchEvents(SubscriberEvent[] events) {
		// this will batch the following set changes until endInput is called.
		set.getSyncSet().beginInput();
		for (int i = 0; i < events.length; i++) {
			SubscriberEvent event = events[i];
			switch (event.getType()) {
				case SubscriberEvent.CHANGE :
					set.collect(event.getResult());
					break;
				case SubscriberEvent.REMOVAL :
					if (event.getDepth() == IResource.DEPTH_INFINITE) {
						set.getSyncSet().removeAllChildren(event.getResource());
					} else {
						set.remove(event.getResource());
					}
					break;
			}
		}
		set.getSyncSet().endInput();
	}
	
	/**
	 * Initialize all resources for the subscriber associated with the set. This will basically recalculate
	 * all synchronization information for the subscriber.
	 * @param type can be Event.CHANGE to recalculate all states or Event.INITIALIZE to perform the
	 *   optimized recalculation if supported by the subscriber.
	 */
	private void reset(int type) {
		IResource[] resources = set.getSubscriber().roots();
		for (int i = 0; i < resources.length; i++) {
			queueEvent(new SubscriberEvent(resources[i], type, IResource.DEPTH_INFINITE));
		}
	}
	

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.BackgroundEventHandler#getName()
	 */
	public String getName() {
		return Policy.bind("SubscriberEventHandler.jobName"); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.BackgroundEventHandler#getErrorsTitle()
	 */
	public String getErrorsTitle() {
		return Policy.bind("SubscriberEventHandler.errors"); //$NON-NLS-1$;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.BackgroundEventHandler#processEvent(org.eclipse.team.core.subscribers.BackgroundEventHandler.Event, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void processEvent(Event event, IProgressMonitor monitor) throws TeamException {
		// Cancellation is dangerous because this will leave the sync info in a bad state.
		// Purposely not checking -				 	
		int type = event.getType();
		switch (type) {
			case SubscriberEvent.REMOVAL :
				resultCache.add(event);
				break;
			case SubscriberEvent.CHANGE :
				List results = new ArrayList();
				collect(
					event.getResource(),
					event.getDepth(),
					monitor,
					results);
				resultCache.addAll(results);
				break;
			case SubscriberEvent.INITIALIZE :
				monitor.subTask(Policy.bind("SubscriberEventHandler.2", event.getResource().getFullPath().toString())); //$NON-NLS-1$
				SubscriberEvent[] events =
					getAllOutOfSync(
						new IResource[] { event.getResource()},
						event.getDepth(),
						Policy.subMonitorFor(monitor, 64));
				resultCache.addAll(Arrays.asList(events));
				break;
		}
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.BackgroundEventHandler#dispatchEvents()
	 */
	protected void dispatchEvents() {
		dispatchEvents((SubscriberEvent[]) resultCache.toArray(new SubscriberEvent[resultCache.size()]));
		resultCache.clear();
	}
}