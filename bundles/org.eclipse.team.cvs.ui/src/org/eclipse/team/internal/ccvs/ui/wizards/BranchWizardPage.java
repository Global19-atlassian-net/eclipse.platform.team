package org.eclipse.team.internal.ccvs.ui.wizards;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.ui.Policy;

public class BranchWizardPage extends CVSWizardPage {
	boolean update;
	
	String branchTag;
	String versionTag;
	String versionName;
	boolean allStickyResources;
	
	Text versionText;
	Text branchText;
	
	public BranchWizardPage(String pageName,String title,  boolean allStickyResources, String versionName, ImageDescriptor titleImage) {
		super(pageName, title, titleImage);
		if(allStickyResources) {
			setDescription(Policy.bind("BranchWizardPage.pageDescriptionVersion")); //$NON-NLS-1$
		} else {
			setDescription(Policy.bind("BranchWizardPage.pageDescription")); //$NON-NLS-1$
		}
		this.allStickyResources = allStickyResources;
		this.versionName = versionName;
	}
	
	/*
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);
		Label label;
		GridData data;
		
		createLabel(composite, Policy.bind("BranchWizardPage.branchName")); //$NON-NLS-1$
		branchText = createTextField(composite);
		branchText.addListener(SWT.Modify, new Listener() {
			public void handleEvent(Event event) {
				branchTag = branchText.getText();				
				updateEnablement();
				updateVersionName(branchTag);
			}
		});

		createLabel(composite, ""); //$NON-NLS-1$
		createLabel(composite, ""); //$NON-NLS-1$

		final Button check = new Button(composite, SWT.CHECK);
		data = new GridData();
		data.horizontalSpan = 2;
		check.setLayoutData(data);
		check.setText(Policy.bind("BranchWizardPage.startWorking")); //$NON-NLS-1$
		check.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				update = check.getSelection();
			}
		});
		check.setSelection(true);		
		update = true;
		
		createLabel(composite, ""); //$NON-NLS-1$
		createLabel(composite, ""); //$NON-NLS-1$
			
		label = new Label(composite, SWT.WRAP);
		label.setText(Policy.bind("BranchWizardPage.specifyVersion")); //$NON-NLS-1$
		data = new GridData();
		data.horizontalSpan = 2;
		data.widthHint = 350;
		label.setLayoutData(data);
			
		createLabel(composite, Policy.bind("BranchWizardPage.versionName")); //$NON-NLS-1$
		versionText = createTextField(composite);
		versionText.addListener(SWT.Modify, new Listener() {
			public void handleEvent(Event event) {
				versionTag = versionText.getText();
				updateEnablement();
			}
		});
		
		if(allStickyResources) {
			versionText.setEditable(false);
			versionText.setText(versionName);
		}

		branchText.setFocus();
		setControl(composite);
		updateEnablement();
	}

	public String getBranchTag() {
		return branchTag;
	}

	public boolean getUpdate() {
		return update;
	}

	public String getVersionTag() {
		return versionTag;
	}
	
	private void updateVersionName(String branchName) {
		if(versionText!=null && !allStickyResources) {
			versionText.setText(Policy.bind("BranchWizardPage.versionPrefix") + branchName); //$NON-NLS-1$
		}
	}
	
	private void updateEnablement() {
		boolean complete = true;
		String branch = branchText.getText();
		
		if (branch.length() == 0) {
			setMessage(null);
			complete = false;
		} else {
			IStatus status = CVSTag.validateTagName(branch);
			if (!status.isOK()) {
				setMessage(Policy.bind("BranchWizard.branchNameWarning", status.getMessage()), WARNING); //$NON-NLS-1$
				complete = false;
			} else {
				if(versionText!=null) {
					status = CVSTag.validateTagName(versionText.getText());
					if (!status.isOK()) {
						setMessage(Policy.bind("BranchWizard.versionNameWarning", status.getMessage()), WARNING); //$NON-NLS-1$
						complete = false;
					} else {
						if(versionText.getText().equals(branch)) {
							setMessage(Policy.bind("BranchWizard.branchAndVersionMustBeDifferent"), WARNING); //$NON-NLS-1$
							complete = false;
						}
					}
				}
			}
		}
		if(complete) {
			setMessage(null);
		}
		setPageComplete(complete);
	}
}