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


import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Console;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.actions.AddToWorkspaceAction;
import org.eclipse.team.internal.ccvs.ui.actions.CommitAction;
import org.eclipse.team.internal.ccvs.ui.actions.ReplaceWithRemoteAction;
import org.eclipse.team.internal.ccvs.ui.actions.TagAction;
import org.eclipse.team.internal.ccvs.ui.actions.UpdateAction;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.ccvs.ui.sync.CVSSyncCompareInput;
import org.eclipse.team.internal.ccvs.ui.sync.CommitSyncAction;
import org.eclipse.team.internal.ccvs.ui.sync.ForceCommitSyncAction;
import org.eclipse.team.internal.ccvs.ui.sync.ForceUpdateSyncAction;
import org.eclipse.team.internal.ccvs.ui.sync.UpdateSyncAction;
import org.eclipse.team.internal.ccvs.ui.wizards.SharingWizard;
import org.eclipse.team.internal.ui.IPromptCondition;
import org.eclipse.team.internal.ui.sync.ITeamNode;
import org.eclipse.team.internal.ui.sync.SyncSet;
import org.eclipse.team.internal.ui.sync.SyncView;
import org.eclipse.team.tests.ccvs.core.CVSTestSetup;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;

public class CVSUITestCase extends LoggingTestCase {
	protected static Set installedTrap = new HashSet();
	private List testWindows;
	protected IWorkbenchWindow testWindow;
	protected CVSRepositoryLocation testRepository;
	protected boolean USE_CONSOLE = System.getProperty("cvs.tests.use_console") != null;
	protected boolean SHOW_CONSOLE = System.getProperty("cvs.tests.show_console") != null;

	public CVSUITestCase(String name) {
		super(name);
		testWindows = new ArrayList(3);
	}
	
	protected void setUp() throws Exception {
		super.setUp();
		testRepository = CVSTestSetup.repository;
		testWindow = openTestWindow();
		
		Display display = testWindow.getShell().getDisplay();
		if (! installedTrap.contains(display)) {
			installedTrap.add(display);
			Util.waitForErrorDialog(display, 10000 /*ms*/, new Waiter() {
				public boolean notify(Object object) {
					Dialog dialog = (Dialog) object;
					printWarning("Encountered error dialog with title: " + dialog.getShell().getText(), null, null);
					dialog.close();
					return true;
				}
			});
		}

		// disable auto-build
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		description.setAutoBuilding(false);
		workspace.setDescription(description);
						
		// disable CVS GZIP compression
		IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();
		store.setValue(ICVSUIConstants.PREF_COMPRESSION_LEVEL, 0);
		CVSProviderPlugin.getPlugin().setCompressionLevel(0);

		// default case is to not show the console but don't disable it, this is
		// the typical user experience.
		if(SHOW_CONSOLE) {
			showConsole();
		} else if(!USE_CONSOLE) {
			CVSProviderPlugin.getPlugin().setConsoleListener(null);			
		}
	
		// wait for UI to settle
		Util.processEventsUntil(100);
	}
	
	protected void tearDown() throws Exception {
		// wait for UI to settle
		Util.processEventsUntil(100);
		closeAllTestWindows();
		super.tearDown();
	}

	protected void showConsole() {
		try {
			IWorkbenchPage page = CVSUIPlugin.getActivePage();
			IViewPart consolePart = page.findView(Console.CONSOLE_ID);
			if (consolePart == null) {
				IWorkbenchPart activePart = page.getActivePart();
				consolePart = page.showView(Console.CONSOLE_ID);
				//restore focus stolen by the creation of the console
				if (activePart != null) page.activate(activePart);
			} else {
				page.bringToTop(consolePart);
			}
		} catch (PartInitException pe) {
		}
	}
	
 	/** 
	 * Open a test window with the empty perspective.
	 */
	protected IWorkbenchWindow openTestWindow() {
		try {
			IWorkbenchWindow win = PlatformUI.getWorkbench().openWorkbenchWindow(
				EmptyPerspective.PERSP_ID, ResourcesPlugin.getWorkspace());
			testWindows.add(win);
			return win;
		} catch (WorkbenchException e) {
			fail();
			return null;
		}
	}

 	/**
	 * Close all test windows.
	 */
	protected void closeAllTestWindows() {
		Iterator iter = testWindows.iterator();
		IWorkbenchWindow win;
		while (iter.hasNext()) {
			win = (IWorkbenchWindow) iter.next();
			win.close();
		}
		testWindows.clear();
	}

	/**
	 * Checks out the projects with the specified tags from the test repository.
	 */
	protected void actionCheckoutProjects(String[] projectNames, CVSTag[] tags) throws Exception {
		ICVSRemoteFolder[] projects = lookupRemoteProjects(projectNames, tags);
		AddToWorkspaceAction action = new AddToWorkspaceAction() {
			protected IPromptCondition getOverwriteLocalAndFileSystemPrompt() {
				return new DummyPromptCondition();
			}
		};
		runActionDelegate(action, projects, "Repository View Checkout action");
		timestampGranularityHiatus();
	}

