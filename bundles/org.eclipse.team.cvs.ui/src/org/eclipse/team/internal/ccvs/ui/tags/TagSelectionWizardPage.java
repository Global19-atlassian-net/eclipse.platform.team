/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.tags;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.wizards.CVSWizardPage;
import org.eclipse.ui.help.WorkbenchHelp;

/**
 * General tag selection page that allows the selection of a tag
 * for a particular remote folder
 */
public class TagSelectionWizardPage extends CVSWizardPage {

	private CVSTag selectedTag;
	
	// Needed to dynamicaly create refresh buttons
	private Composite composite;
	
	private int includeFlags;
	
	// Fields for allowing the use of the tag from the local workspace
	boolean allowNoTag = false;
	private Button useResourceTagButton;
	private Button selectTagButton;
	private boolean useResourceTag = false;
	private String helpContextId;
    private TagSelectionArea tagArea;
    private String tagLabel;
    private TagSource tagSource;
	
	public TagSelectionWizardPage(String pageName, String title, ImageDescriptor titleImage, String description, TagSource tagSource, int includeFlags) {
		super(pageName, title, titleImage, description);
        this.tagSource = tagSource;
		this.includeFlags = includeFlags;
	}

	/**
	 * Set the help context for the tag selection page. 
	 * This method must be invoked before <code>createControl</code>
	 * @param helpContextId the help context id
	 */
	public void setHelpContxtId(String helpContextId) {
		this.helpContextId = helpContextId;
	}
	
	/**
	 * Set the label to appear over the tag list/tree.
	 * If not set, a default will be used.
	 * 	 * This method must be invoked before <code>createControl</code>
	 * @param tagLabel the label to appear over the tag list/tree
	 */
    public void setTagLabel(String tagLabel) {
        this.tagLabel = tagLabel;
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		composite = createComposite(parent, 1);
		setControl(composite);
		
		// set F1 help
		if (helpContextId != null)
			WorkbenchHelp.setHelp(composite, helpContextId);
		
		if (allowNoTag) {
			SelectionListener listener = new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					useResourceTag = useResourceTagButton.getSelection();
					updateEnablement();
				}
			};
			useResourceTag = true;
			useResourceTagButton = createRadioButton(composite, Policy.bind("TagSelectionWizardPage.0"), 1); //$NON-NLS-1$
			selectTagButton = createRadioButton(composite, Policy.bind("TagSelectionWizardPage.1"), 1); //$NON-NLS-1$
			useResourceTagButton.setSelection(useResourceTag);
			selectTagButton.setSelection(!useResourceTag);
			useResourceTagButton.addSelectionListener(listener);
			selectTagButton.addSelectionListener(listener);
		}
		
		createTagArea();
		updateEnablement();
		Dialog.applyDialogFont(parent);	
	}
	
	private void createTagArea() {
		tagArea = new TagSelectionArea(getShell(), tagSource, includeFlags, null);
	    if (tagLabel != null)
	        tagArea.setTagAreaLabel(tagLabel);
	    tagArea.setRunnableContext(getContainer());
		tagArea.createArea(composite);
		tagArea.addPropertyChangeListener(new IPropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                if (event.getProperty().equals(TagSelectionArea.SELECTED_TAG)) {
                    selectedTag = tagArea.getSelection();
    				updateEnablement();
                } else if (event.getProperty().equals(TagSelectionArea.OPEN_SELECTED_TAG)) {
                    if (selectedTag != null)
                        gotoNextPage();
                }

            }
        });
		refreshTagArea();
    }

    private void refreshTagArea() {
        if (tagArea != null) {
            tagArea.refresh();
            tagArea.setSelection(selectedTag);
        }
	}
	
	protected void updateEnablement() {
		tagArea.setEnabled(!useResourceTag);
		setPageComplete(useResourceTag || selectedTag != null);
	}
	
	public CVSTag getSelectedTag() {
		if (useResourceTag) 
			return null;
		return selectedTag;
	}
	
	protected void gotoNextPage() {
		TagSelectionWizardPage.this.getContainer().showPage(getNextPage());
	}
	
	public void setAllowNoTag(boolean b) {
		allowNoTag = b;
	}
	
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible && tagArea != null) {
			tagArea.setFocus();
		}
	}

    /**
     * Set the tag source used by this wizard page
     * @param source the tag source
     */
    public void setTagSource(TagSource source) {
        this.tagSource = source;
        tagArea.setTagSource(tagSource);
        setSelection(null);
        refreshTagArea();
    }

    /**
     * Set the selection of the page to the given tag
     * @param selectedTag
     */
    public void setSelection(CVSTag selectedTag) {
		if (selectedTag == null && (includeFlags & TagSelectionArea.INCLUDE_HEAD_TAG) > 0) {
			this.selectedTag = CVSTag.DEFAULT;
		} else {
		    this.selectedTag = selectedTag;
		}
    }
}
