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

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.ccvs.core.CVSTeamProvider;
import org.eclipse.team.ccvs.core.ICVSFolder;
import org.eclipse.team.ccvs.core.ICVSResource;
import org.eclipse.team.core.ITeamManager;
import org.eclipse.team.core.ITeamProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.CVSDecorator;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.ui.actions.TeamAction;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

/**
 * UpdateAction performs a 'cvs update' command on the selected resources.
 * If conflicts are present (file has been changed both remotely and locally),
 * the changes will be merged into the local file such that the user must
 * resolve the conflicts. This action is temporary code; it will be removed
 * when a functional synchronize view has been implemented.
 */
public class UnmanageAction extends TeamAction {
	
	static class DeleteProjectDialog extends MessageDialog {

		private IProject[] projects;
		private boolean deleteContent = false;
		private Button radio1;
		private Button radio2;
		
		DeleteProjectDialog(Shell parentShell, IProject[] projects) {
			super(
				parentShell, 
				getTitle(projects), 
				null,	// accept the default window icon
				getMessage(projects),
				MessageDialog.QUESTION, 
				new String[] {IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL},
				0); 	// yes is the default
			this.projects = projects;
		}
		
		static String getTitle(IProject[] projects) {
			if (projects.length == 1)
				return Policy.bind("Unmanage.title");  //$NON-NLS-1$
			else
				return Policy.bind("Unmanage.titleN");  //$NON-NLS-1$
		}
		
		static String getMessage(IProject[] projects) {
			if (projects.length == 1) {
				IProject project = projects[0];
				return Policy.bind("Unmanage.message", project.getName());  //$NON-NLS-1$
			}
			else {
				return Policy.bind("Unmanage.messageN", new Integer(projects.length).toString());  //$NON-NLS-1$
			}
		}
		
		protected Control createCustomArea(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout());
			radio1 = new Button(composite, SWT.RADIO);
			radio1.addSelectionListener(selectionListener);
			
			radio1.setText(Policy.bind("Unmanage.option2"));

			radio2 = new Button(composite, SWT.RADIO);
			radio2.addSelectionListener(selectionListener);

			radio2.setText(Policy.bind("Unmanage.option1"));
			
			// set initial state
			radio1.setSelection(deleteContent);
			radio2.setSelection(!deleteContent);
			
			return composite;
		}
		
		private SelectionListener selectionListener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Button button = (Button) e.widget;
				if (button.getSelection()) {
					deleteContent = (button == radio1);
				}
			}
		};
		
		public boolean getDeleteContent() {
			return deleteContent;
		}
	}
	
	private boolean deleteContent = false;
	
	/*
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		final List unmanagedProjects = new ArrayList();
		
		run(new WorkspaceModifyOperation() {
			public void execute(IProgressMonitor monitor) throws InterruptedException, InvocationTargetException {
				try {					
					if(confirmDeleteProjects()) {		
						Hashtable table = getProviderMapping();
						Set keySet = table.keySet();
						monitor.beginTask("", keySet.size() * 1000);
						monitor.setTaskName(Policy.bind("Unmanage.unmanaging"));
						Iterator iterator = keySet.iterator();
						while (iterator.hasNext()) {
							IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 1000);
							subMonitor.beginTask(null, 100);
							CVSTeamProvider provider = (CVSTeamProvider)iterator.next();
							List list = (List)table.get(provider);
							IResource[] providerResources = (IResource[])list.toArray(new IResource[list.size()]);
							for (int i = 0; i < providerResources.length; i++) {
								IResource resource = providerResources[i];
								ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor((IContainer) resource);
								if(deleteContent) {
									folder.unmanage();
									folder.reloadSyncInfo(Policy.subMonitorFor(subMonitor, 90));
								}
								TeamPlugin.getManager().removeProvider((IProject)resource, Policy.subMonitorFor(subMonitor, 10));							
								CVSDecorator.refresh(resource);
							}											
						}										
					}
				} catch (TeamException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		}, Policy.bind("Unmanage.unmanaging"), this.PROGRESS_DIALOG);
	}

	boolean confirmDeleteProjects() {
		final int[] result = new int[] { MessageDialog.OK };
		IProject[] projects = getSelectedProjects();
		final DeleteProjectDialog dialog = new DeleteProjectDialog(shell, projects);
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				result[0] = dialog.open();
			}
		});		
		deleteContent = dialog.getDeleteContent();
		return result[0] == 0;  // YES
	}
	
	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		IResource[] resources = getSelectedResources();
		if (resources.length == 0) return false;
		ITeamManager manager = TeamPlugin.getManager();
		for (int i = 0; i < resources.length; i++) {
			if(resources[i].getType()!=IResource.PROJECT) return false;
			ITeamProvider provider = manager.getProvider(resources[i].getProject());
			if (provider == null) return false;
			ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resources[i]);
			if (!cvsResource.isManaged()) return false;
		}
		return true;
	}
}