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
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.team.core.subscribers.Subscriber;
import org.eclipse.team.core.synchronize.SyncInfoTree;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.ui.synchronize.StructuredViewerAdvisor;
import org.eclipse.team.ui.synchronize.subscribers.SubscriberParticipant;
import org.eclipse.team.ui.synchronize.subscribers.SubscriberConfiguration;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PartInitException;

public class WorkspaceSynchronizeParticipant extends SubscriberParticipant {

	public final static String ID = "org.eclipse.team.cvs.ui.cvsworkspace-participant"; //$NON-NLS-1$
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#init(org.eclipse.ui.IMemento)
	 */
	public void init(String secondaryId, IMemento memento) throws PartInitException {
		super.init(secondaryId, memento);
		Subscriber subscriber = CVSProviderPlugin.getPlugin().getCVSWorkspaceSubscriber(); 
		setSubscriber(subscriber);
	}
	
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.subscriber.SubscriberParticipant#createSynchronizeViewerAdvisor(org.eclipse.team.ui.synchronize.ISynchronizeView)
	 */
	protected StructuredViewerAdvisor createSynchronizeViewerAdvisor(SubscriberConfiguration configuration, SyncInfoTree syncInfoTree) {
		return new WorkspaceSynchronizeAdvisor(configuration, syncInfoTree);
	}
}