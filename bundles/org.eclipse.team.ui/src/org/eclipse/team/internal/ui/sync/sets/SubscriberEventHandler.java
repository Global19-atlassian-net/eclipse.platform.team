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
package org.eclipse.team.internal.ui.sync.sets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.internal.ui.Policy;

/**
 * This handler collects changes and removals to resources and calculates their
 * synchronization state in a background job. The result is fed input the SyncSetInput.
 * 
 * Exceptions that occur when the job is processing the events are ???.
 */
public class SubscriberEventHandler {
	// The number of events to process before feeding into the set.
	private static final int NOTIFICATION_BATCHING_NUMBER = 10;
	
	// The set that receives notification when the resource synchronization state
	// has been calculated by the job.
	private SyncSetInputFromSubscriber set;

	 // Events that need to be processed
	 private List awaitingProcessing = new ArrayList();
	 
	 // Use to shutdown the job
	 private boolean shutdown = false;

	// The job that runs when events need to be processed
	 Job eventHandlerJob;
	
	/**
	 * Internal resource synchronization event. Can contain a result.
	 */
	class Event {
		static final int REMOVAL = 1;
		static final int CHANGE = 2;
		static final int INITIALIZE = 3;
		IResource resource;
		int type;
		int depth;
		SyncInfo result;
		
		Event(IResource resource, int type, int depth) {
			this.resource = resource;
			this.type = type;
			this.depth = depth;
		}		
		public Event(IResource resource, int type, int depth, SyncInfo result) {
			this(resource, type, depth);
			this.result = result;
		}
		public int getDepth() {
			return depth;
		}		
		public IResource getResource() {
			return resource;
		}		
		public int getType() {
			return type;
		}
		public SyncInfo getResult() {
			return result;
		}
	}
	/**
	 * Create a handler. This will initialize all resources for the subscriber associated with
	 * the set.
	 * @param set the subscriber set to feed changes into
	 */
	public SubscriberEventHandler(SyncSetInputFromSubscriber set) {
		this.set = set;
		reset(Event.INITIALIZE);
		createEventHandlingJob();
		eventHandlerJob.schedule();
	}
	/**
	 * Initialize all resources for the subscriber associated with the set. This will basically recalculate
	 * all synchronization information for the subscriber.
	 * @param resource
	 * @param depth
	 */
	public void initialize() {
		reset(Event.CHANGE);		
	}	
	/**
	 * Called by a client to indicate that a resource has changed and its synchronization state
	 * should be recalculated.  
	 * @param resource the changed resource
	 * @param depth the depth of the change calculation
	 */
	public void change(IResource resource, int depth) {
		queueEvent(new Event(resource, Event.CHANGE, depth));
	}	
	/**
	 * Called by a client to indicate that a resource has been removed and should be removed. The
	 * removal will propagate to the set.
	 * @param resource the resource that was removed
	 */
	public void remove(IResource resource) {
		queueEvent(new Event(resource, Event.REMOVAL, IResource.DEPTH_INFINITE));
	}	
	 /**
	  * Queue the event and start the job if it's not already doing work.
	  */
	 synchronized private void queueEvent(Event event) {
		 awaitingProcessing.add(event);
		 if (shutdown || eventHandlerJob == null || eventHandlerJob.getState() != Job.NONE)
			return;
		 else {
			eventHandlerJob.schedule();
		 }
	 }	
	/**
	  * Shutdown the event handler.
	  */
	 void shutdown() {
	 	shutdown = true;
		eventHandlerJob.cancel();
	 }
	 /**
	  * Get the next resource to be calculated.
	  * @return Event to be processed
	  */
	 synchronized Event nextElement() {
		 if (shutdown || awaitingProcessing.isEmpty()) {
			 return null;
		 }
		 return  (Event)awaitingProcessing.remove(0);
	 }

