/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
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
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.AvoidableMessageDialog;
import org.eclipse.team.internal.ccvs.ui.CVSDecorator;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ui.IPromptCondition;
import org.eclipse.team.internal.ui.actions.TeamAction;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Superclass for all CVS actions. It provides helper enablement
 * methods and common pre-run and post-run hadling.
 */
abstract public class CVSAction extends TeamAction {
	
	private List accumulatedStatus = new ArrayList();
	
	/**
	 * Common run method for all CVS actions.
	 * 
	 * [Note: it would be nice to have common CVS error handling
	 * placed here and have all CVS actions subclass. For example
	 * error handling UI could provide a retry facility for actions
	 * if they have failed.]
	 */
	final public void run(IAction action) {
		accumulatedStatus.clear();
		if(needsToSaveDirtyEditors()) {
			if(!saveAllEditors()) {
				return;
			}
		}
		try {
			execute(action);
			if ( ! accumulatedStatus.isEmpty()) {
				handle(null);
			}
		} catch (InvocationTargetException e) {
			// Handle the exception and any accumulated errors
			handle(e);
		} catch (InterruptedException e) {
			// Show any problems that have occured so far
			handle(null);
		}
	}
	
	/**
	 * Add a status to the list of accumulated status. 
	 * These will be provided to method handle(Exception, IStatus[])
	 * when the action completes.
	 */
	protected void addStatus(IStatus status) {
		accumulatedStatus.add(status);
	}
	
	/**
	 * Return the list of status accumulated so far by the action. This
	 * will include any OK status that were added using addStatus(IStatus)
	 */
	protected IStatus[] getAccumulatedStatus() {
		return (IStatus[]) accumulatedStatus.toArray(new IStatus[accumulatedStatus.size()]);
	}
	
	/**
	 * Return the title to be displayed on error dialogs.
	 * Sunclasses should override to present a custon message.
	 */
	protected String getErrorTitle() {
		return Policy.bind("CVSAction.errorTitle"); //$NON-NLS-1$
	}
	
	/**
	 * Return the title to be displayed on error dialogs when warnigns occur.
	 * Sunclasses should override to present a custon message.
	 */
	protected String getWarningTitle() {
		return Policy.bind("CVSAction.warningTitle"); //$NON-NLS-1$
	}

	/**
	 * Return the message to be used for the parent MultiStatus when 
	 * mulitple errors occur during an action.
	 * Sunclasses should override to present a custon message.
	 */
	protected String getMultiStatusMessage() {
		return Policy.bind("CVSAction.multipleProblemsMessage"); //$NON-NLS-1$
	}
	
	/**
	 * Return the status to be displayed in an error dialog for the given list
	 * of non-OK status.
	 * 
	 * This method can be overridden bu subclasses. Returning an OK status will 
	 * prevent the error dialog from being shown.
	 */
	protected IStatus getStatusToDisplay(IStatus[] problems) {
		if (problems.length == 1) {
			return problems[0];
		}
		MultiStatus combinedStatus = new MultiStatus(CVSUIPlugin.ID, 0, getMultiStatusMessage(), null); //$NON-NLS-1$
		for (int i = 0; i < problems.length; i++) {
			combinedStatus.merge(problems[i]);
		}
		return combinedStatus;
	}
	
