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
package org.eclipse.team.internal.ccvs.ui.operations;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.synchronize.IRemoteResource;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * Reconcile an existing unshared local project with an existing remote folder.
 */
public class ReconcileProjectOperation extends CVSOperation {

	private IProject project;
	private ICVSRemoteFolder folder;

	public ReconcileProjectOperation(Shell shell, IProject project, ICVSRemoteFolder folder) {
		super(shell);
		this.folder = folder;
		this.project = project;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CVSOperation#execute(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void execute(IProgressMonitor monitor) throws CVSException, InterruptedException {
		try {
			monitor.beginTask(null, 300);
			ICVSRemoteFolder remote = CheckoutToRemoteFolderOperation.checkoutRemoteFolder(getShell(), folder, Policy.subMonitorFor(monitor, 100));
			// TODO: make -in-sync should also be done by the subscriber
//			makeFoldersInSync(project, remote, Policy.subMonitorFor(monitor, 100));
			CVSProviderPlugin.getPlugin().getCVSWorkspaceSubscriber().setRemote(project, (IRemoteResource)remote, Policy.subMonitorFor(monitor, 100));
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					CVSUIPlugin.showInSyncView(getShell(), null, 0 /* no mode in particular */);
				}
			});
		} catch (InvocationTargetException e) {
			throw CVSException.wrapException(e);
		} catch (TeamException e) {
			throw CVSException.wrapException(e);
		} finally {
			monitor.done();
		}

	}

//	/**
//	 * Sync the given unshared project with the given repository and module.
//	 */
//	public void makeFoldersInSync(final IProject project, ICVSRemoteFolder remote, IProgressMonitor progress) throws TeamException {
//		final CVSRemoteSyncElement tree = new CVSRemoteSyncElement(true /*three way*/, project, null, remote);
//		CVSWorkspaceRoot.getCVSFolderFor(project).run(new ICVSRunnable() {
//			public void run(IProgressMonitor monitor) throws CVSException {
//				monitor.beginTask(null, 100);
//				try {
//					tree.makeFoldersInSync(Policy.subMonitorFor(monitor, 100));
//					RepositoryProvider.map(project, CVSProviderPlugin.getTypeId());
//				} catch (TeamException e) {
//					throw CVSException.wrapException(e);
//				}
//				monitor.done();
//			}
//		}, progress);
//	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CVSOperation#getTaskName()
	 */
	protected String getTaskName() {
		return Policy.bind("ReconcileProjectOperation.0", project.getName(), folder.getRepositoryRelativePath()); //$NON-NLS-1$
	}

}
