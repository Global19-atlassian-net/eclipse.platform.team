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
package org.eclipse.team.internal.ui.synchronize.actions;

import org.eclipse.team.ui.synchronize.ISynchronizeParticipantReference;
import org.eclipse.team.ui.synchronize.ISynchronizeView;

public class ComparePageDropDownAction extends SynchronizePageDropDownAction {
	
	public ComparePageDropDownAction(ISynchronizeView view) {
		super(view);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.actions.SynchronizePageDropDownAction#select(org.eclipse.team.ui.synchronize.ISynchronizeParticipantReference)
	 */
	protected boolean select(ISynchronizeParticipantReference ref) {
		return ! ref.getDescriptor().isStatic();
	}
}
