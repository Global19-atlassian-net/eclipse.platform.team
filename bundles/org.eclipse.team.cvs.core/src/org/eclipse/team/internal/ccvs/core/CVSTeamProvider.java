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
package org.eclipse.team.internal.ccvs.core;
 
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFileModificationValidator;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.team.IMoveDeleteHook;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Commit;
import org.eclipse.team.internal.ccvs.core.client.Diff;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.Tag;
import org.eclipse.team.internal.ccvs.core.client.Update;
import org.eclipse.team.internal.ccvs.core.client.Command.KSubstOption;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.client.listeners.AdminKSubstListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.DiffListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.EditorsListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.ICommandOutputListener;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.connection.CVSServerException;
import org.eclipse.team.internal.ccvs.core.resources.CVSRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.resources.EclipseSynchronizer;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.MutableResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.MoveDeleteHook;
import org.eclipse.team.internal.ccvs.core.util.PrepareForReplaceVisitor;
import org.eclipse.team.internal.ccvs.core.util.ReplaceWithBaseVisitor;
import org.eclipse.team.internal.ccvs.core.util.SyncFileWriter;
import org.eclipse.team.internal.core.streams.CRLFtoLFInputStream;
import org.eclipse.team.internal.core.streams.LFtoCRLFInputStream;

/**
 * This class acts as both the ITeamNature and the ITeamProvider instances
 * required by the Team core.
 * 
 * The current stat of this class and it's plugin is EXPERIMENTAL.
 * As such, it is subject to change except in it's conformance to the
 * TEAM API which it implements.
 * 
 * Questions:
 * 
 * How should a project/reource rename/move effect the provider?
 * 
 * Currently we always update with -P. Is this OK?
 *  - A way to allow customizable options would be nice
 * 
 * Is the -l option valid for commit and does it work properly for update and commit?
 * 
 * Do we need an IUserInteractionProvider in the CVS core
 * 	- prompt for user info (caching could be separate)
 * 	- get release comments
 * 	- prompt for overwrite of unmanaged files
 * 
 * Need a mechanism for communicating meta-information (provided by Team?)
 * 
 * Should pass null when there are no options for a cvs command
 * 
 * We currently write the files to disk and do a refreshLocal to
 * have them appear in Eclipse. This may be changed in the future.
 */
public class CVSTeamProvider extends RepositoryProvider {

	private static final boolean IS_CRLF_PLATFORM = Arrays.equals(
		System.getProperty("line.separator").getBytes(), new byte[] { '\r', '\n' }); //$NON-NLS-1$
	
	public static final IStatus OK = new Status(IStatus.OK, CVSProviderPlugin.ID, 0, Policy.bind("ok"), null); //$NON-NLS-1$

	private static final int UNIFIED_FORMAT = 0;
	private static final int CONTEXT_FORMAT = 1;
	private static final int STANDARD_FORMAT = 2;
	
	private CVSWorkspaceRoot workspaceRoot;
	private IProject project;
	private String comment = "";  //$NON-NLS-1$
	
	private static MoveDeleteHook moveDeleteHook= new MoveDeleteHook();
	private static IFileModificationValidator fileModificationValidator;
	
	// property used to indicate whether new directories should be discovered for the project
	private final static QualifiedName FETCH_ABSENT_DIRECTORIES_PROP_KEY = 
		new QualifiedName("org.eclipse.team.cvs.core", "fetch_absent_directories");  //$NON-NLS-1$  //$NON-NLS-2$
	// property used to indicate whether the project is configured to use Watch/edit
	private final static QualifiedName WATCH_EDIT_PROP_KEY = 
		new QualifiedName("org.eclipse.team.cvs.core", "watch_edit");  //$NON-NLS-1$  //$NON-NLS-2$

	private static IFileModificationValidator getPluggedInValidator() {
		IExtension[] extensions = Platform.getPluginRegistry().getExtensionPoint(CVSProviderPlugin.ID, CVSProviderPlugin.PT_FILE_MODIFICATION_VALIDATOR).getExtensions();
		if (extensions.length == 0)
			return null;
		IExtension extension = extensions[0];
		IConfigurationElement[] configs = extension.getConfigurationElements();
		if (configs.length == 0) {
			CVSProviderPlugin.log(IStatus.ERROR, Policy.bind("CVSAdapter.noConfigurationElement", new Object[] {extension.getUniqueIdentifier()}), null);//$NON-NLS-1$
			return null;
		}
		try {
			IConfigurationElement config = configs[0];
			return (IFileModificationValidator) config.createExecutableExtension("run");//$NON-NLS-1$
		} catch (CoreException ex) {
			CVSProviderPlugin.log(IStatus.ERROR, Policy.bind("CVSAdapter.unableToInstantiate", new Object[] {extension.getUniqueIdentifier()}), ex);//$NON-NLS-1$
			return null;
		}
	}
				
	/**
	 * No-arg Constructor for IProjectNature conformance
	 */
	public CVSTeamProvider() {
	}

	/**
	 * @see IProjectNature#deconfigure()
	 */
	public void deconfigure() throws CoreException {
		// when a nature is removed from the project, notify the synchronizer that
		// we no longer need the sync info cached. This does not affect the actual CVS
		// meta directories on disk, and will remain unless a client calls unmanage().
		try {
			EclipseSynchronizer.getInstance().deconfigure(getProject(), null);
		} catch(CVSException e) {
			throw new CoreException(e.getStatus());
		}
	}
	
	public void deconfigured() {
		CVSProviderPlugin.broadcastProjectDeconfigured(getProject());
	}
	/**
	 * @see IProjectNature#getProject()
	 */
	public IProject getProject() {
		return project;
	}
	


	/**
	 * @see IProjectNature#setProject(IProject)
	 */
	public void setProject(IProject project) {
		this.project = project;
		try {
			this.workspaceRoot = new CVSWorkspaceRoot(project);
			// Ensure that the project has CVS info
			if (workspaceRoot.getLocalRoot().getFolderSyncInfo() == null) {
				throw new CVSException(new CVSStatus(CVSStatus.ERROR, Policy.bind("CVSTeamProvider.noFolderInfo", project.getName()))); //$NON-NLS-1$
			}
		} catch (CVSException e) {
			// Log any problems creating the CVS managed resource
			CVSProviderPlugin.log(e);
		}
	}



