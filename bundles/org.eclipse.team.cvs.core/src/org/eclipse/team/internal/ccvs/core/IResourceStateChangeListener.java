package org.eclipse.team.internal.ccvs.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.EventListener;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

/**
 * A resource state change listener is notified of changes to resources
 * regarding their team state. 
 * <p>
 * Clients may implement this interface.
 * </p>
 * @see ITeamManager#addResourceStateChangeListener(IResourceStateChangeListener)
 */
public interface IResourceStateChangeListener extends EventListener{
	
	/**
	 * Notifies this listener that some resource sync info state changes have
	 * already happened. For example, a resource's base revision may have
	 * changed. The resource tree is open for modification when this method is
	 * invoked, so markers can be created, etc.
	 * <p>
	 * Note: This method is called by the CVS core; it is not intended to be
	 * called directly by clients.
	 * </p>
	 *
	 * @param changedResources that have sync info state changes
	 * 
	 * [Note: The changed state event is purposely vague. For now it is only
	 * a hint to listeners that they should query the provider to determine the
	 * resources new sync info.]
	 */
	public void resourceSyncInfoChanged(IResource[] changedResources);
	
	/**
	 * Notifies this listener that the resource's have been modified. This
	 * doesn't necessarily mean that the resource state isModified. The listener
	 * must check the state.
	 * <p>
	 * Note: This method is called by CVS team core; it is not intended to be
	 * called directly by clients.
	 * </p>
	 *
	 * @param changedResources that have changed state
	 * @param changeType the type of state change.
	 */
	public void resourceModified(IResource[] changedResources);
	
	/**
	 * Notifies this listener that the project has just been configured
	 * to be a CVS project.
	 * <p>
	 * Note: This method is called by the CVS core; it is not intended to be
	 * called directly by clients.
	 * </p>
	 *
	 * @param project The project that has just been configured
	 */
	public void projectConfigured(IProject project);
	
	/**
	 * Notifies this listener that the project has just been deconfigured
	 * and no longer has the CVS nature.
	 * <p>
	 * Note: This method is called by the CVS core; it is not intended to be
	 * called directly by clients.
	 * </p>
	 *
	 * @param project The project that has just been configured
	 */
	public void projectDeconfigured(IProject project);
	
}

