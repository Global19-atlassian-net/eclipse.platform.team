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
package org.eclipse.team.ui.synchronize;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.internal.ResizableDialog;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.TeamUI;

/**
 * A dialog that displays a synchronize participant. There are some cases when it
 * is more appropriate to display synchronization information in a dialog instead of
 * in the Synchronize View. This class provides a modal context in which to show
 * this.
 * <p>
 * To allow a user to transfer this synchronization state to the Synchronize View simply 
 * set the participant.
 * </p><p>
 * The dialog tracks changes made in the content merge viewers, displays a change
 * indication and will ensure that changes are saved when the dialog is closed.
 * </p><p>
 * Buffered or un-buffered compare editor inputs are supported.
 * </p>
 * @see SynchronizeCompareInput
 * @since 3.0
 */
public class SynchronizeDialog extends ResizableDialog implements IPropertyChangeListener {
		
	private SynchronizeInput input;
	private ISynchronizeParticipant participant;
	private Button saveButton;
	private Button rememberParticipantButton;
	private String title;
	private boolean isDirty = false;
	private CompareConfiguration cc;

	/**
	 * Creates a dialog with the given title and input. The input is not created until the dialog
	 * is opened.
	 * 
	 * @param shell the parent shell or <code>null</code> to create a top level shell. 
	 * @param title the shell's title
	 * @param input the compare input to show in the dialog
	 */
	public SynchronizeDialog(Shell shell, String title, CompareConfiguration cc, ISynchronizeParticipant participant) {
		super(shell, null);
		this.title = title;
		this.cc = cc;
		this.participant = participant;
	
		//fCompareEditorInput= input;
		//fCompareEditorInput.addPropertyChangeListener(this);
	}
	
	public void setSynchronizeParticipant(ISynchronizeParticipant participant) {
		this.participant = participant;
	}
	
	/* (non-Javadoc)
	 * Method declared on Dialog.
	 */
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
	}
	
	/* (non-Javadoc)
	 * Method declared on Dialog.
	 */
	protected Control createDialogArea(Composite parent2) {
		Composite parent = (Composite) super.createDialogArea(parent2);
		this.input = new SynchronizeInput(getShell(), cc, participant);
		Control c = input.createControl(parent);
		c.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		ISynchronizeParticipantReference[] participants = TeamUI.getSynchronizeManager().getSynchronizeParticipants();
		
		if (participant != null && ! particantRegisteredWithSynchronizeManager(participant)) {
			rememberParticipantButton = new Button(parent, SWT.CHECK);
			rememberParticipantButton.setText(Policy.bind("ParticipantCompareDialog.1")); //$NON-NLS-1$
		}
		Shell shell = c.getShell();
		shell.setText(title);
		//shell.setImage(fCompareEditorInput.getTitleImage());
		Dialog.applyDialogFont(parent2);
		return parent;
	}
	
	private boolean particantRegisteredWithSynchronizeManager(ISynchronizeParticipant participant) {
		return TeamUI.getSynchronizeManager().get(participant.getId(), participant.getSecondaryId()) != null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#buttonPressed(int)
	 */
	protected void buttonPressed(int buttonId) {
		saveChanges();
		if(buttonId == IDialogConstants.OK_ID && isRememberParticipant()) {
			rememberParticipant();
		}
		super.buttonPressed(buttonId);
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.compare.internal.ResizableDialog#close()
	 */
	public boolean close() {
		saveChanges();
		return super.close();
	}
	
	protected boolean isRememberParticipant() {
		return getParticipant() != null && rememberParticipantButton != null && rememberParticipantButton.getSelection();
	}
	
	protected void rememberParticipant() {
		if(getParticipant() != null) {
			ISynchronizeManager mgr = TeamUI.getSynchronizeManager();
			ISynchronizeView view = mgr.showSynchronizeViewInActivePage();
			mgr.addSynchronizeParticipants(new ISynchronizeParticipant[] {getParticipant()});
			view.display(participant);
		}
	}

	public void propertyChange(PropertyChangeEvent event) {
		if (input != null) {
			if(input.isSaveNeeded()) {
				// the dirty flag is required because there is a compare bug that causes the dirty bit to be reset sometimes
				// although the underlying compare editor input is still dirty.
				isDirty = true;
				getShell().setText(title + " *"); //$NON-NLS-1$
			} else {
				getShell().setText(title);
			}
		}
	}
	
	/**
	 * Save any changes to the compare editor.
	 */
	private void saveChanges() {
		if (input.isSaveNeeded() && MessageDialog.openConfirm(getShell(), Policy.bind("ParticipantCompareDialog.2"), Policy.bind("ParticipantCompareDialog.3"))) {						 //$NON-NLS-1$ //$NON-NLS-2$
			BusyIndicator.showWhile(null, new Runnable() {
				public void run() {
					try {
						input.saveChanges(new NullProgressMonitor());
					} catch (CoreException e) {
						Utils.handle(e);
					}
				}
			});		
		}
	}
	
	protected ISynchronizeParticipant getParticipant() {
		return participant;
	}
}