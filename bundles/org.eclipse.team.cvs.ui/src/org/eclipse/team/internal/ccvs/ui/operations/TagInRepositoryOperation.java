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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.RTag;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.ui.Policy;


public class TagInRepositoryOperation extends RemoteOperation implements ITagOperation {

	private Set localOptions = new HashSet();
	private CVSTag tag;

	public TagInRepositoryOperation(Shell shell, ICVSRemoteResource[] remoteResource) {
		super(shell, remoteResource);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CVSOperation#execute(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void execute(IProgressMonitor monitor) throws CVSException, InterruptedException {
		ICVSRemoteResource[] resources = getRemoteResources();
		monitor.beginTask(null, 1000 * resources.length);
		for (int i = 0; i < resources.length; i++) {
			IStatus status = resources[i].tag(getTag(), getLocalOptions(), new SubProgressMonitor(monitor, 1000));
			collectStatus(status);
		}		
	}

	/**
	 * Override to dislay the number of tag operations that succeeded
	 */
	protected String getErrorMessage(IStatus[] problems, int operationCount) {
		if(operationCount == 1) {
			return Policy.bind("TagInRepositoryAction.tagProblemsMessage"); //$NON-NLS-1$
		} else {
			return Policy.bind("TagInRepositoryAction.tagProblemsMessageMultiple", //$NON-NLS-1$
				Integer.toString(operationCount - problems.length), Integer.toString(problems.length));
		}
	}

	private LocalOption[] getLocalOptions() {
		return (LocalOption[]) localOptions.toArray(new LocalOption[localOptions.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ITagOperation#getTag()
	 */
	public CVSTag getTag() {
		return tag;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ITagOperation#setTag(org.eclipse.team.internal.ccvs.core.CVSTag)
	 */
	public void setTag(CVSTag tag) {
		this.tag = tag;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ITagOperation#addLocalOption(org.eclipse.team.internal.ccvs.core.client.Command.LocalOption)
	 */
	public void addLocalOption(LocalOption option)  {
		localOptions.add(option);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ITagOperation#moveTag()
	 */
	public void moveTag() {
		addLocalOption(RTag.FORCE_REASSIGNMENT);
		addLocalOption(RTag.CLEAR_FROM_REMOVED);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ITagOperation#recurse()
	 */
	public void recurse() {
		addLocalOption(Command.DO_NOT_RECURSE);
	}

	protected String getTaskName() {
		return Policy.bind("TagFromRepository.taskName"); //$NON-NLS-1$
	}
}
