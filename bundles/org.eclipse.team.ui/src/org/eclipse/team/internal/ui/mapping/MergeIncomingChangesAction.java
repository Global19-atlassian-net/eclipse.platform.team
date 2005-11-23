/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.mapping;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.mapping.IMergeContext;
import org.eclipse.team.ui.operations.ModelProviderOperation;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;

/**
 * Action that performs an optimistic merge
 */
public class MergeIncomingChangesAction extends ModelProviderAction {

	public MergeIncomingChangesAction(ISynchronizePageConfiguration configuration) {
		super(null, configuration);
		Utils.initAction(this, "action.merge."); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#run()
	 */
	public void run() {
		final IMergeContext context = (IMergeContext)((ModelSynchronizeParticipant)getConfiguration().getParticipant()).getContext();
		try {
			new ModelProviderOperation(getConfiguration()) {
				public void run(IProgressMonitor monitor) throws InvocationTargetException,
						InterruptedException {
					try {
						performMerge(context, monitor);
						if (context.getSyncInfoTree().isEmpty() || !hasIncomingChanges(context.getSyncInfoTree())) {
							promptForNoChanges();
						}
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			
			}.run();
		} catch (InvocationTargetException e) {
			Utils.handle(e);
		} catch (InterruptedException e) {
			// Ignore
		}
	}

	private void promptForNoChanges() {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				MessageDialog.openInformation(getConfiguration().getSite().getShell(), "Merge Success", "All changes were merged successfully");
			};
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.ModelProviderAction#isEnabledForSelection(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	protected boolean isEnabledForSelection(IStructuredSelection selection) {
		// This action is always enabled
		return true;
	}
	

}
