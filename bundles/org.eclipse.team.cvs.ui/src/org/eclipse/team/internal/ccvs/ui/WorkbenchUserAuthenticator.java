/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Atsuhiko Yamanaka, JCraft,Inc. - implementation of promptForKeyboradInteractive
 * *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.IFileTypeInfo;
import org.eclipse.team.core.IIgnoreInfo;
import org.eclipse.team.core.Team;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.IUserAuthenticator;
import org.eclipse.team.internal.ccvs.core.IUserInfo;

/**
 * An authenticator that prompts the user for authentication info,
 * and stores the results in the Platform's authentication keyring.
 */
public class WorkbenchUserAuthenticator implements IUserAuthenticator {
	public static boolean USE_ALTERNATE_PROMPTER = false;
	
	/**
	 * WorkbenchUserAuthenticator constructor.
	 */
	public WorkbenchUserAuthenticator() {
		super();
		// Initialize USE_ALTERNATE_PROMPTER
		IIgnoreInfo[] ignores = Team.getAllIgnores();
		boolean found = false;
		for (int i = 0; i < ignores.length; i++) {
			if (ignores[i].getPattern().equals("*.notes")) { //$NON-NLS-1$
				found = true;
			}
		}
		if (!found) return;
		IFileTypeInfo[] types = Team.getAllTypes();
		for (int i = 0; i < types.length; i++) {
			if (types[i].getExtension().equals("notes")) { //$NON-NLS-1$
				USE_ALTERNATE_PROMPTER = true;
				return;
			}
		}
		USE_ALTERNATE_PROMPTER = false;
	}
	/**
	 * @see IUserAuthenticator#authenticateUser
	 */
	public void promptForUserInfo(final ICVSRepositoryLocation location, final IUserInfo userinfo, final String message) throws CVSException {
		if (!userinfo.isUsernameMutable() && USE_ALTERNATE_PROMPTER) {
			alternatePromptForUserInfo(userinfo);
			return;
		}
		// ask the user for a password
		final String[] result = new String[2];
		Display display = Display.getCurrent();
		if (display != null) {
			promptForPassword(location, userinfo.getUsername(), message, userinfo.isUsernameMutable(), result);
		} else {
			// sync exec in default thread
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					promptForPassword(location, userinfo.getUsername(), message, userinfo.isUsernameMutable(), result);
				}
			});
		}
			
		if (result[0] == null) {
			throw new OperationCanceledException(Policy.bind("WorkbenchUserAuthenticator.cancelled")); //$NON-NLS-1$
		}
		
		if (userinfo.isUsernameMutable())
			userinfo.setUsername(result[0]);
		userinfo.setPassword(result[1]);
	}
	
	/**
	 * Asks the user to enter a password. Places the
	 * results in the supplied string[].  result[0] must
	 * contain the username, result[1] must contain the password.
	 * If the user canceled, both values must be zero.
	 * 
	 * @param location  the location to obtain the password for
	 * @param username  the username
	 * @param message  a message to display to the user
	 * @param userMutable  whether the user can be changed in the dialog
	 * @param result  a String array of length two in which to put the result
	 */
	private void promptForPassword(final ICVSRepositoryLocation location, final String username, final String message, final boolean userMutable, final String[] result) {
		Display display = Display.getCurrent();
		Shell shell = new Shell(display);
		String domain = location == null ? null : location.getLocation();
		UserValidationDialog dialog = new UserValidationDialog(shell, domain, (username==null)?"":username, message);//$NON-NLS-1$
		dialog.setUsernameMutable(userMutable);
		dialog.open();
		shell.dispose();
		
		result[0] = dialog.getUsername();
		result[1] = dialog.getPassword();
	}

	/**
	 * Asks the user to enter values. 
	 * 
	 * @param location  the location to obtain the password for
	 * @param destication the location
	 * @param name the name
	 * @param instruction the instruction
	 * @param prompt the titles for textfields
	 * @param echo '*' should be used or not
	 * @param result the entered values, or null if user canceled.
	 */
	public String[] promptForKeyboradInteractive(final ICVSRepositoryLocation location,
						     final String destination,
						     final String name,
						     final String instruction,
						     final String[] prompt,
						     final boolean[] echo) throws CVSException {
	    final String[][] result = new String[1][];
	    Display display = Display.getCurrent();
	    if (display != null) {
		result[0]=_promptForUserInteractive(location, destination, name, instruction, prompt, echo);
	    } 
	    else {
	    	// sync exec in default thread
	    	Display.getDefault().syncExec(new Runnable() {
	    		public void run() {
	    			result[0]=_promptForUserInteractive(location, destination, name, instruction, prompt, echo);
	    		}
		    });
	    }
	    return result[0];
	}

	private String[] _promptForUserInteractive(final ICVSRepositoryLocation location, 
						   final String destination,
						   final String name,
						   final String instruction,
						   final String[] prompt,
						   final boolean[] echo) {
		Display display = Display.getCurrent();
		Shell shell = new Shell(display);
		String domain = location == null ? null : location.getLocation();
		KeyboardInteractiveDialog dialog = new KeyboardInteractiveDialog(shell, 
										 domain,
										 destination,
										 name,
										 instruction,
										 prompt,
										 echo);
		dialog.open();
		shell.dispose();
		return dialog.getResult();
	}
	
	/**
	 * Special alternate prompting. Returns the password. Username must be fixed.
	 */
	private String alternatePromptForPassword(final String username) {
		Display display = Display.getCurrent();
		Shell shell = new Shell(display);
		AlternateUserValidationDialog dialog = new AlternateUserValidationDialog(shell, (username == null) ? "" : username); //$NON-NLS-1$
		dialog.setUsername(username);
		int result = dialog.open();
		shell.dispose();
		if (result == Dialog.CANCEL) return null;
		return dialog.getPassword();
	}
	
	/**
	 * Special alternate prompting.
	 */
	public void alternatePromptForUserInfo(final IUserInfo userinfo) throws CVSException {
		// ask the user for a password
		final String[] result = new String[1];
		Display display = Display.getCurrent();
		if (display != null) {
			result[0] = alternatePromptForPassword(userinfo.getUsername());
		} else {
			// sync exec in default thread
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					result[0] = alternatePromptForPassword(userinfo.getUsername());
				}
			});
		}
			
		if (result[0] == null) {
			throw new OperationCanceledException(Policy.bind("WorkbenchUserAuthenticator.The_operation_was_canceled_by_the_user_1")); //$NON-NLS-1$
		}
		
		userinfo.setPassword(result[0]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IUserAuthenticator#prompt(org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation, int, java.lang.String, java.lang.String, int[], int)
	 */
	public int prompt(ICVSRepositoryLocation location, final int promptType, final String title, final String message, final int[] promptResponses, final int defaultResponse) {
		final Display display = CVSUIPlugin.getStandardDisplay();
		final int[] retval = new int[1];
		final String[] buttons = new String[promptResponses.length];
		for (int i = 0; i < promptResponses.length; i++) {
			int prompt = promptResponses[i];
			switch(prompt) { 
				case IUserAuthenticator.OK_ID: buttons[i] = IDialogConstants.OK_LABEL; break;
				case IUserAuthenticator.CANCEL_ID: buttons[i] = IDialogConstants.CANCEL_LABEL; break;
				case IUserAuthenticator.NO_ID: buttons[i] = IDialogConstants.NO_LABEL; break;
				case IUserAuthenticator.YES_ID: buttons[i] = IDialogConstants.YES_LABEL; break;
			}
		}
		
		display.syncExec(new Runnable() {
			public void run() {
				final MessageDialog dialog = new MessageDialog(
						new Shell(display),
						title,
						null /* title image */,
						message,
						promptType,
						buttons,
						1
				);				
				retval[0] = dialog.open();
			}
		});
		return retval[0];
	}
}
