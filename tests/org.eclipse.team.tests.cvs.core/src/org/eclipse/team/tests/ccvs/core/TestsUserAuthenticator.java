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
package org.eclipse.team.tests.ccvs.core;

import org.eclipse.team.internal.ccvs.core.*;

/**
 * A test authenticator that provide defaults for all methods.
 */
public class TestsUserAuthenticator implements IUserAuthenticator {

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IUserAuthenticator#promptForUserInfo(org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation, org.eclipse.team.internal.ccvs.core.IUserInfo, java.lang.String)
	 */
	public void promptForUserInfo(ICVSRepositoryLocation location, IUserInfo userInfo, String message) throws CVSException {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IUserAuthenticator#prompt(org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation, int, java.lang.String, java.lang.String, int[], int)
	 */
	public int prompt(ICVSRepositoryLocation location, int promptType, String title, String message, int[] promptResponses, int defaultResponseIndex) {
		return defaultResponseIndex;
	}
}
