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
package org.eclipse.team.internal.ccvs.ui.wizards;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.team.core.synchronize.FastSyncInfoFilter;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.FastSyncInfoFilter.SyncInfoDirectionFilter;
import org.eclipse.team.internal.ccvs.ui.actions.IgnoreAction;
import org.eclipse.team.internal.ccvs.ui.subscriber.CVSActionDelegateWrapper;
import org.eclipse.team.internal.ccvs.ui.subscriber.CVSParticipantAction;
import org.eclipse.team.internal.ccvs.ui.subscriber.WorkspaceCommitOperation;
import org.eclipse.team.ui.synchronize.ISynchronizeModelElement;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.SynchronizeModelOperation;
import org.eclipse.team.ui.synchronize.SynchronizePageActionGroup;
import org.eclipse.ui.IWorkbenchPart;

/**
 * Adviser used to add toolbar buttons to the last page of the sharing wizard.
 */
public class SharingWizardPageActionGroup extends SynchronizePageActionGroup {
	
	public static final String ACTION_GROUP = "cvs_sharing_page_actions"; //$NON-NLS-1$

	private SharingCommitAction commitAction;
	
	/**
	 * Custom commit that includes outgoing and conflicting.
	 */
	class SharingCommitAction extends CVSParticipantAction {
		protected SharingCommitAction(ISynchronizePageConfiguration configuration) {
			super(configuration);
		}
		protected void initialize(ISynchronizePageConfiguration configuration) {
			// Override to avoid being registered as a selection listener
		}
		protected String getBundleKeyPrefix() {
			return "SharingCommitAction."; //$NON-NLS-1$
		}
		protected FastSyncInfoFilter getSyncInfoFilter() {
			return new SyncInfoDirectionFilter(new int[] {SyncInfo.CONFLICTING, SyncInfo.OUTGOING});
		}
		protected SynchronizeModelOperation getSubscriberOperation(IWorkbenchPart part, IDiffElement[] elements) {
			return new WorkspaceCommitOperation(part, elements, true /* override */);
		}
		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.subscriber.SynchronizeViewerAdvisor#initializeActions(org.eclipse.jface.viewers.StructuredViewer)
	 */
	public void initialize(ISynchronizePageConfiguration configuration) {
		super.initialize(configuration);
		configuration.addMenuGroup(ISynchronizePageConfiguration.P_TOOLBAR_MENU, ACTION_GROUP);
		
		commitAction = new SharingCommitAction(configuration);
		appendToGroup(
				ISynchronizePageConfiguration.P_TOOLBAR_MENU, 
				ACTION_GROUP,
				commitAction);
	
		appendToGroup(
				ISynchronizePageConfiguration.P_TOOLBAR_MENU, 
				ACTION_GROUP,
				new CVSActionDelegateWrapper(new IgnoreAction(), configuration){ 
					protected String getBundleKeyPrefix() {
						return "SharingWizardIgnore."; //$NON-NLS-1$
					}
				});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.presentation.ISynchronizeModelChangeListener#inputChanged(org.eclipse.team.ui.synchronize.presentation.SynchronizeModelProvider)
	 */
	public void modelChanged(final ISynchronizeModelElement root) {
		commitAction.setSelection(root);
	}
}
