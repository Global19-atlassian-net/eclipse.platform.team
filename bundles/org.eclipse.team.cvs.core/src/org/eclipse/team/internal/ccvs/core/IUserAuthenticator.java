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
package org.eclipse.team.internal.ccvs.core;

/**
 * IUserAuthenticators are used to ensure that the user
 * is validated for access to a given repository.  The
 * user is prompted for a username and password as
 * appropriate for the given repository type.
 */
public interface IUserAuthenticator {
	
	/**
	 * Button id for an "Ok" button (value 0).
	 */
	public int OK_ID = 0;

	/**
	 * Button id for a "Cancel" button (value 1).
	 */
	public int CANCEL_ID = 1;

	/**
	 * Button id for a "Yes" button (value 2).
	 */
	public int YES_ID = 2;

	/**
	 * Button id for a "No" button (value 3).
	 */
	public int NO_ID = 3;
	
	/**
	 * 	Constant for a prompt with no type (value 0).
	 */
	public final static int NONE = 0;

	/**
	 * Constant for an error prompt (value 1).
	 */
	public final static int ERROR = 1;

	/**
	 * 	Constant for an information prompt (value 2).
	 */
	public final static int INFORMATION = 2;

	/**
	 * 	Constant for a question prompt (value 3).
	 */
	public final static int QUESTION = 3;

	/**
	 * 	Constant for a warning dialog (value 4).
	 */
	public final static int WARNING = 4;	
	
	/**
	 * Authenticates the user for access to a given repository.
	 * The obtained values for user name and password will be placed
	 * into the supplied user info object. Implementors are allowed to
	 * save user names and passwords. The user should be prompted for
	 * user name and password if there is no saved one, or if <code>retry</code>
	 * is <code>true</code>.
	 *
	 * @param location The repository location to authenticate the user for.
	 * @param info The object to place user validation information into.
	 * @param retry <code>true</code> if a previous attempt to log in failed.
	 * @param message An optional message to display if, e.g., previous authentication failed.
	 * @return true if the validation was successful, and false otherwise.
	 */
	public void promptForUserInfo(ICVSRepositoryLocation location, IUserInfo userInfo, String message) throws CVSException;
	
	/**
	 * Prompts the authenticator for additional information regarding this authentication 
	 * request. A default implementation of this method should return the <code>defaultResponse</code>,
	 * whereas alternate implementations could prompt the user with a dialog.
	 * 
	 * @param location the repository location for this authentication
	 * @param promptType one of the following values:
	 * <ul>
	 *	<li> <code>NONE</code> for a unspecified prompt type </li>
	 *	<li> <code>ERROR</code> for an error prompt </li>
	 *	<li> <code>INFORMATION</code> for an information prompt </li>
	 * 	<li> <code>QUESTION </code> for a question prompt </li>
	 *	<li> <code>WARNING</code> for a warning prompt </li>
	 * </ul>
	 * @param title the prompt title that could be displayed to the user
	 * @param message the prompt
	 * @param promptResponses the possible responses to the prompt
	 * @param defaultResponse the default response to the prompt
	 * @return the response to the prompt
	 * 
	 * @since 3.0
	 */
	public int prompt(ICVSRepositoryLocation location, int promptType, String title, String message, int[] promptResponses, int defaultResponseIndex);
}