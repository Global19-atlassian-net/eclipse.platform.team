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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.TagConfigurationDialog;
import org.eclipse.team.internal.ccvs.ui.model.RemoteModule;;

/**
 * DefineTagAction remembers a tag by name
 */
public class ConfigureTagsFromRepoViewOnFolder extends CVSAction {
	
	/**
	 * Returns the selected remote folders
	 */
	protected ICVSRemoteFolder[] getSelectedRemoteFolders() {
		ArrayList resources = null;
		if (!selection.isEmpty()) {
			resources = new ArrayList();
			Iterator elements = ((IStructuredSelection) selection).iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ICVSRemoteFolder) {
					if(new Path(((ICVSRemoteFolder)next).getRepositoryRelativePath()).segmentCount()==1) {
						resources.add(next);
					}
					continue;
				}
				if(next instanceof RemoteModule) {
					resources.add((ICVSRemoteFolder)((RemoteModule)next).getCVSResource());
				}
			}
		}
		if (resources != null && !resources.isEmpty()) {
			return (ICVSRemoteFolder[])resources.toArray(new ICVSRemoteFolder[resources.size()]);
		}
		return new ICVSRemoteFolder[0];
	}

	/*
	 * @see CVSAction@execute(IAction)
	 */
	public void execute(IAction action) throws InvocationTargetException, InterruptedException {
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				final ICVSRemoteFolder[] roots = getSelectedRemoteFolders();
				final Shell shell = getShell();
				shell.getDisplay().syncExec(new Runnable() {
					public void run() {
						ICVSFolder[] cvsFolders = new ICVSFolder[roots.length];
						for (int i = 0; i < roots.length; i++) {
							cvsFolders[i] = (ICVSFolder)roots[i];
						}
						TagConfigurationDialog d = new TagConfigurationDialog(shell, cvsFolders);
						d.open();
					}
				});
			}
		}, false /* cancelable */, this.PROGRESS_BUSYCURSOR);
	}

	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		ICVSRemoteFolder[] roots = getSelectedRemoteFolders();
		if (roots.length == 0) return false;
		return true;
	}
	/**
	 * @see org.eclipse.team.internal.ccvs.ui.actions.CVSAction#getErrorTitle()
	 */
	protected String getErrorTitle() {
		return Policy.bind("ConfigureTagsFromRepoViewConfigure_Tag_Error_1"); //$NON-NLS-1$
	}

}