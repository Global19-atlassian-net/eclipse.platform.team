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
package org.eclipse.team.internal.ccvs.ui.actions;
 
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.IAction;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.sync.CVSSyncCompareInput;
import org.eclipse.team.internal.ui.sync.SyncCompareInput;
import org.eclipse.team.internal.ui.sync.SyncView;
import org.eclipse.ui.PartInitException;

/**
 * Action for catchup/release in popup menus.
 */
public class SyncAction extends WorkspaceAction {
	public void execute(IAction action) {
		IResource[] resources = getSelectedResources();
		SyncView view = (SyncView)CVSUIPlugin.getActivePage().findView(SyncView.VIEW_ID);
		if (view == null) {
			view = SyncView.findInActivePerspective();
		}
		if (view != null) {
			try {
				CVSUIPlugin.getActivePage().showView(SyncView.VIEW_ID);
			} catch (PartInitException e) {
				CVSUIPlugin.log(e.getStatus());
			}
			view.showSync(getCompareInput(resources));
		}
	}
	protected boolean isEnabled() throws TeamException {
		IResource[] resources = getSelectedResources();
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (!resource.isAccessible()) return false;
			if (resource.getType() == IResource.PROJECT) continue;
			// If the resource is not managed and its parent is not managed, disable.
			ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
			if (!cvsResource.isManaged()) {
				// The resource is not managed. See if its parent is managed.
				if (!cvsResource.getParent().isCVSFolder()) return false;
			}
		}
		return true;
	}
	protected SyncCompareInput getCompareInput(IResource[] resources) {
		return new CVSSyncCompareInput(resources);
	}
}
