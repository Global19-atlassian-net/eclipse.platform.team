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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.ICVSRunnable;
import org.eclipse.team.internal.ccvs.core.client.Checkout;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Request;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.Update;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.resources.EclipseSynchronizer;
import org.eclipse.team.internal.ccvs.ui.Policy;
/**
 * This class acts as an abstract class for checkout operations.
 * It provides a few common methods.
 */
public abstract class CheckoutProjectOperation extends CheckoutOperation {

	private String targetLocation;

	/**
	 * @param shell
	 * @param targetLocation
	 */
	public CheckoutProjectOperation(Shell shell, ICVSRemoteFolder[] remoteFolders, String targetLocation) {
		super(shell, remoteFolders);
		this.targetLocation = targetLocation;
	}
	
	/**
	 * Create and open the project, using a custom location if there is one.
	 * 
	 * @param project
	 * @param monitor
	 * @throws CVSException
	 */
	protected void createAndOpenProject(IProject project, IProgressMonitor monitor) throws CVSException {
		try {
			monitor.beginTask(null, 5);
			IProjectDescription desc = getDescriptionFor(project);
			if (project.exists()) {
				if (desc != null) {
					project.move(desc, true, Policy.subMonitorFor(monitor, 3));
				}
			} else {
				if (desc == null) {
					// create in default location
					project.create(Policy.subMonitorFor(monitor, 3));
				} else {
					// create in some other location
					project.create(desc, Policy.subMonitorFor(monitor, 3));
				}
			}
			if (!project.isOpen()) {
				project.open(Policy.subMonitorFor(monitor, 2));
			}
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		} finally {
			monitor.done();
		}
	}
	
	protected IProjectDescription getDescriptionFor(IProject project) {
		if (targetLocation == null) return null;
		String projectName = project.getName();
		IProjectDescription description = ResourcesPlugin.getWorkspace().newProjectDescription(projectName);
		description.setLocation(getTargetLocationFor(project));
		return description;
	}
	
	/**
	 * Return the target location where the given project should be located or
	 * null if the default location should be used.
	 * 
	 * @param project
	 */
	protected IPath getTargetLocationFor(IProject project) {
		if (targetLocation == null) return null;
		return new Path(targetLocation);
	}

	protected String getRemoteModuleName(ICVSRemoteFolder resource) {
		String moduleName;
		if (resource.isDefinedModule()) {
			moduleName = resource.getName();
		} else {
			moduleName = resource.getRepositoryRelativePath();
		}
		return moduleName;
	}

	protected IStatus checkout(final ICVSRemoteFolder resource, final IProject project, IProgressMonitor pm) throws CVSException {
		// Get the location and the workspace root
		ICVSFolder root = CVSWorkspaceRoot.getCVSFolderFor(ResourcesPlugin.getWorkspace().getRoot());
		ICVSRepositoryLocation repository = resource.getRepository();
		// Open a connection session to the repository
		final Session session = new Session(repository, root);
		pm.beginTask(null, 100);
		session.open(Policy.subMonitorFor(pm, 5), false /* read-only */);
		try {
			
			// Determine the local target projects (either the project provider or the module expansions)
			// Note: Module expansions can be run over the same connection as a checkout
			final IProject[] targetProjects = determineProjects(session, resource, project, Policy.subMonitorFor(pm, 5));
			if (targetProjects == null) {
				// An error occurred and was recorded so return it
				return getLastError();
			} else if (targetProjects.length == 0) {
				return OK;
			}
			
			final IStatus[] result = new IStatus[] { null };
			EclipseSynchronizer.getInstance().run(getSchedulingRule(targetProjects), new ICVSRunnable() {
				public void run(IProgressMonitor monitor) throws CVSException {
					result[0] = performCheckout(session, resource, targetProjects, project != null, Policy.subMonitorFor(monitor, 90));
				}
			}, Policy.subMonitorFor(pm, 90));
			return result[0];
		} catch (CVSException e) {
			// An exception occurred either during the module-expansion or checkout
			// Since we were able to make a connection, return the status so the
			// checkout of any other modules can proceed
			return e.getStatus();
		} finally {
			session.close();
			pm.done();
		}
	}

	private ISchedulingRule getSchedulingRule(IProject[] projects) {
		if (projects.length == 1) {
			return projects[0];
		} else {
			return new MultiRule(projects);
		}
	}

