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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.team.core.subscribers.TeamSubscriber;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ui.synchronize.sets.SubscriberInput;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.IPageBookViewPage;

public class MergeSynchronizeParticipant extends TeamSubscriberParticipant {
	
	private final static String CTX_QUALIFIER = "qualifier"; //$NON-NLS-1$
	private final static String CTX_LOCALNAME = "localname"; //$NON-NLS-1$
	private final static String CTX_ROOT = "root"; //$NON-NLS-1$
	private final static String CTX_ROOT_PATH = "root_resource"; //$NON-NLS-1$
	private final static String CTX_START_TAG = "start_tag"; //$NON-NLS-1$
	private final static String CTX_START_TAG_TYPE = "start_tag_type"; //$NON-NLS-1$
	private final static String CTX_END_TAG = "end_tag"; //$NON-NLS-1$
	private final static String CTX_END_TAG_TYPE = "end_tag_type"; //$NON-NLS-1$
	
	public MergeSynchronizeParticipant() {
		super();
	}
	
	public MergeSynchronizeParticipant(CVSMergeSubscriber subscriber) {
		super();
		setSubscriber(subscriber);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.TeamSubscriberParticipant#setSubscriber(org.eclipse.team.core.subscribers.TeamSubscriber)
	 */
	protected void setSubscriber(TeamSubscriber subscriber) {
		super.setSubscriber(subscriber);
		String id = CVSMergeSubscriber.QUALIFIED_NAME;
		try {
			ISynchronizeParticipantDescriptor descriptor = TeamUI.getSynchronizeManager().getParticipantDescriptor(id); 
			setInitializationData(descriptor);
		} catch (CoreException e) {
			CVSUIPlugin.log(e);
		}
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#init(org.eclipse.ui.IMemento)
	 */
	public void init(IMemento memento) throws PartInitException {
		super.init(memento);
		if(memento != null) {
			String qualifier = memento.getString(CTX_QUALIFIER);
			String localname = memento.getString(CTX_LOCALNAME);
			if(qualifier == null || localname == null) {
				throw new PartInitException(Policy.bind("MergeSynchronizeParticipant.8")); //$NON-NLS-1$
			}
			try {
				setSubscriber(read(new QualifiedName(qualifier, localname), memento));
			} catch (CVSException e) {
				throw new PartInitException(Policy.bind("MergeSynchronizeParticipant.9"), e); //$NON-NLS-1$
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#init(org.eclipse.ui.IMemento)
	 */
	public void saveState(IMemento memento) {
		super.saveState(memento);
		SubscriberInput input = getInput();
		CVSMergeSubscriber s = (CVSMergeSubscriber)input.getSubscriber();
		QualifiedName sId = s.getId();
		memento.putString(CTX_QUALIFIER, sId.getQualifier());
		memento.putString(CTX_LOCALNAME, sId.getLocalName());
		write(s, memento);
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.AbstractSynchronizeParticipant#dispose()
	 */
	public void dispose() {
		super.dispose();
		((CVSMergeSubscriber)getInput().getSubscriber()).cancel();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#createPage(org.eclipse.team.ui.synchronize.ISynchronizeView)
	 */
	public IPageBookViewPage createPage(ISynchronizeView view) {		
		return new MergeSynchronizePage(this, view, getInput());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipant#getName()
	 */
	public String getName() {		
		return ((CVSMergeSubscriber)getInput().getSubscriber()).getName();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.TeamSubscriber#saveState(org.eclipse.team.internal.core.SaveContext)
	 */
	private void write(CVSMergeSubscriber s, IMemento memento) {
		// start and end tags
		CVSTag start = s.getStartTag();
		CVSTag end = s.getEndTag();
		memento.putString(CTX_START_TAG, start.getName());
		memento.putInteger(CTX_START_TAG_TYPE, start.getType());
		memento.putString(CTX_END_TAG, end.getName());
		memento.putInteger(CTX_END_TAG_TYPE, end.getType());
		
		// resource roots
		IResource[] roots = s.roots();
		for (int i = 0; i < roots.length; i++) {
			IResource resource = roots[i];
			IMemento rootNode = memento.createChild(CTX_ROOT);
			rootNode.putString(CTX_ROOT_PATH, resource.getFullPath().toString());
		}
	}
	
	private CVSMergeSubscriber read(QualifiedName id, IMemento memento) throws CVSException {
		CVSTag start = new CVSTag(memento.getString(CTX_START_TAG), memento.getInteger(CTX_START_TAG_TYPE).intValue()); //$NON-NLS-1$ //$NON-NLS-2$
		CVSTag end = new CVSTag(memento.getString(CTX_END_TAG), memento.getInteger(CTX_END_TAG_TYPE).intValue()); //$NON-NLS-1$ //$NON-NLS-2$
		
		IMemento[] rootNodes = memento.getChildren(CTX_ROOT);
		if(rootNodes == null || rootNodes.length == 0) {
			throw new CVSException(Policy.bind("MergeSynchronizeParticipant.10",id.toString())); //$NON-NLS-1$
		}
		
		List resources = new ArrayList();
		for (int i = 0; i < rootNodes.length; i++) {
			IMemento rootNode = rootNodes[i];
			IPath path = new Path(rootNode.getString(CTX_ROOT_PATH)); //$NON-NLS-1$
			IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path, true /* include phantoms */);
			if(resource != null) {
				resources.add(resource);
			} else {
				// log that a resource previously in the merge set is no longer in the workspace
				CVSProviderPlugin.log(CVSStatus.INFO, Policy.bind("MergeSynchronizeParticipant.11", resource.getFullPath().toString()), null); //$NON-NLS-1$
			}
		}
		if(resources.isEmpty()) {
			throw new CVSException(Policy.bind("MergeSynchronizeParticipant.12", id.toString())); //$NON-NLS-1$
		}
		IResource[] roots = (IResource[]) resources.toArray(new IResource[resources.size()]);
		return new CVSMergeSubscriber(id, roots, start, end);
	}
}