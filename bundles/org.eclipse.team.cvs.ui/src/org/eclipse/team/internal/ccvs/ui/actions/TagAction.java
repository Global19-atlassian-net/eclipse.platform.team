package org.eclipse.team.internal.ccvs.ui.actions;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.ccvs.core.CVSStatus;
import org.eclipse.team.ccvs.core.CVSTag;
import org.eclipse.team.ccvs.core.CVSTeamProvider;
import org.eclipse.team.core.ITeamManager;
import org.eclipse.team.core.ITeamProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.resources.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.LocalFile;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.ui.actions.TeamAction;

/**
 * TagAction tags the selected resources with a version tag specified by the user.
 */
public class TagAction extends TeamAction {
	// The previously remembered tag
	private static String previousTag = "";
	
	/*
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		final List messages = new ArrayList();
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					final String[] result = new String[1];
					getShell().getDisplay().syncExec(new Runnable() {
						public void run() {
							result[0] = promptForTag();
						}
					});
					if (result[0] == null) return;
					Hashtable table = getProviderMapping();
					Set keySet = table.keySet();
					monitor.beginTask("", keySet.size() * 1000);
					Iterator iterator = keySet.iterator();
					
					while (iterator.hasNext()) {
						IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 1000);
						CVSTeamProvider provider = (CVSTeamProvider)iterator.next();
						List list = (List)table.get(provider);
						IResource[] providerResources = (IResource[])list.toArray(new IResource[list.size()]);
						IStatus status = provider.tag(providerResources, IResource.DEPTH_INFINITE, new CVSTag(result[0], CVSTag.VERSION), subMonitor);
						if (status.getCode() != CVSStatus.OK) {
							messages.add(status);
						}
					}	
					previousTag = result[0];				
				} catch (TeamException e) {
					throw new InvocationTargetException(e);
				}
			}
		}, Policy.bind("TagAction.tagErrorTitle"), this.PROGRESS_DIALOG);
		
		// Check for any status messages and display them
		if ( ! messages.isEmpty()) {
			boolean error = false;
			MultiStatus combinedStatus = new MultiStatus(CVSUIPlugin.ID, 0, Policy.bind("TagAction.tagProblemsMessage"), null);
			for (int i = 0; i < messages.size(); i++) {
				IStatus status = (IStatus)messages.get(i);
				if (status.getSeverity() == IStatus.ERROR || status.getCode() == CVSStatus.SERVER_ERROR) {
					error = true;
				}
				combinedStatus.merge(status);
			}
			String message = null;
			IStatus statusToDisplay;
			if (combinedStatus.getChildren().length == 1) {
				message = combinedStatus.getMessage();
				statusToDisplay = combinedStatus.getChildren()[0];
			} else {
				statusToDisplay = combinedStatus;
			}
			String title;
			if (error) {
				title = Policy.bind("TagAction.tagErrorTitle");
			} else {
				title = Policy.bind("TagAction.tagWarningTitle");
			}
			ErrorDialog.openError(getShell(), title, message, statusToDisplay);
		}		
	}
	
	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		IResource[] resources = getSelectedResources();
		if (resources.length == 0) return false;
		ITeamManager manager = TeamPlugin.getManager();
		for (int i = 0; i < resources.length; i++) {
			ITeamProvider provider = manager.getProvider(resources[i].getProject());
			if (provider == null) return false;
			if (!((CVSTeamProvider)provider).isManaged(resources[i])) return false;
			// If resource is a file and does not exist remotely yet, disable tag.
			if (resources[i] instanceof IFile) {
				ICVSResource cvsResource = new LocalFile(resources[i].getLocation().toFile());
				if (cvsResource.getSyncInfo().isAdded()) return false;
			}
		}
		return true;
	}

	/**
	 * Prompts the user for a tag name.
	 * @return the tag, or null to cancel
	 */
	protected String promptForTag() {
		// Prompt for the tag
		IInputValidator validator = new IInputValidator() {
			public String isValid(String tagName) {
				IStatus status = CVSTag.validateTagName(tagName);
				if (status.isOK()) {
					return null;
				} else {
					return status.getMessage();
				}
			}
		};
		InputDialog dialog = new InputDialog(getShell(),
			Policy.bind("TagAction.tagResources"), Policy.bind("TagAction.enterTag"), previousTag, validator);
		if (dialog.open() != InputDialog.OK) return null;
		return dialog.getValue();
	}
}

