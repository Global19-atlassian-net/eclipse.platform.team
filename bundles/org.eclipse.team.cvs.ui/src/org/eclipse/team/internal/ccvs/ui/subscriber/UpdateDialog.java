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
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.ui.synchronize.actions.SyncInfoSet;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * This dialog prompts for the type of update which should take place
 * (i.e. update of auto-mergable files or update of all ignore local
 * changes.
 */
public class UpdateDialog extends SyncInfoSetDetailsDialog {

	public static final int YES = IDialogConstants.YES_ID;
	
	public UpdateDialog(Shell parentShell, SyncInfoSet syncSet) {
		super(parentShell, Policy.bind("UpdateDialog.overwriteTitle"), Policy.bind("UpdateDialog.overwriteDetailsTitle"), syncSet); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.DetailsDialog#createMainDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	protected void createMainDialogArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout());
		
		// TODO: set F1 help
		//WorkbenchHelp.setHelp(composite, IHelpContextIds.ADD_TO_VERSION_CONTROL_DIALOG);
		
		createWrappingLabel(composite, Policy.bind("UpdateDialog.overwriteMessage")); //$NON-NLS-1$
	}

	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, YES, IDialogConstants.YES_LABEL, true);
		createButton(parent, IDialogConstants.NO_ID, IDialogConstants.NO_LABEL, true);
		super.createButtonsForButtonBar(parent);
	}
	
	protected boolean includeOkButton() {
		return false;
	}
	
	protected boolean includeCancelButton() {
		return false;
	}

	protected void buttonPressed(int id) {
		// hijack yes and no buttons to set the correct return
		// codes.
		if(id == YES || id == IDialogConstants.NO_ID) {
			setReturnCode(id);
			filterSyncSet();
			close();
		} else {
			super.buttonPressed(id);
		}
	}
}
