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
package org.eclipse.team.tests.ccvs.ui;

import junit.framework.AssertionFailedError;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.ccvs.core.CVSMergeSubscriber;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.ui.subscriber.MergeSynchronizeParticipant;
import org.eclipse.team.tests.ccvs.core.EclipseTest;
import org.eclipse.team.tests.ccvs.core.subscriber.SyncInfoSource;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.team.ui.synchronize.subscriber.*;

/**
 * SyncInfoSource that obtains SyncInfo from the SynchronizeView's SyncSet.
 */
public class SynchronizeViewTestAdapter extends SyncInfoSource {

	public SynchronizeViewTestAdapter() {
		TeamUI.getSynchronizeManager().showSynchronizeViewInActivePage(null);
	}
	
	public SyncInfo getSyncInfo(Subscriber subscriber, IResource resource) throws TeamException {
		SyncInfoSet set = getCollector(subscriber).getSubscriberSyncInfoSet();
		SyncInfo info = set.getSyncInfo(resource);
		if (info == null) {
			info = subscriber.getSyncInfo(resource);
			if ((info != null && info.getKind() != SyncInfo.IN_SYNC)) {
				throw new AssertionFailedError();
			}
		}
		return info;
	}
	
	private SubscriberParticipant getParticipant(Subscriber subscriber) {
		// show the sync view
		ISynchronizeParticipant[] participants = TeamUI.getSynchronizeManager().getSynchronizeParticipants();
		for (int i = 0; i < participants.length; i++) {
			ISynchronizeParticipant participant = participants[i];
			if(participant instanceof SubscriberParticipant) {
				if(((SubscriberParticipant)participant).getSubscriber() == subscriber) {
					return (SubscriberParticipant)participant;
				}
			}
		}
		return null;
	}
	
	private SubscriberSyncInfoCollector getCollector(Subscriber subscriber) {
		SubscriberParticipant participant = getParticipant(subscriber);
		if (participant == null) return null;
		SubscriberSyncInfoCollector syncInfoCollector = participant.getSubscriberSyncInfoCollector();
		EclipseTest.waitForSubscriberInputHandling(syncInfoCollector);
		return syncInfoCollector;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.subscriber.SyncInfoSource#assertProjectRemoved(org.eclipse.team.core.subscribers.TeamSubscriber, org.eclipse.core.resources.IProject)
	 */
	protected void assertProjectRemoved(Subscriber subscriber, IProject project) throws TeamException {		
		super.assertProjectRemoved(subscriber, project);
		SyncInfoTree set = getCollector(subscriber).getSyncInfoTree();
		if (set.hasMembers(project)) {
			throw new AssertionFailedError("The sync set still contains resources from the deleted project " + project.getName());	
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.subscriber.SyncInfoSource#createMergeSubscriber(org.eclipse.core.resources.IProject, org.eclipse.team.internal.ccvs.core.CVSTag, org.eclipse.team.internal.ccvs.core.CVSTag)
	 */
	public CVSMergeSubscriber createMergeSubscriber(IProject project, CVSTag root, CVSTag branch) {
		CVSMergeSubscriber mergeSubscriber = super.createMergeSubscriber(project, root, branch);
		ISynchronizeManager synchronizeManager = TeamUI.getSynchronizeManager();
		ISynchronizeParticipant participant = new MergeSynchronizeParticipant(mergeSubscriber);
		synchronizeManager.addSynchronizeParticipants(
				new ISynchronizeParticipant[] {participant});		
		ISynchronizeView view = synchronizeManager.showSynchronizeViewInActivePage(null);
		view.display(participant);
		return mergeSubscriber;
	}
	
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.subscriber.SyncInfoSource#tearDown()
	 */
	public void tearDown() {
		ISynchronizeParticipant[] participants = TeamUI.getSynchronizeManager().getSynchronizeParticipants();
		for (int i = 0; i < participants.length; i++) {
			ISynchronizeParticipant participant = participants[i];
			if(participant.getId().equals(CVSMergeSubscriber.QUALIFIED_NAME)) {
				TeamUI.getSynchronizeManager().removeSynchronizeParticipants(new ISynchronizeParticipant[] {participant});
			}
		}
		// Process all async events that may have been generated above
		while (Display.getCurrent().readAndDispatch()) {};
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.subscriber.SyncInfoSource#refresh(org.eclipse.team.core.subscribers.TeamSubscriber, org.eclipse.core.resources.IResource)
	 */
	public void refresh(Subscriber subscriber, IResource resource) throws TeamException {
		super.refresh(subscriber, resource);
		// Getting the collector waits for the subscriber input handlers
		getCollector(subscriber);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.subscriber.SyncInfoSource#reset()
	 */
	public void reset(Subscriber subscriber) throws TeamException {
		super.reset(subscriber);
		getCollector(subscriber).reset();
	}
}
