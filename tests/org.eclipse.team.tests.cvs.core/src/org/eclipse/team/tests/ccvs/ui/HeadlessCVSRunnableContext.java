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

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.ui.synchronize.subscriber.JobRunnableContext;
import org.eclipse.team.ui.synchronize.subscriber.ITeamRunnableContext;

public class HeadlessCVSRunnableContext implements ITeamRunnableContext {

	private boolean background;
	private IJobChangeListener listener;

	public HeadlessCVSRunnableContext() {
		this(null);
	}
	
	public HeadlessCVSRunnableContext(IJobChangeListener listener) {
		this.listener = listener;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ITeamRunnableContext#run(java.lang.String, org.eclipse.core.runtime.jobs.ISchedulingRule, org.eclipse.jface.operation.IRunnableWithProgress)
	 */
	public void run(
		String title,
		ISchedulingRule schedulingRule,
		boolean postponeBuild, IRunnableWithProgress runnable)
		throws InvocationTargetException, InterruptedException {
		
		if (listener != null) {
			new JobRunnableContext(listener).run("Headless Job", null, true, runnable);
		} else {
			runnable.run(new NullProgressMonitor());
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ITeamRunnableContext#getShell()
	 */
	public Shell getShell() {
		return null;
	}

}
