package org.eclipse.team.ccvs.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.team.core.ITeamProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProvider;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Command.QuietOption;
import org.eclipse.team.internal.ccvs.core.resources.Synchronizer;
import org.eclipse.team.internal.ccvs.core.util.ProjectDescriptionManager;
import org.eclipse.team.internal.ccvs.core.util.Util;

public class CVSProviderPlugin extends Plugin {

	private static CVSProviderPlugin instance;
	/** 
	 * Int used for the communications timeout on server connections (in seconds)
	 */
	public static final int DEFAULT_TIMEOUT = 60;
	private int communicationsTimeout = DEFAULT_TIMEOUT;

	/**
	 * Boolean which determines if empty directories received
	 * from the server should be pruned
	 */
	public static final boolean DEFAULT_PRUNE = true;
	private boolean pruneEmptyDirectories = DEFAULT_PRUNE; 

	private QuietOption quietness;
	
	public static final String ID = "org.eclipse.team.cvs.core";
	public static final String PT_AUTHENTICATOR = "authenticator";
	public static final String PT_CONNECTIONMETHODS = "connectionmethods";
	
	/**
	 * The identifier for the CVS nature
	 * (value <code>"org.eclipse.team.cvs.core.nature"</code>).
	 * The presence of this nature on a project indicates that it is 
	 * CVS-capable.
	 *
	 * @see org.eclipse.core.resources.IProject#hasNature
	 */
	public static final String NATURE_ID = ID + ".cvsnature" ;

	/**
	 * Constructor for CVSProviderPlugin.
	 * @param descriptor
	 */
	public CVSProviderPlugin(IPluginDescriptor descriptor) {
		super(descriptor);
		instance = this;
	}
	
	/**
	 * Convenience method for logging CVSExceptiuons to the plugin log
	 */
	public static void log(TeamException e) {
		// For now, we'll log the status. However we should do more
		instance.getLog().log(e.getStatus());
	}
	public static void log(IStatus status) {
		// For now, we'll log the status. However we should do more
		instance.getLog().log(status);
	}

	/**
	 * Returns the singleton plug-in instance.
	 * 
	 * @return the plugin instance
	 */
	public static CVSProviderPlugin getPlugin() {
		return instance;
	}
	
	/**
	 * Get the ICVSProvider
	 */
	public static ICVSProvider getProvider() {
		return CVSProvider.getInstance();
	}

	/**
	 * Should the CVS adapter prune empty directories
	 */
	public boolean getPruneEmptyDirectories() {
		return pruneEmptyDirectories;
	}

	/**
	 * Set whether the CVS adapter should prune empty directories
	 */
	public void setPruneEmptyDirectories(boolean prune) {
		pruneEmptyDirectories = prune;
	}

	/**
	 * Set the timeout value for communications to a value in seconds.
	 * The value must be greater than or equal 0. If is it 0, there is no timeout.
	 */
	public void setTimeout(int timeout) {
		this.communicationsTimeout = Math.max(0, timeout);
	}

	/**
	 * Get the communications timeout value in seconds
	 */
	public int getTimeout() {
		return communicationsTimeout;
	}
	
	/**
	 * Set the quietness option to use with cvs commands.
	 * Can be "", "-q" or "-Q"
	 */
	public void setQuietness(QuietOption option) {
			this.quietness = option;
	}

	/**
	 * Get the quietness option for commands
	 */
	public QuietOption getQuietness() {
		return quietness;
	}
	
	/**
	 * @see Plugin#startup()
	 */
	public void startup() throws CoreException {
		super.startup();
		Policy.localize("org.eclipse.team.internal.ccvs.core.messages");
		CVSProvider.initialize();
		try {
			((CVSProvider)getProvider()).startup();
		} catch (TeamException e) {
			throw new CoreException(e.getStatus());
		}
		ProjectDescriptionManager.initializeChangeListener();
		Synchronizer.startup();
	}
	
	/**
	 * @see Plugin#shutdown()
	 */
	public void shutdown() throws CoreException {
		super.shutdown();
		try {
			((CVSProvider)getProvider()).shutdown();
		} catch (TeamException e) {
			throw new CoreException(e.getStatus());
		}
	}
	
	/*
	 * Add a resource change listener to the workspace in order to respond to 
	 * resource deletions and moves and to ensure or project desription file is up to date.
	 */
	private void initializeChangeListener() {
		
		// Build a change listener for changes to thr project meta-information
		IResourceChangeListener projectChangeListener = new IResourceChangeListener() {
			public void resourceChanged(IResourceChangeEvent event) {
				try {
					IResourceDelta root = event.getDelta();
					IResourceDelta[] projectDeltas = root.getAffectedChildren(IResourceDelta.CHANGED);
					for (int i = 0; i < projectDeltas.length; i++) {
						IResourceDelta delta = projectDeltas[i];
						IResource resource = delta.getResource();
						if (resource.getType() == IResource.PROJECT) {
							IProject project = (IProject)resource;
							// Get the team provider for the project and
							ITeamProvider provider = TeamPlugin.getManager().getProvider(project);
							if (!(provider instanceof CVSTeamProvider)) continue;
							/* Check if the project description changed. */
							if ((delta.getFlags() & IResourceDelta.DESCRIPTION) != 0) {
								/* The project description changed. Write the file. */
								ProjectDescriptionManager.writeProjectDescriptionIfNecessary((CVSTeamProvider)provider, project, Policy.monitorFor(null));
							}
	
							/* Check if the .vcm_meta file for the project is in the delta. */
							IResourceDelta[] children = delta.getAffectedChildren(IResourceDelta.REMOVED);
							for (int j = 0; j < children.length; j++) {
								IResourceDelta childDelta = children[j];
								IResource childResource = childDelta.getResource();
								if (ProjectDescriptionManager.isProjectDescription(childResource)) {
									ProjectDescriptionManager.writeProjectDescriptionIfNecessary((CVSTeamProvider)provider, project, Policy.monitorFor(null));
								}
							}
						}
					}
				} catch (CVSException ex) {
					Util.logError(Policy.bind("CVSProviderPlugin.cannotUpdateDescription"), ex);
				}
			}
		};
		ResourcesPlugin.getWorkspace().addResourceChangeListener(projectChangeListener, IResourceChangeEvent.POST_AUTO_BUILD);
	}
}

