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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.UpdateMergableOnly;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * This operation performs an update that will only effect files
 * that have no conflicts or automergable conflicts.
 */
public class UpdateOnlyMergableOperation extends SingleCommandOperation {

	List skippedFiles = new ArrayList();
	
	public UpdateOnlyMergableOperation(Shell shell, IResource[] resources, LocalOption[] localOptions) {
		super(shell, resources, localOptions);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.SingleCommandOperation#executeCommand(org.eclipse.team.internal.ccvs.core.client.Session, org.eclipse.team.internal.ccvs.core.CVSTeamProvider, org.eclipse.core.resources.IResource[], org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IStatus executeCommand(Session session, CVSTeamProvider provider, IResource[] resources, IProgressMonitor monitor) throws CVSException, InterruptedException {
		UpdateMergableOnly update = new UpdateMergableOnly();
		IStatus status = update.execute(
			session,
			Command.NO_GLOBAL_OPTIONS, 
			getLocalOptions(), 
			getCVSArguments(resources),
			null, 
			Policy.subMonitorFor(monitor, 90));
		if (status.getCode() != IStatus.ERROR) {
			addSkippedFiles(update.getSkippedFiles());
		} 
		return status;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.RepositoryProviderOperation#getTaskName()
	 */
	protected String getTaskName() {
		return Policy.bind("UpdateOnlyMergeable.taskName");
	}

	protected void addSkippedFiles(IFile[] files) {
		skippedFiles.addAll(Arrays.asList(files));
	}
	
	public IFile[] getSkippedFiles() {
		return (IFile[]) skippedFiles.toArray(new IFile[skippedFiles.size()]);
	}
}
