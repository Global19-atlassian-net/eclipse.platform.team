/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.compare.internal;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.ui.texteditor.PropagatingFontFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class ComparePreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
		
	private static final String PREFIX= CompareUIPlugin.PLUGIN_ID + "."; //$NON-NLS-1$
	public static final String SYNCHRONIZE_SCROLLING= PREFIX + "SynchronizeScrolling"; //$NON-NLS-1$
	public static final String SHOW_PSEUDO_CONFLICTS= PREFIX + "ShowPseudoConflicts"; //$NON-NLS-1$
	public static final String INITIALLY_SHOW_ANCESTOR_PANE= PREFIX + "InitiallyShowAncestorPane"; //$NON-NLS-1$
	public static final String TEXT_FONT= PREFIX + "TextFont"; //$NON-NLS-1$


	public ComparePreferencePage() {
		super(GRID);
	}
	
	public static void initDefaults(IPreferenceStore store) {
		store.setDefault(SYNCHRONIZE_SCROLLING, true);
		store.setDefault(SHOW_PSEUDO_CONFLICTS, false);
		store.setDefault(INITIALLY_SHOW_ANCESTOR_PANE, false);
		
		PropagatingFontFieldEditor.startPropagate(store, TEXT_FONT);
	}

	public void init(IWorkbench workbench) {
	}	

	protected IPreferenceStore doGetPreferenceStore() {
		return CompareUIPlugin.getDefault().getPreferenceStore();
	}

	public void createFieldEditors() {
				
		Composite parent= getFieldEditorParent();
			
		{
			BooleanFieldEditor editor= new BooleanFieldEditor(SYNCHRONIZE_SCROLLING,
				Utilities.getString("ComparePreferences.synchronizeScrolling.label"), BooleanFieldEditor.DEFAULT, parent); //$NON-NLS-1$
			addField(editor);	
		}
		
		// three way merging
		{
			BooleanFieldEditor editor= new BooleanFieldEditor(SHOW_PSEUDO_CONFLICTS,
				Utilities.getString("ComparePreferences.showPseudoConflicts.label"), BooleanFieldEditor.DEFAULT, parent); //$NON-NLS-1$
			addField(editor);	
		}
		
		{
			BooleanFieldEditor editor= new BooleanFieldEditor(INITIALLY_SHOW_ANCESTOR_PANE,
				Utilities.getString("ComparePreferences.initiallyShowAncestorPane.label"), BooleanFieldEditor.DEFAULT, parent); //$NON-NLS-1$
			addField(editor);	
		}
		
		{
			PropagatingFontFieldEditor editor= new PropagatingFontFieldEditor(TEXT_FONT,
				Utilities.getString("ComparePreferences.textFont.label"), parent); //$NON-NLS-1$
			addField(editor);
		}
	}
}
