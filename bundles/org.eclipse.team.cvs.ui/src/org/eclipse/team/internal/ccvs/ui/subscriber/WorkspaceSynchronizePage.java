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
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.jface.action.*;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.team.ui.synchronize.actions.DirectionFilterActionGroup;
import org.eclipse.ui.IActionBars;

public class WorkspaceSynchronizePage extends CVSSynchronizeViewPage {

	private DirectionFilterActionGroup modes;

	private Action commitToolbar;
	private Action updateToolbar;

	public WorkspaceSynchronizePage(TeamSubscriberParticipant page, ISynchronizeView view, ITeamSubscriberSyncInfoSets input) {
		super(page, view, input);
		modes = new DirectionFilterActionGroup(getParticipant(), TeamSubscriberParticipant.ALL_MODES);

		commitToolbar = new CVSActionDelegate(new SubscriberCommitAction());
		updateToolbar = new CVSActionDelegate(new WorkspaceUpdateAction());

		Utils.initAction(commitToolbar, "action.SynchronizeViewCommit.", Policy.getBundle()); //$NON-NLS-1$
		Utils.initAction(updateToolbar, "action.SynchronizeViewUpdate.", Policy.getBundle()); //$NON-NLS-1$
		
		// force enablement to be update on action delegates
		//syncSetChanged(null);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#setActionBars(org.eclipse.ui.IActionBars)
	 */
	public void setActionBars(IActionBars actionBars) {
		super.setActionBars(actionBars);
		IToolBarManager toolbar = actionBars.getToolBarManager();
		modes.fillToolBar(toolbar);
		toolbar.add(new Separator());
		toolbar.add(updateToolbar);
		toolbar.add(commitToolbar);
	}
}