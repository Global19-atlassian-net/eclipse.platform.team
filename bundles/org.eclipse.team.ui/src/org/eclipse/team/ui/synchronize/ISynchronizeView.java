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
package org.eclipse.team.ui.synchronize;

import org.eclipse.ui.IViewPart;

/**
 * A view that displays synchronization participants that are registered with the
 * synchronize manager. This is essentially a generic container that allows
 * multiple {@link ISynchronizeParticipant} implementations to share the same
 * view. The only behavior provided by the view is a mechanism for switching 
 * between participants.
 * <p> 
 * Clients should not add viewActions to this view because they will be global
 * to all participants. Instead, add participant specific actions.
 * </p>
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 * @see ISynchronizeManager
 * @since 3.0
 */
public interface ISynchronizeView extends IViewPart {
	/**
	 * The id for this view
	 */
	public static final String VIEW_ID = "org.eclipse.team.sync.views.SynchronizeView"; //$NON-NLS-1$
	
	/**
	 * Displays the participant overview page.
	 */
	public void displayOverviewPage();
	
	/**
	 * Displays the given synchronize participant in the Synchronize View. This
	 * has no effect if this participant is already being displayed.
	 * 
	 * @param participant participant to be displayed, cannot be <code>null</code>
	 */
	public void display(ISynchronizeParticipant participant);
	
	/**
	 * Returns the participant currently being displayed in the Synchronize View
	 * or <code>null</code> if none.
	 *  
	 * @return the participant currently being displayed in the Synchronize View
	 * or <code>null</code> if none
	 */
	public ISynchronizeParticipant getParticipant();
}