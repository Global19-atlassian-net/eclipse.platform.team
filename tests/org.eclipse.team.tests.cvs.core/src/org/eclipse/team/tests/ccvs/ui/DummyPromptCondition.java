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
package org.eclipse.team.tests.ccvs.ui;

import org.eclipse.core.resources.IResource;
import org.eclipse.team.internal.ui.dialogs.IPromptCondition;

public class DummyPromptCondition implements IPromptCondition {
	public boolean needsPrompt(IResource resource) {
		return false;
	}
	public String promptMessage(IResource resource) {
		// this method should never be called
		return resource.getName();
	}
}