	/**
	 * Method that implements generic handling of an exception. 
	 * 
	 * Thsi method will also use any accumulated status when determining what
	 * information (if any) to show the user.
	 * 
	 * @param exception the exception that occured (or null if none occured)
	 * @param status any status accumulated by the action before the end of 
	 * the action or the exception occured.
	 */
	protected void handle(Exception exception) {
		// Get the non-OK statii
		List problems = new ArrayList();
		IStatus[] status = getAccumulatedStatus();
		if (status != null) {
			for (int i = 0; i < status.length; i++) {
				IStatus iStatus = status[i];
				if ( ! iStatus.isOK() || iStatus.getCode() == CVSStatus.SERVER_ERROR) {
					problems.add(iStatus);
				}
			}
		}
		// Handle the case where there are no problem statii
		if (problems.size() == 0) {
			if (exception == null) return;
			handle(exception, getErrorTitle(), null);
			return;
		}

		// For now, display both the exception and the problem status
		// Later, we can determine how to display both together
		if (exception != null) {
			handle(exception, getErrorTitle(), null);
		}
		
		String message = null;
		IStatus statusToDisplay = getStatusToDisplay((IStatus[]) problems.toArray(new IStatus[problems.size()]));
		if (statusToDisplay.isOK()) return;
		if (statusToDisplay.isMultiStatus() && statusToDisplay.getChildren().length == 1) {
			message = statusToDisplay.getMessage();
			statusToDisplay = statusToDisplay.getChildren()[0];
		}
		String title;
		if (statusToDisplay.getSeverity() == IStatus.ERROR) {
			title = getErrorTitle();
		} else {
			title = getWarningTitle();
		}
		ErrorDialog.openError(getShell(), title, message, statusToDisplay);
	}
	
	/**
	 * Actions must override to do their work.
	 */
	abstract protected void execute(IAction action) throws InvocationTargetException, InterruptedException;
	
	/**
	 * Answers if the action would like dirty editors to saved
	 * based on the CVS preference before running the action. By
	 * default most CVS action modify the workspace and thus should
	 * save dirty editors.
	 */
	protected boolean needsToSaveDirtyEditors() {
		return true;
	}
	
	/**
	 * Answers <code>true</code> if the current selection contains resource that don't
	 * have overlapping paths and <code>false</code> otherwise. 
	 */
	protected boolean isSelectionNonOverlapping() throws TeamException {
		IResource[] resources = getSelectedResources();
		// allow operation for non-overlapping resource selections
		if(resources.length>0) {
			List validPaths = new ArrayList(2);
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];
				
				// only allow cvs resources to be selected
				if(RepositoryProvider.getProvider(resource.getProject(), CVSProviderPlugin.getTypeId()) == null) {
					return false;
				}
				
				// check if this resource overlaps other selections		
				IPath resourceFullPath = resource.getFullPath();
				if(!validPaths.isEmpty()) {
					for (Iterator it = validPaths.iterator(); it.hasNext();) {
						IPath path = (IPath) it.next();
						if(path.isPrefixOf(resourceFullPath) || 
					       resourceFullPath.isPrefixOf(path)) {
							return false;
						}
					}
				}
				validPaths.add(resourceFullPath);
				
				// ensure that resources are managed
				ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
				if(cvsResource.isFolder()) {
					if( ! ((ICVSFolder)cvsResource).isCVSFolder()) return false;
				} else {
					if( ! cvsResource.isManaged()) return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Returns the selected CVS resources
	 */
	protected ICVSResource[] getSelectedCVSResources() {
		ArrayList resources = null;
		if (!selection.isEmpty()) {
			resources = new ArrayList();
			Iterator elements = ((IStructuredSelection) selection).iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ICVSResource) {
					resources.add(next);
					continue;
				}
				if (next instanceof IAdaptable) {
					IAdaptable a = (IAdaptable) next;
					Object adapter = a.getAdapter(ICVSResource.class);
					if (adapter instanceof ICVSResource) {
						resources.add(adapter);
						continue;
					}
				}
			}
		}
		if (resources != null && !resources.isEmpty()) {
			return (ICVSResource[])resources.toArray(new ICVSResource[resources.size()]);
		}
		return new ICVSResource[0];
	}