	/* private */ IStatus performCheckout(Session session, ICVSRemoteFolder resource, IProject[] targetProjects, boolean sendModuleName, IProgressMonitor pm) throws CVSException {
		// Set the task name of the progress monitor to let the user know
		// which project we're on. Don't use subTask since that will be
		// changed when the checkout command is run.
		String taskName;
		if (targetProjects.length == 1) {
			taskName = Policy.bind("CheckoutProjectOperation.8", resource.getName(), targetProjects[0].getName()); //$NON-NLS-1$
		} else {
			taskName = Policy.bind("CheckoutProjectOperation.9", resource.getName(), String.valueOf(targetProjects.length)); //$NON-NLS-1$
		}
		pm.beginTask(taskName, 100);
		pm.setTaskName(taskName);
		
		try {
			// Scrub the local contents if requested
			if (performScrubProjects()) {
				IStatus result = scrubProjects(resource, targetProjects, Policy.subMonitorFor(pm, 9));
				if (!result.isOK()) {
					return result;
				}
			}
				
			// Determine if the target project is the same name as the remote folder
			// in which case we'll use -d to flatten the directory structure
			IProject project;
			if (targetProjects.length == 1 && (sendModuleName || targetProjects[0].getName().equals(resource.getName()))) {
				project = targetProjects[0];
			} else {
				project = null;
			}
				
			// Build the local options
			List localOptions = new ArrayList();
			// Add the option to load into the target project if one was supplied
			if (project != null) {
				localOptions.add(Checkout.makeDirectoryNameOption(project.getName()));
			}
			// Prune empty directories if pruning enabled
			if (CVSProviderPlugin.getPlugin().getPruneEmptyDirectories()) 
				localOptions.add(Checkout.PRUNE_EMPTY_DIRECTORIES);
			// Add the options related to the CVSTag
			CVSTag tag = resource.getTag();
			if (tag == null) {
				// A null tag in a remote resource indicates HEAD
				tag = CVSTag.DEFAULT;
			}
			localOptions.add(Update.makeTagOption(tag));
			
			// Perform the checkout
			IStatus status = Command.CHECKOUT.execute(session,
				Command.NO_GLOBAL_OPTIONS,
				(LocalOption[])localOptions.toArray(new LocalOption[localOptions.size()]),
				new String[]{getRemoteModuleName(resource)},
				null,
				Policy.subMonitorFor(pm, 90));
			if (status.getCode() == CVSStatus.SERVER_ERROR) {
				// Any created projects will exist but will not be mapped to CVS
				return status;
			}
				
			// Bring the project into the workspace
			refreshProjects(targetProjects, Policy.subMonitorFor(pm, 1));
			
			return OK;
		} finally {
			pm.done();
		}
	}

	/*
	 * Determine the workspace project(s) that will be affected by the checkout. 
	 * If project is not null, then it will be the only target project of the checkout. 
	 * Otherwise, the remote folder could expand to multiple projects.
	 * 
	 * If the remote resource is a folder which is not a root folder (i.e. a/b/c),
	 * then the target project will be the last segment (i.e. c).
	 */
	private IProject[] determineProjects(Session session, final ICVSRemoteFolder remoteFolder, IProject project, IProgressMonitor pm) throws CVSException {
			
		Set targetProjectSet = new HashSet();
		String moduleName = getRemoteModuleName(remoteFolder);
		if (project == null) {
			
			// Fetch the module expansions
			IStatus status = Request.EXPAND_MODULES.execute(session, new String[] {moduleName}, pm);
			if (status.getCode() == CVSStatus.SERVER_ERROR) {
				collectStatus(status);
				return null;
			}
			
			// Convert the module expansions to local projects
			String[] expansions = session.getModuleExpansions();
			if (expansions.length == 1 && expansions[0].equals(moduleName)) {
				// For a remote folder, use the last segment as the project to be created
				String lastSegment = new Path(expansions[0]).lastSegment();
				targetProjectSet.add(ResourcesPlugin.getWorkspace().getRoot().getProject(lastSegment));
			} else {
				for (int j = 0; j < expansions.length; j++) {
					targetProjectSet.add(ResourcesPlugin.getWorkspace().getRoot().getProject(new Path(expansions[j]).segment(0)));
				}
			}
			
		} else {
			targetProjectSet.add(project);
		}
		
		// Return the local projects affected by the checkout
		IProject[] targetProjects = (IProject[]) targetProjectSet.toArray(new IProject[targetProjectSet.size()]);
		return targetProjects;
	}

	/**
	 * Return true if the target projects should be scrubbed before the checkout occurs.
	 * Default is to scrub the projects. Can be overridden by subclasses.
	 */
	protected boolean performScrubProjects() {
		return true;
	}

