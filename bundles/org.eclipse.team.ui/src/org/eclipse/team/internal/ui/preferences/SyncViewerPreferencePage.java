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
package org.eclipse.team.internal.ui.preferences;

import java.text.Collator;
import java.text.DateFormat;
import java.util.*;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.*;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.internal.ui.*;
import org.eclipse.ui.*;

/**
 * This area provides the widgets for providing the CVS commit comment
 */
public class SyncViewerPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage, IPreferenceIds {
	
	private BooleanFieldEditor bkgRefresh = null;
	private BooleanFieldEditor bkgScheduledRefresh = null;
	private IntegerFieldEditor2 scheduledDelay = null;
	private BooleanFieldEditor compressFolders = null;
	private BooleanFieldEditor showSyncInLabels = null;
	private BooleanFieldEditor promptWithChanges = null;
	private BooleanFieldEditor promptWhenNoChanges = null;
	private BooleanFieldEditor promptWithChangesBkg = null;
	private BooleanFieldEditor promptWhenNoChangesBkg = null;
	
	private Group refreshGroup;
	
	private static class PerspectiveDescriptorComparator implements Comparator {
		/*
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(Object o1, Object o2) {
			if (o1 instanceof IPerspectiveDescriptor && o2 instanceof IPerspectiveDescriptor) {
				String id1= ((IPerspectiveDescriptor)o1).getLabel();
				String id2= ((IPerspectiveDescriptor)o2).getLabel();
				return Collator.getInstance().compare(id1, id2);
			}
			return 0;
		}
	}
	
	class IntegerFieldEditor2 extends IntegerFieldEditor {
			/* (non-Javadoc)
			 * @see org.eclipse.jface.preference.FieldEditor#createControl(org.eclipse.swt.widgets.Composite)
			 */
			protected void createControl(Composite parent) {
				super.createControl(parent);
			}

			public IntegerFieldEditor2(String name, String labelText, Composite parent, int size) {
				super(name, labelText, parent, size);
			}

			protected boolean checkState() {
				Text control= getTextControl();
				if (!control.isEnabled()) {
					clearErrorMessage();
					return true;
				}
				return super.checkState();
			}
		
			/**
			 * Overrode here to be package visible.
			 */
			protected void refreshValidState() {
				super.refreshValidState();
			}
		
			/**
			 * Only store if the text control is enabled
			 * @see FieldEditor#doStore()
			 */
			protected void doStore() {
				Text text = getTextControl();
				if (text.isEnabled()) {
					super.doStore();
				}
			}
			/**
			 * Clears the error message from the message line if the error
			 * message is the error message from this field editor.
			 */
			protected void clearErrorMessage() {
				if (getPreferencePage() != null) {
					String message= getPreferencePage().getErrorMessage();
					if (message != null) {
						if(getErrorMessage().equals(message)) {
							super.clearErrorMessage();
						}
					
					} else {
						super.clearErrorMessage();
					}
				}
			}
		}
	
	class BooleanFieldEditor2 extends BooleanFieldEditor {
		public BooleanFieldEditor2(String name, String labelText, int style, Composite parent) {
			super(name, labelText, style, parent);
		}

		protected void refreshValidState() {
			updateEnablements();
		}
	}
	
