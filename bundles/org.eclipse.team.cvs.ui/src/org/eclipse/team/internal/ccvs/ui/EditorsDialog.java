/*******************************************************************************
 * Copyright (c) 2003 CSC SoftwareConsult GmbH & Co. OHG, Germany and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 * 	CSC - Intial implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.core.EditorsInfo;
import org.eclipse.ui.help.WorkbenchHelp;



/**
 * 
 * A dialog for showing the result of a cvs editors command.
 * Currently not in use, but can be used before executing the edit command
 * 
 * @author <a href="mailto:gregor.kohlwes@csc.com,kohlwes@gmx.net">Gregor Kohlwes</a>
 */
public class EditorsDialog extends Dialog {
	/**
	 * Constructor EditorsDialog.
	 * @param shell
	 * @param iEditorsInfos
	 */
	
	EditorsView editorsView;
	EditorsInfo[] editorsInfo;
	
	public EditorsDialog(Shell shell, EditorsInfo[] infos) {
		super(shell);
		editorsInfo = infos;
	}

	protected Control createDialogArea(Composite container) {

		Composite parent = (Composite) super.createDialogArea(container);
		Layout layout = parent.getLayout();
						
		getShell().setText(Policy.bind("EditorsDialog.title")); //$NON-NLS-1$
		createMessageArea(parent);
		editorsView = new EditorsView();
		editorsView.createPartControl(container);
		editorsView.setInput(editorsInfo);
		
		// set F1 help
		WorkbenchHelp.setHelp(parent, IHelpContextIds.EDITORS_DIALOG);
		
		Dialog.applyDialogFont(parent);

		return parent;
	}
	/**
	 * Method createMessageArea.
	 * @param parent
	 */
	private void createMessageArea(Composite parent) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(Policy.bind("EditorsDialog.question")); //$NON-NLS-1$		
	}
	
}
