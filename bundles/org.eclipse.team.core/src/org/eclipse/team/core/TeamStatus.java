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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Status;

public class TeamStatus extends Status implements ITeamStatus {

	private IResource resource;

	/**
	 * Create a new status object.
	 * @param severity the severity; one of <code>OK</code>,
	 *   <code>ERROR</code>, <code>INFO</code>, or <code>WARNING</code>
	 * @param pluginId the unique identifier of the relevant plug-in
	 * @param code the plug-in-specific status code, or <code>OK</code>
	 * @param message a human-readable message, localized to the
	 *    current locale
	 * @param exception a low-level exception, or <code>null</code> if not
	 *    applicable 
	 * @param resource the resource asociated with the exception
	 */
	public TeamStatus(int severity, String pluginId, int code, String message, Throwable exception, IResource resource) {
		super(severity, pluginId, code, message, exception);
		this.resource = resource;
	}
	
	/**
	 * Return the resource associated with this status.
	 * @return Returns the resource.
	 */
	public IResource getResource() {
		return resource;
	}
}