package org.eclipse.team.internal.ccvs.ui.wizards;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.IHelpContextIds;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.internal.model.AdaptableList;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * First wizard page for importing a project into a CVS repository.
 * This page prompts the user to select an existing repo or create a new one.
 * If the user selected an existing repo, then getLocation() will return it.
 */
public class RepositorySelectionPage extends CVSWizardPage {
	private TableViewer table;
	private Button useExistingRepo;
	private Button useNewRepo;
	
	private ICVSRepositoryLocation result;
	
	/**
	 * RepositorySelectionPage constructor.
	 * 
	 * @param pageName  the name of the page
	 * @param title  the title of the page
	 * @param titleImage  the image for the page
	 */
	public RepositorySelectionPage(String pageName, String title, ImageDescriptor titleImage) {
		super(pageName, title, titleImage);
	}
	protected TableViewer createTable(Composite parent, int span) {
		Table table = new Table(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.horizontalSpan = span;
		table.setLayoutData(data);
		TableLayout layout = new TableLayout();
		layout.addColumnData(new ColumnWeightData(100, true));
		table.setLayout(layout);
		TableColumn col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
	
		return new TableViewer(table);
	}
	/**
	 * Creates the UI part of the page.
	 * 
	 * @param parent  the parent of the created widgets
	 */
	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 2);
		// set F1 help
		WorkbenchHelp.setHelp(composite, IHelpContextIds.SHARING_SELECT_REPOSITORY_PAGE);
		
		Label description = new Label(composite, SWT.WRAP);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.horizontalSpan = 2;
		data.widthHint = 350;
		description.setLayoutData(data);
		description.setText(Policy.bind("RepositorySelectionPage.description")); //$NON-NLS-1$

		useExistingRepo = createRadioButton(composite, Policy.bind("RepositorySelectionPage.useExisting"), 2); //$NON-NLS-1$
		table = createTable(composite, 2);
		table.setContentProvider(new WorkbenchContentProvider());
		table.setLabelProvider(new WorkbenchLabelProvider());
		table.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				result = (ICVSRepositoryLocation)((IStructuredSelection)table.getSelection()).getFirstElement();
				setPageComplete(true);
			}
		});
		useNewRepo = createRadioButton(composite, Policy.bind("RepositorySelectionPage.useNew"), 2); //$NON-NLS-1$

		useExistingRepo.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				if (useNewRepo.getSelection()) {
					table.getTable().setEnabled(false);
					result = null;
				} else {
					table.getTable().setEnabled(true);
					result = (ICVSRepositoryLocation)((IStructuredSelection)table.getSelection()).getFirstElement();
				}
				setPageComplete(true);
			}
		});

		setControl(composite);

		initializeValues();
	}
	/**
	 * Initializes states of the controls.
	 */
	private void initializeValues() {
		ICVSRepositoryLocation[] locations = CVSUIPlugin.getPlugin().getRepositoryManager().getKnownRepositoryLocations();
		AdaptableList input = new AdaptableList(locations);
		table.setInput(input);
		if (locations.length == 0) {
			useNewRepo.setSelection(true);	
		} else {
			useExistingRepo.setSelection(true);	
			table.setSelection(new StructuredSelection(locations[0]));
		}
	}
	
	public ICVSRepositoryLocation getLocation() {
		return result;
	}
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			useExistingRepo.setFocus();
		}
	}
}
