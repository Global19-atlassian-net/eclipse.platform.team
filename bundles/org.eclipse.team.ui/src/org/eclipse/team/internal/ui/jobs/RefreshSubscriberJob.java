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
package org.eclipse.team.internal.ui.jobs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.core.subscribers.TeamSubscriber;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;

/**
 * Job to refresh a subscriber with its remote state.
 * 
 * There can be several refresh jobs created but they will be serialized.
 * This is accomplished using a synchrnized block on the family id. It is
 * important that no scheduling rules are used for the job in order to
 * avoid possible deadlock. 
 */
public class RefreshSubscriberJob extends WorkspaceJob {
	
	/**
	 * Uniquely identifies this type of job. This is used for cancellation.
	 */
	private final static Object FAMILY_ID = new Object();
	
	/**
	 * If true this job will be restarted when it completes 
	 */
	private boolean reschedule = false;
	
	/**
	 * If true a rescheduled refresh job should be retarted when cancelled
	 */
	private boolean restartOnCancel = true; 
	
	/**
	 * The schedule delay used when rescheduling a completed job 
	 */
	private static long scheduleDelay;
	
	/**
	 * The subscribers and roots to refresh. If these are changed when the job
	 * is running the job is cancelled.
	 */
	private IResource[] resources;
	private TeamSubscriberSyncInfoCollector collector;
	
	/**
	 * Refresh started/completed listener for every refresh
	 */
	private static List listeners = new ArrayList(1);
	
	protected static class RefreshEvent implements IRefreshEvent {
		int type; 
		TeamSubscriber subscriber;
		SyncInfo[] changes;
		long startTime = 0;
		long stopTime = 0;
		IStatus status;
		IResource[] resources;
		
		RefreshEvent(int type, IResource[] resources, TeamSubscriber subscriber) {
			this.type = type;
			this.subscriber = subscriber;
			this.resources = resources;
		}
		
		public int getRefreshType() {
			return type;
		}

		public TeamSubscriber getSubscriber() {
			return subscriber;
		}

		public SyncInfo[] getChanges() {
			return changes;
		}
		
		public void setChanges(SyncInfo[] changes) {
			this.changes = changes;
		}
		
		/**
		 * @return Returns the startTime.
		 */
		public long getStartTime() {
			return startTime;
		}

		/**
		 * @param startTime The startTime to set.
		 */
		public void setStartTime(long startTime) {
			this.startTime = startTime;
		}

		/**
		 * @return Returns the stopTime.
		 */
		public long getStopTime() {
			return stopTime;
		}

		/**
		 * @param stopTime The stopTime to set.
		 */
		public void setStopTime(long stopTime) {
			this.stopTime = stopTime;
		}

		public IStatus getStatus() {
			return status;
		}
		
		public void setStatus(IStatus status) {
			this.status = status;
		}

		public IResource[] getResources() {
			return resources;
		}
	}
	
	private abstract class Notification implements ISafeRunnable {
		private IRefreshSubscriberListener listener;
		public void handleException(Throwable exception) {
			// don't log the exception....it is already being logged in Platform#run
		}
		public void run(IRefreshSubscriberListener listener) {
			this.listener = listener;
			Platform.run(this);
		}
		public void run() throws Exception {
			notify(listener);
		}
		/**
		 * Subsclasses overide this method to send an event safely to a lsistener
		 * @param listener
		 */
		protected abstract void notify(IRefreshSubscriberListener listener);
	}
	
		
	public RefreshSubscriberJob(String name, IResource[] resources, TeamSubscriberSyncInfoCollector collector) {
		this(name, collector);		
		this.resources = resources;
	}
	
	public RefreshSubscriberJob(String name, TeamSubscriberSyncInfoCollector collector) {
		super(name);
		
		this.collector = collector;
		
		setPriority(Job.DECORATE);
		setRefreshInterval(3600 /* 1 hour */);
		
		addJobChangeListener(new JobChangeAdapter() {
			public void done(IJobChangeEvent event) {
				if(shouldReschedule()) {
					if(event.getResult().getSeverity() == IStatus.CANCEL && ! restartOnCancel) {					
						return;
					}
					RefreshSubscriberJob.this.schedule(scheduleDelay);
					restartOnCancel = true;
				}
			}
		});		
	}
	
	public boolean shouldRun() {
		return collector != null && getSubscriber() != null;
	}
	
	public boolean belongsTo(Object family) {		
		return family == getFamily();
	}
	
	public static Object getFamily() {
		return FAMILY_ID;
	}
	
