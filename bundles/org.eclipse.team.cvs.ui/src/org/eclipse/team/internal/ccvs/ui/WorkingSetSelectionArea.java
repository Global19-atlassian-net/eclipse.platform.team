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

import java.util.Iterator;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IWorkingSetSelectionDialog;

/**
 * @author Administrator
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class WorkingSetSelectionArea extends DialogArea {

	private Button workingSetButton;
	private Combo mruList;
	private Button selectButton;
	private IWorkingSet workingSet, oldWorkingSet;
	
	private static final String USE_WORKING_SET = "UseWorkingSet"; //$NON-NLS-1$
	public static final String SELECTED_WORKING_SET = "SelectedWorkingSet"; //$NON-NLS-1$
	
	/*
	 * Used to update the mru list box when working sets are
	 * renamed in the working set selection dialog.
	 */
	private IPropertyChangeListener workingSetChangeListener = new IPropertyChangeListener() {
		public void propertyChange(PropertyChangeEvent event) {
			String property = event.getProperty();
			Object newValue = event.getNewValue();

			if (IWorkingSetManager.CHANGE_WORKING_SET_NAME_CHANGE.equals(property) &&
				newValue instanceof IWorkingSet) {
				String newName = ((IWorkingSet) newValue).getName();
				int count = mruList.getItemCount();
				for (int i = 0; i < count; i++) {
					String item = mruList.getItem(i);
					IWorkingSet workingSet = (IWorkingSet) mruList.getData(item);
					if (workingSet == newValue) {
						boolean isTopItem = (mruList.getData(mruList.getText()) == workingSet);
						mruList.remove(i);
						mruList.add(newName, i);
						mruList.setData(newName, workingSet);
						if (isTopItem) {
							mruList.setText(newName);
						}
						break;
					}
				}
			}
		}
	};
	
	public WorkingSetSelectionArea(Dialog parentDialog) {
		super(parentDialog, null);
	}
	
	public WorkingSetSelectionArea(Dialog parentDialog, IDialogSettings settings) {
		super(parentDialog, settings);
	}
	
	/**
	 * Overrides method in Dialog
	 *
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(Composite)
	 */
	public Control createArea(Composite parent) {
		Composite composite = createComposite(parent, 2);
		initializeDialogUnits(composite);
		GridData data = new GridData(GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_CENTER);
		composite.setLayoutData(data);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.numColumns = 2;
		composite.setLayout(layout);

		// Create the checkbox to enable/disable working set use
		workingSetButton = createCheckbox(composite, Policy.bind("WorkingSetSelectionArea.workingSet"), 2); //$NON-NLS-1$
		workingSetButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleWorkingSetButtonSelection();
			}
		});
		if (settings != null)
			workingSetButton.setSelection(settings.getBoolean(USE_WORKING_SET));

		// Create the combo/button which allows working set selection
		mruList = new Combo(composite, SWT.DROP_DOWN | SWT.READ_ONLY);
		data = new GridData(GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_CENTER);
		mruList.setLayoutData(data);
		mruList.setFont(composite.getFont());
		mruList.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleMruSelection();
			}
		});
		selectButton = createButton(composite, Policy.bind("WorkingSetSelectionArea.workingSetOther")); //$NON-NLS-1$
		selectButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				handleWorkingSetSelection();
			}
		});

		initializeMru();
		initializeWorkingSet();
		return composite;
	}

	/**
	 * Method handleMruSelection.
	 */
	private void handleMruSelection() {
		String selectedWorkingSet = mruList.getText();
		oldWorkingSet = workingSet;
		workingSet = (IWorkingSet) mruList.getData(selectedWorkingSet);
		if (settings != null)
			settings.put(SELECTED_WORKING_SET, selectedWorkingSet);
		handleWorkingSetChange();
	}
	
	/**
	 * Opens the working set selection dialog if the "Other..." item
	 * is selected in the most recently used working set list.
	 */
	private void handleWorkingSetSelection() {
		IWorkingSetSelectionDialog dialog = PlatformUI.getWorkbench().getWorkingSetManager().createWorkingSetSelectionDialog(getShell(), false);
		IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
		IWorkingSet workingSet = workingSetManager.getWorkingSet(mruList.getText());

		if (workingSet != null) {
			dialog.setSelection(new IWorkingSet[]{workingSet});
		}
		// add a change listener to detect a working set name change
		workingSetManager.addPropertyChangeListener(workingSetChangeListener);
		if (dialog.open() == Window.OK) {
			IWorkingSet[] result = dialog.getSelection();
			if (result != null && result.length > 0) {
				workingSet = result[0];
				String workingSetName = workingSet.getName();
				if (mruList.indexOf(workingSetName) != -1) {
					mruList.remove(workingSetName);
				}
				mruList.add(workingSetName, 0);
				mruList.setText(workingSetName);
				mruList.setData(workingSetName, workingSet);
				handleMruSelection();
			}
			else {
				workingSet = null;
			}
			// remove deleted working sets from the mru list box
			String[] mruNames = mruList.getItems();
			for (int i = 0; i < mruNames.length; i++) {
				if (workingSetManager.getWorkingSet(mruNames[i]) == null) {
					mruList.remove(mruNames[i]);
				}
			}
		}
		workingSetManager.removePropertyChangeListener(workingSetChangeListener);
	}
	
	/**
	 * Sets the enabled state of the most recently used working set list
	 * based on the checked state of the working set check box.
	 */
	private void handleWorkingSetButtonSelection() {
		boolean useWorkingSet = workingSetButton.getSelection();
		if (settings != null)
			settings.put(USE_WORKING_SET, useWorkingSet);
		mruList.setEnabled(useWorkingSet);
		selectButton.setEnabled(useWorkingSet);
		if (useWorkingSet && mruList.getSelectionIndex() >= 0) {
			handleMruSelection();
		}
	}
	
	private void handleWorkingSetChange() {
		PropertyChangeEvent event = new PropertyChangeEvent(this, SELECTED_WORKING_SET, oldWorkingSet, workingSet);
		for (Iterator iter = listeners.iterator(); iter.hasNext();) {
			IPropertyChangeListener listener = (IPropertyChangeListener) iter.next();
			listener.propertyChange(event);
		}
	}
	
	/**
	 * Populates the most recently used working set list with MRU items from
	 * the working set manager as well as adds an item to enable selection of
	 * a working set not in the MRU list.
	 */
	private void initializeMru() {
		IWorkingSet[] workingSets = PlatformUI.getWorkbench().getWorkingSetManager().getRecentWorkingSets();

		for (int i = 0; i < workingSets.length; i++) {
			String workingSetName = workingSets[i].getName();
			mruList.add(workingSetName);
			mruList.setData(workingSetName, workingSets[i]);
		}
		if (workingSets.length > 0) {
			mruList.setText(workingSets[0].getName());
		}
	}
	
	/**
	 * Initializes the state of the working set part of the dialog.
	 */
	private void initializeWorkingSet() {
		if (workingSet == null && settings != null && settings.getBoolean(USE_WORKING_SET)) {
			setWorkingSet(PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSet(settings.get(SELECTED_WORKING_SET)));
		}
		workingSetButton.setSelection(workingSet != null);
		handleWorkingSetButtonSelection();
		if (workingSet != null && mruList.indexOf(workingSet.getName()) != -1) {
			mruList.setText(workingSet.getName());
		}
		handleWorkingSetChange();
	}
	
	/**
	 * Returns the selected working set or null if none is selected.
	 *
	 * @return the selected working set or null if none is selected.
	 */
	public IWorkingSet getWorkingSet() {
		return workingSet;
	}
	
	/**
	 * Indicate that the selected working set is actually being used so it can
	 * be added to the "most recently used" list.
	 */
	public void useSelectedWorkingSet() {
		// Add the selected working set to the MRU list before returning it
		if (workingSet != null) {
			PlatformUI.getWorkbench().getWorkingSetManager().addRecentWorkingSet(workingSet);
		}
	}
	
	/**
	 * Sets the working set that should be selected in the most recently
	 * used working set list.
	 *
	 * @param workingSet the working set that should be selected.
	 * 	has to exist in the list returned by
	 * 	org.eclipse.ui.IWorkingSetManager#getRecentWorkingSets().
	 * 	Must not be null.
	 */
	public void setWorkingSet(IWorkingSet workingSet) {
		oldWorkingSet = this.workingSet;
		this.workingSet = workingSet;

		if (workingSetButton != null && mruList != null) {
			initializeWorkingSet();
		}
	}
	
}
