package org.eclipse.team.internal.ccvs.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.core.TeamException;

/**
 * A checked expection representing a failure in the CVS plugin.
 * <p>
 * CVS exceptions contain a status object describing the cause of 
 * the exception.
 * </p>
 *
 * @see IStatus
 */
public class CVSException extends TeamException {

	/*
	 * Helpers for creating CVS exceptions
	 */
	public CVSException(int severity, int code, String message, Throwable e) {
		super(new CVSStatus(severity, code, message, null));
	}
	
	public CVSException(int severity, int code, String message) {
		this(severity, code, message, null);
	}

	public CVSException(String message) {
		super(new CVSStatus(IStatus.ERROR, UNABLE, message, null));
	}

	public CVSException(String message, Throwable e) {
		this(IStatus.ERROR, UNABLE, message, e);
	}

	public CVSException(IStatus status) {
		super(status);
	}

	/*
	 * Static helper methods for creating exceptions
	 */
	public static CVSException wrapException(
		IResource resource,
		String message,
		IOException e) {
		// NOTE: we should record the resource somehow
		// We should also inlcude the IO message
		return new CVSException(new CVSStatus(IStatus.ERROR, IO_FAILED, message, e));
	}

	/*
	 * Static helper methods for creating exceptions
	 */
	public static CVSException wrapException(IResource resource, String message, CoreException e) {
		return new CVSException(new CVSStatus(IStatus.ERROR, e.getStatus().getCode(), message, e));
	}

	/*
	 * Static helper methods for creating exceptions
	 */
	public static CVSException wrapException(Exception e) {
		Throwable t = e;
		if (e instanceof InvocationTargetException) {
			Throwable target = ((InvocationTargetException) e).getTargetException();
			if (target instanceof CVSException) {
				return (CVSException) target;
			}
			t = target;
		}
		return new CVSException(new CVSStatus(IStatus.ERROR, UNABLE, t.getMessage() != null ? t.getMessage() : "",	t)); //$NON-NLS-1$
	}
	
	public static CVSException wrapException(CoreException e) {
		IStatus status = e.getStatus();
		// If the exception is not a multi-status, wrap the exception to keep the original stack trace.
		// If the exception is a maulit-status, the interesting stack traces should eb in the childen already
		if ( ! status.isMultiStatus()) {
			status = new CVSStatus(status.getSeverity(), status.getCode(), status.getMessage(), e);
		}
		return new CVSException(status);
	}
	
	/*
	 * Static helper methods for creating exceptions
	 */
	public static CVSException wrapException(TeamException e) {
		if (e instanceof CVSException)
			return (CVSException)e;
		else
			return new CVSException(e.getStatus());
	}
	
	public CoreException toCoreException() {
		IStatus status = getStatus();
		return new CoreException(new Status(status.getSeverity(), status.getPlugin(), 0, status.getMessage(), this));
	}
}