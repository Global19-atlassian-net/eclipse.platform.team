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
package org.eclipse.team.internal.ccvs.ui.wizards;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.team.internal.ccvs.ui.IHelpContextIds;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.wizards.KSubstWizard.KSubstChangeElement;
import org.eclipse.ui.help.WorkbenchHelp;

/**
 * Page to warn user about uncommitted outgoing changes.
 */
public class KSubstWizardDirtyFilesPage extends CVSWizardPage {
	private boolean includeDirtyFiles;
	private Button includeDirtyFilesButton;
	private ListViewer listViewer;

	public KSubstWizardDirtyFilesPage(String pageName, String title, ImageDescriptor image, boolean includeDirtyFiles) {
		super(pageName, title, image);
		this.includeDirtyFiles = includeDirtyFiles;
	}
	
	public void createControl(Composite parent) {
		Composite top = new Composite(parent, SWT.NONE);
		top.setLayout(new GridLayout());
		setControl(top);
		
		// set F1 help
		WorkbenchHelp.setHelp(top, IHelpContextIds.KEYWORD_SUBSTITUTION_CHANGED_PAGE);
			
		createWrappingLabel(top, Policy.bind("KSubstWizardDirtyFilesPage.contents"), 0); //$NON-NLS-1$
		
		includeDirtyFilesButton = new Button(top, SWT.CHECK);
		includeDirtyFilesButton.setText(Policy.bind("KSubstWizardDirtyFilesPage.includeDirtyFiles")); //$NON-NLS-1$
		includeDirtyFilesButton.setSelection(includeDirtyFiles);
		includeDirtyFilesButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				includeDirtyFiles = includeDirtyFilesButton.getSelection();
			}
		});
		
		createSeparator(top, SPACER_HEIGHT);
		listViewer = createFileListViewer(top,
			Policy.bind("KSubstWizardDirtyFilesPage.dirtyFilesViewer.title"), LIST_HEIGHT_HINT); //$NON-NLS-1$
        Dialog.applyDialogFont(parent);
	}
	
	public boolean includeDirtyFiles() {
		return includeDirtyFiles;
	}
	
	public void setChangeList(List changes) {
		List filteredFiles = new ArrayList();
		for (Iterator it = changes.iterator(); it.hasNext();) {
			KSubstChangeElement change = (KSubstChangeElement) it.next();
			if (change.matchesFilter(KSubstChangeElement.CHANGED_FILE)) {
				filteredFiles.add(change.getFile());
			}
		}
		listViewer.setInput(filteredFiles.toArray());
	}
	
	public boolean isListEmpty() {
		// returns true iff the list is empty after filtering
		return listViewer.getList().getItemCount() == 0;
	}
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			includeDirtyFilesButton.setFocus();
		}
	}
}
