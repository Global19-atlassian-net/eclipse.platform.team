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
package org.eclipse.team.internal.ccvs.ui.merge;


import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.TeamProvider;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.subscribers.CVSMergeSubscriber;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.sync.ISynchronizeView;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;

public class MergeWizard extends Wizard {
	MergeWizardStartPage startPage;
	MergeWizardEndPage endPage;
	IResource[] resources;

	public void addPages() {
		setNeedsProgressMonitor(true);
		// when merging multiple resources, use the tags found on the first selected
		// resource. This makes sense because you would typically merge resources that
		// have a common context and are versioned and branched together.
		IProject projectForTagRetrieval = resources[0].getProject();
				
		setWindowTitle(Policy.bind("MergeWizard.title")); //$NON-NLS-1$
		ImageDescriptor mergeImage = CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_WIZBAN_MERGE);
		startPage = new MergeWizardStartPage("startPage", Policy.bind("MergeWizard.start"), mergeImage); //$NON-NLS-1$ //$NON-NLS-2$
		startPage.setProject(projectForTagRetrieval);
		addPage(startPage);
		endPage = new MergeWizardEndPage("endPage", Policy.bind("MergeWizard.end"), mergeImage, startPage); //$NON-NLS-1$ //$NON-NLS-2$
		endPage.setProject(projectForTagRetrieval);
		addPage(endPage);
	}

	/*
	 * @see IWizard#performFinish()
	 */
	public boolean performFinish() {
		
		IWorkbenchWindow wWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage activePage = null;
		if(wWindow != null) {
			activePage = wWindow.getActivePage();
		}
		
		CVSTag startTag = startPage.getTag();
		CVSTag endTag = endPage.getTag();				
		
		final CVSMergeSubscriber s = new CVSMergeSubscriber(resources, startTag, endTag);
		try {
			getContainer().run(false, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor)	throws InvocationTargetException, InterruptedException {
						try {
							s.refresh(resources, IResource.DEPTH_INFINITE, monitor);
						} catch (TeamException e) {
							s.cancel();
							throw new InvocationTargetException(e);
						}
						TeamProvider.registerSubscriber(s);
				}
			});
			ISynchronizeView view = TeamUI.showSyncViewInActivePage(null /* no default page */);
			IWorkingSet workingSet = CVSUIPlugin.getWorkingSet(resources, Policy.bind("SyncAction.workingSetName")); //$NON-NLS-1$
			if(view != null) {
				view.setWorkingSet(workingSet);
				view.selectSubscriber(s);
			} else {
				CVSUIPlugin.openError(getContainer().getShell(), Policy.bind("error"), Policy.bind("Error.unableToShowSyncView"), null);
			}
		} catch (InvocationTargetException e) {
			CVSUIPlugin.openError(getContainer().getShell(), null, null, e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
		return true;
	}
	
	/*
	 * Set the resources that should be merged.
	 */
	public void setResources(IResource[] resources) {
		this.resources = resources;
	}
}