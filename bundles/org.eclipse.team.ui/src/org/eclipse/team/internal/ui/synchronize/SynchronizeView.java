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
package org.eclipse.team.internal.ui.synchronize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.internal.ui.synchronize.actions.PinParticipantAction;
import org.eclipse.team.internal.ui.synchronize.actions.SynchronizePageDropDownAction;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.ISynchronizeManager;
import org.eclipse.team.ui.synchronize.ISynchronizePage;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipant;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipantListener;
import org.eclipse.team.ui.synchronize.ISynchronizeParticipantReference;
import org.eclipse.team.ui.synchronize.ISynchronizeView;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.part.IPageBookViewPage;
import org.eclipse.ui.part.MessagePage;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.PageBookView;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;

/**
 * Implements a Synchronize View that contains multiple synchronize participants. 
 */
public class SynchronizeView extends PageBookView implements ISynchronizeView, ISynchronizeParticipantListener, IPropertyChangeListener {
	
	/**
	 * The participant being displayed, or <code>null</code> if none
	 */
	private ISynchronizeParticipant activeParticipantRef = null;
	
	/**
	 * Map of participants to dummy participant parts (used to close pages)
	 */
	private Map fParticipantToPart;
	
	/**
	 * Map of parts to participants
	 */
	private Map fPartToParticipant;

	/**
	 * Drop down action to switch between participants
	 */
	private SynchronizePageDropDownAction fPageDropDown;
	
	/**
	 * Action to remove the selected participant
	 */
	private PinParticipantAction fPinAction;
	
	/**
	 * Preference key to save
	 */
	private static final String KEY_LAST_ACTIVE_PARTICIPANT = "lastactiveparticipant"; //$NON-NLS-1$
	private static final String KEY_SETTINGS_SECTION= "SynchronizeViewSettings"; //$NON-NLS-1$

	/* (non-Javadoc)
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		Object source = event.getSource();
		if (source instanceof ISynchronizeParticipant && event.getProperty().equals(IBasicPropertyConstants.P_TEXT)) {
			if (source.equals(getParticipant())) {
				updateTitle();
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener#partClosed(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partClosed(IWorkbenchPart part) {
		super.partClosed(part);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeView#getParticipant()
	 */
	public ISynchronizeParticipant getParticipant() {
		return activeParticipantRef;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#showPageRec(org.eclipse.ui.part.PageBookView.PageRec)
	 */
	protected void showPageRec(PageRec pageRec) {
		super.showPageRec(pageRec);
		activeParticipantRef = (ISynchronizeParticipant)fPartToParticipant.get(pageRec.part);
		updateActionEnablements();
		updateTitle();		
	}

	/*
	 * Updates the view title based on the active participant
	 */
	protected void updateTitle() {
		ISynchronizeParticipant participant = getParticipant();
		if (participant == null) {
			setTitle(getViewName()); //$NON-NLS-1$
		} else {
			SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)fParticipantToPart.get(participant);
			setTitle(Policy.bind("SynchronizeView.2", getViewName(), part.getParticipant().getName())); //$NON-NLS-1$
		}
	}

