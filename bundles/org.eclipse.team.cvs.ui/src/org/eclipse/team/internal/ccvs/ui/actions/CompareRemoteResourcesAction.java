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

import org.eclipse.compare.CompareUI;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.operations.RemoteCompareOperation;

/**
 * This action is used for comparing two arbitrary remote resources. This is
 * enabled in the repository explorer.
 */
public class CompareRemoteResourcesAction extends CVSAction {

	public void execute(IAction action) throws InvocationTargetException, InterruptedException {
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				ICVSRemoteResource[] editions = getSelectedRemoteResources();
				if (editions == null || editions.length != 2) {
					MessageDialog.openError(getShell(), Policy.bind("CompareRemoteResourcesAction.unableToCompare"), Policy.bind("CompareRemoteResourcesAction.selectTwoResources")); //$NON-NLS-1$ //$NON-NLS-2$
					return;
				}
				if (isSameFolder(editions)) {
					// Run the compare operation in the background
					try {
						new RemoteCompareOperation(null, editions[0], RemoteCompareOperation.getTag(editions[1]))
							.run();
					} catch (CVSException e) {
						throw new InvocationTargetException(e);
					} catch (InterruptedException e) {
						// Ignored
					}
				} else {
					ResourceEditionNode left = new ResourceEditionNode(editions[0]);
					ResourceEditionNode right = new ResourceEditionNode(editions[1]);
					CompareUI.openCompareEditorOnPage(
					  new CVSCompareEditorInput(left, right),
					  getTargetPage());
				}
			}
		}, false /* cancelable */, PROGRESS_BUSYCURSOR);
	}

	protected boolean isSameFolder(ICVSRemoteResource[] editions) {
		return editions[0].getRepository().equals(editions[1].getRepository())
				&& editions[0].getRepositoryRelativePath().equals(editions[1].getRepositoryRelativePath());
	}
	
	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		ICVSRemoteResource[] resources = getSelectedRemoteResources();
		if (resources.length != 2) return false;
		if (resources[0].isContainer() != resources[1].isContainer()) return false;
		// Don't allow comparisons of two unrelated remote projects
		return !resources[0].isContainer() || isSameFolder(resources);
	}

}
