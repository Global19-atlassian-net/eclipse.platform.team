/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.wizards;

import org.eclipse.core.resources.IContainer;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * This wizard allows the user to show deleted resources in the history view
 */
public class RestoreFromRepositoryWizard extends Wizard {

	private RestoreFromRepositoryFileSelectionPage fileSelectionPage;
	private IContainer parent;
	private ICVSFile[] files;
	
	/**
	 * Constructor for RestoreFromRepositoryWizard.
	 */
	public RestoreFromRepositoryWizard(IContainer parent, ICVSFile[] files) {
		this.parent = parent;
		this.files = files;
	}

	/**
	 * @see org.eclipse.jface.wizard.IWizard#performFinish()
	 */
	public boolean performFinish() {
		return fileSelectionPage.restoreSelectedFiles();
	}
	
	/**
	 * @see org.eclipse.jface.wizard.IWizard#addPages()
	 */
	public void addPages() {
		setNeedsProgressMonitor(true);
		ImageDescriptor substImage = CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_WIZBAN_CHECKOUT);
		
		fileSelectionPage = new RestoreFromRepositoryFileSelectionPage("FileSelectionPage", Policy.bind("RestoreFromRepositoryWizard.fileSelectionPageTitle"), substImage, Policy.bind("RestoreFromRepositoryWizard.fileSelectionPageDescription")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		fileSelectionPage.setInput(parent, files);
		addPage(fileSelectionPage);
	}
}
