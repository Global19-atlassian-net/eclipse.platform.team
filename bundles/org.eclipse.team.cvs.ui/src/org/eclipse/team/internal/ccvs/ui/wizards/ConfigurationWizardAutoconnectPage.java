package org.eclipse.team.internal.ccvs.ui.wizards;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.ccvs.core.CVSTag;
import org.eclipse.team.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * This configuration page explains to the user that CVS/ directories already exists and
 * it will attach the selected project to the repository that is specified in the CVS/ files.
 * 
 * This is useful for people who have checked out a project using command-line tools.
 */
public class ConfigurationWizardAutoconnectPage extends CVSWizardPage {
	private boolean validate = true;
	private FolderSyncInfo info;
	ICVSRepositoryLocation location;

	public ConfigurationWizardAutoconnectPage(String pageName, String title, ImageDescriptor titleImage) {
		super(pageName, title, titleImage);
	}

	/*
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);
		setControl(composite);
		
		Label description = new Label(composite, SWT.WRAP);
		GridData data = new GridData();
		data.horizontalSpan = 2;
		data.widthHint = 350;
		description.setLayoutData(data);
		description.setText(Policy.bind("ConfigurationWizardAutoconnectPage.description"));
		
		if (location == null) return;

		// Spacer
		createLabel(composite, "");
		createLabel(composite, "");
		
		createLabel(composite, Policy.bind("ConfigurationWizardAutoconnectPage.user"));
		createLabel(composite, location.getUsername());
		createLabel(composite, Policy.bind("ConfigurationWizardAutoconnectPage.host"));
		createLabel(composite, location.getHost());
		createLabel(composite, Policy.bind("ConfigurationWizardAutoconnectPage.port"));
		int port = location.getPort();
		if (port == location.USE_DEFAULT_PORT) {
			createLabel(composite, Policy.bind("ConfigurationWizardAutoconnectPage.default"));
		} else {
			createLabel(composite, "" + port);
		}
		createLabel(composite, Policy.bind("ConfigurationWizardAutoconnectPage.connectionType"));
		createLabel(composite, location.getMethod().getName());
		createLabel(composite, Policy.bind("ConfigurationWizardAutoconnectPage.repositoryPath"));
		createLabel(composite, location.getRootDirectory());
		createLabel(composite, Policy.bind("ConfigurationWizardAutoconnectPage.module"));
		createLabel(composite, info.getRepository());
		
		CVSTag tag = info.getTag();
		if (tag != null) {
			// XXX Should we show a tag
			//properties.setProperty("tag", tag.getName());
		}
		 
		// Spacer
		createLabel(composite, "");
		createLabel(composite, "");
		
		final Button check = new Button(composite, SWT.CHECK);
		data = new GridData();
		data.horizontalSpan = 2;
		check.setText(Policy.bind("ConfigurationWizardAutoconnectPage.validate"));
		check.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				validate = check.getSelection();
			}
		});
		check.setSelection(true);		
	}
	
	public FolderSyncInfo getFolderSyncInfo() {
		return info;
	}
	public boolean getValidate() {
		return validate;
	}
	public void setProject(IProject project) {
		try {
			ICVSFolder folder = (ICVSFolder)Session.getManagedResource(project);
			info = folder.getFolderSyncInfo();
			if (info == null) {
				// This should never happen
				ErrorDialog.openError(getContainer().getShell(), Policy.bind("ConfigurationWizardAutoconnectPage.noSyncInfo"), Policy.bind("ConfigurationWizardAutoconnectPage.noCVSDirectory"), null);
				return;
			}
			location = CVSProviderPlugin.getProvider().getRepository(info.getRoot());
		} catch (TeamException e) {
			Shell shell = new Shell(Display.getDefault());
			ErrorDialog.openError(shell, null, null, e.getStatus());
			shell.dispose();
		}
	}
}
