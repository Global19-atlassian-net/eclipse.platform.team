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
package org.eclipse.team.ui.synchronize;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.team.core.synchronize.FastSyncInfoFilter;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.SyncInfoSet;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.internal.ui.synchronize.SyncInfoModelElement;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.internal.EditorManager;

/**
 * This action provides utilities for performing operations on selections that
 * are obtained from a view populated by a 
 * {@link org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider}.
 * The {@link org.eclipse.team.internal.ui.synchronize.SubscriberParticipantPage} is an example of such a view.
 * Subclasses can use this support to filter the selection in order to 
 * determine action enablement and generate the input for a {@link SynchronizeModelOperation}.
 * @see SyncInfo
 * @see SyncInfoSet
 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider
 * @see org.eclipse.team.internal.ui.synchronize.SubscriberParticipantPage
 * @see org.eclipse.team.ui.synchronize.SynchronizeModelOperation
 * @since 3.0
 */
public abstract class SynchronizeModelAction extends BaseSelectionListenerAction {
	
	private ISynchronizePageConfiguration configuration;

	/**
	 * Create an action with the given text and configuration. By default,
	 * the action registers for selection change with the selection provider 
	 * from the configuration's site.
	 * @param text the action's text
	 * @param configuration the actions synchronize page configuration
	 */
	protected SynchronizeModelAction(String text, ISynchronizePageConfiguration configuration) {
		this(text, configuration, configuration.getSite().getSelectionProvider());
	}
	
	/**
	 * Create an action with the given text and configuration. By default,
	 * the action registers for selection change with the given selection provider.
	 * @param text the action's text
	 * @param configuration the actions synchronize page configuration
	 * @param selectionProvider a selection provider
	 */
	protected SynchronizeModelAction(String text, ISynchronizePageConfiguration configuration, ISelectionProvider selectionProvider) {
		super(text);
		this.configuration = configuration;
		initialize(configuration, selectionProvider);
	}
	
	/**
	 * Method invoked from the constructor.
	 * The default implementation registers the action as a selection change
	 * listener. Subclasses may override.
	 * @param configuration the synchronize page configuration
	 * @param selectionProvider a selection provider
	 */
	protected void initialize(final ISynchronizePageConfiguration configuration, final ISelectionProvider selectionProvider) {
		selectionProvider.addSelectionChangedListener(this);
		configuration.getPage().getViewer().getControl().addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				selectionProvider.removeSelectionChangedListener(SynchronizeModelAction.this);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#run()
	 */
	public void run() {
		if(needsToSaveDirtyEditors()) {
			if(!saveAllEditors(confirmSaveOfDirtyEditor())) {
				return;
			}
		}
		try {
			getSubscriberOperation(configuration, getFilteredDiffElements()).run();
		} catch (InvocationTargetException e) {
			handle(e);
		} catch (InterruptedException e) {
			handle(e);
		}
	}

	/**
	 * Return whether dirty editor should be saved before this action is run.
	 * Default is <code>true</code>.
	 * @return whether dirty editor should be saved before this action is run
	 */
	protected boolean needsToSaveDirtyEditors() {
		return true;
	}

	/**
	 * Returns whether the user should be prompted to save dirty editors.
	 * The default is <code>true</code>.
	 * @return whether the user should be prompted to save dirty editors
	 */
	protected boolean confirmSaveOfDirtyEditor() {
		return true;
	}
	
	/**
	 * Return the subscriber operation associated with this action. This operation
	 * will be run when the action is run. Subclass may implement this method and provide 
	 * an operation subclass or may override the <code>run(IAction)</code> method directly
	 * if they choose not to implement a <code>SynchronizeModelOperation</code>.
	 * @param configuration the synchronize page configuration for the page
	 * to which this action is associated
	 * @param elements the selected diff element for which this action is enabled.
	 * @return the subscriber operation to be run by this action.
	 */
	protected abstract SynchronizeModelOperation getSubscriberOperation(ISynchronizePageConfiguration configuration, IDiffElement[] elements);
	
	/** 
	 * Generic error handling code that uses an error dialog to show the error to the 
	 * user. Subclasses can use this method and/or override it.
	 * @param e the exception that occurred.
	 */
	protected void handle(Exception e) {
		Utils.handle(e);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.actions.BaseSelectionListenerAction#updateSelection(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	protected boolean updateSelection(IStructuredSelection selection) {
		super.updateSelection(selection);
		return (getFilteredDiffElements().length > 0);
	}
	
	/**
	 * This method returns all instances of IDiffElement that are in the current
	 * selection.
	 * 
	 * @return the selected elements
	 */
	protected final IDiffElement[] getSelectedDiffElements() {
		return Utils.getDiffNodes(getStructuredSelection().toArray());
	}

	/**
	 * Filter uses to filter the user selection to contain only those
	 * elements for which this action is enabled.
	 * Default filter includes all out-of-sync elements in the current
	 * selection. Subsclasses may override.
	 * @return a sync info filter which selects all out-of-sync resources.
	 */
	protected FastSyncInfoFilter getSyncInfoFilter() {
		return new FastSyncInfoFilter();
	}

	/**
	 * Return the selected diff element for which this action is enabled.
	 * @return the list of selected diff elements for which this action is enabled.
	 */
	protected final IDiffElement[] getFilteredDiffElements() {
		IDiffElement[] elements = getSelectedDiffElements();
		List filtered = new ArrayList();
		for (int i = 0; i < elements.length; i++) {
			IDiffElement e = elements[i];
			if (e instanceof SyncInfoModelElement) {
				SyncInfo info = ((SyncInfoModelElement) e).getSyncInfo();
				if (info != null && getSyncInfoFilter().select(info)) {
					filtered.add(e);
				}
			}
		}
		return (IDiffElement[]) filtered.toArray(new IDiffElement[filtered.size()]);
	}

	/**
	 * Set the selection of this action to the given selection
	 * @param selection the selection
	 */
	public void selectionChanged(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			selectionChanged((IStructuredSelection)selection);
		} else {
			selectionChanged(StructuredSelection.EMPTY);
		}
		
	}
	
