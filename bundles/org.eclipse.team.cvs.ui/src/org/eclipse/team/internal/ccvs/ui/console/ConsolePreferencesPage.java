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
package org.eclipse.team.internal.ccvs.ui.console;

import org.eclipse.jface.preference.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.help.WorkbenchHelp;

public class ConsolePreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public ConsolePreferencesPage() {
		super(GRID);
		setPreferenceStore(CVSUIPlugin.getPlugin().getPreferenceStore());
	}
	private ColorFieldEditor commandColorEditor;
	private ColorFieldEditor messageColorEditor;
	private ColorFieldEditor errorColorEditor;
	private BooleanFieldEditor showOnMessage;

	protected void createFieldEditors() {
		Composite composite = getFieldEditorParent();
		createLabel(composite, Policy.bind("ConsolePreferencePage.consoleColorSettings")); //$NON-NLS-1$
				
		commandColorEditor = createColorFieldEditor(ICVSUIConstants.PREF_CONSOLE_COMMAND_COLOR,
			Policy.bind("ConsolePreferencePage.commandColor"), composite); //$NON-NLS-1$
		addField(commandColorEditor);
		
		messageColorEditor = createColorFieldEditor(ICVSUIConstants.PREF_CONSOLE_MESSAGE_COLOR,
			Policy.bind("ConsolePreferencePage.messageColor"), composite); //$NON-NLS-1$
		addField(messageColorEditor);
		
		errorColorEditor = createColorFieldEditor(ICVSUIConstants.PREF_CONSOLE_ERROR_COLOR,
			Policy.bind("ConsolePreferencePage.errorColor"), composite); //$NON-NLS-1$
		addField(errorColorEditor);
		
		showOnMessage = new BooleanFieldEditor(ICVSUIConstants.PREF_CONSOLE_SHOW_ON_MESSAGE, Policy.bind("ConsolePreferencesPage.4"), composite); //$NON-NLS-1$
		addField(showOnMessage);
		
		WorkbenchHelp.setHelp(composite, IHelpContextIds.CONSOLE_PREFERENCE_PAGE);
	}

	/**
	 * Utility method that creates a label instance
	 * and sets the default layout data.
	 *
	 * @param parent  the parent for the new label
	 * @param text  the text for the new label
	 * @return the new label
	 */
	private Label createLabel(Composite parent, String text) {
		Label label = new Label(parent, SWT.LEFT);
		label.setText(text);
		GridData data = new GridData();
		data.horizontalSpan = 2;
		data.horizontalAlignment = GridData.FILL;
		label.setLayoutData(data);
		return label;
	}
	/**
	 * Creates a new color field editor.
	 */
	private ColorFieldEditor createColorFieldEditor(String preferenceName, String label, Composite parent) {
		ColorFieldEditor editor = new ColorFieldEditor(preferenceName, label, parent);
		editor.setPreferencePage(this);
		editor.setPreferenceStore(getPreferenceStore());
		return editor;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferencePage#performOk()
	 */
	public boolean performOk() {
		CVSUIPlugin.getPlugin().savePluginPreferences();
		return super.performOk();
	}
}