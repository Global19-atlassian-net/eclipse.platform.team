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
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.ui.AdaptableResourceList;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * This page allows the user to select the target parent container for
 * the folders being checked out.
 */
public class CheckoutAsProjectSelectionPage extends CVSWizardPage {
	
	public static final String NAME = "CheckoutAsProjectSelectionPage";
	
	private TreeViewer tree;
	private Text nameField;
	private Combo filterList;
	private Button recurseCheck;
	
	private IResource[] resources;
	private IResource selection;
	private ICVSRemoteFolder[] remoteFolders;
	private String folderName;
	private boolean recurse;
	private int filter;

	/**
	 * Constructor for CheckoutIntoProjectSelectionPage.
	 * @param pageName
	 * @param title
	 * @param titleImage
	 */
	public CheckoutAsProjectSelectionPage(ImageDescriptor titleImage, ICVSRemoteFolder[] remoteFolders) {
		super(NAME, Policy.bind("CheckoutAsProjectSelectionPage.title"), titleImage, Policy.bind("CheckoutAsProjectSelectionPage.description"));
		this.remoteFolders = remoteFolders;
	}

	/**
	 * @return
	 */
	private boolean isSingleFolder() {
		return remoteFolders.length == 1;
	}
	
	/*
	 * For the single folder case, return the name of the folder
	 */
	private String getInputFolderName() {
		return remoteFolders[0].getName();
	}
	
	private String getRepository() throws CVSException {
		return remoteFolders[0].getFolderSyncInfo().getRoot();
	}
	
	/**
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite composite= createComposite(parent, 2);
		setControl(composite);
		
		// WorkbenchHelp.setHelp(composite, IHelpContextIds.CHECKOUT_INTO_RESOURCE_SELECTION_PAGE);
		
		if (isSingleFolder()) {
			createLabel(composite, Policy.bind("CheckoutAsProjectSelectionPage.name")); //$NON-NLS-1$
			nameField = createTextField(composite);
			nameField.addListener(SWT.Modify, new Listener() {
				public void handleEvent(Event event) {
					folderName = nameField.getText();
					updateWidgetEnablements();
				}
			});
		}
		
		createWrappingLabel(composite, Policy.bind("CheckoutAsProjectSelectionPage.treeLabel"), 0, 2); //$NON-NLS-1$
		
		tree = createResourceSelectionTree(composite, IResource.PROJECT | IResource.FOLDER, 2 /* horizontal span */);
		tree.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				handleResourceSelection(event);
			}
		});

		Composite filterComposite = createComposite(composite, 2);
		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		data.horizontalSpan = 2;
		filterComposite.setLayoutData(data);
		createLabel(filterComposite, Policy.bind("CheckoutAsProjectSelectionPage.showLabel")); //$NON-NLS-1$
		filterList = createCombo(filterComposite);
		filterList.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleFilterSelection();
			}
		});
		
		createWrappingLabel(composite, "", 0, 2); //$NON-NLS-1$
				
		// Should subfolders of the folder be checked out?
		recurseCheck = createCheckBox(composite, Policy.bind("CheckoutAsProjectSelectionPage.recurse")); //$NON-NLS-1$
		recurseCheck.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				recurse = recurseCheck.getSelection();
				updateWidgetEnablements();
			}
		});
		
		initializeValues();
		updateWidgetEnablements();
		tree.getControl().setFocus();
	}

	/**
	 * Method initializeValues.
	 */
	private void initializeValues() {
		if (isSingleFolder()) {
			nameField.setText(getInputFolderName());
		}
		tree.setInput(ResourcesPlugin.getWorkspace().getRoot());
		recurse = true;
		recurseCheck.setSelection(recurse);
		filter = 0;
		updateTreeContents(filter);
		filterList.add(Policy.bind("CheckoutAsProjectSelectionPage.showAll")); //$NON-NLS-1$
		filterList.add(Policy.bind("CheckoutAsProjectSelectionPage.showUnshared")); //$NON-NLS-1$
		filterList.add(Policy.bind("CheckoutAsProjectSelectionPage.showSameRepo")); //$NON-NLS-1$
		filterList.select(filter);
	}

	private void handleResourceSelection(SelectionChangedEvent event) {
		ISelection sel = event.getSelection();
		if (sel.isEmpty()) {
			this.selection = null;
		} else if (selection instanceof IStructuredSelection) {
			this.selection = (IResource)((IStructuredSelection)sel).getFirstElement();
		}
		updateWidgetEnablements();
	}
	
	/**
	 * Method updateWidgetEnablement.
	 */
	private void updateWidgetEnablements() {
		if (isSingleFolder() && !Path.EMPTY.isValidSegment(folderName)) {
			setPageComplete(false);
			setErrorMessage(Policy.bind("CheckoutAsProjectSelectionPage.invalidFolderName", folderName)); //$NON-NLS-1$
			return;
		}
		boolean complete = selection != null && selection.getType() != IResource.FILE;
		setErrorMessage(null);
		setPageComplete(complete);
	}
	
	/**
	 * Returns the selection.
	 * @return IResource
	 */
	public IResource getSelection() {
		return selection;
	}

	/**
	 * Returns the folderName.
	 * @return String
	 */
	public String getFolderName() {
		return folderName;
	}

	private void updateTreeContents(int selected) {
		try {
			if (selected == 0) {
				tree.setInput(new AdaptableResourceList(getProjects(getRepository(), true)));
			} else if (selected == 1) {
				tree.setInput(new AdaptableResourceList(getProjects(null, true)));
			} else if (selected == 2) {
				tree.setInput(new AdaptableResourceList(getProjects(getRepository(), false)));
			}
		} catch (CVSException e) {
			CVSUIPlugin.log(e.getStatus());
		}
	}
			
	/**
	 * Method getValidTargetProjects returns the et of projects that match the provided criteria.
	 * @return IResource
	 */
	private IProject[] getProjects(String root, boolean unshared) throws CVSException {
		List validTargets = new ArrayList();
		try {
			IResource[] projects = ResourcesPlugin.getWorkspace().getRoot().members();
			for (int i = 0; i < projects.length; i++) {
				IResource resource = projects[i];
				if (resource instanceof IProject) {
					IProject project = (IProject) resource;
					if (project.isAccessible()) {
						RepositoryProvider provider = RepositoryProvider.getProvider(project);
						if (provider == null && unshared) {
							validTargets.add(project);
						} else if (provider != null && provider.getID().equals(CVSProviderPlugin.getTypeId())) {
							ICVSFolder cvsFolder = CVSWorkspaceRoot.getCVSFolderFor(project);
							FolderSyncInfo info = cvsFolder.getFolderSyncInfo();
							if (root != null && root.equals(info.getRoot())) {
								validTargets.add(project);
							}
						}
					}
				}
			}
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
		return (IProject[]) validTargets.toArray(new IProject[validTargets.size()]);
	}
	
	public IContainer getLocalFolder() {
		if (Path.EMPTY.isValidSegment(folderName)) {
			return ((IContainer)getSelection()).getFolder(new Path(folderName));
		} else {
			return null;
		}
	}
	
	/**
	 * Returns the recurse.
	 * @return boolean
	 */
	public boolean isRecurse() {
		return recurse;
	}
	
	private void handleFilterSelection() {
		filter = filterList.getSelectionIndex();
		updateTreeContents(filter);
	}

}
