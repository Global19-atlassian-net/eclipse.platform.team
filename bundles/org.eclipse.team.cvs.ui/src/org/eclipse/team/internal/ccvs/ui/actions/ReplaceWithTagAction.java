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
package org.eclipse.team.internal.ccvs.ui.actions;
 
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.operations.ReplaceOperation;

/**
 * Action for replace with tag.
 */
public class ReplaceWithTagAction extends WorkspaceAction {
	/*
	 * Method declared on IActionDelegate.
	 */
	public void execute(IAction action) throws InterruptedException, InvocationTargetException {
		
		// Setup the holders
		final IResource[][] resources = new IResource[][] {null};
		final CVSTag[] tag = new CVSTag[] {null};
		final boolean[] recurse = new boolean[] {true};
		
		// Show a busy cursor while display the tag selection dialog
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
				
				try {
					resources[0] =
						checkOverwriteOfDirtyResources(
							getSelectedResources(),
							null /* no progress just a busy cursor for now */);
				} catch (CVSException e) {
					throw new InvocationTargetException(e);
				} 
				if(resources[0].length == 0) {
					// nothing to do
					return;
				}
				// show the tags for the projects of the selected resources
				IProject[] projects = new IProject[resources[0].length];
				for (int i = 0; i < resources[0].length; i++) {
					projects[i] = resources[0][i].getProject();
				}
				TagSelectionDialog dialog = new TagSelectionDialog(getShell(), projects, 
					Policy.bind("ReplaceWithTagAction.message"), //$NON-NLS-1$
					Policy.bind("TagSelectionDialog.Select_a_Tag_1"), //$NON-NLS-1$
					TagSelectionDialog.INCLUDE_ALL_TAGS, 
					true, /*show recurse*/
					IHelpContextIds.REPLACE_TAG_SELECTION_DIALOG); //$NON-NLS-1$
				dialog.setBlockOnOpen(true);
				if (dialog.open() == Dialog.CANCEL) {
					return;
				}
				tag[0] = dialog.getResult();
				recurse[0] = dialog.getRecursive();
				
				// For non-projects determine if the tag being loaded is the same as the resource's parent
				// If it's not, warn the user that they will have strange sync behavior
				try {
					if(!CVSAction.checkForMixingTags(getShell(), resources[0], tag[0])) {
						tag[0] = null;
						return;
					}
				} catch (CVSException e) {
					throw new InvocationTargetException(e);
				}
			}
		}, false /* cancelable */, PROGRESS_BUSYCURSOR);			 //$NON-NLS-1$
		
		if (resources[0] == null || resources[0].length == 0 || tag[0] == null) return;
		
		try {
			// Peform the replace in the background
			new ReplaceOperation(getShell(), resources[0], tag[0], recurse[0]).run();
		} catch (CVSException e) {
			throw new InvocationTargetException(e);
		}
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.ui.actions.CVSAction#getErrorTitle()
	 */
	protected String getErrorTitle() {
		return Policy.bind("ReplaceWithTagAction.replace"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.actions.WorkspaceAction#isEnabledForNonExistantResources()
	 */
	protected boolean isEnabledForNonExistantResources() {
		return true;
	}
	
}
