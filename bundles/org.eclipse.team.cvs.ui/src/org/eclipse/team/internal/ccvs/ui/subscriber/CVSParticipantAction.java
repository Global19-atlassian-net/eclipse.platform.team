/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.SynchronizeModelAction;

/**
 * Superclass of CVS participant actions that uses the classname as the key
 * to access the text from the resource bundle
 */
public abstract class CVSParticipantAction extends SynchronizeModelAction {

	protected CVSParticipantAction(ISynchronizePageConfiguration configuration) {
		super(null, configuration);
		Utils.initAction(this, getBundleKeyPrefix(), Policy.getBundle());
	}
	
	protected CVSParticipantAction(ISynchronizePageConfiguration configuration, ISelectionProvider provider, String bundleKey) {
		super(null, configuration, provider);
		Utils.initAction(this, bundleKey, Policy.getBundle());
	}

	/**
	 * Return the key to the action text in the resource bundle.
	 * The default is the class name followed by a dot (.).
	 * @return the bundle key prefix
	 */
	protected String getBundleKeyPrefix() {
		String name = getClass().getName();
		int lastDot = name.lastIndexOf("."); //$NON-NLS-1$
		if (lastDot == -1) {
			return name;
		}
		return name.substring(lastDot + 1)  + "."; //$NON-NLS-1$
	}
}