	public SyncViewerPreferencePage() {
		super(GRID);
		setTitle(Policy.bind("SyncViewerPreferencePage.6")); //$NON-NLS-1$
		setDescription(Policy.bind("SyncViewerPreferencePage.7")); //$NON-NLS-1$
		setPreferenceStore(TeamUIPlugin.getPlugin().getPreferenceStore());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);
		//WorkbenchHelp.setHelp(getControl(), IDebugHelpContextIds.CONSOLE_PREFERENCE_PAGE);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.FieldEditorPreferencePage#createFieldEditors()
	 */
	public void createFieldEditors() {
		
		GridData data;
		Group displayGroup = createGroup(getFieldEditorParent(), Policy.bind("SyncViewerPreferencePage.8")); 		 //$NON-NLS-1$

		compressFolders = new BooleanFieldEditor(SYNCVIEW_COMPRESS_FOLDERS, Policy.bind("SyncViewerPreferencePage.9"), SWT.NONE, displayGroup); //$NON-NLS-1$
		addField(compressFolders);
		showSyncInLabels = new BooleanFieldEditor(SYNCVIEW_VIEW_SYNCINFO_IN_LABEL, Policy.bind("SyncViewerPreferencePage.19"), SWT.NONE, displayGroup); //$NON-NLS-1$
		addField(showSyncInLabels);
		
		refreshGroup = createGroup(getFieldEditorParent(), Policy.bind("SyncViewerPreferencePage.11")); //$NON-NLS-1$
		
		bkgRefresh = new BooleanFieldEditor(SYNCVIEW_BACKGROUND_SYNC, Policy.bind("SyncViewerPreferencePage.12"), SWT.NONE, refreshGroup); //$NON-NLS-1$
		addField(bkgRefresh);
		
		bkgScheduledRefresh = new BooleanFieldEditor2(SYNCVIEW_SCHEDULED_SYNC, Policy.bind("SyncViewerPreferencePage.13"), SWT.NONE, refreshGroup); //$NON-NLS-1$
		addField(bkgScheduledRefresh);
		
		scheduledDelay = new IntegerFieldEditor2(SYNCVIEW_DELAY, Policy.bind("SyncViewerPreferencePage.14"), refreshGroup, 2); //$NON-NLS-1$
		addField(scheduledDelay);
				
		updateLastRunTime(createLabel(refreshGroup, null, 0));
		
		Group promptGroup = createGroup(getFieldEditorParent(), Policy.bind("SyncViewerPreferencePage.30")); //$NON-NLS-1$
		
		promptWhenNoChanges = new BooleanFieldEditor(SYNCVIEW_VIEW_PROMPT_WHEN_NO_CHANGES, Policy.bind("SyncViewerPreferencePage.16"), SWT.NONE, promptGroup); //$NON-NLS-1$
		addField(promptWhenNoChanges);
		promptWithChanges = new BooleanFieldEditor(SYNCVIEW_VIEW_PROMPT_WITH_CHANGES, Policy.bind("SyncViewerPreferencePage.17"), SWT.NONE, promptGroup); //$NON-NLS-1$
		addField(promptWithChanges);
		
		promptWhenNoChangesBkg = new BooleanFieldEditor(SYNCVIEW_VIEW_BKG_PROMPT_WHEN_NO_CHANGES, Policy.bind("SyncViewerPreferencePage.31"), SWT.NONE, promptGroup); //$NON-NLS-1$
		addField(promptWhenNoChangesBkg);
		promptWithChangesBkg = new BooleanFieldEditor(SYNCVIEW_VIEW_BKG_PROMPT_WITH_CHANGES, Policy.bind("SyncViewerPreferencePage.32"), SWT.NONE, promptGroup); //$NON-NLS-1$
		addField(promptWithChangesBkg);
				
		Group perspectiveGroup = createGroup(getFieldEditorParent(), Policy.bind("SyncViewerPreferencePage.15")); //$NON-NLS-1$
		
		createLabel(perspectiveGroup, Policy.bind("SynchronizationViewPreference.defaultPerspectiveDescription"), 1); //$NON-NLS-1$
		
		handleDeletedPerspectives();
		String[][] perspectiveNamesAndIds = getPerspectiveNamesAndIds();
		ComboFieldEditor comboEditor= new ComboFieldEditor(
			SYNCVIEW_DEFAULT_PERSPECTIVE,
			Policy.bind("SynchronizationViewPreference.defaultPerspectiveLabel"), //$NON-NLS-1$
			perspectiveNamesAndIds,
			perspectiveGroup);
		addField(comboEditor);

		Dialog.applyDialogFont(getFieldEditorParent());
		updateLayout(displayGroup);
		updateLayout(perspectiveGroup);
		updateLayout(refreshGroup);
		getFieldEditorParent().layout(true);	
	}
	
