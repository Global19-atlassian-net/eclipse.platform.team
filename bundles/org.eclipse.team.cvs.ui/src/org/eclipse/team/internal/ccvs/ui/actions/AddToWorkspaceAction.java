/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.client.Checkout;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ui.PromptingDialog;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

/**
 * Add some remote resources to the workspace. Current implementation:
 * -Works only for remote folders
 * -Does not prompt for project name; uses folder name instead
 */
public class AddToWorkspaceAction extends CheckoutAction {
	/**
	 * Returns the selected remote folders. 
	 * Remove any module aliases as they may cause problems on checkout this way
	 */
	protected ICVSRemoteFolder[] getSelectedRemoteFolders() {
		ICVSRemoteFolder[] allFolders = super.getSelectedRemoteFolders();
		if (allFolders.length == 0) return allFolders;
		ArrayList resources = new ArrayList();
		for (int i = 0; i < allFolders.length; i++) {
			ICVSRemoteFolder folder = allFolders[i];
			if (!Checkout.ALIAS.isElementOf(folder.getLocalOptions())) {
				resources.add(folder);
			}
		}
		return (ICVSRemoteFolder[])resources.toArray(new ICVSRemoteFolder[resources.size()]);
	}

	/*
	 * @see CVSAction#execute()
	 */
	public void execute(IAction action) throws InvocationTargetException, InterruptedException {
		run(new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
				try {
					ICVSRemoteFolder[] folders = getSelectedRemoteFolders();
							
					List targetProjects = new ArrayList();
					Map targetFolders = new HashMap();
					for (int i = 0; i < folders.length; i++) {
						String name = folders[i].getName();
						IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
						targetFolders.put(name, folders[i]);
						targetProjects.add(project);
					}
					
					IResource[] projects = (IResource[]) targetProjects.toArray(new IResource[targetProjects.size()]);
					
					PromptingDialog prompt = new PromptingDialog(getShell(), projects, 
																  getOverwriteLocalAndFileSystemPrompt(), 
																  Policy.bind("ReplaceWithAction.confirmOverwrite"));//$NON-NLS-1$
					projects = prompt.promptForMultiple();
															
					monitor.beginTask(null, 100);
					if (projects.length != 0) {
						IProject[] localFolders = new IProject[projects.length];
						ICVSRemoteFolder[] remoteFolders = new ICVSRemoteFolder[projects.length];
						for (int i = 0; i < projects.length; i++) {
							localFolders[i] = (IProject)projects[i];
							remoteFolders[i] = (ICVSRemoteFolder)targetFolders.get(projects[i].getName());
						}
						
						monitor.setTaskName(getTaskName(remoteFolders));						
						CVSProviderPlugin.getProvider().checkout(remoteFolders, localFolders, Policy.subMonitorFor(monitor, 100));
					}
				} catch (TeamException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		}, true /* cancelable */, this.PROGRESS_DIALOG);
	}
		
	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		ICVSRemoteFolder[] resources = getSelectedRemoteFolders();
		if (resources.length == 0) return false;
		for (int i = 0; i < resources.length; i++) {
			if (resources[i] instanceof ICVSRepositoryLocation) return false;
		}
		return true;
	}
	/**
	 * @see org.eclipse.team.internal.ccvs.ui.actions.CVSAction#getErrorTitle()
	 */
	protected String getErrorTitle() {
		return Policy.bind("AddToWorkspaceAction.checkoutFailed"); //$NON-NLS-1$
	}

}