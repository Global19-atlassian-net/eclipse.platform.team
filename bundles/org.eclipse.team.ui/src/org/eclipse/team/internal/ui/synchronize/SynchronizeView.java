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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.synchronize.actions.SynchronizePageDropDownAction;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.*;

/**
 * Implements a Synchronize View that contains multiple synchronize participants. 
 */
public class SynchronizeView extends PageBookView implements ISynchronizeView, ISynchronizeParticipantListener, IPropertyChangeListener {
	
	/**
	 * The participant being displayed, or <code>null</code> if none
	 */
	private ISynchronizeParticipant activeParticipant = null;
	
	/**
	 * Map of participants to dummy participant parts (used to close pages)
	 */
	private Map fParticipantToPart;
	
	/**
	 * Map of parts to participants
	 */
	private Map fPartToPage;

	/**
	 * Drop down action to switch between participants
	 */
	private SynchronizePageDropDownAction fPageDropDown;
	
	/**
	 * Preference key to save
	 */
	private static final String KEY_LAST_ACTIVE_PARTICIPANT = "lastactiveparticipant";
	private static final String KEY_SETTINGS_SECTION= "SynchronizeViewSettings";

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
		return activeParticipant;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#showPageRec(org.eclipse.ui.part.PageBookView.PageRec)
	 */
	protected void showPageRec(PageRec pageRec) {
		super.showPageRec(pageRec);
		activeParticipant = (ISynchronizeParticipant)fPartToPage.get(pageRec.part);
		updateTitle();		
	}

	/*
	 * Updates the view title based on the active participant
	 */
	protected void updateTitle() {
		ISynchronizeParticipant page = getParticipant();
		if (page == null) {
			setTitle(Policy.bind("SynchronizeView.1")); //$NON-NLS-1$
		} else {
			setTitle(Policy.bind("SynchronizeView.2") + page.getName()); //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#doDestroyPage(org.eclipse.ui.IWorkbenchPart, org.eclipse.ui.part.PageBookView.PageRec)
	 */
	protected void doDestroyPage(IWorkbenchPart part, PageRec pageRecord) {
		IPage page = pageRecord.page;
		page.dispose();
		pageRecord.dispose();
		ISynchronizeParticipant participant = (ISynchronizeParticipant) fPartToPage.get(part);
		participant.removePropertyChangeListener(this);
		// empty cross-reference cache
		fPartToPage.remove(part);
		fParticipantToPart.remove(participant);		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#doCreatePage(org.eclipse.ui.IWorkbenchPart)
	 */
	protected PageRec doCreatePage(IWorkbenchPart dummyPart) {
		SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)dummyPart;
		Object component = part.getPage();
		IPageBookViewPage page = null;
		if(component instanceof ISynchronizeParticipant) {
			ISynchronizeParticipant participant = (ISynchronizeParticipant)component;			
			participant.addPropertyChangeListener(this);
			page = participant.createPage(this);
		} else if(component instanceof IPageBookViewPage) {
			page = (IPageBookViewPage)component;
		}
		
		if(page != null) {
			initPage(page);
			page.createControl(getPageBook());
			PageRec rec = new PageRec(dummyPart, page);
			return rec;
		}
		return null;
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
		
		IDialogSettings workbenchSettings = TeamUIPlugin.getPlugin().getDialogSettings();
		if(activeParticipant != null) {
			IDialogSettings section = workbenchSettings.getSection(KEY_SETTINGS_SECTION);//$NON-NLS-1$
			if (section == null) {
				section = workbenchSettings.addNewSection(KEY_SETTINGS_SECTION);
			}
			section.put(KEY_LAST_ACTIVE_PARTICIPANT, activeParticipant.getId());
		}
		
		TeamUI.getSynchronizeManager().removeSynchronizeParticipantListener(this);
		
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
			if (isAvailable()) {
				ISynchronizeParticipant participant = participants[i];
				SynchronizeViewWorkbenchPart part = new SynchronizeViewWorkbenchPart(participant, getSite());
				fParticipantToPart.put(participant, part);
				fPartToPage.put(part, participant);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeParticipantListener#participantsRemoved(org.eclipse.team.ui.sync.ISynchronizeParticipant[])
	 */
	public void participantsRemoved(final ISynchronizeParticipant[] consoles) {
		if (isAvailable()) {
			Runnable r = new Runnable() {
				public void run() {
					for (int i = 0; i < consoles.length; i++) {
						if (isAvailable()) {
							ISynchronizeParticipant console = consoles[i];
							SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)fParticipantToPart.get(console);
							if (part != null) {
								partClosed(part);
							}
							if (getParticipant() == null) {
								ISynchronizeParticipant[] available = TeamUI.getSynchronizeManager().getSynchronizeParticipants();
								if (available.length > 0) {
									display(available[available.length - 1]);
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
		fPartToPage = new HashMap();
	}
	
	/**
	 * Create the default actions for the view. These will be shown regardless of the
	 * participant being displayed.
	 */
	protected void createActions() {
		fPageDropDown = new SynchronizePageDropDownAction(this);
	}

	/**
	 * Add the actions to the toolbar
	 * 
	 * @param mgr toolbar manager
	 */
	protected void configureToolBar(IToolBarManager mgr) {
		mgr.add(fPageDropDown);
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
	 * 
	 * @see org.eclipse.swt.widgets.Display#asyncExec(java.lang.Runnable)
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
	}
	
	/**
	 * Initialize for existing participants
	 */
	private void updateForExistingParticipants() {
		ISynchronizeManager manager = TeamUI.getSynchronizeManager();
		// create pages for consoles
		ISynchronizeParticipant[] participants = manager.getSynchronizeParticipants();
		participantsAdded(participants);
		// decide which participant to show	on startup
		if (participants.length > 0) {
			ISynchronizeParticipant participantToSelect = participants[0];
			IDialogSettings workbenchSettings = TeamUIPlugin.getPlugin().getDialogSettings();
			IDialogSettings section = workbenchSettings.getSection(KEY_SETTINGS_SECTION);//$NON-NLS-1$
			if (section != null) {
				String selectedParticipantId = section.get(KEY_LAST_ACTIVE_PARTICIPANT);
				if(selectedParticipantId != null) {
					ISynchronizeParticipant[] selectedParticipant = manager.find(selectedParticipantId);
					if(selectedParticipant.length > 0) {
						participantToSelect = selectedParticipant[0];
					}
				}
			}
			display(participantToSelect);
		}
		
		// add as a listener to update when new participants are added
		manager.addSynchronizeParticipantListener(this);
	}
	
	private boolean isAvailable() {
		return getPageBook() != null && !getPageBook().isDisposed();
	}
}