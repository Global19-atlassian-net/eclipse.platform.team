/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ui.target;

import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.target.TargetManager;
import org.eclipse.team.core.target.TargetProvider;
import org.eclipse.team.internal.core.InfiniteSubProgressMonitor;
import org.eclipse.team.internal.core.TeamPlugin;
import org.eclipse.team.internal.ui.IPromptCondition;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.PromptingDialog;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

public class UploadAction extends TargetAction {

	/**
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		IResource[] resources = getSelectedResources();
		if (resources.length == 0) return false;
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			try {
				TargetProvider provider = TargetManager.getProvider(resource.getProject());			
				if(provider == null)
					return false;
				if(! provider.canPut(resource))
					return false;	//if one can't don't allow for any
				// Don't want to go though the resources deeply to see if there are dirty children
			} catch (TeamException e) {
				TeamPlugin.log(IStatus.ERROR, Policy.bind("UploadAction.Exception_getting_provider"), e); //$NON-NLS-1$
				return false;
			}
		}
		return true;
	}

	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		run(new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
				try {
					Hashtable table = getTargetProviderMapping();
					Set keySet = table.keySet();
					monitor.beginTask("", keySet.size() * 1000); //$NON-NLS-1$
					Iterator iterator = keySet.iterator();
					while (iterator.hasNext()) {					
						IProgressMonitor subMonitor = new InfiniteSubProgressMonitor(monitor, 1024);
						final TargetProvider provider = (TargetProvider)iterator.next();
						subMonitor.setTaskName(Policy.bind("UploadAction.working", provider.getURL().toExternalForm()));  //$NON-NLS-1$
						
						IResource[] providerResources = (IResource[])((List)table.get(provider)).toArray(new IResource[0]);
						IResource[] outgoingChanges = findResourcesWithOutgoingChanges(providerResources);
						if (outgoingChanges.length == 0) {
							getShell().getDisplay().syncExec(
								new Runnable() {
									public void run() {
										MessageDialog.openInformation(getShell(), 
											Policy.bind("UploadAction.noDirtyTitle"), 
											Policy.bind("UploadAction.noDirtyMessage"));
									}
								});
							return;
						};
						
						// Prompt for any outgoing deletions
						PromptingDialog prompt = new PromptingDialog(
							getShell(), 
							outgoingChanges,
							new IPromptCondition() {
								public boolean needsPrompt(IResource resource) {
									return ! resource.exists();
								}
								public String promptMessage(IResource resource) {
									return Policy.bind("UploadAction.confirmFileDeletionMessage", resource.getFullPath().toString());
								}
							}, 
							Policy.bind("UploadAction.confirmDeletionTitle"));//$NON-NLS-1$
						
						// Put the resources that were selected
						provider.put(prompt.promptForMultiple(), subMonitor);
					}
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} catch (TeamException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		}, Policy.bind("UploadAction.problemMessage"), this.PROGRESS_DIALOG); //$NON-NLS-1$
	}
}
