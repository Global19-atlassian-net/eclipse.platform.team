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
 * Page to warn user about the side-effects of changing keyword
 * substitution on already committed files.
 */
public class KSubstWizardSharedFilesPage extends CVSWizardPage {
	private boolean includeSharedFiles;
	private Button includeSharedFilesButton;
	private ListViewer listViewer;

	public KSubstWizardSharedFilesPage(String pageName, String title, ImageDescriptor image, boolean includeSharedFiles) {
		super(pageName, title, image);
		this.includeSharedFiles = includeSharedFiles;
	}
	
	public void createControl(Composite parent) {
		Composite top = new Composite(parent, SWT.NONE);
		top.setLayout(new GridLayout());
		setControl(top);
		createWrappingLabel(top, Policy.bind("KSubstWizardSharedFilesPage.contents"), 0); //$NON-NLS-1$

		// set F1 help
		WorkbenchHelp.setHelp(top, IHelpContextIds.KEYWORD_SUBSTITUTION_SHARED_PAGE);
				
		includeSharedFilesButton = new Button(top, SWT.CHECK);
		includeSharedFilesButton.setText(Policy.bind("KSubstWizardSharedFilesPage.includeSharedFiles")); //$NON-NLS-1$
		includeSharedFilesButton.setSelection(includeSharedFiles);
		includeSharedFilesButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				includeSharedFiles = includeSharedFilesButton.getSelection();
			}
		});

		createSeparator(top, SPACER_HEIGHT);
		listViewer = createFileListViewer(top,
			Policy.bind("KSubstWizardSharedFilesPage.sharedFilesViewer.title"), LIST_HEIGHT_HINT); //$NON-NLS-1$
        Dialog.applyDialogFont(parent);
	}
	
	public boolean includeSharedFiles() {
		return includeSharedFiles;
	}

	public void setChangeList(List changes) {
		List filteredFiles = new ArrayList();
		for (Iterator it = changes.iterator(); it.hasNext();) {
			KSubstChangeElement change = (KSubstChangeElement) it.next();
			if (change.matchesFilter(KSubstChangeElement.CHANGED_FILE | KSubstChangeElement.UNCHANGED_FILE)) {
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
			includeSharedFilesButton.setFocus();
		}
	}
}