	/**
	 * Add the given resources to the project. 
	 * <p>
	 * The sematics follow that of CVS in the sense that any folders 
	 * being added are created remotely as a result of this operation 
	 * while files are created remotely on the next commit. 
	 * </p>
	 * <p>
	 * This method uses the team file type registry to determine the type
	 * of added files. If the extension of the file is not in the registry,
	 * the file is assumed to be binary.
	 * </p>
	 * <p>
	 * NOTE: for now we do three operations: one each for folders, text files and binary files.
	 * We should optimize this when time permits to either use one operations or defer server
	 * contact until the next commit.
	 * </p>
	 * 
	 * <p>
	 * There are special semantics for adding the project itself to the repo. In this case, the project 
	 * must be included in the resources array.
	 * </p>
	 */
	public void add(IResource[] resources, int depth, IProgressMonitor progress) throws TeamException {	
		
		// Visit the children of the resources using the depth in order to
		// determine which folders, text files and binary files need to be added
		// A TreeSet is needed for the folders so they are in the right order (i.e. parents created before children)
		final SortedSet folders = new TreeSet();
		// Sets are required for the files to ensure that files will not appear twice if there parent was added as well
		// and the depth isn't zero
		final Map /* from KSubstOption to Set */ files = new HashMap();
		final TeamException[] eHolder = new TeamException[1];
		for (int i=0; i<resources.length; i++) {
			
			final IResource currentResource = resources[i];
			
			// Throw an exception if the resource is not a child of the receiver
			checkIsChild(currentResource);
			
			try {		
				// Auto-add parents if they are not already managed
				IContainer parent = currentResource.getParent();
				ICVSResource cvsParentResource = CVSWorkspaceRoot.getCVSResourceFor(parent);
				while (parent.getType() != IResource.ROOT && parent.getType() != IResource.PROJECT && ! isManaged(cvsParentResource)) {
					folders.add(cvsParentResource);
					parent = parent.getParent();
					cvsParentResource = cvsParentResource.getParent();
				}
					
				// Auto-add children
				final TeamException[] exception = new TeamException[] { null };
				currentResource.accept(new IResourceVisitor() {
					public boolean visit(IResource resource) {
						try {
							ICVSResource mResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
							// Add the resource is its not already managed and it was either
							// added explicitly (is equal currentResource) or is not ignored
							if (! isManaged(mResource) && (currentResource.equals(resource) || ! mResource.isIgnored())) {
								if (resource.getType() == IResource.FILE) {
									KSubstOption ksubst = KSubstOption.fromFile((IFile) resource);
									Set set = (Set) files.get(ksubst);
									if (set == null) {
										set = new HashSet();
										files.put(ksubst, set);
									}
									set.add(mResource);
								} else {
									folders.add(mResource);
								}
							}
							// Always return true and let the depth determine if children are visited
							return true;
						} catch (CVSException e) {
							exception[0] = e;
							return false;
						}
					}
				}, depth, false);
				if (exception[0] != null) {
					throw exception[0];
				}
			} catch (CoreException e) {
				throw new CVSException(new Status(IStatus.ERROR, CVSProviderPlugin.ID, TeamException.UNABLE, Policy.bind("CVSTeamProvider.visitError", new Object[] {resources[i].getFullPath()}), e)); //$NON-NLS-1$
			}
		}
		// If an exception occured during the visit, throw it here
		if (eHolder[0] != null)
			throw eHolder[0];
		
		// Add the folders, followed by files!
		progress.beginTask(null, files.size() * 10 + (folders.isEmpty() ? 0 : 10));
		try {
			if (!folders.isEmpty()) {
				Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true, new ICVSRunnable() {
					public void run(IProgressMonitor monitor) throws CVSException {
						IStatus status = Command.ADD.execute(
							Command.NO_GLOBAL_OPTIONS,
							Command.NO_LOCAL_OPTIONS,
							(ICVSResource[])folders.toArray(new ICVSResource[folders.size()]),
							null,
							monitor);
						if (status.getCode() == CVSStatus.SERVER_ERROR) {
							throw new CVSServerException(status);
						}
					}
				}, Policy.subMonitorFor(progress, 10));
			}
			for (Iterator it = files.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();
				final KSubstOption ksubst = (KSubstOption) entry.getKey();
				final Set set = (Set) entry.getValue();
				Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true, new ICVSRunnable() {
					public void run(IProgressMonitor monitor) throws CVSException {
						IStatus status = Command.ADD.execute(
							Command.NO_GLOBAL_OPTIONS,
							new LocalOption[] { ksubst },
							(ICVSResource[])set.toArray(new ICVSResource[set.size()]),
							null,
							monitor);
						if (status.getCode() == CVSStatus.SERVER_ERROR) {
							throw new CVSServerException(status);
						}
					}
				}, Policy.subMonitorFor(progress, 10));
			}
		} finally {
			progress.done();
		}
	}

	/*
	 * Consider a folder managed only if it's also a CVS folder
	 */
	private boolean isManaged(ICVSResource cvsResource) throws CVSException {
		return cvsResource.isManaged() && (!cvsResource.isFolder() || ((ICVSFolder)cvsResource).isCVSFolder());
	}
	
	/**
	 * Checkin any local changes using "cvs commit ...".
	 * 
	 * @see ITeamProvider#checkin(IResource[], int, IProgressMonitor)
	 */
	public void checkin(IResource[] resources, int depth, IProgressMonitor progress) throws TeamException {
			
		// Build the local options
		List localOptions = new ArrayList();
		localOptions.add(Commit.makeArgumentOption(Command.MESSAGE_OPTION, comment));

		// If the depth is not infinite, we want the -l option
		if (depth != IResource.DEPTH_INFINITE) {
			localOptions.add(Commit.DO_NOT_RECURSE);
		}
		LocalOption[] commandOptions = (LocalOption[])localOptions.toArray(new LocalOption[localOptions.size()]);

		// Build the arguments list
		String[] arguments = getValidArguments(resources, commandOptions);
					
		// Commit the resources
		IStatus status;
		Session s = new Session(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot());
		progress.beginTask(null, 100);
		try {
			// Opening the session takes 20% of the time
			s.open(Policy.subMonitorFor(progress, 20));
			status = Command.COMMIT.execute(s,
			Command.NO_GLOBAL_OPTIONS,
			commandOptions,
			arguments, null,
			Policy.subMonitorFor(progress, 80));
		} finally {
			s.close();
			progress.done();
		}
		if (status.getCode() == CVSStatus.SERVER_ERROR) {
			throw new CVSServerException(status);
		}
	}
		
	/**
	 * @see ITeamProvider#delete(IResource[], int, IProgressMonitor)
	 */
	public void delete(IResource[] resources, final IProgressMonitor progress) throws TeamException {
		try {
			progress.beginTask(null, 100);
			
			// Delete any files locally and record the names.
			// Use a resource visitor to ensure the proper depth is obtained
			final IProgressMonitor subProgress = Policy.infiniteSubMonitorFor(progress, 30);
			subProgress.beginTask(null, 256);
			final List files = new ArrayList(resources.length);
			final TeamException[] eHolder = new TeamException[1];
			for (int i=0;i<resources.length;i++) {
				IResource resource = resources[i];
				checkIsChild(resource);
				try {
					if (resource.exists()) {
						resource.accept(new IResourceVisitor() {
							public boolean visit(IResource resource) {
								try {
									ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
									if (cvsResource.isManaged()) {
										String name = resource.getProjectRelativePath().toString();
										if (resource.getType() == IResource.FILE) {
											files.add(name);
											((IFile)resource).delete(false, true, subProgress);
										}
									}
								} catch (TeamException e) {
									eHolder[0] = e;
								} catch (CoreException e) {
									eHolder[0] = wrapException(e);
									// If there was a problem, don't visit the children
									return false;
								}
								// Always return true and let the depth determine if children are visited
								return true;
							}
						}, IResource.DEPTH_INFINITE, false);
					} else if (resource.getType() == IResource.FILE) {
						// If the resource doesn't exist but is a file, queue it for removal
						files.add(resource.getProjectRelativePath().toString());
					}
				} catch (CoreException e) {
					throw wrapException(e);
				}
			}
			subProgress.done();
			// If an exception occured during the visit, throw it here
			if (eHolder[0] != null) throw eHolder[0];		
			// If there are no files to delete, we are done
			if (files.isEmpty()) return;
			
			// Remove the files remotely
			IStatus status;
			Session s = new Session(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot());
			s.open(progress);
			try {
				status = Command.REMOVE.execute(s,
				Command.NO_GLOBAL_OPTIONS,
				Command.NO_LOCAL_OPTIONS,
				(String[])files.toArray(new String[files.size()]),
				null,
				Policy.subMonitorFor(progress, 70));
			} finally {
				s.close();
			}
			if (status.getCode() == CVSStatus.SERVER_ERROR) {
				throw new CVSServerException(status);
			}	
		} finally {
			progress.done();
		}
	}
	
	/** 
	 * Diff the resources with the repository and write the output to the provided 
	 * PrintStream in a form that is usable as a patch. The patch is rooted at the
	 * project.
	 */
	public void diff(IResource resource, LocalOption[] options, PrintStream stream,
		IProgressMonitor progress) throws TeamException {
		
		boolean includeNewFiles = false;
		boolean doNotRecurse = false;
		int format = STANDARD_FORMAT;
		
		// Determine the command root and arguments arguments list
		ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
		ICVSFolder commandRoot;
		String[] arguments;
		if (cvsResource.isFolder()) {
			commandRoot = (ICVSFolder)cvsResource;
			arguments = new String[] {Session.CURRENT_LOCAL_FOLDER};
		} else {
			commandRoot = cvsResource.getParent();
			arguments = new String[] {cvsResource.getName()};
		}

		Session s = new Session(workspaceRoot.getRemoteLocation(), commandRoot);
		progress.beginTask(null, 100);
		try {
			s.open(Policy.subMonitorFor(progress, 20));
			Command.DIFF.execute(s,
				Command.NO_GLOBAL_OPTIONS,
				options,
				arguments,
				new DiffListener(stream),
				Policy.subMonitorFor(progress, 80));
		} finally {
			s.close();
			progress.done();
		}
		
		// Append our diff output to the server diff output.
		// Our diff output includes new files and new files in new directories.
			
		for (int i = 0; i < options.length; i++)  {
			LocalOption option = options[i];
			if (option.equals(Diff.INCLUDE_NEWFILES))  {
				includeNewFiles = true;
			} else if (option.equals(Diff.DO_NOT_RECURSE))  {
				doNotRecurse = true;
			} else if (option.equals(Diff.UNIFIED_FORMAT))  {
				format = UNIFIED_FORMAT;
			} else if (option.equals(Diff.CONTEXT_FORMAT))  {
				format = CONTEXT_FORMAT;
			}
		}
		
		if (includeNewFiles)  {
			newFileDiff(commandRoot, stream, doNotRecurse, format);
		}
	}
	
	/**
	 * This diff adds new files and directories to the stream.
	 * @param resource
	 * @param stream
	 * @param doNotRecurse
	 * @param format
	 * @throws CVSException
	 */
	
	private void newFileDiff(final ICVSResource resource, final PrintStream stream, final boolean doNotRecurse, final int format) throws CVSException {
	
		resource.accept(new ICVSResourceVisitor() {
			public void visitFile(ICVSFile file) throws CVSException {
				if (!(file.isIgnored() || file.isManaged()))  {
					addFileToDiff(file, stream, format);
				}
			}
			public void visitFolder(ICVSFolder folder) throws CVSException {
				// Even if we are not supposed to recurse we still need to go into
				// the root directory.
				if (folder.isIgnored() || (doNotRecurse && !folder.equals(resource)))  {
					return;
				} else  {
					folder.acceptChildren(this);
				}
			}
		});
	}

	private void addFileToDiff(ICVSFile file, PrintStream stream, int format) throws CVSException {
		
		String nullFilePrefix = "";
		String newFilePrefix = "";
		String positionInfo = "";
		String linePrefix = "";
		
		String pathString = file.getIResource().getProjectRelativePath().toString();

		BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getContents()));
		int lines = 0;
		try {
			while (fileReader.readLine() != null)  {
				lines++;
			}
			fileReader.close();
			
			switch (format) {
				case UNIFIED_FORMAT :
					nullFilePrefix = "--- ";	//$NON-NLS-1$
					newFilePrefix = "+++ "; 	//$NON-NLS-1$
					positionInfo = "@@ -0,0 +1," + lines + " @@" ;	//$NON-NLS-1$
					linePrefix = "+"; //$NON-NLS-1$
					break;

				case CONTEXT_FORMAT :
					nullFilePrefix = "*** ";	//$NON-NLS-1$
					newFilePrefix = "--- ";		//$NON-NLS-1$
					positionInfo = "--- 1," + lines + " ----";	//$NON-NLS-1$
					linePrefix = "+ ";	//$NON-NLS-1$
					break;
				
				default :
					positionInfo = "0a1," + lines;	//$NON-NLS-1$
					linePrefix = "> ";	//$NON-NLS-1$
					break;
			}
			
			fileReader = new BufferedReader(new InputStreamReader(file.getContents()));
				
			stream.println("Index: " + pathString);		//$NON-NLS-1$
			stream.println("===================================================================");	//$NON-NLS-1$
			stream.println("RCS file: " + pathString);	//$NON-NLS-1$
			stream.println("diff -N " + pathString);	//$NON-NLS-1$
			
			if (lines > 0)  {
				
				if (format != STANDARD_FORMAT)  {
					stream.println(nullFilePrefix + "/dev/null	1 Jan 1970 00:00:00 -0000");	//$NON-NLS-1$
					// Technically this date should be the local file date but nobody really cares.
					stream.println(newFilePrefix + pathString + "	1 Jan 1970 00:00:00 -0000");	//$NON-NLS-1$
				}
				
				if (format == CONTEXT_FORMAT)  {
					stream.println("***************");	//$NON-NLS-1$
					stream.println("*** 0 ****");		//$NON-NLS-1$
				}
				
				stream.println(positionInfo);
				
				for (int i = 0; i < lines; i++)  {
					stream.print(linePrefix);
					stream.println(fileReader.readLine());
				}
			}
		} catch (IOException e) {
			throw new CVSException(Policy.bind("java.io.IOException", pathString));
		} finally  {
			try {
				fileReader.close();
			} catch (IOException e1) {
			}
		}
	}

	/**
	 * Replace the local version of the provided resources with the remote using "cvs update -C ..."
	 * 
	 * @see ITeamProvider#get(IResource[], int, IProgressMonitor)
	 */
	public void get(IResource[] resources, final int depth, IProgressMonitor progress) throws TeamException {
		get(resources, depth, null, progress);
	}
	
	public void get(final IResource[] resources, final int depth, final CVSTag tag, IProgressMonitor progress) throws TeamException {
			
		// Handle the retrival of the base in a special way
		if (tag != null && tag.equals(CVSTag.BASE)) {
			new ReplaceWithBaseVisitor().replaceWithBase(getProject(), resources, depth, progress);
			return;
		}

		// Make a connection before preparing for the replace to avoid deletion of resources before a failed connection
		Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */,
			new ICVSRunnable() {
				public void run(IProgressMonitor progress) throws CVSException {
					// Prepare for the replace (special handling for "cvs added" and "cvs removed" resources
					progress.beginTask(null, 100);
					try {
						new PrepareForReplaceVisitor().visitResources(getProject(), resources, "CVSTeamProvider.scrubbingResource", depth, Policy.subMonitorFor(progress, 30)); //$NON-NLS-1$
									
						// Perform an update, ignoring any local file modifications
						List options = new ArrayList();
						options.add(Update.IGNORE_LOCAL_CHANGES);
						if(depth != IResource.DEPTH_INFINITE) {
							options.add(Command.DO_NOT_RECURSE);
						}
						LocalOption[] commandOptions = (LocalOption[]) options.toArray(new LocalOption[options.size()]);
						try {
							update(resources, commandOptions, tag, true /*createBackups*/, Policy.subMonitorFor(progress, 70));
						} catch (TeamException e) {
							throw CVSException.wrapException(e);
						}
					} finally {
						progress.done();
					}
				}
			}, progress);
	}
	
	/**
	 * Return the remote location to which the receiver's project is mapped.
	 */
	public ICVSRepositoryLocation getRemoteLocation() throws CVSException {
		try {
			return workspaceRoot.getRemoteLocation();
		} catch (CVSException e) {
			// If we can't get the remote location, we should disconnect since nothing can be done with the provider
			try {
				RepositoryProvider.unmap(project);
			} catch (TeamException ex) {
				CVSProviderPlugin.log(ex);
			}
			// We need to trigger a decorator refresh					
			throw e;
		}
	}
	
	/*
	 * Use specialiazed tagging to move all local changes (including additions and
	 * deletions) to the specified branch.
	 */
	public void makeBranch(IResource[] resources, final CVSTag versionTag, final CVSTag branchTag, boolean moveToBranch, IProgressMonitor monitor) throws TeamException {
		
		// Determine the total amount of work
		int totalWork = (versionTag!= null ? 60 : 40) + (moveToBranch ? 20 : 0);
		monitor.beginTask(Policy.bind("CVSTeamProvider.makeBranch"), totalWork);  //$NON-NLS-1$
		try {
			// Build the arguments list
			final ICVSResource[] arguments = getCVSArguments(resources);
			
			// Tag the remote resources
			Session s = new Session(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot());
			try {
				final IStatus status[] = new IStatus[] { null };
				if (versionTag != null) {
					// Version using a custom tag command that skips added but not commited reesources
					Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */,
						new ICVSRunnable(						) {
							public void run(IProgressMonitor monitor) throws CVSException {
								status[0] = Command.CUSTOM_TAG.execute(
									Command.NO_GLOBAL_OPTIONS,
									Command.NO_LOCAL_OPTIONS,
									versionTag,
									arguments,
									null,
									monitor);
							}
						}, Policy.subMonitorFor(monitor, 40));
					if (status[0].isOK()) {
						// Branch using the tag
						Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */,
							new ICVSRunnable() {
								public void run(IProgressMonitor monitor) throws CVSException {
									status[0] = Command.CUSTOM_TAG.execute(
										Command.NO_GLOBAL_OPTIONS,
										Command.NO_LOCAL_OPTIONS,
										branchTag,
										arguments,
										null,
										monitor);
								}
							}, Policy.subMonitorFor(monitor, 20));
					}
				} else {
					// Just branch using tag
					Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */,
						new ICVSRunnable() {
							public void run(IProgressMonitor monitor) throws CVSException {
								status[0] = Command.CUSTOM_TAG.execute(
									Command.NO_GLOBAL_OPTIONS,
									Command.NO_LOCAL_OPTIONS,
									branchTag,
									arguments,
									null,
									monitor);
							}
						}, Policy.subMonitorFor(monitor, 40));
	
				}
				if ( ! status[0].isOK()) {
					throw new CVSServerException(status[0]);
				}
			} finally {
				s.close();
			}
			
			// Set the tag of the local resources to the branch tag (The update command will not
			// properly update "cvs added" and "cvs removed" resources so a custom visitor is used
			if (moveToBranch) {
				setTag(resources, branchTag, Policy.subMonitorFor(monitor, 20));
			}
		} finally {
			monitor.done();
		}
	}
	
	/**
	 * Update the sync info of the local resource associated with the sync element such that
	 * the revision of the local resource matches that of the remote resource.
	 * This will allow commits on the local resource to succeed.
	 * 
	 * Only file resources can be merged.
	 */
	public void merged(IRemoteSyncElement[] elements) throws TeamException {	
		for (int i=0;i<elements.length;i++) {
			((CVSRemoteSyncElement)elements[i]).makeOutgoing(Policy.monitorFor(null));
		}
	}
	
	/**
	 * @see ITeamProvider#move(IResource, IPath, IProgressMonitor)
	 */
	public void moved(IPath source, IResource resource, IProgressMonitor progress) throws TeamException {
	}

	/**
	 * Set the comment to be used on the next checkin
	 */
	public void setComment(String comment) {
		this.comment = comment;
	}
		
	/**
	 * Set the connection method for the given resource's
	 * project. If the conection method name is invalid (i.e.
	 * no corresponding registered connection method), false is returned.
	 */
	public boolean setConnectionInfo(IResource resource, String methodName, IUserInfo userInfo, IProgressMonitor monitor) throws TeamException {
		checkIsChild(resource);
		try {
			monitor.beginTask(Policy.bind("CVSTeamProvider.connectionInfo", project.getName()), 100); //$NON-NLS-1$
			
			if (!CVSRepositoryLocation.validateConnectionMethod(methodName))
				return false;
				
			// Get the original location
			ICVSRepositoryLocation location = workspaceRoot.getRemoteLocation();
			
			// Make a copy to work on
			CVSRepositoryLocation newLocation = CVSRepositoryLocation.fromString(location.getLocation());
			newLocation.setMethod(methodName);
			newLocation.setUserInfo(userInfo);
	
			// Validate that a connection can be made with the new location
			boolean isKnown = CVSProviderPlugin.getPlugin().isKnownRepository(newLocation.getLocation());
			try {
				newLocation.validateConnection(Policy.subMonitorFor(monitor, 20));
			} catch (CVSException e) {
				if (!isKnown)
					CVSProviderPlugin.getPlugin().disposeRepository(newLocation);
				throw e;
			}
			
			// Add the location to the provider
			CVSProviderPlugin.getPlugin().addRepository(newLocation);
			
			// Set the project to use the new Locations
			setRemoteRoot(newLocation, Policy.subMonitorFor(monitor, 80));
			return true;
		} finally {
			monitor.done();
		}
	}
	
	/*
	 * This method sets the tag for a project.
	 * It expects to be passed an InfiniteSubProgressMonitor
	 */
	private void setTag(final IResource[] resources, final CVSTag tag, IProgressMonitor monitor) throws TeamException {
	
		workspaceRoot.getLocalRoot().run(new ICVSRunnable() {
			public void run(IProgressMonitor progress) throws CVSException {
				try {
					// 512 ticks gives us a maximum of 2048 which seems reasonable for folders and files in a project
					progress.beginTask(null, 100);
					final IProgressMonitor monitor = Policy.infiniteSubMonitorFor(progress, 100);
					monitor.beginTask(Policy.bind("CVSTeamProvider.folderInfo", project.getName()), 512);  //$NON-NLS-1$
					
					// Visit all the children folders in order to set the root in the folder sync info
					for (int i = 0; i < resources.length; i++) {
						CVSWorkspaceRoot.getCVSResourceFor(resources[i]).accept(new ICVSResourceVisitor() {
							public void visitFile(ICVSFile file) throws CVSException {
								monitor.worked(1);
								//ResourceSyncInfo info = file.getSyncInfo();
								byte[] syncBytes = file.getSyncBytes();
								if (syncBytes != null) {
									monitor.subTask(Policy.bind("CVSTeamProvider.updatingFile", file.getName())); //$NON-NLS-1$
									file.setSyncBytes(ResourceSyncInfo.setTag(syncBytes, tag), ICVSFile.UNKNOWN);
								}
							};
							public void visitFolder(ICVSFolder folder) throws CVSException {
								monitor.worked(1);
								FolderSyncInfo info = folder.getFolderSyncInfo();
								if (info != null) {
									monitor.subTask(Policy.bind("CVSTeamProvider.updatingFolder", info.getRepository())); //$NON-NLS-1$
									folder.setFolderSyncInfo(new FolderSyncInfo(info.getRepository(), info.getRoot(), tag, info.getIsStatic()));
									folder.acceptChildren(this);
								}
							};
						});
					}
				} finally {
					progress.done();
				}
			}
		}, monitor);
	}
	
	/** 
	 * Tag the resources in the CVS repository with the given tag.
	 * 
	 * The returned IStatus will be a status containing any errors or warnings.
	 * If the returned IStatus is a multi-status, the code indicates the severity.
	 * Possible codes are:
	 *    CVSStatus.OK - Nothing to report
	 *    CVSStatus.SERVER_ERROR - The server reported an error
	 *    any other code - warning messages received from the server
	 */
	public IStatus tag(IResource[] resources, int depth, CVSTag tag, IProgressMonitor progress) throws CVSException {
						
		// Build the local options
		List localOptions = new ArrayList();
		// If the depth is not infinite, we want the -l option
		if (depth != IResource.DEPTH_INFINITE)
			localOptions.add(Tag.DO_NOT_RECURSE);
		LocalOption[] commandOptions = (LocalOption[])localOptions.toArray(new LocalOption[localOptions.size()]);
				
		// Build the arguments list
		String[] arguments = getValidArguments(resources, commandOptions);

		// Execute the command
		Session s = new Session(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot());
		progress.beginTask(null, 100);
		try {
			// Opening the session takes 20% of the time
			s.open(Policy.subMonitorFor(progress, 20));
			return Command.TAG.execute(s,
				Command.NO_GLOBAL_OPTIONS,
				commandOptions,
				tag,
				arguments,
				null,
				Policy.subMonitorFor(progress, 80));
		} finally {
			s.close();
			progress.done();
		}
	}
	
	/**
	 * Generally useful update.
	 * 
	 * The tag parameter determines any stickyness after the update is run. If tag is null, any tagging on the
	 * resources being updated remain the same. If the tag is a branch, version or date tag, then the resources
	 * will be appropriatly tagged. If the tag is HEAD, then there will be no tag on the resources (same as -A
	 * clear sticky option).
	 * 
	 * @param createBackups if true, creates .# files for updated files
	 */
	public void update(IResource[] resources, LocalOption[] options, CVSTag tag, final boolean createBackups, IProgressMonitor progress) throws TeamException {
		// Build the local options
		List localOptions = new ArrayList();
		
		// Use the appropriate tag options
		if (tag != null) {
			localOptions.add(Update.makeTagOption(tag));
		}
		
		// Build the arguments list
		localOptions.addAll(Arrays.asList(options));
		final LocalOption[] commandOptions = (LocalOption[])localOptions.toArray(new LocalOption[localOptions.size()]);
		final ICVSResource[] arguments = getCVSArguments(resources);

		Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */,
			new ICVSRunnable() {
				public void run(IProgressMonitor monitor) throws CVSException {
					IStatus status = Command.UPDATE.execute(Command.NO_GLOBAL_OPTIONS, commandOptions, arguments,
						null, monitor);
					if (status.getCode() == CVSStatus.SERVER_ERROR) {
						throw new CVSServerException(status);
					}
				}
			}, progress);
	}
	
	/*
	 * @see ITeamProvider#refreshState(IResource[], int, IProgressMonitor)
	 */
	public void refreshState(IResource[] resources, int depth, IProgressMonitor progress) throws TeamException {
		Assert.isTrue(false);
	}
	
	/*
	 * @see ITeamProvider#isDirty(IResource)
	 */
	public boolean isDirty(IResource resource) {
		Assert.isTrue(false);
		return false;
	}
	
	public CVSWorkspaceRoot getCVSWorkspaceRoot() {
		return workspaceRoot;
	}
	
	/*
	 * Generate an exception if the resource is not a child of the project
	 */
	 private void checkIsChild(IResource resource) throws CVSException {
	 	if (!isChildResource(resource))
	 		throw new CVSException(new Status(IStatus.ERROR, CVSProviderPlugin.ID, TeamException.UNABLE, 
	 			Policy.bind("CVSTeamProvider.invalidResource", //$NON-NLS-1$
	 				new Object[] {resource.getFullPath().toString(), project.getName()}), 
	 			null));
	 }
	 
	/*
	 * Get the arguments to be passed to a commit or update
	 */
	private String[] getValidArguments(IResource[] resources, LocalOption[] options) throws CVSException {
		List arguments = new ArrayList(resources.length);
		for (int i=0;i<resources.length;i++) {
			checkIsChild(resources[i]);
			IPath cvsPath = resources[i].getFullPath().removeFirstSegments(1);
			if (cvsPath.segmentCount() == 0) {
				arguments.add(Session.CURRENT_LOCAL_FOLDER);
			} else {
				arguments.add(cvsPath.toString());
			}
		}
		return (String[])arguments.toArray(new String[arguments.size()]);
	}
	
	private ICVSResource[] getCVSArguments(IResource[] resources) {
		ICVSResource[] cvsResources = new ICVSResource[resources.length];
		for (int i = 0; i < cvsResources.length; i++) {
			cvsResources[i] = CVSWorkspaceRoot.getCVSResourceFor(resources[i]);
		}
		return cvsResources;
	}
	
	/*
	 * This method expects to be passed an InfiniteSubProgressMonitor
	 */
	public void setRemoteRoot(ICVSRepositoryLocation location, IProgressMonitor monitor) throws TeamException {

		// Check if there is a differnece between the new and old roots	
		final String root = location.getLocation();
		if (root.equals(workspaceRoot.getRemoteLocation())) 
			return;
	
		try {
			workspaceRoot.getLocalRoot().run(new ICVSRunnable() {
				public void run(IProgressMonitor progress) throws CVSException {
					try {
						// 256 ticks gives us a maximum of 1024 which seems reasonable for folders is a project
						progress.beginTask(null, 100);
						final IProgressMonitor monitor = Policy.infiniteSubMonitorFor(progress, 100);
						monitor.beginTask(Policy.bind("CVSTeamProvider.folderInfo", project.getName()), 256);  //$NON-NLS-1$
		
						// Visit all the children folders in order to set the root in the folder sync info
						workspaceRoot.getLocalRoot().accept(new ICVSResourceVisitor() {
							public void visitFile(ICVSFile file) throws CVSException {};
							public void visitFolder(ICVSFolder folder) throws CVSException {
								monitor.worked(1);
								FolderSyncInfo info = folder.getFolderSyncInfo();
								if (info != null) {
									monitor.subTask(Policy.bind("CVSTeamProvider.updatingFolder", info.getRepository())); //$NON-NLS-1$
									folder.setFolderSyncInfo(new FolderSyncInfo(info.getRepository(), root, info.getTag(), info.getIsStatic()));
									folder.acceptChildren(this);
								}
							};
						});
					} finally {
						progress.done();
					}
				}
			}, monitor);
		} finally {
			monitor.done();
		}
	}
	
	/*
	 * Helper to indicate if the resource is a child of the receiver's project
	 */
	private boolean isChildResource(IResource resource) {
		return resource.getProject().getName().equals(project.getName());
	}
	
	private static TeamException wrapException(CoreException e) {
		return CVSException.wrapException(e);
	}
	
	public void configureProject() throws CoreException {
		CVSProviderPlugin.broadcastProjectConfigured(getProject());
	}
	/**
	 * Sets the keyword substitution mode for the specified resources.
	 * <p>
	 * Applies the following rules in order:<br>
	 * <ul>
	 *   <li>If a file is not managed, skips it.</li>
	 *   <li>If a file is not changing modes, skips it.</li>
	 *   <li>If a file is being changed from binary to text, corrects line delimiters
	 *       then commits it, then admins it.</li>
	 *   <li>If a file is added, changes the resource sync information locally.</li>
	 *   <li>Otherwise commits the file (with FORCE to create a new revision), then admins it.</li>
	 * </ul>
	 * All files that are admin'd are committed with FORCE to prevent other developers from
	 * casually trying to commit pending changes to the repository without first checking out
	 * a new copy.  This is not a perfect solution, as they could just as easily do an UPDATE
	 * and not obtain the new keyword sync info.
	 * </p>
	 * 
	 * @param changeSet a map from IFile to KSubstOption
	 * @param monitor the progress monitor
	 * @return a status code indicating success or failure of the operation
	 * 
	 * @throws TeamException
	 */
	public IStatus setKeywordSubstitution(final Map /* from IFile to KSubstOption */ changeSet,
		final String comment,
		IProgressMonitor monitor) throws TeamException {
		final IStatus[] result = new IStatus[] { ICommandOutputListener.OK };
		workspaceRoot.getLocalRoot().run(new ICVSRunnable() {
			public void run(final IProgressMonitor monitor) throws CVSException {
				final Map /* from KSubstOption to List of String */ filesToAdmin = new HashMap();
				final List /* of ICVSResource */ filesToCommit = new ArrayList();
				final Collection /* of ICVSFile */ filesToCommitAsText = new HashSet(); // need fast lookup
		
				/*** determine the resources to be committed and/or admin'd ***/
				for (Iterator it = changeSet.entrySet().iterator(); it.hasNext();) {
					Map.Entry entry = (Map.Entry) it.next();
					IFile file = (IFile) entry.getKey();
					KSubstOption toKSubst = (KSubstOption) entry.getValue();

					// only set keyword substitution if resource is a managed file
					checkIsChild(file);
					ICVSFile mFile = CVSWorkspaceRoot.getCVSFileFor(file);
					if (! mFile.isManaged()) continue;
					
					// only set keyword substitution if new differs from actual
					byte[] syncBytes = mFile.getSyncBytes();
					KSubstOption fromKSubst = ResourceSyncInfo.getKeywordMode(syncBytes);
					if (toKSubst.equals(fromKSubst)) continue;
					
					// change resource sync info immediately for an outgoing addition
					if (ResourceSyncInfo.isAddition(syncBytes)) {
						mFile.setSyncBytes(ResourceSyncInfo.setKeywordMode(syncBytes, toKSubst), ICVSFile.UNKNOWN);
						continue;
					}

					// nothing do to for deletions
					if (ResourceSyncInfo.isDeletion(syncBytes)) continue;

					// file exists remotely so we'll have to commit it
					if (fromKSubst.isBinary() && ! toKSubst.isBinary()) {
						// converting from binary to text
						cleanLineDelimiters(file, IS_CRLF_PLATFORM, new NullProgressMonitor()); // XXX need better progress monitoring
						// remember to commit the cleaned resource as text before admin
						filesToCommitAsText.add(mFile);
					}
					// force a commit to bump the revision number
					makeDirty(file);
					filesToCommit.add(mFile);
					// remember to admin the resource
					List list = (List) filesToAdmin.get(toKSubst);
					if (list == null) {
						list = new ArrayList();
						filesToAdmin.put(toKSubst, list);
					}
					list.add(mFile);
				}
			
				/*** commit then admin the resources ***/
				// compute the total work to be performed
				int totalWork = filesToCommit.size();
				for (Iterator it = filesToAdmin.values().iterator(); it.hasNext();) {
					List list = (List) it.next();
					totalWork += list.size();
				}
				if (totalWork != 0) {
					monitor.beginTask(Policy.bind("CVSTeamProvider.settingKSubst"), totalWork); //$NON-NLS-1$
					try {
						// commit files that changed from binary to text
						// NOTE: The files are committed as text with conversions even if the
						//       resource sync info still says "binary".
						if (filesToCommit.size() != 0) {
							Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */,
								new ICVSRunnable(								) {
									public void run(IProgressMonitor monitor) throws CVSException {
										String keywordChangeComment = comment;
										if (keywordChangeComment == null || keywordChangeComment.length() == 0)
											keywordChangeComment = Policy.bind("CVSTeamProvider.changingKeywordComment"); //$NON-NLS-1$
										result[0] = Command.COMMIT.execute(
											Command.NO_GLOBAL_OPTIONS,
											new LocalOption[] { Commit.DO_NOT_RECURSE, Commit.FORCE,
												Commit.makeArgumentOption(Command.MESSAGE_OPTION, keywordChangeComment) },
											(ICVSResource[]) filesToCommit.toArray(new ICVSResource[filesToCommit.size()]),
											filesToCommitAsText,
											null, Policy.subMonitorFor(monitor, filesToCommit.size()));
									}
								}, Policy.subMonitorFor(monitor, filesToCommit.size()));
							// if errors were encountered, abort
							if (! result[0].isOK()) return;
						}
						
						// admin files that changed keyword substitution mode
						// NOTE: As confirmation of the completion of a command, the server replies
						//       with the RCS command output if a change took place.  Rather than
						//       assume that the command succeeded, we listen for these lines
						//       and update the local ResourceSyncInfo for the particular files that
						//       were actually changed remotely.
						for (Iterator it = filesToAdmin.entrySet().iterator(); it.hasNext();) {
							Map.Entry entry = (Map.Entry) it.next();
							final KSubstOption toKSubst = (KSubstOption) entry.getKey();
							final List list = (List) entry.getValue();
							// do it
							Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */,
								new ICVSRunnable(								) {
									public void run(IProgressMonitor monitor) throws CVSException {
										result[0] = Command.ADMIN.execute(Command.NO_GLOBAL_OPTIONS,
											new LocalOption[] { toKSubst },
											(ICVSResource[]) list.toArray(new ICVSResource[list.size()]),
											new AdminKSubstListener(toKSubst),
											Policy.subMonitorFor(monitor, list.size()));
									}
								}, Policy.subMonitorFor(monitor, list.size()));
							// if errors were encountered, abort
							if (! result[0].isOK()) return;
						}
					} finally {
						monitor.done();
					}
				}
			}
		}, Policy.monitorFor(monitor));
		return result[0];
	}
	
	/**
	 * Fixes the line delimiters in the local file to reflect the platform's
	 * native encoding.  Performs CR/LF -> LF or LF -> CR/LF conversion
	 * depending on the platform but does not affect delimiters that are
	 * already correctly encoded.
	 */
	public static void cleanLineDelimiters(IFile file, boolean useCRLF, IProgressMonitor progress)
		throws CVSException {
		try {
			// convert delimiters in memory
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			InputStream is = new BufferedInputStream(file.getContents());
			try {
				is = new CRLFtoLFInputStream(is);
				if (useCRLF) is = new LFtoCRLFInputStream(is);
				for (int b; (b = is.read()) != -1;) bos.write(b);
				bos.close();
			} finally {
				is.close();
			}
			// write file back to disk with corrected delimiters if changes were made
			ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
			file.setContents(bis, false /*force*/, false /*keepHistory*/, progress);
		} catch (CoreException e) {
			throw CVSException.wrapException(file, Policy.bind("CVSTeamProvider.cleanLineDelimitersException"), e); //$NON-NLS-1$
		} catch (IOException e) {
			throw CVSException.wrapException(file, Policy.bind("CVSTeamProvider.cleanLineDelimitersException"), e); //$NON-NLS-1$
		}
	}
	
	/*
	 * Marks a file as dirty.
	 */
	private static void makeDirty(IFile file) throws CVSException {
		ICVSFile mFile = CVSWorkspaceRoot.getCVSFileFor(file);
		ResourceSyncInfo origInfo = mFile.getSyncInfo();
		MutableResourceSyncInfo info = origInfo.cloneMutable();
		info.setTimeStamp(null);/*set the sync timestamp to null to trigger dirtyness*/
		mFile.setSyncInfo(info, ICVSFile.UNKNOWN);
	}
	
	/*
	 * @see RepositoryProvider#getID()
	 */
	public String getID() {
		return CVSProviderPlugin.getTypeId();
	}
	
	/*
	 * @see RepositoryProvider#getMoveDeleteHook()
	 */
	public IMoveDeleteHook getMoveDeleteHook() {
		return moveDeleteHook;
	}
	
	/*
	 * Return the currently registered Move/Delete Hook
	 */
	public static MoveDeleteHook getRegisteredMoveDeleteHook() {
		return moveDeleteHook;
	}
	 
	/**
	 * @see org.eclipse.team.core.RepositoryProvider#getFileModificationValidator()
	 */
	public IFileModificationValidator getFileModificationValidator() {
		if (CVSTeamProvider.fileModificationValidator == null) {
			CVSTeamProvider.fileModificationValidator = CVSTeamProvider.getPluggedInValidator();
			if (CVSTeamProvider.fileModificationValidator == null) {
				CVSTeamProvider.fileModificationValidator =super.getFileModificationValidator();
			}
		}
		return CVSTeamProvider.fileModificationValidator;
	}
	
	/**
	 * Checkout (cvs edit) the provided resources so they can be modified locally and committed.
	 * This will make any read-only resources in the list writable and will notify the server
	 * that the file is being edited. This notification may be done immediately or at some 
	 * later point depending on whether contact with the server is possble at the time of 
	 * invocation or the value of the notify server parameter.
	 * 
	 * The recurse parameter is equivalent to the cvs local options -l (<code>true</code>) and 
	 * -R (<code>false</code>). The notifyServer parameter can be used to defer server contact
	 * until the next command. This may be approrpiate if no shell or progress monitor is available
	 * to the caller. The notification bit field indicates what temporary watches are to be used while
	 * the file is being edited. The possible values that can be ORed together are ICVSFile.EDIT, 
	 * ICVSFile.UNEDIT and ICVSFile.COMMIT. There pre-ORed convenience values ICVSFile.NO_NOTIFICATION
	 * and ICVSFile.NOTIFY_ON_ALL are also available.
	 * 
	 * @param resources the resources to be edited
	 * @param recurse indicates whether to recurse (-R) or not (-l)
	 * @param notifyServer indicates whether to notify the server now, if possible,
	 *     or defer until the next command.
	 * @param notification the temporary watches.
	 * @param progress progress monitor to provide progress indication/cancellation or <code>null</code>
	 * @exception CVSException if this method fails.
	 * @since 2.1
	 * 
	 * @see CVSTeamProvider#unedit
	 */
	public void edit(IResource[] resources, boolean recurse, boolean notifyServer, final int notification, IProgressMonitor progress) throws CVSException {
		notifyEditUnedit(resources, recurse, notifyServer, new ICVSResourceVisitor() {
			public void visitFile(ICVSFile file) throws CVSException {
				if (file.isReadOnly())
					file.edit(notification, Policy.monitorFor(null));
			}
			public void visitFolder(ICVSFolder folder) throws CVSException {
				// nothing needs to be done here as the recurse will handle the traversal
			}
		}, progress);
	}
	
	/**
	 * Unedit the given resources. Any writtable resources will be reverted to their base contents
	 * and made read-only and the server will be notified that the file is no longer being edited.
	 * This notification may be done immediately or at some 
	 * later point depending on whether contact with the server is possble at the time of 
	 * invocation or the value of the notify server parameter.
	 * 
	 * The recurse parameter is equivalent to the cvs local options -l (<code>true</code>) and 
	 * -R (<code>false</code>). The notifyServer parameter can be used to defer server contact
	 * until the next command. This may be approrpiate if no shell or progress monitor is available
	 * to the caller.
	 * 
	 * @param resources the resources to be unedited
	 * @param recurse indicates whether to recurse (-R) or not (-l)
	 * @param notifyServer indicates whether to notify the server now, if possible,
	 *     or defer until the next command.
	 * @param progress progress monitor to provide progress indication/cancellation or <code>null</code>
	 * @exception CVSException if this method fails.
	 * @since 2.1
	 * 
	 * @see CVSTeamProvider#edit
	 */
	public void unedit(IResource[] resources, boolean recurse, boolean notifyServer, IProgressMonitor progress) throws CVSException {
		notifyEditUnedit(resources, recurse, notifyServer, new ICVSResourceVisitor() {
			public void visitFile(ICVSFile file) throws CVSException {
				if (!file.isReadOnly())
					file.unedit(Policy.monitorFor(null));
			}
			public void visitFolder(ICVSFolder folder) throws CVSException {
				// nothing needs to be done here as the recurse will handle the traversal
			}
		}, progress);
	}
	
	/*
	 * This method captures the common behavior between the edit and unedit methods.
	 */
	private void notifyEditUnedit(final IResource[] resources, final boolean recurse, final boolean notifyServer, final ICVSResourceVisitor editUneditVisitor, IProgressMonitor monitor) throws CVSException {
		final IProgressMonitor progress = Policy.monitorFor(monitor);
		final CVSException[] exception = new CVSException[] { null };
		IWorkspaceRunnable workspaceRunnable = new IWorkspaceRunnable() {
			public void run(IProgressMonitor pm) throws CoreException {
				final ICVSResource[] cvsResources = getCVSArguments(resources);
				
				// mark the files locally as being checked out
				try {
					for (int i = 0; i < cvsResources.length; i++) {
						cvsResources[i].accept(editUneditVisitor, recurse);
					}
				} catch (CVSException e) {
					exception[0] = e;
					return;
				}
				
				// send the noop command to the server in order to deliver the notifications
				if (notifyServer) {
					final boolean[] connected = new boolean[] { false };
					try {
						Session.run(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true, new ICVSRunnable() {
							public void run(IProgressMonitor monitor) throws CVSException {
								connected[0] = true;
								Command.NOOP.execute(Command.NO_GLOBAL_OPTIONS, Command.NO_LOCAL_OPTIONS, 
								cvsResources, null, monitor);
							}
						}, progress);
					} catch (CVSException e) {
						// Only report the exception if we were able to connect.
						// If we couldn't connect, the notification will be sent the next time we do.
						if (connected[0]) exception[0] = e;
					} finally {
						progress.done();
					}
				}
			}
		};
		try {
			ResourcesPlugin.getWorkspace().run(workspaceRunnable, monitor);
		} catch (CoreException e) {
			if (exception[0] == null) {
				throw CVSException.wrapException(e);
			} else {
				CVSProviderPlugin.log(CVSException.wrapException(e));
			}
		}
		if (exception[0] != null) {
			throw exception[0];
		}
	}
	
	/**
	 * Gets the etchAbsentDirectories.
	 * @return Returns a boolean
	 */
	public boolean getFetchAbsentDirectories() throws CVSException {
		try {
			String property = getProject().getPersistentProperty(FETCH_ABSENT_DIRECTORIES_PROP_KEY);
			if (property == null) return CVSProviderPlugin.getPlugin().getFetchAbsentDirectories();
			return Boolean.valueOf(property).booleanValue();
		} catch (CoreException e) {
			throw new CVSException(new CVSStatus(IStatus.ERROR, Policy.bind("CVSTeamProvider.errorGettingFetchProperty", project.getName()), e)); //$NON-NLS-1$
		}
	}
	
	/**
	 * Sets the fetchAbsentDirectories.
	 * @param etchAbsentDirectories The etchAbsentDirectories to set
	 */
	public void setFetchAbsentDirectories(boolean fetchAbsentDirectories) throws CVSException {
		try {
			getProject().setPersistentProperty(FETCH_ABSENT_DIRECTORIES_PROP_KEY, fetchAbsentDirectories ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
		} catch (CoreException e) {
			throw new CVSException(new CVSStatus(IStatus.ERROR, Policy.bind("CVSTeamProvider.errorSettingFetchProperty", project.getName()), e)); //$NON-NLS-1$
		}
	}

	/**
	 * @see org.eclipse.team.core.RepositoryProvider#canHandleLinkedResources()
	 */
	public boolean canHandleLinkedResources() {
		return true;
	}

	/**
	 * @see org.eclipse.team.core.RepositoryProvider#validateCreateLink(org.eclipse.core.resources.IResource, int, org.eclipse.core.runtime.IPath)
	 */
	public IStatus validateCreateLink(IResource resource, int updateFlags, IPath location) {
		ICVSFolder cvsFolder = CVSWorkspaceRoot.getCVSFolderFor(resource.getParent().getFolder(new Path(resource.getName())));
		try {
			if (cvsFolder.isCVSFolder()) {
				// There is a remote folder that overlaps with the link so disallow
				return new CVSStatus(IStatus.ERROR, Policy.bind("CVSTeamProvider.overlappingRemoteFolder", resource.getFullPath().toString())); //$NON-NLS-1$
			} else {
				ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor(resource.getParent().getFile(new Path(resource.getName())));
				if (cvsFile.isManaged()) {
					// there is an outgoing file deletion that overlaps the link so disallow
					return new CVSStatus(IStatus.ERROR, Policy.bind("CVSTeamProvider.overlappingFileDeletion", resource.getFullPath().toString())); //$NON-NLS-1$
				}
			}
		} catch (CVSException e) {
			CVSProviderPlugin.log(e);
			return e.getStatus();
		}

		return super.validateCreateLink(resource, updateFlags, location);
	}
	
	/**
	 * Get the editors of the resources by calling the <code>cvs editors</code> command.
	 * 
	 * @author <a href="mailto:gregor.kohlwes@csc.com,kohlwes@gmx.net">Gregor Kohlwes</a>
	 * @param resources
	 * @param progress
	 * @return IEditorsInfo[]
	 * @throws CVSException
	 */
	public EditorsInfo[] editors(
		IResource[] resources,
		IProgressMonitor progress)
		throws CVSException {

		// Build the local options
		LocalOption[] commandOptions = new LocalOption[] {
		};

		// Build the arguments list
		String[] arguments = getValidArguments(resources, commandOptions);

		// Build the listener for the command
		EditorsListener listener = new EditorsListener();

		// Check if canceled
		if (progress.isCanceled()) {
			return new EditorsInfo[0];
		}
		// Build the session
		Session session =
			new Session(
				workspaceRoot.getRemoteLocation(),
				workspaceRoot.getLocalRoot());

		// Check if canceled
		if (progress.isCanceled()) {
			return new EditorsInfo[0];
		}
		progress.beginTask(null, 100);
		try {
			// Opening the session takes 20% of the time
			session.open(Policy.subMonitorFor(progress, 20));

			if (!progress.isCanceled()) {
				// Execute the editors command
				Command.EDITORS.execute(
					session,
					Command.NO_GLOBAL_OPTIONS,
					commandOptions,
					arguments,
					listener,
					Policy.subMonitorFor(progress, 80));
			}
		} finally {
			session.close();
			progress.done();
		}
		// Return the infos about the editors
		return listener.getEditorsInfos();
	}

	/**
	 * Return the commit comment template that was provided by the server.
	 * 
	 * @return String
	 * @throws CVSException
	 */
	public String getCommitTemplate() throws CVSException {
		ICVSFolder localFolder = getCVSWorkspaceRoot().getLocalRoot();
		ICVSFile templateFile = CVSWorkspaceRoot.getCVSFileFor(
			SyncFileWriter.getTemplateFile(
				(IContainer)localFolder.getIResource()));
		if (!templateFile.exists()) return null;
		InputStream in = new BufferedInputStream(templateFile.getContents());
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int b;
			do {
				b = in.read();
				if (b != -1)
					out.write((byte)b);
			} while (b != -1);
			out.close();
			return new String(out.toString());
		} catch (IOException e) {
			throw CVSException.wrapException(e);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				// Since we already have the contents, just log this exception
				CVSProviderPlugin.log(CVSException.wrapException(e));
			}
		}
	}
	
	/**
	 * Return true if the project is configured to use watch/edit. A project will use 
	 * watch/edit if it was checked out when the global preference to use watch/edit is
	 * turned on.
	 * @return boolean
	 */
	public boolean isWatchEditEnabled() throws CVSException {
		try {
			IProject project = getProject();
			String property = (String)project.getSessionProperty(WATCH_EDIT_PROP_KEY);
			if (property == null) {
				property = project.getPersistentProperty(WATCH_EDIT_PROP_KEY);
				if (property == null) {
					// The persistant property for the project was never set (i.e. old project)
					// Use the global preference to determinw if the project is using watch/edit
					return CVSProviderPlugin.getPlugin().isWatchEditEnabled();
				} else {
					project.setSessionProperty(WATCH_EDIT_PROP_KEY, property);
				}
			}
			return Boolean.valueOf(property).booleanValue();
		} catch (CoreException e) {
			throw new CVSException(new CVSStatus(IStatus.ERROR, Policy.bind("CVSTeamProvider.errorGettingWatchEdit", project.getName()), e)); //$NON-NLS-1$
		}
	}
	
	public void setWatchEditEnabled(boolean enabled) throws CVSException {
		try {
			IProject project = getProject();
			project.setPersistentProperty(WATCH_EDIT_PROP_KEY, enabled ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
			project.setSessionProperty(WATCH_EDIT_PROP_KEY, enabled ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
		} catch (CoreException e) {
			throw new CVSException(new CVSStatus(IStatus.ERROR, Policy.bind("CVSTeamProvider.errorSettingWatchEdit", project.getName()), e)); //$NON-NLS-1$
		}
	}
}