	/**
	 * Replaces the specified resources with the remote contents using the action contribution.
	 */
	protected void actionReplaceWithRemote(IResource[] resources) {
		ReplaceWithRemoteAction action = new ReplaceWithRemoteAction() {
			protected IPromptCondition getPromptCondition(IResource[] resources) {
				return new DummyPromptCondition();
			}
		};
		runActionDelegate(action, resources, "Replace with Remote action");
		timestampGranularityHiatus();
	}
		
	/**
	 * Shares the specified project with the test repository.
	 * @param project the project to share
	 */
	protected void actionShareProject(IProject project) {
		final SharingWizard wizard = new SharingWizard();
		wizard.init(PlatformUI.getWorkbench(), project);
		Util.waitForWizardToOpen(testWindow.getShell(), wizard, new Waiter() {
			public boolean notify(Object object) {
				WizardDialog dialog = (WizardDialog) object;
				startTask("set sharing, pop up sync viewer");
				wizard.performFinish();
				endTask();
				dialog.close();
				return false;
			}
		});
		timestampGranularityHiatus();
	}

	/**
	 * Updates the specified resources using the action contribution.
	 */
	protected void actionCVSCommit(IResource[] resources, final String comment) {
		assertNotNull(comment);
		CommitAction action = new CommitAction() {
			protected String promptForComment() {
				return comment;
			}
		};
		runActionDelegate(action, resources, "CVS Commit action");
		timestampGranularityHiatus();
	}
	
	/**
	 * Tags the specified resources using the action contribution.
	 */
	protected void actionCVSTag(IResource[] resources, final String name) {
		assertNotNull(name);
		TagAction action = new TagAction() {
			protected String promptForTag(ICVSFolder folder) {
				return name;
			}
			protected IPromptCondition getPromptCondition(IResource[] resources) {
				return new DummyPromptCondition();
			}
		};
		runActionDelegate(action, resources, "CVS Tag action");
	}

	/**
	 * Updates the specified resources using the action contribution.
	 */
	protected void actionCVSUpdate(IResource[] resources) {
		runActionDelegate(new UpdateAction(), resources, "CVS Update action");
		timestampGranularityHiatus();
	}
	
	/**
	 * Pops up the synchronizer view for the specified resources.
	 * @param resources the resources to sync
	 * @return the compare input used
	 */
	protected CVSSyncCompareInput syncResources(IResource[] resources) {
		startTask("Synchronize with Repository action");
		SyncView syncView = getSyncView();
		CVSSyncCompareInput input = new CVSSyncCompareInput(resources) {
			// overridden to prevent "nothing to synchronize" dialog from popping up
			public void run(IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
				super.run(monitor);
				DiffNode result = getDiffRoot(); // (DiffNode) getCompareResult()
				if (result == null || Util.isEmpty(result)) throw new InterruptedException();
			}
		};
		syncView.showSync(input);
		endTask();
		return input;
	}
	
	/**
	 * Commits the specified resources using the synchronizer view.
	 * @param resources the resources to commit
	 * @param input the compare input for the sync view, or null to create a new one
	 * @param comment the comment string, or ""
	 */
	protected void syncCommitResources(IResource[] resources, CVSSyncCompareInput input, String comment) {
		if (input == null) input = syncResources(resources);
		IDiffContainer diffRoot = input.getDiffRoot();
		if (Util.isEmpty(diffRoot)) {
			startTask("Nothing to Commit");
		} else {
			ITeamNode[] nodes = getTeamNodesForResources(diffRoot, resources);
			startTask("Sync View Commit action");
			syncCommitInternal(input, nodes, comment);
		}
		endTask();
		timestampGranularityHiatus();
	}
	
	/**
	 * Updates the specified resources using the synchronizer view.
	 * @param resources the resources to update
	 * @param input the compare input for the sync view, or null to create a new one
	 * @param comment the comment string, or ""
	 */
	protected void syncUpdateResources(IResource[] resources, CVSSyncCompareInput input) {
		if (input == null) input = syncResources(resources);
		IDiffContainer diffRoot = input.getDiffRoot();
		if (Util.isEmpty(diffRoot)) {
			startTask("Nothing to Update");
		} else {
			ITeamNode[] nodes = getTeamNodesForResources(diffRoot, resources);
			startTask("Sync View Update action");
			syncGetInternal(input, nodes);
		}
		endTask();
		timestampGranularityHiatus();
	}
	
	/**
	 * Creates and imports project contents from a zip file.
	 */
	protected IProject createAndImportProject(String prefix, File zipFile) throws Exception {
		IProject project = Util.createUniqueProject(prefix);
		Util.importZip(project, zipFile);
		return project;
	}
	
	/**
	 * Looks up handles for remote projects by name.
	 */
	protected ICVSRemoteFolder[] lookupRemoteProjects(String[] projectNames, CVSTag[] tags) throws Exception {
		ICVSRemoteFolder[] folders = new ICVSRemoteFolder[projectNames.length];
		for (int i = 0; i < projectNames.length; ++i) {
			folders[i] = testRepository.getRemoteFolder(projectNames[i], tags[i]);
		}
		return folders;
	}
	
