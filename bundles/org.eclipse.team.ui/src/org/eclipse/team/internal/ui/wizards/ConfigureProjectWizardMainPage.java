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
package org.eclipse.team.internal.ui.wizards;


import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.team.internal.ui.IHelpContextIds;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.ui.IConfigurationWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.internal.model.AdaptableList;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * The main page of the configure project wizard. It contains a table
 * which lists possible team providers with which to configure the project.
 * The user may select one and press "Next", which will display a provider-
 * specific wizard page.
 */
public class ConfigureProjectWizardMainPage extends WizardPage {
	private Table table;
	private TableViewer viewer;
	private AdaptableList wizards;
	private IWorkbench workbench;
	private IProject project;
	private String description;
	
	private IConfigurationWizard selectedWizard;
	
	/**
	 * Create a new ConfigureProjectWizardMainPage
	 * 
	 * @param pageName  the name of the page
	 * @param title  the title of the page
	 * @param titleImage  the image for the page title
	 * @param wizards  the wizards to populate the table with
	 */
	public ConfigureProjectWizardMainPage(String pageName, String title, ImageDescriptor titleImage, AdaptableList wizards) {
		this(pageName,title,titleImage,wizards,Policy.bind("ConfigureProjectWizardMainPage.selectRepository")); //$NON-NLS-1$
	}
	
	/**
	 * Create a new ConfigureProjectWizardMainPage
	 * 
	 * @param pageName  the name of the page
	 * @param title  the title of the page
	 * @param titleImage  the image for the page title
	 * @param wizards  the wizards to populate the table with
	 * @param description The string to use as a description label
	 */
	public ConfigureProjectWizardMainPage(String pageName, String title, ImageDescriptor titleImage, AdaptableList wizards, String description) {
		super(pageName, title, titleImage);
		this.wizards = wizards;
		this.description = description;
	}
	
	public IConfigurationWizard getSelectedWizard() {
		return selectedWizard;
	}
	/*
	 * @see WizardPage#canFlipToNextPage
	 */
	public boolean canFlipToNextPage() {		
		return selectedWizard != null && selectedWizard.getPageCount() > 0;
	}
	/*
	 * @see WizardPage#createControl
	 */
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		setControl(composite);

		// set F1 help
		WorkbenchHelp.setHelp(composite, IHelpContextIds.SHARE_PROJECT_PAGE);
				
		Label label = new Label(composite, SWT.LEFT);
		label.setText(description);
		GridData data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		label.setLayoutData(data);
	
		table = new Table(composite, SWT.SINGLE | SWT.BORDER);
		data = new GridData(GridData.FILL_BOTH);
		data.heightHint = table.getItemHeight() * 7;
		table.setLayoutData(data);
		viewer = new TableViewer(table);
		viewer.setContentProvider(new WorkbenchContentProvider());
		viewer.setLabelProvider(new WorkbenchLabelProvider());
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				// Initialize the wizard so we can tell whether to enable the Next button
				ISelection selection = event.getSelection();
				if (selection == null || !(selection instanceof IStructuredSelection)) {
					selectedWizard = null;
					setPageComplete(false);
					return;
				}
				IStructuredSelection ss = (IStructuredSelection)selection;
				if (ss.size() != 1) {
					selectedWizard = null;
					setPageComplete(false);
					return;
				}
				ConfigurationWizardElement selectedElement = (ConfigurationWizardElement)ss.getFirstElement();
				try {
					selectedWizard = (IConfigurationWizard)selectedElement.createExecutableExtension();
					selectedWizard.init(workbench, project);
				} catch (CoreException e) {					
					return;
				}
				selectedWizard.addPages();
				
				// Ask the container to update button enablement
				setPageComplete(true);
			}
		});
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				getWizard().getContainer().showPage(getNextPage());
			}
		});
		viewer.setInput(wizards);
        Dialog.applyDialogFont(parent);
	}
	/**
	 * The <code>WizardSelectionPage</code> implementation of 
	 * this <code>IWizardPage</code> method returns the first page 
	 * of the currently selected wizard if there is one.
	 * 
	 * @see WizardPage#getNextPage
	 */
	public IWizardPage getNextPage() {
		if (selectedWizard == null) return null;
		return selectedWizard.getStartingPage();
	}
	/**
	 * Set the workbench to the argument
	 * 
	 * @param workbench  the workbench to set
	 */
	public void setWorkbench(IWorkbench workbench) {
		this.workbench = workbench;
	}
	/**
	 * Set the project to the argument
	 * 
	 * @param project  the project to set
	 */
	public void setProject(IProject project) {
		this.project = project;
	}
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			table.setFocus();
		}
	}
}