	/**
	 * @return Returns the configuration.
	 */
	public ISynchronizePageConfiguration getConfiguration() {
		return configuration;
	}
	
	/**
	 * Save all dirty editors in the workbench that are open on files that
	 * may be affected by this operation. Opens a dialog to prompt the
	 * user if <code>confirm</code> is true. Return true if successful.
	 * Return false if the user has cancelled the command. Must be called
	 * from the UI thread.
	 * 
	 * @param confirm
	 *            prompt the user if true
	 * @return boolean false if the operation was cancelled.
	 */
	public final boolean saveAllEditors(boolean confirm) {
		final boolean finalConfirm = confirm;
		final boolean[] result = new boolean[1];
		result[0] = true;

		Platform.run(new SafeRunnable("Error") { //$NON-NLS-1$
			public void run() {
				IResource[] resources = Utils.getResources(getFilteredDiffElements());
				if (resources.length == 0) return;
				List dirtyEditors = getDirtyFileEditors(Arrays.asList(resources));
				if (dirtyEditors.size() > 0) {
					IWorkbenchWindow w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
					if (w == null && PlatformUI.getWorkbench().getWorkbenchWindowCount() > 0)
						w = PlatformUI.getWorkbench().getWorkbenchWindows()[0];
					result[0] = EditorManager.saveAll(dirtyEditors, finalConfirm, w);
				}
			}
		});
		return result[0];
	}
	
	/* private */ List getDirtyFileEditors(Collection resources) {
	    ArrayList dirtyFileEditors = new ArrayList();
	    IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
	    for (int i = 0; i < windows.length; i++) {
            IWorkbenchWindow window = windows[i];
            IWorkbenchPage[] pages = window.getPages();
            for (int j = 0; j < pages.length; j++) {
                IWorkbenchPage page = pages[j];
                IEditorPart[] dirtyEditors = page.getDirtyEditors();
                for (int k = 0; k < dirtyEditors.length; k++) {
                    IEditorPart part = dirtyEditors[k];
                    IFile file = (IFile) part.getEditorInput().getAdapter(IFile.class);
                    if (file != null && resources.contains(file)) {
                    	dirtyFileEditors.add(part);
                    }
                }
            }
        }
	    return dirtyFileEditors;
	}
}