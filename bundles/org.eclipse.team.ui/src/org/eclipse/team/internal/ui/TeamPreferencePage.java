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
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.help.WorkbenchHelp;

public class TeamPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	Button syncModeButton;
	
	public TeamPreferencePage() {
		setDescription(Policy.bind("TeamPreferencePage.General_settings_for_Team_support_1")); //$NON-NLS-1$
	}
	
	/**
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);

		// set F1 help
		WorkbenchHelp.setHelp(composite, IHelpContextIds.TEAM_PREFERENCE_PAGE);
		
		// GridLayout
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		composite.setLayout(layout);

		// GridData
		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		composite.setLayoutData(data);
			
		// Create the checkbox for sync mode
		syncModeButton = createCheckBox(composite, Policy.bind("TeamPreferencePage.&Use_Incoming/Outgoing_mode_when_synchronizing_2")); //$NON-NLS-1$

		initializeValues();
		Dialog.applyDialogFont(parent);
		return composite;
	}
	/**
	 * Creates an new checkbox instance and sets the default
	 * layout data.
	 *
	 * @param group  the composite in which to create the checkbox
	 * @param label  the string to set into the checkbox
	 * @return the new checkbox
	 */
	private Button createCheckBox(Composite group, String label) {
		Button button = new Button(group, SWT.CHECK | SWT.LEFT);
		button.setText(label);
		GridData data = new GridData();
		data.horizontalSpan = 1;
		button.setLayoutData(data);
		return button;
	}
	/**
	 * Returns preference store that belongs to the our plugin.
	 * This is important because we want to store
	 * our preferences separately from the desktop.
	 *
	 * @return the preference store for this plugin
	 */
	protected IPreferenceStore doGetPreferenceStore() {
		return TeamUIPlugin.getPlugin().getPreferenceStore();
	}
	/**
	 * Defaults was clicked. Restore the CVS preferences to
	 * their default values
	 */
	protected void performDefaults() {
		super.performDefaults();
		IPreferenceStore store = getPreferenceStore();
		syncModeButton.setSelection(store.getDefaultBoolean(ISharedImages.PREF_ALWAYS_IN_INCOMING_OUTGOING));
	}
	/**
	 * OK was clicked. Store the CVS preferences.
	 *
	 * @return whether it is okay to close the preference page
	 */
	public boolean performOk() {
		IPreferenceStore store = getPreferenceStore();
		store.setValue(ISharedImages.PREF_ALWAYS_IN_INCOMING_OUTGOING, syncModeButton.getSelection());
		TeamUIPlugin.getPlugin().savePluginPreferences();
		return true;
	}
	/**
	 * Initializes states of the controls from the preference store.
	 */
	private void initializeValues() {
		IPreferenceStore store = getPreferenceStore();
		syncModeButton.setSelection(store.getBoolean(ISharedImages.PREF_ALWAYS_IN_INCOMING_OUTGOING));
	}
   /**
	* @see IWorkbenchPreferencePage#init(IWorkbench)
	*/
	public void init(IWorkbench workbench) {
	}
}
