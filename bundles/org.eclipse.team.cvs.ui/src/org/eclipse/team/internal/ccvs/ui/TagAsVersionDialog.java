/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.ui;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.ui.merge.TagElement;
import org.eclipse.team.internal.ccvs.ui.merge.TagRootElement;
import org.eclipse.team.internal.ui.*;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

public class TagAsVersionDialog extends DetailsDialog {

	private ICVSFolder folder;
	
	private Text tagText;
	
	private String tagName = ""; //$NON-NLS-1$
	
	private static final int TABLE_HEIGHT_HINT = 150;
	
	private TableViewer existingVersionTable;
	
	public TagAsVersionDialog(Shell parentShell, String title, ICVSFolder folder) {
		super(parentShell, title);
		this.folder = folder;
	}	

	/**
	 * @see DetailsDialog#createMainDialogArea(Composite)
	 */
	protected void createMainDialogArea(Composite parent) {
		// create message
		Label label = new Label(parent, SWT.WRAP);
		label.setText(Policy.bind("TagAction.enterTag")); //$NON-NLS-1$
		GridData data = new GridData(
			GridData.GRAB_HORIZONTAL |
			GridData.GRAB_VERTICAL |
			GridData.HORIZONTAL_ALIGN_FILL |
			GridData.VERTICAL_ALIGN_CENTER);
		data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);;
		label.setLayoutData(data);
		label.setFont(parent.getFont());

		tagText = new Text(parent, SWT.SINGLE | SWT.BORDER);
		tagText.setLayoutData(new GridData(
			GridData.GRAB_HORIZONTAL |
			GridData.HORIZONTAL_ALIGN_FILL));
		tagText.addModifyListener(
			new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					tagName = tagText.getText();
					updateEnablements();
				}
			}
		);
		// Add F1 help
		WorkbenchHelp.setHelp(parent, IHelpContextIds.TAG_AS_VERSION_DIALOG);
	}

	protected TableViewer createTable(Composite parent) {
		Table table = new Table(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.heightHint = TABLE_HEIGHT_HINT;
		table.setLayoutData(data);
		TableLayout layout = new TableLayout();
		layout.addColumnData(new ColumnWeightData(100, true));
		table.setLayout(layout);
		TableColumn col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		return new TableViewer(table);
	}
	
	/**
	 * @see DetailsDialog#createDropDownDialogArea(Composite)
	 */
	protected Composite createDropDownDialogArea(Composite parent) {
		// create a composite with standard margins and spacing
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		composite.setFont(parent.getFont());
		
		Label label = new Label(composite, SWT.WRAP);
		label.setText(Policy.bind("TagAction.existingVersions")); //$NON-NLS-1$
		GridData data = new GridData(
			GridData.GRAB_HORIZONTAL |
			GridData.GRAB_VERTICAL |
			GridData.HORIZONTAL_ALIGN_FILL |
			GridData.VERTICAL_ALIGN_CENTER);
		data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);;
		label.setLayoutData(data);
		label.setFont(composite.getFont());
		
		existingVersionTable = createTable(composite);
		existingVersionTable.setContentProvider(new WorkbenchContentProvider());
		existingVersionTable.setLabelProvider(new WorkbenchLabelProvider());
		existingVersionTable.setSorter(new ViewerSorter() {
			public int compare(Viewer v, Object o1, Object o2) {
				int result = super.compare(v, o1, o2);
				if (o1 instanceof TagElement && o2 instanceof TagElement) {
					return -result;
				}
				return result;
			}
		});
		existingVersionTable.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection)existingVersionTable.getSelection();
				if(!selection.isEmpty()) {
					TagElement element = (TagElement)((IStructuredSelection)existingVersionTable.getSelection()).getFirstElement();
					if(element!=null) {
						tagText.setText(element.getTag().getName());
					}
				}
			}
		});

		Runnable afterRefresh = new Runnable() {
			public void run() {
				getShell().getDisplay().syncExec(new Runnable() {
					public void run() {
						existingVersionTable.refresh();
					}
				});
			}
		};
		
		Runnable afterConfigure = new Runnable() {
			public void run() {
				getShell().getDisplay().syncExec(new Runnable() {
					public void run() {
						existingVersionTable.setInput(new TagRootElement(folder, CVSTag.VERSION));
					}
				});
			}
		};
		
		TagConfigurationDialog.createTagDefinitionButtons(getShell(), composite, new ICVSFolder[] {folder}, 
														  convertVerticalDLUsToPixels(IDialogConstants.BUTTON_HEIGHT), 
														  convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH),
														  afterRefresh, afterConfigure);
		
		existingVersionTable.setInput(new TagRootElement(folder, CVSTag.VERSION));
		
		return composite;
	}
	
	/**
	 * Validates tag name
	 */
	protected void updateEnablements() {
		String message = null;
		boolean exists = false;
		if(tagName.length() == 0) {
			message = ""; //$NON-NLS-1$
		} else {		
			IStatus status = CVSTag.validateTagName(tagName);
			if (!status.isOK()) {
				message = status.getMessage();
			}
		}
		setPageComplete(message == null);
		setErrorMessage(message);
	}
	
	/**
	 * Returns the tag name entered into this dialog
	 */
	public String getTagName() {
		return tagName;
	}
}