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


import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.Policy;

public class NewLocationWizard extends Wizard {
	private ConfigurationWizardMainPage mainPage;

	private Properties properties = null;
	
	// Type of wizard, whether GENERIC or STANDARD.
	private int type;

	/** Type used when creating a dialog that can find an arbitrary
		cvs repository.  */
	public static final int GENERIC = 0;
	/** Type used when creating a dialog that knows about the standard
		public repositories.  */
	public static final int STANDARD = 1;

	public NewLocationWizard() {
		this(GENERIC);
	}

	public NewLocationWizard(int type) {
		this.type = type;
		IDialogSettings workbenchSettings = CVSUIPlugin.getPlugin().getDialogSettings();
		IDialogSettings section = workbenchSettings.getSection("NewLocationWizard");//$NON-NLS-1$
		if (section == null) {
			section = workbenchSettings.addNewSection("NewLocationWizard");//$NON-NLS-1$
		}
		setDialogSettings(section);
		setWindowTitle(Policy.bind("NewLocationWizard.title")); //$NON-NLS-1$
	}
	
	public NewLocationWizard(Properties initialProperties) {
		this();
		this.properties = initialProperties;
	}

	/**
	 * Creates the wizard pages
	 */
	public void addPages() {
		if (type == GENERIC) {
			mainPage = new ConfigurationWizardMainPage("repositoryPage1", Policy.bind("NewLocationWizard.heading"), CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_WIZBAN_NEW_LOCATION)); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			mainPage = new StandardWizardMainPage("repositoryPage1", Policy.bind("NewLocationWizard.heading"), CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_WIZBAN_NEW_LOCATION)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (properties != null) {
			mainPage.setProperties(properties);
		}
		mainPage.setShowValidate(true);
		mainPage.setDescription(Policy.bind("NewLocationWizard.description")); //$NON-NLS-1$
		mainPage.setDialogSettings(getDialogSettings());
		addPage(mainPage);
	}
	/*
	 * @see IWizard#performFinish
	 */
	public boolean performFinish() {
		mainPage.finish(new NullProgressMonitor());
		Properties properties = mainPage.getProperties();
		final ICVSRepositoryLocation[] root = new ICVSRepositoryLocation[1];
		CVSProviderPlugin provider = CVSProviderPlugin.getPlugin();
		try {
			root[0] = provider.createRepository(properties);
			if (mainPage.getValidate()) {
				try {
					new ProgressMonitorDialog(getShell()).run(true, true, new IRunnableWithProgress() {
						public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
							try {
								root[0].validateConnection(monitor);
							} catch (TeamException e) {
								throw new InvocationTargetException(e);
							}
						}
					});
				} catch (InterruptedException e) {
					return false;
				} catch (InvocationTargetException e) {
					Throwable t = e.getTargetException();
					if (t instanceof TeamException) {
						throw (TeamException)t;
					}
				}
			}
			provider.addRepository(root[0]);
		} catch (TeamException e) {
			if (root[0] == null) {
				// Exception creating the root, we cannot continue
				CVSUIPlugin.openError(getContainer().getShell(), Policy.bind("NewLocationWizard.exception"), null, e); //$NON-NLS-1$
				return false;
			} else {
				// Exception validating. We can continue if the user wishes.
				IStatus error = e.getStatus();
				if (error.isMultiStatus() && error.getChildren().length == 1) {
					error = error.getChildren()[0];
				}
					
				boolean keep = false;
				if (error.isMultiStatus()) {
					CVSUIPlugin.openError(getContainer().getShell(), Policy.bind("NewLocationWizard.validationFailedTitle"), null, e); //$NON-NLS-1$
				} else {
					keep = MessageDialog.openQuestion(getContainer().getShell(),
						Policy.bind("NewLocationWizard.validationFailedTitle"), //$NON-NLS-1$
						Policy.bind("NewLocationWizard.validationFailedText", new Object[] {error.getMessage()})); //$NON-NLS-1$
				}
				try {
					if (keep) {
						provider.addRepository(root[0]);
					} else {
						provider.disposeRepository(root[0]);
					}
				} catch (TeamException e1) {
					CVSUIPlugin.openError(getContainer().getShell(), Policy.bind("exception"), null, e1); //$NON-NLS-1$
					return false;
				}
				return keep;
			}
		}
		return true;	
	}
}