	private Label createLabel(Composite parent, String title, int spacer) {
		GridData data;
		Label l = new Label(parent, SWT.WRAP);
		l.setFont(parent.getFont());
		data = new GridData();
		data.horizontalSpan = 2;
		if(spacer != 0) {
			data.verticalSpan = spacer;
		}
		data.horizontalAlignment = GridData.FILL;		
		l.setLayoutData(data);
		if(title != null) {
			l.setText(title); //$NON-NLS-1$
		}
		return l;
	}

	private Group createGroup(Composite parent, String title) {
		Group display = new Group(parent, SWT.NONE);
		updateLayout(display);
		GridData data = new GridData();
		data.horizontalSpan = 2;
		data.horizontalAlignment = GridData.FILL;
		display.setLayoutData(data);						
		display.setText(title);
		return display;
	}
	
	private void updateLayout(Composite composite) {
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginWidth = 5;
		layout.marginHeight =5;
		layout.horizontalSpacing = 5;
		layout.verticalSpacing = 5;
		composite.setLayout(layout);
	}

	private void updateLastRunTime(Label label) {
		String text;
		long mills = 0;
		if(mills == 0) {
			String never = Policy.bind("SyncViewPreferencePage.lastRefreshRunNever"); //$NON-NLS-1$
			text = Policy.bind("SyncViewPreferencePage.lastRefreshRun", never); //$NON-NLS-1$
		} else {
			Date lastTimeRun = new Date(mills);
			String sLastTimeRun = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(lastTimeRun);
			text = Policy.bind("SyncViewPreferencePage.lastRefreshRun", sLastTimeRun); //$NON-NLS-1$
		}
		label.setText(text);		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		if(event.getSource() == bkgScheduledRefresh || event.getSource() == scheduledDelay) {			
			updateEnablements();	
		}
		super.propertyChange(event);
	}
			
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.IPreferencePage#performOk()
	 */
	public boolean performOk() {
		TeamUIPlugin.getPlugin().savePluginPreferences();
		return super.performOk();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.FieldEditorPreferencePage#checkState()
	 */
	protected void initialize() {
		super.initialize();		
		updateEnablements();
	}

	protected void updateEnablements() {
		boolean enabled = bkgScheduledRefresh.getBooleanValue();
		scheduledDelay.setEnabled(enabled, refreshGroup);
		scheduledDelay.refreshValidState();
	}
	
	/**
	 * Return a 2-dimensional array of perspective names and ids.
	 */
	private String[][] getPerspectiveNamesAndIds() {
	
		IPerspectiveRegistry registry= PlatformUI.getWorkbench().getPerspectiveRegistry();
		IPerspectiveDescriptor[] perspectiveDescriptors= registry.getPerspectives();
	
		Arrays.sort(perspectiveDescriptors, new PerspectiveDescriptorComparator());
	
		String[][] table = new String[perspectiveDescriptors.length + 1][2];
		table[0][0] = Policy.bind("SynchronizationViewPreference.defaultPerspectiveNone"); //$NON-NLS-1$;
		table[0][1] = SYNCVIEW_DEFAULT_PERSPECTIVE_NONE;
		for (int i = 0; i < perspectiveDescriptors.length; i++) {
			table[i + 1][0] = perspectiveDescriptors[i].getLabel();
			table[i + 1][1] = perspectiveDescriptors[i].getId();
		}
		return table;
	}

	private static void handleDeletedPerspectives() {
		IPreferenceStore store= TeamUIPlugin.getPlugin().getPreferenceStore();
		String id= store.getString(SYNCVIEW_DEFAULT_PERSPECTIVE);
		if (PlatformUI.getWorkbench().getPerspectiveRegistry().findPerspectiveWithId(id) == null) {
			store.putValue(SYNCVIEW_DEFAULT_PERSPECTIVE, SYNCVIEW_DEFAULT_PERSPECTIVE_NONE);
		}
	}	
}