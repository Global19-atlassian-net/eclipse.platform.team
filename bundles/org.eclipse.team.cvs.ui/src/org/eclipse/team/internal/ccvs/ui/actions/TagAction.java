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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.CVSLightweightDecorator;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.TagAsVersionDialog;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.ui.IPromptCondition;
import org.eclipse.team.internal.ui.PromptingDialog;

/**
 * TagAction tags the selected resources with a version tag specified by the user.
 */
public class TagAction extends WorkspaceAction {
	// The previously remembered tag
	private static String previousTag = ""; //$NON-NLS-1$
	
	/**
	 * @see CVSAction#execute(IAction)
	 */
	public void execute(IAction action) throws InvocationTargetException, InterruptedException {
		
		// Prompt for any uncommitted changes
		PromptingDialog prompt = new PromptingDialog(getShell(), getSelectedResources(),
			getPromptCondition(), Policy.bind("TagAction.uncommittedChangesTitle"));//$NON-NLS-1$
		final IResource[] resources;
		try {
			 resources = prompt.promptForMultiple();
		} catch(InterruptedException e) {
			return;
		}
		if(resources.length == 0) {
			// nothing to do
			return;						
		}
		
		// Prompt for the tag name
		final String[] result = new String[1];
		getShell().getDisplay().syncExec(new Runnable() {
			public void run() {
				ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor(resources[0].getProject());
				result[0] = promptForTag(folder);
			}
		});
		if (result[0] == null) return;
		
		final RepositoryManager manager = CVSUIPlugin.getPlugin().getRepositoryManager();
		
		// Tag the local resources, divided by project/provider
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				Hashtable table = getProviderMapping(resources);
				Set keySet = table.keySet();
				monitor.beginTask(null, keySet.size() * 1000);
				Iterator iterator = keySet.iterator();
				
				while (iterator.hasNext()) {
					IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 1000);
					CVSTeamProvider provider = (CVSTeamProvider)iterator.next();
					List list = (List)table.get(provider);
					IResource[] providerResources = (IResource[])list.toArray(new IResource[list.size()]);
					CVSTag tag = new CVSTag(result[0], CVSTag.VERSION);
					try {
						addStatus(provider.tag(providerResources, IResource.DEPTH_INFINITE, tag, subMonitor));
					} catch (CVSException e) {
						throw new InvocationTargetException(e);
					}
					// Cache the new tag creation even if the tag may have had warnings.
					try {
						manager.addTags(
							CVSWorkspaceRoot.getCVSFolderFor(provider.getProject()), 
							new CVSTag[] {tag});
					} catch (CVSException e) {
						addStatus(e.getStatus());
					}

				}	
				previousTag = result[0];				
			}
		}, true /* cancelable */, PROGRESS_DIALOG);
	}
	
	/**
	 * Override to dislay the number of tag operations that succeeded
	 */
	protected IStatus getStatusToDisplay(IStatus[] problems) {
		// We accumulated 1 status per resource above.
		IStatus[] status = getAccumulatedStatus();
		int resourceCount = status.length;
		
		MultiStatus combinedStatus;
		if(resourceCount == 1) {
			combinedStatus = new MultiStatus(CVSUIPlugin.ID, 0, Policy.bind("TagAction.tagProblemsMessage"), null); //$NON-NLS-1$
		} else {
			combinedStatus = new MultiStatus(CVSUIPlugin.ID, 0, Policy.bind("TagAction.tagProblemsMessageMultiple", //$NON-NLS-1$
											  Integer.toString(resourceCount - problems.length), Integer.toString(problems.length)), null); //$NON-NLS-1$
		}
		for (int i = 0; i < problems.length; i++) {
			combinedStatus.merge(problems[i]);
		}
		return combinedStatus;
	}

	/**
	 * Prompts the user for a tag name.
	 * Note: This method is designed to be overridden by test cases.
	 * @return the tag, or null to cancel
	 */
	protected String promptForTag(ICVSFolder folder) {
		TagAsVersionDialog dialog = new TagAsVersionDialog(getShell(),
											Policy.bind("TagAction.tagResources"), //$NON-NLS-1$
											folder);
		if (dialog.open() != InputDialog.OK) return null;
		return dialog.getTagName();
	}
	/**
	 * Note: This method is designed to be overridden by test cases.
	 */
	protected IPromptCondition getPromptCondition() {
		return new IPromptCondition() {
			public boolean needsPrompt(IResource resource) {
				return CVSLightweightDecorator.isDirty(resource);
			}
			public String promptMessage(IResource resource) {
				return Policy.bind("TagAction.uncommittedChanges", resource.getName());//$NON-NLS-1$
			}
		};
	}
	
	protected String getErrorTitle() {
		return Policy.bind("TagAction.tagErrorTitle"); //$NON-NLS-1$
	}
	
	protected String getWarningTitle() {
		return Policy.bind("TagAction.tagWarningTitle"); //$NON-NLS-1$
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.ui.actions.WorkspaceAction#isEnabledForAddedResources()
	 */
	protected boolean isEnabledForAddedResources() {
		return false;
	}

}

