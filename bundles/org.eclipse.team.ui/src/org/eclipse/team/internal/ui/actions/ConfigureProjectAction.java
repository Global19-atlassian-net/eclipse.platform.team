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
package org.eclipse.team.internal.ui.actions;

 
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.wizards.ConfigureProjectWizard;

/**
 * Action for configuring a project. Configuring involves associating
 * the project with a Team provider and performing any provider-specific
 * configuration that is necessary.
 */
public class ConfigureProjectAction extends TeamAction {
	/*
	 * Method declared on IActionDelegate.
	 */
	public void run(IAction action) {
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				try {
					IProject project = getSelectedProjects()[0];
					ConfigureProjectWizard wizard = new ConfigureProjectWizard();
					wizard.init(null, project);
					WizardDialog dialog = new WizardDialog(getShell(), wizard);
					dialog.open();
				} catch (Exception e) {
					throw new InvocationTargetException(e);
				}
			}
		}, Policy.bind("ConfigureProjectAction.configureProject"), PROGRESS_BUSYCURSOR); //$NON-NLS-1$
	}
	/**
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() {
		IProject[] selectedProjects = getSelectedProjects();
		if (selectedProjects.length != 1) return false;
		if (!selectedProjects[0].isAccessible()) return false;
		if (RepositoryProvider.getProvider(selectedProjects[0]) == null) return true;
		return false;
	}
}