	protected String getViewName() {
		return Policy.bind("SynchronizeView.1"); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#doDestroyPage(org.eclipse.ui.IWorkbenchPart, org.eclipse.ui.part.PageBookView.PageRec)
	 */
	protected void doDestroyPage(IWorkbenchPart part, PageRec pageRecord) {
		IPage page = pageRecord.page;
		page.dispose();
		pageRecord.dispose();
		SynchronizeViewWorkbenchPart syncPart = (SynchronizeViewWorkbenchPart) part;
		ISynchronizeParticipant participant = syncPart.getParticipant();
		participant.removePropertyChangeListener(this);
		
		// empty cross-reference cache
		fPartToParticipant.remove(part);
		fParticipantToPart.remove(participant);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#doCreatePage(org.eclipse.ui.IWorkbenchPart)
	 */
	protected PageRec doCreatePage(IWorkbenchPart dummyPart) {
		SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)dummyPart;
		ISynchronizeParticipant participant = part.getParticipant();	
		participant.addPropertyChangeListener(this);
		ISynchronizePageConfiguration configuration = participant.createPageConfiguration();
		IPageBookViewPage page = participant.createPage(configuration);
		if(page != null) {
			initPage(page);
			initPage(configuration, page);
			page.createControl(getPageBook());
			PageRec rec = new PageRec(dummyPart, page);
			return rec;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#initPage(org.eclipse.ui.part.IPageBookViewPage)
	 */
	protected void initPage(ISynchronizePageConfiguration configuration, IPageBookViewPage page) {
		// A page site does not provide everything the page may need
		// Also provide the synchronize page site if the page is a synchronize view page
		((SynchronizePageConfiguration)configuration).setSite(new WorkbenchPartSynchronizePageSite(this, page.getSite(), getDialogSettings(configuration.getParticipant())));
		if (page instanceof ISynchronizePage) {
			try {
				((ISynchronizePage)page).init(configuration.getSite());
			} catch (PartInitException e) {
				TeamUIPlugin.log(IStatus.ERROR, e.getMessage(), e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#isImportant(org.eclipse.ui.IWorkbenchPart)
	 */
	protected boolean isImportant(IWorkbenchPart part) {
		return part instanceof SynchronizeViewWorkbenchPart;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	public void dispose() {
		super.dispose();
		TeamUI.getSynchronizeManager().removeSynchronizeParticipantListener(this);
		if(activeParticipantRef != null) {
			IDialogSettings section = getDialogSettings();
			section.put(KEY_LAST_ACTIVE_PARTICIPANT, activeParticipantRef.getId());
		}			
		fParticipantToPart = null;
		fPartToParticipant = null;
		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#createDefaultPage(org.eclipse.ui.part.PageBook)
	 */
	protected IPage createDefaultPage(PageBook book) {
		MessagePage page = new MessagePage();
		page.createControl(getPageBook());
		initPage(page);
		return page;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeParticipantListener#participantsAdded(org.eclipse.team.ui.sync.ISynchronizeParticipant[])
	 */
	public void participantsAdded(final ISynchronizeParticipant[] participants) {
		for (int i = 0; i < participants.length; i++) {
			ISynchronizeParticipant participant = participants[i];
			if (isAvailable() && select(TeamUI.getSynchronizeManager().get(participant.getId(), participant.getSecondaryId()))) {
				SynchronizeViewWorkbenchPart part = new SynchronizeViewWorkbenchPart(participant, getSite());
				fParticipantToPart.put(participant, part);
				fPartToParticipant.put(part, participant);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeParticipantListener#participantsRemoved(org.eclipse.team.ui.sync.ISynchronizeParticipant[])
	 */
	public void participantsRemoved(final ISynchronizeParticipant[] participants) {
		if (isAvailable()) {
			Runnable r = new Runnable() {
				public void run() {
					for (int i = 0; i < participants.length; i++) {
						ISynchronizeParticipant participant = participants[i];
						if (isAvailable()) {
							SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)fParticipantToPart.get(participant);
							if (part != null) {
								partClosed(part);
							}
							// Remove any settings created for the participant
							removeDialogSettings(participant);
							if (getParticipant() == null) {
								ISynchronizeParticipantReference[] available = TeamUI.getSynchronizeManager().getSynchronizeParticipants();
								if (available.length > 0) {
									ISynchronizeParticipant p;
									try {
										p = available[available.length - 1].getParticipant();
									} catch (TeamException e) {
										return;
									}
									display(p);
								}
							}
						}
					}
				}
			};
			asyncExec(r);
		}
	}

	/**
	 * Constructs a synchronize view
	 */
	public SynchronizeView() {
		super();
		fParticipantToPart = new HashMap();
		fPartToParticipant = new HashMap();
		updateTitle();
	}
	
	/**
	 * Create the default actions for the view. These will be shown regardless of the
	 * participant being displayed.
	 */
	protected void createActions() {
		fPageDropDown = new SynchronizePageDropDownAction(this);
		fPinAction = new PinParticipantAction();
		updateActionEnablements();
	}

	private void updateActionEnablements() {
		if (fPinAction != null) {
			fPinAction.setParticipant(activeParticipantRef);
		}
	}

	/**
	 * Add the actions to the toolbar
	 * 
	 * @param mgr toolbar manager
	 */
	protected void configureToolBar(IToolBarManager mgr) {
		mgr.add(fPageDropDown);
		mgr.add(fPinAction);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeView#display(org.eclipse.team.ui.synchronize.ISynchronizeParticipant)
	 */
	public void display(ISynchronizeParticipant participant) {
		SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)fParticipantToPart.get(participant);
		if (part != null) {
			partActivated(part);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#getBootstrapPart()
	 */
	protected IWorkbenchPart getBootstrapPart() {
		return null;
	}
	
	/**
	 * Registers the given runnable with the display
	 * associated with this view's control, if any.
	 */
	public void asyncExec(Runnable r) {
		if (isAvailable()) {
			getPageBook().getDisplay().asyncExec(r);
		}
	}
	
	/**
	 * Creates this view's underlying viewer and actions.
	 * Hooks a pop-up menu to the underlying viewer's control,
	 * as well as a key listener. When the delete key is pressed,
	 * the <code>REMOVE_ACTION</code> is invoked. Hooks help to
	 * this view. Subclasses must implement the following methods
	 * which are called in the following order when a view is
	 * created:<ul>
	 * <li><code>createViewer(Composite)</code> - the context
	 *   menu is hooked to the viewer's control.</li>
	 * <li><code>createActions()</code></li>
	 * <li><code>configureToolBar(IToolBarManager)</code></li>
	 * <li><code>getHelpContextId()</code></li>
	 * </ul>
	 * @see IWorkbenchPart#createPartControl(Composite)
	 */
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		createActions();
		IToolBarManager tbm= getViewSite().getActionBars().getToolBarManager();
		configureToolBar(tbm);
		updateForExistingParticipants();
		getViewSite().getActionBars().updateActionBars();
		updateTitle();
		
		IWorkbenchSiteProgressService progress = (IWorkbenchSiteProgressService)getSite().getAdapter(IWorkbenchSiteProgressService.class);
		if(progress != null) {
			progress.showBusyForFamily(ISynchronizeManager.FAMILY_SYNCHRONIZE_OPERATION);
		}
	}
	
	/**
	 * Initialize for existing participants
	 */
	private void updateForExistingParticipants() {
		ISynchronizeManager manager = TeamUI.getSynchronizeManager();
		List participants = Arrays.asList(getParticipants());
		boolean errorOccurred = false;
		for (int i = 0; i < participants.size(); i++) {
			try {
				ISynchronizeParticipantReference ref = (ISynchronizeParticipantReference)participants.get(i);
				participantsAdded(new ISynchronizeParticipant[] {ref.getParticipant()});
			} catch (TeamException e) {
				errorOccurred = true;
				continue;
			}
			
		}
		if (errorOccurred) {
			participants = Arrays.asList(getParticipants());
		}
		try {
			// decide which participant to show	on startup
			if (participants.size() > 0) {
				ISynchronizeParticipantReference participantToSelect = (ISynchronizeParticipantReference)participants.get(0);
				IDialogSettings section = getDialogSettings();
				String selectedParticipantId = section.get(KEY_LAST_ACTIVE_PARTICIPANT);
				if(selectedParticipantId != null) {
					ISynchronizeParticipantReference selectedParticipant = manager.get(selectedParticipantId, null);
					if(selectedParticipant != null) {
						participantToSelect = selectedParticipant;
					}
				}
				display(participantToSelect.getParticipant());
			}
			
			// add as a listener to update when new participants are added
			manager.addSynchronizeParticipantListener(this);
		} catch (TeamException e) {
			Utils.handle(e);
		}
	}
	
	private ISynchronizeParticipantReference[] getParticipants() {
		ISynchronizeManager manager = TeamUI.getSynchronizeManager();
		// create pages
		List participants = new ArrayList();
		ISynchronizeParticipantReference[] refs = manager.getSynchronizeParticipants();
		for (int i = 0; i < refs.length; i++) {
			ISynchronizeParticipantReference ref =refs[i];
			if(select(ref)) {
				participants.add(ref);
			}
		}
		return (ISynchronizeParticipantReference[]) participants.toArray(new ISynchronizeParticipantReference[participants.size()]);
	}
	
	private boolean isAvailable() {
		return getPageBook() != null && !getPageBook().isDisposed();
	}
	
	/*
	 * Method used by test cases to access the page for a participant
	 */
	public IPage getPage(ISynchronizeParticipant participant) {
		IWorkbenchPart part = (IWorkbenchPart)fParticipantToPart.get(participant);
		if (part == null) return null;
		try {
			return getPageRec(part).page;
		} catch (NullPointerException e) {
			// The PageRec class is not visible so we can't do a null check
			// before accessing the page.
			return null;
		}
	}
	
	protected boolean select(ISynchronizeParticipantReference ref) {
		return true;
	}
	
	/*
	 * Return the dialog settings for the view
	 */
	private IDialogSettings getDialogSettings() {
		IDialogSettings workbenchSettings = TeamUIPlugin.getPlugin().getDialogSettings();
		IDialogSettings syncViewSettings = workbenchSettings.getSection(KEY_SETTINGS_SECTION); //$NON-NLS-1$
		if (syncViewSettings == null) {
			syncViewSettings = workbenchSettings.addNewSection(KEY_SETTINGS_SECTION);
		}
		return syncViewSettings;
	}
	
	private String getSettingsKey(ISynchronizeParticipant participant) {
		String id = participant.getId();
		String secondaryId = participant.getSecondaryId();
	    return secondaryId == null ? id : id + '.' + secondaryId;
	}
	
	private IDialogSettings getDialogSettings(ISynchronizeParticipant participant) {
		String key = getSettingsKey(participant);
		IDialogSettings viewsSettings = getDialogSettings();
		IDialogSettings settings = viewsSettings.getSection(key);
		if (settings == null) {
			settings = viewsSettings.addNewSection(key);
		}
		return settings;
	}
	
	private void removeDialogSettings(ISynchronizeParticipant participant) {
		String key = getSettingsKey(participant);
		IDialogSettings settings = getDialogSettings();
		if (settings.getSection(key) != null) {
			// There isn't an explicit remove so just make sure
			// That the old settings are forgotten
			getDialogSettings().addSection(new DialogSettings(key));
		}
	}
}