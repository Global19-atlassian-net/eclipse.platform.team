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
package org.eclipse.team.ui.sync.actions;

import java.util.*;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.sync.TeamSubscriberParticipant;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.ActionGroup;

/**
 * This ActionGroup provides filtering of a sync set by change direction.
 * The actions are presented to the user as toolbar buttons where only one
 * button is active at a time
 */
public class DirectionFilterActionGroup extends ActionGroup implements IPropertyChangeListener {
	
	// An array of the selection actions for the modes (indexed by mode constant)	
	private List actions = new ArrayList(3);
	
	private DirectionFilterAction incomingMode;					
	private DirectionFilterAction outgoingMode;
	private DirectionFilterAction bothMode;
	private DirectionFilterAction conflictsMode;
	private TeamSubscriberParticipant page;
	
	private int supportedModes;
	
	class DirectionFilterAction extends Action {
		private int modeId;
		
		public DirectionFilterAction(String prefix,String commandId, int modeId) {
			super("", AS_RADIO_BUTTON); //$NON-NLS-1$
			this.modeId = modeId;
			Utils.initAction(this, prefix);
			Action a = new Action() {
				public void run() {
					DirectionFilterAction.this.run();
				}
			};
			//IKeyBindingService kbs = site.getKeyBindingService();
			//Utils.registerAction(kbs, a, commandId);	//$NON-NLS-1$
		}
		public void run() {
			// checkMode() is called because programatic checking of radio buttons doesn't 
			// consider radio buttons, hence breaks the radio-button behavior. As a workaround
			// we have to manually check/uncheck the set instead.
			checkMode(modeId);
			page.setMode(modeId);
		}
		public int getModeId() {
			return modeId;
		}
	}
	
	public DirectionFilterActionGroup(TeamSubscriberParticipant page, int supportedModes) {		
		this.supportedModes = supportedModes;
		this.page = page;
		createActions();
		page.addPropertyChangeListener(this);
		checkMode(page.getMode());
	}
	
	/**
	 * Sets up the sync modes and the actions for switching between them.
	 */
	private void createActions() {
		// Create the actions
		if((supportedModes & TeamSubscriberParticipant.INCOMING_MODE) != 0) {
			incomingMode = new DirectionFilterAction("action.directionFilterIncoming.", "org.eclipse.team.ui.syncview.incomingFilter",  TeamSubscriberParticipant.INCOMING_MODE); //$NON-NLS-1$ //$NON-NLS-2$
			actions.add(incomingMode);
		}
		
		if((supportedModes & TeamSubscriberParticipant.OUTGOING_MODE) != 0) {
			outgoingMode = new DirectionFilterAction("action.directionFilterOutgoing.", "org.eclipse.team.ui.syncview.outgoingFilter",  TeamSubscriberParticipant.OUTGOING_MODE); //$NON-NLS-1$ //$NON-NLS-2$
			actions.add(outgoingMode);
		}
		
		if((supportedModes & TeamSubscriberParticipant.BOTH_MODE) != 0) {
			bothMode = new DirectionFilterAction("action.directionFilterBoth.", "org.eclipse.team.ui.syncview.bothFilter", TeamSubscriberParticipant.BOTH_MODE); //$NON-NLS-1$ //$NON-NLS-2$
			actions.add(bothMode);
		}
		
		if((supportedModes & TeamSubscriberParticipant.CONFLICTING_MODE) != 0) {
			conflictsMode = new DirectionFilterAction("action.directionFilterConflicts.", "org.eclipse.team.ui.syncview.conflictsFilter", TeamSubscriberParticipant.CONFLICTING_MODE); //$NON-NLS-1$ //$NON-NLS-2$
			actions.add(conflictsMode);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.actions.ActionGroup#fillActionBars(org.eclipse.ui.IActionBars)
	 */
	public void fillActionBars(IActionBars actionBars, String group) {
		super.fillActionBars(actionBars);
		IToolBarManager toolBar = actionBars.getToolBarManager();
		for (Iterator it = actions.iterator(); it.hasNext();) {
			DirectionFilterAction action = (DirectionFilterAction) it.next();
			if(group != null) {
				toolBar.appendToGroup(group, action);
			} else {
				toolBar.add(action);
			}
		}
	}
	
	private void checkMode(int mode) {
		for (Iterator it = actions.iterator(); it.hasNext();) {
			DirectionFilterAction action = (DirectionFilterAction)it.next();
			if(action.getModeId() == mode) {
				action.setChecked(true);
			} else {
				action.setChecked(false);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		if(event.getProperty().equals(TeamSubscriberParticipant.P_SYNCVIEWPAGE_MODE)) {
			Integer mode = (Integer)event.getNewValue();
			checkMode(mode.intValue());
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.actions.ActionGroup#dispose()
	 */
	public void dispose() {
		// TODO Auto-generated method stub
		super.dispose();
	}
}