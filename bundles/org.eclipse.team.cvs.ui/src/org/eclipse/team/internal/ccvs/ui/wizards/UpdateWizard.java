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

 
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.operations.UpdateOperation;
import org.eclipse.team.internal.ccvs.ui.tags.*;
import org.eclipse.ui.IWorkbenchPart;

public class UpdateWizard extends Wizard {

	private ResourceMapping[] mappers;
	private final IWorkbenchPart part;
	private TagSelectionWizardPage tagSelectionPage;
	
	public UpdateWizard(IWorkbenchPart part, ResourceMapping[] mappers) {
		this.part = part;
		this.mappers = mappers;
		setWindowTitle(Policy.bind("UpdateWizard.title")); //$NON-NLS-1$
	}
	
	public void addPages() {
		ImageDescriptor substImage = CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_WIZBAN_CHECKOUT);
        tagSelectionPage = new TagSelectionWizardPage("tagPage", Policy.bind("UpdateWizard.0"), substImage, Policy.bind("UpdateWizard.1"), TagSource.create(mappers), TagSourceWorkbenchAdapter.INCLUDE_ALL_TAGS); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		tagSelectionPage.setAllowNoTag(true);
		tagSelectionPage.setHelpContxtId(IHelpContextIds.UPDATE_TAG_SELETION_PAGE);
		CVSTag tag = getInitialSelection();
		if (tag != null) {
			tagSelectionPage.setSelection(tag);
		}
		addPage(tagSelectionPage);
	}
	
	/**
     * @return
     */
    private CVSTag getInitialSelection() {
        try {
            for (int i = 0; i < mappers.length; i++) {
                ResourceMapping mapper = mappers[i];
                IProject[] projects = mapper.getProjects();
                for (int k = 0; k < projects.length; k++) {
                    IProject project = projects[k];
                    ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor(project);
                    FolderSyncInfo info = folder.getFolderSyncInfo();
                    if (info != null) {
                        return info.getTag();
                    }
                }
            }
        } catch (CoreException e) {
            CVSUIPlugin.log(e);
        }
        return null;
    }

	/*
	 * @see IWizard#performFinish()
	 */
	public boolean performFinish() {
		try {
			new UpdateOperation(part, mappers, Command.NO_LOCAL_OPTIONS, tagSelectionPage.getSelectedTag()).run();
		} catch (InvocationTargetException e) {
			CVSUIPlugin.openError(getShell(), null, null, e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
		return true;
	}
}
