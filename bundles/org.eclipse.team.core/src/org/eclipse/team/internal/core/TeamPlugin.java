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
package org.eclipse.team.internal.core;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.core.Team;
import org.eclipse.team.core.TeamException;

/**
 * <code>TeamPlugin</code> is the plug-in runtime class for the Team 
 * resource management plugin.
 * <p>
 * 
 * @see Team
 * @see RepositoryProvider
 * 
 * @since 2.0
 */
final public class TeamPlugin extends Plugin {

	// The id of the core team plug-in
	public static final String ID = "org.eclipse.team.core"; //$NON-NLS-1$

	// The id of the providers extension point
	public static final String PROVIDER_EXTENSION = "repository-provider-type"; //$NON-NLS-1$
	
	// The id of the file types extension point
	public static final String FILE_TYPES_EXTENSION = "fileTypes"; //$NON-NLS-1$
	
	// The id of the global ignore extension point
	public static final String IGNORE_EXTENSION = "ignore"; //$NON-NLS-1$
	// The id of the project set extension point
	public static final String PROJECT_SET_EXTENSION = "projectSets"; //$NON-NLS-1$
	// The id of the targets extension point
	public static final String REPOSITORY_EXTENSION = "repository"; //$NON-NLS-1$


	// The one and only plug-in instance
	private static TeamPlugin plugin;	

	/** 
	 * Constructs a plug-in runtime class for the given plug-in descriptor.
	 */
	public TeamPlugin(IPluginDescriptor pluginDescriptor) {
		super(pluginDescriptor);
		plugin = this;
	}
	
	/**
	 * @see Plugin#startup()
	 */
	public void startup() throws CoreException {
		Policy.localize("org.eclipse.team.internal.core.messages"); //$NON-NLS-1$
		Team.startup();
	}
	
	/**
	 * @see Plugin#shutdown()
	 */
	public void shutdown() {
		Team.shutdown();
	}
	
	/**
	 * Returns the Team plug-in.
	 *
	 * @return the single instance of this plug-in runtime class
	 */
	public static TeamPlugin getPlugin() {
		return plugin;
	}
	
	/**
	 * Log the given exception alloing with the provided message and severity indicator
	 */
	public static void log(int severity, String message, Throwable e) {
		plugin.getLog().log(new Status(severity, ID, 0, message, e));
	}
	
	/**
	 * Log the given CoreException in a manner that will include the stacktrace of
	 * the exception in the log.
	 */
	public static void log(CoreException e) {
		log(e.getStatus().getSeverity(), e.getMessage(), e);
	}
	
	/*
	 * Static helper methods for creating exceptions
	 */
	public static TeamException wrapException(Exception e) {
		return new TeamException(new Status(IStatus.ERROR, ID, 0, e.getMessage() != null ? e.getMessage() : "",	e)); //$NON-NLS-1$
	}
	
	public static TeamException wrapException(CoreException e) {
		IStatus status = e.getStatus();
		return new TeamException(new Status(status.getSeverity(), ID, status.getCode(), status.getMessage(), e));
	}

}
