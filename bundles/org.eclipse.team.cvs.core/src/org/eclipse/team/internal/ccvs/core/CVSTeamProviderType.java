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

import org.eclipse.team.core.ProjectSetCapability;
import org.eclipse.team.core.RepositoryProviderType;


/**
 * This class represents the CVS Provider's capabilities in the absence of a
 * particular project.
 */

public class CVSTeamProviderType extends RepositoryProviderType {
	
	/**
	 * @see org.eclipse.team.core.RepositoryProviderType#supportsProjectSetImportRelocation()
	 */
	public boolean supportsProjectSetImportRelocation() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.RepositoryProviderType#getProjectSetCapability()
	 */
	public ProjectSetCapability getProjectSetCapability() {
		return new CVSProjectSetCapability();
	}
}