	 /**
	  * Create the job used for processing the events in the queue. The job stops working when
	  * the queue is empty.
	  */
	 private void createEventHandlingJob() {
			 eventHandlerJob = new Job(Policy.bind("SubscriberEventHandler.jobName")) { //$NON-NLS-1$
	
			 public IStatus run(IProgressMonitor monitor) {
				 monitor.beginTask(null, 100); //$NON-NLS-1$
				List resultCache = new ArrayList();
				 Event event;
				 while ((event = nextElement()) != null) {
				 	
				 	// cancellation is dangerous because this will leave the sync info in a bad state.
				 	// purposely not checking
				 	
				 	try {
						int type = event.getType();
						switch(type) {
							case Event.REMOVAL : 
								resultCache.add(new Event(event.getResource(), event.getType(), event.getDepth()));
								break;
							case Event.CHANGE :
								List results = new ArrayList();
								collect(event.getResource(), event.getDepth(), monitor, results);
								resultCache.addAll(results);
								break;
							case Event.INITIALIZE :
								Event[] events = getAllOutOfSync(new IResource[] {event.getResource()}, event.getDepth(), monitor);
								resultCache.addAll(Arrays.asList(events));
								break;				 										
						}
					} catch (TeamException e) {
						// TODO: 
						// accumulate but keep processing the other events.
						// the user may need a way to revalidate 
					}
				 	
					 if (awaitingProcessing.isEmpty() || resultCache.size() > NOTIFICATION_BATCHING_NUMBER) {
						 dispatchEvents((Event[])resultCache.toArray(new Event[resultCache.size()]));
						 resultCache.clear();
					 }
				 }
				 return Status.OK_STATUS;
			 }
		 };
		eventHandlerJob.setPriority(Job.SHORT);
		eventHandlerJob.setSystem(true);
	 }
	/**
	 * Collect the calculated synchronization information for the given resource at the given depth. The
	 * results are added to the provided list.
	 */
	private void collect(IResource resource, int depth, IProgressMonitor monitor, List results) throws TeamException {
		
		if(resource.getType() != IResource.FILE && depth != IResource.DEPTH_ZERO) {
			IResource[] members = set.getSubscriber().members((IContainer) resource);
			for (int i = 0; i < members.length; i++) {
				collect(members[i], depth == IResource.DEPTH_INFINITE ? IResource.DEPTH_INFINITE : IResource.DEPTH_ZERO, monitor, results);
			}
		}
		
		SyncInfo info = set.getSubscriber().getSyncInfo(resource, monitor);
		// resource is no longer under the subscriber control
		if (info == null) {
			results.add(new Event(resource, Event.REMOVAL, IResource.DEPTH_ZERO));
		} else { 
			results.add(new Event(resource, Event.CHANGE, IResource.DEPTH_ZERO, info));
		}
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
	private Event[] getAllOutOfSync(IResource[] resources, int depth, IProgressMonitor monitor) throws TeamException {		
		SyncInfo[] infos = set.getSubscriber().getAllOutOfSync(resources, depth, monitor);
		
		// The subscriber hasn't cached out-of-sync resources. We will have to
		// traverse all resources and calculate their state. 
		if(infos == null) {
			List events = new ArrayList();
			for (int i = 0; i < resources.length; i++) {
				collect(resources[i], IResource.DEPTH_INFINITE, monitor, events);
			}
			return (Event[]) events.toArray(new Event[events.size()]);
		// The subscriber has returned the list of out-of-sync resources.
		} else {
			Event[] events = new Event[infos.length];
			for (int i = 0; i < infos.length; i++) {
				SyncInfo info = infos[i];
				events[i] = new Event(info.getLocal(), Event.CHANGE, depth, info);
			}
			return events;
		}		
	}
	
	/**
	 * Feed the given events to the set. The appropriate method on the set is called
	 * for each event type. 
	 * @param events
	 */
	private void dispatchEvents(Event[] events) {
		// this will batch the following set changes until endInput is called.
		set.getSyncSet().beginInput();
		for (int i = 0; i < events.length; i++) {
			Event event = events[i];
			switch(event.getType()) {
				case Event.CHANGE : 
					set.collect(event.getResult());
					break;
				case Event.REMOVAL :
					if(event.getDepth() == IResource.DEPTH_INFINITE) {
						set.getSyncSet().removeAllChildren(event.getResource());
					} else {
						set.remove(event.getResource());
					}
					break;
			}
		}
		set.getSyncSet().endInput();
	};
	/**
	 * Initialize all resources for the subscriber associated with the set. This will basically recalculate
	 * all synchronization information for the subscriber.
	 * @param resource
	 * @param depth
	 */
	private void reset(int type) {
		IResource[] resources = set.getSubscriber().roots(); 
		for (int i = 0; i < resources.length; i++) {
			queueEvent(new Event(resources[i], type, IResource.DEPTH_INFINITE));			
		}
	}
}
