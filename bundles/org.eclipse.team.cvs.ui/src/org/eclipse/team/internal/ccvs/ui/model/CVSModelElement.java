package org.eclipse.team.internal.ccvs.ui.model;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.ui.model.IWorkbenchAdapter;

public abstract class CVSModelElement implements IWorkbenchAdapter {
	
	private IRunnableContext runnableContext;
	
	/**
	 * Handles exceptions that occur in CVS model elements.
	 */
	protected void handle(Throwable t) {
		CVSUIPlugin.openError(null, null, null, t, CVSUIPlugin.LOG_NONTEAM_EXCEPTIONS);
	}
	
	/**
	 * Gets the children of the receiver by invoking the <code>internalGetChildren</code>.
	 * A appropriate progress indicator will be used if requested.
	 */
	public Object[] getChildren(final Object o, boolean needsProgress) {
		try {
			if (needsProgress) {
				final Object[][] result = new Object[1][];
				IRunnableWithProgress runnable = new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						try {
							result[0] = CVSModelElement.this.internalGetChildren(o, monitor);
						} catch (TeamException e) {
							throw new InvocationTargetException(e);
						}
					}
				};
				getRunnableContext().run(isInterruptable() /*fork*/, isInterruptable() /*cancelable*/, runnable);
				return result[0];
			} else {
				return internalGetChildren(o, null);
			}
		} catch (InterruptedException e) {
		} catch (InvocationTargetException e) {
			handle(e);
		} catch (TeamException e) {
			handle(e);
		}
		return new Object[0];
	}
	
	/**
	 * Method internalGetChildren.
	 * @param o
	 * @return Object[]
	 */
	public abstract Object[] internalGetChildren(Object o, IProgressMonitor monitor) throws TeamException;
	
	/**
	 * Get the childen using <code>internalGetChildren</code> without requesting a
	 * progress indicator.
	 * 
	 * @see org.eclipse.ui.model.IWorkbenchAdapter#getChildren(java.lang.Object)
	 */
	public Object[] getChildren(Object o) {
		return getChildren(o, isNeedsProgress());
	}

	public boolean isNeedsProgress() {
		return false;
	}
	
	public boolean isInterruptable() {
		return false;
	}
	
	/**
	 * Returns the runnableContext.
	 * @return IRunnableContext
	 */
	public IRunnableContext getRunnableContext() {
		if (runnableContext == null) {
			return new IRunnableContext() {
				public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable) throws InvocationTargetException, InterruptedException {
					CVSUIPlugin.runWithProgress(null, cancelable, runnable);
				}
			};
		}
		return runnableContext;
	}

	/**
	 * Sets the runnableContext.
	 * @param runnableContext The runnableContext to set
	 */
	public void setRunnableContext(IRunnableContext runnableContext) {
		this.runnableContext = runnableContext;
	}

}

