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
package org.eclipse.team.internal.ui;

 
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.team.core.IIgnoreInfo;
import org.eclipse.team.core.Team;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.help.WorkbenchHelp;
public class IgnorePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private Table ignoreTable;
	private Button addButton;
	private Button removeButton;
	public void init(IWorkbench workbench) {
		setDescription(Policy.bind("IgnorePreferencePage.description")); //$NON-NLS-1$
	}
	
	/**
	 * Creates preference page controls on demand.
	 *
	 * @param parent  the parent for the preference page
	 */
	protected Control createContents(Composite ancestor) {
		
		Composite parent = new Composite(ancestor, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.numColumns = 2;
		parent.setLayout(layout);
		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		parent.setLayoutData(data);
	
		// set F1 help
		WorkbenchHelp.setHelp(parent, IHelpContextIds.IGNORE_PREFERENCE_PAGE);
		
		Label l1 = new Label(parent, SWT.NULL);
		l1.setText(Policy.bind("IgnorePreferencePage.ignorePatterns")); //$NON-NLS-1$
		data = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		data.horizontalSpan = 2;
		l1.setLayoutData(data);
		
		ignoreTable = new Table(parent, SWT.CHECK | SWT.BORDER);
		GridData gd = new GridData(GridData.FILL_BOTH);
		//gd.widthHint = convertWidthInCharsToPixels(30);
		gd.heightHint = 300;
		ignoreTable.setLayoutData(gd);
		ignoreTable.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				handleSelection();
			}
		});
		
		Composite buttons = new Composite(parent, SWT.NULL);
		buttons.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		buttons.setLayout(layout);
		
		addButton = new Button(buttons, SWT.PUSH);
		addButton.setText(Policy.bind("IgnorePreferencePage.add")); //$NON-NLS-1$
		data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		data.heightHint = convertVerticalDLUsToPixels(IDialogConstants.BUTTON_HEIGHT);
		int widthHint = convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
		data.widthHint = Math.max(widthHint, addButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
		addButton.setLayoutData(data);
		addButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				addIgnore();
			}
		});
		
		
		removeButton = new Button(buttons, SWT.PUSH);
		removeButton.setText(Policy.bind("IgnorePreferencePage.remove")); //$NON-NLS-1$
		data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		data.heightHint = convertVerticalDLUsToPixels(IDialogConstants.BUTTON_HEIGHT);
		widthHint = convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
		data.widthHint = Math.max(widthHint, removeButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
		removeButton.setLayoutData(data);
		removeButton.setEnabled(false);
		removeButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				removeIgnore();
			}
		});
		fillTable(Team.getAllIgnores());
		Dialog.applyDialogFont(ancestor);
		return parent;
	}
	/**
	 * Do anything necessary because the OK button has been pressed.
	 *
	 * @return whether it is okay to close the preference page
	 */
	public boolean performOk() {
		int count = ignoreTable.getItemCount();
		String[] patterns = new String[count];
		boolean[] enabled = new boolean[count];
		TableItem[] items = ignoreTable.getItems();
		for (int i = 0; i < count; i++) {
			patterns[i] = items[i].getText();
			enabled[i] = items[i].getChecked();
		}
		Team.setAllIgnores(patterns, enabled);
		TeamUIPlugin.broadcastPropertyChange(new PropertyChangeEvent(this, TeamUI.GLOBAL_IGNORES_CHANGED, null, null));
		return true;
	}
	
	protected void performDefaults() {
		super.performDefaults();
		ignoreTable.removeAll();
		IIgnoreInfo[] ignore = Team.getDefaultIgnores();
		fillTable(ignore);
	}
	
	/**
	 * @param ignore
	 */
	private void fillTable(IIgnoreInfo[] ignore) {
		for (int i = 0; i < ignore.length; i++) {
			IIgnoreInfo info = ignore[i];
			TableItem item = new TableItem(ignoreTable, SWT.NONE);
			item.setText(info.getPattern());
			item.setChecked(info.getEnabled());
		}		
	}

	private void addIgnore() {
		InputDialog dialog = new InputDialog(getShell(), Policy.bind("IgnorePreferencePage.enterPatternShort"), Policy.bind("IgnorePreferencePage.enterPatternLong"), null, null); //$NON-NLS-1$ //$NON-NLS-2$
		dialog.open();
		if (dialog.getReturnCode() != InputDialog.OK) return;
		String pattern = dialog.getValue();
		if (pattern.equals("")) return; //$NON-NLS-1$
		// Check if the item already exists
		TableItem[] items = ignoreTable.getItems();
		for (int i = 0; i < items.length; i++) {
			if (items[i].getText().equals(pattern)) {
				MessageDialog.openWarning(getShell(), Policy.bind("IgnorePreferencePage.patternExistsShort"), Policy.bind("IgnorePreferencePage.patternExistsLong")); //$NON-NLS-1$ //$NON-NLS-2$
				return;
			}
		}
		TableItem item = new TableItem(ignoreTable, SWT.NONE);
		item.setText(pattern);
		item.setChecked(true);
	}
	
	private void removeIgnore() {
		int[] selection = ignoreTable.getSelectionIndices();
		ignoreTable.remove(selection);
	}
	private void handleSelection() {
		if (ignoreTable.getSelectionCount() > 0) {
			removeButton.setEnabled(true);
		} else {
			removeButton.setEnabled(false);
		}
	}
}
