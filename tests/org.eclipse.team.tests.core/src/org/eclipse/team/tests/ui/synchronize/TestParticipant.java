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
package org.eclipse.team.tests.ui.synchronize;

import org.eclipse.jface.wizard.IWizard;
import org.eclipse.team.ui.synchronize.AbstractSynchronizeParticipant;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.ui.part.IPageBookViewPage;

public class TestParticipant extends AbstractSynchronizeParticipant {
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.AbstractSynchronizeParticipant#initializeConfiguration(org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration)
	 */
	protected void initializeConfiguration(ISynchronizePageConfiguration configuration) {
		configuration.setMode(ISynchronizePageConfiguration.BOTH_MODE);
	}
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#createPage(org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration)
	 */
	public IPageBookViewPage createPage(ISynchronizePageConfiguration configuration) {
		// TODO Auto-generated method stub
		return null;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#createSynchronizeWizard()
	 */
	public IWizard createSynchronizeWizard() {
		// TODO Auto-generated method stub
		return null;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#dispose()
	 */
	public void dispose() {
		// TODO Auto-generated method stub
	}
}