	/*
	 * This method is invoked to scrub the local projects that are the check out target of
	 * a single remote module.
	 */
	private IStatus scrubProjects(ICVSRemoteFolder remoteFolder, IProject[] projects, IProgressMonitor monitor) throws CVSException {
		if (projects == null) {
			monitor.done();
			return OK;
		}
		// Prompt first before any work is done
		if (projects.length > 1) {
			setInvolvesMultipleResources(true);
		}
		for (int i=0;i<projects.length;i++) {
			IProject project = projects[i];
			if (needsPromptForOverwrite(project) && !promptToOverwrite(remoteFolder, project)) {
				Policy.cancelOperation();
			}
		}
		// Create the projects and remove any previous content
		monitor.beginTask(null, projects.length * 100); //$NON-NLS-1$
		for (int i=0;i<projects.length;i++) {
			IProject project = projects[i];
			createAndOpenProject(project, Policy.subMonitorFor(monitor, 10));
			scrubProject(project, Policy.subMonitorFor(monitor, 90));
		}
		monitor.done();
		return OK;
	}

	private void scrubProject(IProject project, IProgressMonitor monitor) throws CVSException {
		try {
			// unmap the project from any previous repository provider
			if (RepositoryProvider.getProvider(project) != null)
				RepositoryProvider.unmap(project);
			// We do not want to delete the project to avoid a project deletion delta
			// We do not want to delete the .project to avoid core exceptions
			IResource[] children = project.members(IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS);
			monitor.beginTask(null, children.length * 100);
			monitor.subTask(Policy.bind("CheckoutOperation.scrubbingProject", project.getName())); //$NON-NLS-1$	
			try {
				for (int j = 0; j < children.length; j++) {
					if ( ! children[j].getName().equals(".project")) {//$NON-NLS-1$
						children[j].delete(true /*force*/, Policy.subMonitorFor(monitor, 100));
					}
				}
			} finally {
				monitor.done();
			}
		} catch (TeamException e) {
			throw CVSException.wrapException(e);
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
	}

	protected boolean needsPromptForOverwrite(IProject project) {
				
		// First, check the description location
		IProjectDescription desc = getDescriptionFor(project);
		if (desc != null) {
			File localLocation = desc.getLocation().toFile();
			if (localLocation.exists()) return true;
		}
				
		// Next, check if the resource itself exists
		if (project.exists()) return true;
				
		// Finally, check if the location in the workspace exists;
		File localLocation  = getFileLocation(project);
		if (localLocation.exists()) return true;
				
		// The target doesn't exist
		return false;
	}
	
	protected File getFileLocation(IProject project) {
		return new File(project.getParent().getLocation().toFile(), project.getName());
	}
	
	/**
	 * @param project
	 * @return
	 */
	private boolean promptToOverwrite(ICVSRemoteFolder remoteFolder, IProject project) {
		return promptToOverwrite(Policy.bind("CheckoutOperation.confirmOverwrite"), getOverwritePromptMessage(remoteFolder, project)); //$NON-NLS-1$
	}

	protected String getOverwritePromptMessage(ICVSRemoteFolder remoteFolder, IProject project) {
		if(project.exists()) {
			return Policy.bind("CheckoutOperation.thisResourceExists", project.getName(), getRemoteModuleName(remoteFolder));//$NON-NLS-1$
		} else {
			return Policy.bind("CheckoutOperation.thisExternalFileExists", project.getName(), getRemoteModuleName(remoteFolder));//$NON-NLS-1$
		}
	}
	
	/*
	 * Bring the provied projects into the workspace
	 */
	private void refreshProjects(IProject[] projects, IProgressMonitor monitor) throws CVSException {
		monitor.beginTask(null, projects.length * 100);
		try {
			for (int i = 0; i < projects.length; i++) {
				IProject project = projects[i];
				// Register the project with Team
				try {
					monitor.subTask(Policy.bind("CheckoutOperation.refreshingProject", project.getName())); //$NON-NLS-1$
					RepositoryProvider.map(project, CVSProviderPlugin.getTypeId());
				} catch (TeamException e) {
					throw CVSException.wrapException(e);
				}
				CVSTeamProvider provider = (CVSTeamProvider)RepositoryProvider.getProvider(project, CVSProviderPlugin.getTypeId());
				provider.setWatchEditEnabled(CVSProviderPlugin.getPlugin().isWatchEditEnabled());
			}
		} finally {
			monitor.done();
		}
	}

	protected String getTaskName() {
		ICVSRemoteFolder[] remoteFolders = getRemoteFolders();
		if (remoteFolders.length == 1) {
			return Policy.bind("CheckoutSingleProjectOperation.taskname", remoteFolders[0].getName()); //$NON-NLS-1$
		} else {
			return Policy.bind("CheckoutMultipleProjectsOperation.taskName", new Integer(remoteFolders.length).toString());  //$NON-NLS-1$
		}
	}
	
}