	/**
	 * Gets an instance of the Synchronize view
	 */
	protected SyncView getSyncView() {
		SyncView view = SyncView.findViewInActivePage(null);
		assertNotNull("Could not obtain a Sync View.", view);
		return view;
	}
	
	/**
	 * Runs an IActionDelegate prototype instance on a given selection.
	 */
	protected void runActionDelegate(IActionDelegate delegate, Object[] selection, String taskName) {
		Action action = new Action() { };
		if (delegate instanceof IObjectActionDelegate) {
			((IObjectActionDelegate) delegate).setActivePart(action,
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService().getActivePart());
		}
		delegate.selectionChanged(action, new StructuredSelection(selection));
		startTask(taskName);
		delegate.run(action);
		endTask();
	}
	
	/**
	 * Commits NON-CONFLICTING and CONFLICTING resources represented by an array of synchronizer nodes.
	 */
	private void syncCommitInternal(CVSSyncCompareInput input, ITeamNode[] nodes, final String comment) {
		FakeSelectionProvider selectionProvider = new FakeSelectionProvider(nodes);
		// Commit ONLY NON-CONFLICTING changes
		CommitSyncAction commitAction = new CommitSyncAction(input, selectionProvider, "Commit",
			testWindow.getShell()) {
			protected int promptForConflicts(SyncSet syncSet) {
				return 0; // yes! sync conflicting changes
			}
			protected String promptForComment(RepositoryManager manager, IResource[] resourcesToCommit) {
				return comment;
			}
			protected IResource[] promptForResourcesToBeAdded(RepositoryManager manager, IResource[] unadded) {
				return unadded;
			}
		};
		commitAction.run();
		// Commit ONLY CONFLICTING changes
		ForceCommitSyncAction forceCommitAction = new ForceCommitSyncAction(input, selectionProvider, "Force Commit",
			testWindow.getShell()) {
			protected int promptForConflicts(SyncSet syncSet) {
				return 0; // yes! sync conflicting changes
			}
			protected String promptForComment(RepositoryManager manager, IResource[] resourcesToCommit) {
				return comment;
			}
			protected IResource[] promptForResourcesToBeAdded(RepositoryManager manager, IResource[] unadded) {
				return unadded;
			}
		};
		forceCommitAction.run();
	}

	/**
	 * Updates NON-CONFLICTING and CONFLICTING resources represented by an array of synchronizer nodes.
	 */
	private void syncGetInternal(CVSSyncCompareInput input, ITeamNode[] nodes) {
		FakeSelectionProvider selectionProvider = new FakeSelectionProvider(nodes);
		// Update ONLY NON-CONFLICTING changes
		UpdateSyncAction updateAction = new UpdateSyncAction(input, selectionProvider, "Update",
			testWindow.getShell()) {
			protected boolean promptForConflicts() {
				return true;
			}
			protected int promptForMergeableConflicts() {
				return 2;
			}
		};
		updateAction.run();
		// Update ONLY CONFLICTING changes
		ForceUpdateSyncAction forceUpdateAction = new ForceUpdateSyncAction(input, selectionProvider, "Force Update",
			testWindow.getShell()) {
			protected boolean promptForConflicts() {
				return true;
			}
			protected int promptForMergeableConflicts() {
				return 2;
			}
		};
		forceUpdateAction.run();
	}

	/**
	 * Gets an array of synchronizer nodes corresponding to an array of resouces.
	 */
	protected static ITeamNode[] getTeamNodesForResources(IDiffContainer root, IResource[] resources) {
		ITeamNode[] nodes = new ITeamNode[resources.length];
		for (int i = 0; i < resources.length; ++i) {
			nodes[i] = findTeamNodeForResource(root, resources[i]);
			assertNotNull(nodes[i]);
		}
		return nodes;
	}
	
	private static ITeamNode findTeamNodeForResource(IDiffElement root, IResource resource) {
		RepositoryProvider provider = RepositoryProvider.getProvider(resource.getProject(), CVSProviderPlugin.getTypeId());
		assertNotNull("Resource " + resource.getFullPath() + " must have an associated CVSProvider", provider);
		
		if (root instanceof ITeamNode) {
			ITeamNode node = (ITeamNode) root;
			if (resource.equals(node.getResource())) return node;
			// prune the backtracking tree
			IResource parent = resource.getParent();
			do {
				if (parent == null) return null; // can't possibly be child of this node
			} while (! resource.equals(parent));
		}
		if (root instanceof IDiffContainer) {
			IDiffContainer container = (IDiffContainer) root;
			if (container.hasChildren()) {
				IDiffElement[] children = container.getChildren();
				for (int i = 0; i < children.length; ++i) {
					ITeamNode node = findTeamNodeForResource(children[i], resource);
					if (node != null) return node;
				}
			}
		}
		return null;
	}
	
	/**
	 * Waits for a small amount of time to compensate for file system time stamp granularity.
	 */
	private void timestampGranularityHiatus() {
		//JUnitTestCase.waitMsec(1500);
		Util.processEventsUntil(1500);
	}
}
