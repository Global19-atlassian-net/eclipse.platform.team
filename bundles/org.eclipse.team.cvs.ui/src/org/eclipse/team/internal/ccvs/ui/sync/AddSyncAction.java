/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/

package org.eclipse.team.internal.ccvs.ui.sync;
 
import java.util.ArrayList;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.MutableResourceSyncInfo;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.IHelpContextIds;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.ui.sync.ITeamNode;
import org.eclipse.team.internal.ui.sync.SyncSet;
import org.eclipse.team.internal.ui.sync.TeamFile;

/**
 * This is a CVS sync view action that will  
 */
public class AddSyncAction extends MergeAction {
	public AddSyncAction(CVSSyncCompareInput model, ISelectionProvider sp, String label, Shell shell) {
		super(model, sp, label, shell);
	}

	protected SyncSet run(SyncSet syncSet, IProgressMonitor monitor) {
		boolean result = saveIfNecessary();
		if (!result) return null;

		ITeamNode[] changed = syncSet.getChangedNodes();
		if (changed.length == 0) {
			return syncSet;
		}
		List additions = new ArrayList();

		try {
			for (int i = 0; i < changed.length; i++) {
				int kind = changed[i].getKind();
				// leave the added nodes in the sync view. Their sync state
				// won't change but the decoration should.
				IResource resource = changed[i].getResource();
				if ((kind & Differencer.DIRECTION_MASK) == ITeamNode.CONFLICTING) {
					if (resource.getType() == IResource.FOLDER) {
						makeInSync(changed[i]);
					} else {
						makeAdded(changed[i]);
					}
				} else {	
					if (resource.getType() == resource.FILE) {
						additions.add(resource);
					}
				}
			}
		
			RepositoryManager manager = CVSUIPlugin.getPlugin().getRepositoryManager();
			if (additions.size() != 0) {
				manager.add((IResource[])additions.toArray(new IResource[0]), monitor);
			}
			
			// for all files ensure that parent folders are made in sync after
			// the add completes.
			for (int i = 0; i < changed.length; i++) {
				ITeamNode node = changed[i];
				IResource resource = changed[i].getResource();
				if (resource.getType() == resource.FILE) {
					syncSet.remove(node);
				}
			}
		} catch (final TeamException e) {
			handle(e);
			return null;
		}
		
		return syncSet;
	}

	protected void makeAdded(ITeamNode changed)
		throws TeamException, CVSException {
		// Fake the add locally since add command will fail
		makeInSync(changed.getParent());
		CVSRemoteSyncElement syncElement = (CVSRemoteSyncElement)((TeamFile)changed).getMergeResource().getSyncElement();
		ICVSResource remote = (ICVSResource)syncElement.getRemote();
		MutableResourceSyncInfo info = remote.getSyncInfo().cloneMutable();
		info.setTimeStamp(null);
		info.setAdded();
		ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile)changed.getResource());
		cvsFile.setSyncInfo(info);
	}
	
	/**
	 * Enabled for folders and files that aren't added.
	 */
	protected boolean isEnabled(ITeamNode node) {
		try {
			CVSSyncSet set = new CVSSyncSet(new StructuredSelection(node));
			set.removeConflictingNodes();
			return set.hasNonAddedChanges();
		} catch (CVSException e) {
			CVSUIPlugin.log(e.getStatus());
			return false;
		}
	}	
	
	/**
	 * Remove all nodes that aren't files and folders that need to be added.
	 */
	protected void removeNonApplicableNodes(SyncSet set, int syncMode) {
		set.removeIncomingNodes();
		set.removeConflictingNodes();
		((CVSSyncSet)set).removeAddedChanges();
	}	
	/**
	 * @see MergeAction#getHelpContextID()
	 */
	protected String getHelpContextID() {
		return IHelpContextIds.SYNC_ADD_ACTION;
	}
	
	protected String getErrorTitle() {
		return Policy.bind("AddAction.addFailed"); //$NON-NLS-1$
	}
}