	/**
	 * Get selected CVS remote folders
	 */
	protected ICVSRemoteFolder[] getSelectedRemoteFolders() {
		ArrayList resources = null;
		if (!selection.isEmpty()) {
			resources = new ArrayList();
			Iterator elements = ((IStructuredSelection) selection).iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ICVSRemoteFolder) {
					resources.add(next);
					continue;
				}
				if (next instanceof IAdaptable) {
					IAdaptable a = (IAdaptable) next;
					Object adapter = a.getAdapter(ICVSRemoteFolder.class);
					if (adapter instanceof ICVSRemoteFolder) {
						resources.add(adapter);
						continue;
					}
				}
			}
		}
		if (resources != null && !resources.isEmpty()) {
			return (ICVSRemoteFolder[])resources.toArray(new ICVSRemoteFolder[resources.size()]);
		}
		return new ICVSRemoteFolder[0];
	}
	
	/**
	 * A helper prompt condition for prompting for CVS dirty state.
	 */
	public static IPromptCondition getOverwriteLocalChangesPrompt() {
		return new IPromptCondition() {
			public boolean needsPrompt(IResource resource) {
				return CVSDecorator.isDirty(resource);
			}
			public String promptMessage(IResource resource) {
				return Policy.bind("ReplaceWithAction.localChanges", resource.getName());//$NON-NLS-1$
			}
		};
	}
		
	/**
	 * Checks if a the resources' parent's tags are different then the given tag. 
	 * Prompts the user that they are adding mixed tags and returns <code>true</code> if 
	 * the user wants to continue or <code>false</code> otherwise.
	 */
	public static boolean checkForMixingTags(final Shell shell, IResource[] resources, final CVSTag tag) throws CVSException {
		final IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();
		if(!store.getBoolean(ICVSUIConstants.PREF_PROMPT_ON_MIXED_TAGS)) {
			return true;
		};
		
		final boolean[] result = new boolean[] { true };
		
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (resource.getType() != IResource.PROJECT) {
				ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
				CVSTag parentTag = cvsResource.getParent().getFolderSyncInfo().getTag();
				if (!CVSTag.equalTags(tag, parentTag)) {
					shell.getDisplay().syncExec(new Runnable() {
						public void run() {							
							AvoidableMessageDialog dialog = new AvoidableMessageDialog(
									shell,
									Policy.bind("CVSAction.mixingTagsTitle"),  //$NON-NLS-1$
									null,	// accept the default window icon
									Policy.bind("CVSAction.mixingTags", tag.getName()),  //$NON-NLS-1$
									MessageDialog.QUESTION, 
									new String[] {IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL}, 
									0);
									
							result[0] = dialog.open() == 0;
							if(result[0] && dialog.isDontShowAgain()) {
								store.setValue(ICVSUIConstants.PREF_PROMPT_ON_MIXED_TAGS, false);
							}																				
						}
					});
					// only prompt once
					break;										
				}
			}
		}
		return result[0];
	}
	
	/**
	 * Based on the CVS preference for saving dirty editors this method will either
	 * ignore dirty editors, save them automatically, or prompt the user to save them.
	 * 
	 * @return <code>true</code> if the command succeeded, and <code>false</code>
	 * if at least one editor with unsaved changes was not saved
	 */
	private boolean saveAllEditors() {
		final int option = CVSUIPlugin.getPlugin().getPreferenceStore().getInt(ICVSUIConstants.PREF_SAVE_DIRTY_EDITORS);
		final boolean[] okToContinue = new boolean[] {true};
		if (option != ICVSUIConstants.OPTION_NEVER) {		
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					IWorkbenchPage oldPage = CVSUIPlugin.getActivePage();
					boolean confirm = option == ICVSUIConstants.OPTION_PROMPT;
					IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
					for (int i = 0; i < windows.length; i++) {
						IWorkbenchPage[] pages = windows[i].getPages();
						for (int j = 0; j < pages.length; j++) {	
							okToContinue[0] = pages[j].saveAllEditors(confirm);
							if (!okToContinue[0]) break;
						}
						if (!okToContinue[0]) break;
					}
					if (oldPage != null) {
						oldPage.getWorkbenchWindow().getShell().setFocus();
					}
				}
			});
		} 
		return okToContinue[0];
	}
}