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
package org.eclipse.team.core;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.internal.core.TeamPlugin;

/**
 * This exception is thrown by the team provider API.  It represents a failure in an API call.
 * Since some API calls take multiple arguments, the exception is capable of returning multiple
 * statuses.  The API definition determinies if the exception represents a single or multiple status
 * response; this can also be tested on the exception instance itself.
 * <p>
 * To determine the exact cause of the failure the caller should look at each status in detail.</p>
 * 
 * @since 2.0
 */
public class TeamException extends Exception {

	protected IStatus status = null;
	
	// The operation completed successfully.
	public static final int OK = 0;

	// The operation failed because the resource is not checked-in.
	public static final int NOT_CHECKED_IN = -1;

	// The operation failed because the resource is not checked-out.
	public static final int NOT_CHECKED_OUT = -2;

	// The corresponding remote resource no longer exists or was never created.
	public static final int NO_REMOTE_RESOURCE = -3;

	// The provider suffered an IO failure, the operation may be retried.
	public static final int IO_FAILED = -4;

	// The user is not authorized to execute the attempted operation.
	public static final int NOT_AUTHORIZED = -5;

	// The provider was unable to complete the operation for an unspecified reason.
	public static final int UNABLE = -6;
	
	// The operation cannot be performed due to a conflict with other work.
	public static final int CONFLICT = -7;

	/**
	 * Single status constructor for a <code>TeamProviderException</code>.
	 * @param status
	 */
	public TeamException(IStatus status) {
		super(status.getMessage());	
		this.status = status;
	}

	/**
	 * Answer the single status resulting from the attempted API call.
	 * 
	 * @return IStatus the single status of the result, or <code>null</code> if this is a multi-status
	 * response.
	 */
	public IStatus getStatus() {
		return status;
	}
	
	/**
	 * Method TeamException.
	 * @param message
	 * @param e
	 */
	public TeamException(String message, Exception e) {
		super(e.getMessage());
		this.status = new Status(IStatus.ERROR, TeamPlugin.ID, 0, message, e);
	}
	
	/**
	 * @see java.lang.Throwable#Throwable(String)
	 */
	public TeamException(String message) {
		super(message);
		this.status = new Status(IStatus.ERROR, TeamPlugin.ID, 0, message, null);
	}
	
	public TeamException(CoreException e) {
		super(e.getMessage());
		IStatus status = e.getStatus();
		// If the exception is not a multi-status, wrap the exception to keep the original stack trace.
		// If the exception is a multi-status, the interesting stack traces should be in the childen already
		if ( ! status.isMultiStatus()) {
			status = new Status(status.getSeverity(), status.getPlugin(), status.getCode(), status.getMessage(), e);
		}
		this.status = status;
	}
}
