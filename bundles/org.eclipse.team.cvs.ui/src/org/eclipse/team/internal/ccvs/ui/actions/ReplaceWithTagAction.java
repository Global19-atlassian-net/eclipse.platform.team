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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.IHelpContextIds;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.TagSelectionDialog;
import org.eclipse.team.internal.ui.PromptingDialog;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

/**
 * Action for replace with tag.
 */
public class ReplaceWithTagAction extends WorkspaceAction {
	/*
	 * Method declared on IActionDelegate.
	 */
	public void execute(IAction action) throws InterruptedException, InvocationTargetException {
		
		// Setup the holders
		final IResource[] resource = new IResource[] {null};
		final CVSTag[] tag = new CVSTag[] {null};
		final boolean[] recurse = new boolean[] {true};
		
		// Show a busy cursor while display the tag selection dialog
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
				PromptingDialog prompt = new PromptingDialog(getShell(), getSelectedResources(), 
																  getOverwriteLocalChangesPrompt(), 
																  Policy.bind("ReplaceWithAction.confirmOverwrite"));//$NON-NLS-1$
				final IResource[] resources = prompt.promptForMultiple();
				if(resources.length == 0) {
					// nothing to do
					return;
				}
				// show the tags for the projects of the selected resources
				IProject[] projects = new IProject[resources.length];
				for (int i = 0; i < resources.length; i++) {
					projects[i] = resources[i].getProject();
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
					if(!CVSAction.checkForMixingTags(getShell(), resources, tag[0])) {
						tag[0] = null;
						return;
					}
				} catch (CVSException e) {
					throw new InvocationTargetException(e);
				}
			}
		}, false /* cancelable */, this.PROGRESS_BUSYCURSOR);			 //$NON-NLS-1$
		
		if (tag[0] == null) return;
		
		// Display a progress dialog while replacing
		run(new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
				try {
					int depth = recurse[0] ? IResource.DEPTH_INFINITE : IResource.DEPTH_ONE;
					Hashtable table = getProviderMapping();
					Set keySet = table.keySet();
					monitor.beginTask("", keySet.size() * 1000); //$NON-NLS-1$
					monitor.setTaskName(Policy.bind("ReplaceWithTagAction.replacing", tag[0].getName())); //$NON-NLS-1$
					Iterator iterator = keySet.iterator();
					while (iterator.hasNext()) {
						IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 1000);
						CVSTeamProvider provider = (CVSTeamProvider)iterator.next();
						List list = (List)table.get(provider);
						IResource[] providerResources = (IResource[])list.toArray(new IResource[list.size()]);
						provider.get(providerResources, depth, tag[0], Policy.subMonitorFor(monitor, 100));
					}
				} catch (TeamException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		}, true /* cancelable */, this.PROGRESS_DIALOG);
	}
	
	protected boolean isEnabled() throws TeamException {		
		return isSelectionNonOverlapping();
	}
	
	protected boolean equalTags(CVSTag tag1, CVSTag tag2) {
		if (tag1 == null) tag1 = CVSTag.DEFAULT;
		if (tag2 == null) tag2 = CVSTag.DEFAULT;
		return tag1.equals(tag2);
	}
	/**
	 * @see org.eclipse.team.internal.ccvs.ui.actions.CVSAction#getErrorTitle()
	 */
	protected String getErrorTitle() {
		return Policy.bind("ReplaceWithTagAction.replace"); //$NON-NLS-1$
	}

}