	/**
	 * This is run by the job scheduler. A list of subscribers will be refreshed, errors will not stop the job 
	 * and it will continue to refresh the other subscribers.
	 */
	public IStatus runInWorkspace(IProgressMonitor monitor) {
		// Synchronized to ensure only one refresh job is running at a particular time
		synchronized (getFamily()) {	
			MultiStatus status = new MultiStatus(TeamUIPlugin.ID, TeamException.UNABLE, Policy.bind("RefreshSubscriberJob.0"), null); //$NON-NLS-1$
			TeamSubscriber subscriber = getSubscriber();
			IResource[] roots = getResources();
			
			// if there are no resources to refresh, just return
			if(subscriber == null || roots == null) {
				return Status.OK_STATUS;
			}
			
			monitor.beginTask(null, 100);
			RefreshEvent event = new RefreshEvent(reschedule ? IRefreshEvent.SCHEDULED_REFRESH : IRefreshEvent.USER_REFRESH, roots, collector.getTeamSubscriber());
			RefreshChangeListener changeListener = new RefreshChangeListener(collector);
			try {
				// Only allow one refresh job at a time
				// NOTE: It would be cleaner if this was done by a scheduling
				// rule but at the time of writting, it is not possible due to
				// the scheduling rule containment rules.
				event.setStartTime(System.currentTimeMillis());
				if(monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				try {
					// Set-up change listener so that we can determine the changes found
					// during this refresh.						
					subscriber.addListener(changeListener);
					// Pre-Notify
					notifyListeners(true, event);
					// Perform the refresh										
					subscriber.refresh(roots, IResource.DEPTH_INFINITE, Policy.subMonitorFor(monitor, 100));					
				} catch(TeamException e) {
					status.merge(e.getStatus());
				}
			} catch(OperationCanceledException e2) {
				return Status.CANCEL_STATUS;
			} finally {
				monitor.done();
			}
			
			// Post-Notify
			event.setChanges(changeListener.getChanges());
			event.setStopTime(System.currentTimeMillis());
			event.setStatus(status.isOK() ? Status.OK_STATUS : (IStatus) status);
			notifyListeners(false, event);
			changeListener.clear();
			
			return event.getStatus();
		}
	}
	
	protected IResource[] getResources() {
		if(resources != null) {
			return resources;
		} else {
			return collector.getTeamSubscriber().roots();
		}
	}
	
	protected TeamSubscriber getSubscriber() {
		return collector.getTeamSubscriber();
	}
	
	public long getScheduleDelay() {
		return scheduleDelay;
	}
	
	protected void start() {
		if(getState() == Job.NONE) {
			if(shouldReschedule()) {
				schedule(getScheduleDelay());
			}
		}
	}
	
	/**
	 * Specify the interval in seconds at which this job is scheduled.
	 * @param seconds delay specified in seconds
	 */
	public void setRefreshInterval(long seconds) {
		boolean restart = false;
		if(getState() == Job.SLEEPING) {
			restart = true;
			cancel();
		}
		scheduleDelay = seconds * 1000;
		if(restart) {
			start();
		}
	}
	
	/**
	 * Returns the interval of this job in seconds. 
	 * @return
	 */
	public long getRefreshInterval() {
		return scheduleDelay / 1000;
	}
	
	public void setRestartOnCancel(boolean restartOnCancel) {
		this.restartOnCancel = restartOnCancel;
	}
	
	public void setReschedule(boolean reschedule) {
		this.reschedule = reschedule;
	}
	
	public boolean shouldReschedule() {
		return reschedule;
	}
	
	public static void addRefreshListener(IRefreshSubscriberListener listener) {
		synchronized(listeners) {
			if(! listeners.contains(listener)) {
				listeners.add(listener);
			}
		}
	}
	
	public static void removeRefreshListener(IRefreshSubscriberListener listener) {
		synchronized(listeners) {
			listeners.remove(listener);
		}
	}
	
	protected void notifyListeners(final boolean started, final IRefreshEvent event) {
		// Get a snapshot of the listeners so the list doesn't change while we're firing
		IRefreshSubscriberListener[] listenerArray;
		synchronized (listeners) {
			listenerArray = (IRefreshSubscriberListener[]) listeners.toArray(new IRefreshSubscriberListener[listeners.size()]);
		}
		// Notify each listener in a safe manner (i.e. so their exceptions don't kill us)
		for (int i = 0; i < listenerArray.length; i++) {
			IRefreshSubscriberListener listener = listenerArray[i];
			Notification notification = new Notification() {
				protected void notify(IRefreshSubscriberListener listener) {
					if(started) {
						listener.refreshStarted(event);
					} else {
						listener.refreshDone(event);
					}
				}
			};
			notification.run(listener);
		}
	}
}