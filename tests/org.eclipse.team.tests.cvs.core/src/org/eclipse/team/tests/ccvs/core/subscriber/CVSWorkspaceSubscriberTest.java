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
package org.eclipse.team.tests.ccvs.core.subscriber;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.ui.subscriber.CVSSubscriberAction;
import org.eclipse.team.tests.ccvs.core.CVSTestSetup;
import org.eclipse.team.ui.synchronize.actions.SyncInfoSet;

/**
 * This class tests the CVSWorkspaceSubscriber
 */
public class CVSWorkspaceSubscriberTest extends CVSSyncSubscriberTest {
	
	/**
	 * Constructor for CVSProviderTest
	 */
	public CVSWorkspaceSubscriberTest() {
		super();
	}

	/**
	 * Constructor for CVSProviderTest
	 */
	public CVSWorkspaceSubscriberTest(String name) {
		super(name);
	}

	public static Test suite() {
		String testName = System.getProperty("eclipse.cvs.testName");
		if (testName == null) {
			TestSuite suite = new TestSuite(CVSWorkspaceSubscriberTest.class);
			return new CVSTestSetup(suite);
		} else {
			return new CVSTestSetup(new CVSWorkspaceSubscriberTest(testName));
		}
	}
	
	protected CVSSyncTreeSubscriber getSubscriber() throws TeamException {
		return (CVSSyncTreeSubscriber)getWorkspaceSubscriber();
	}
	
	/* (non-Javadoc)
	 * 
	 * The shareProject method is invoked when creating new projects.
	 * @see org.eclipse.team.tests.ccvs.core.EclipseTest#shareProject(org.eclipse.core.resources.IProject)
	 */
	protected void shareProject(final IProject project) throws TeamException, CoreException {
		mapNewProject(project);
		// Everything should be outgoing addition except he project
		assertSyncEquals(project.getName(), getSubscriber(), project, SyncInfo.IN_SYNC);
		assertAllSyncEquals(project.members(true), SyncInfo.OUTGOING | SyncInfo.ADDITION, IResource.DEPTH_INFINITE);

		commitNewProject(project);
		// Everything should be in-sync
		assertAllSyncEquals(project, SyncInfo.IN_SYNC, IResource.DEPTH_INFINITE);
	}
	
	protected void assertAllSyncEquals(final IResource rootResource, final int kind, int depth) throws CoreException {
		if (!rootResource.exists() && !rootResource.isPhantom()) {
			assertTrue(kind == SyncInfo.IN_SYNC);
			return;
		}
		rootResource.accept(new IResourceVisitor() {
			public boolean visit(IResource resource) throws CoreException {
				assertSyncEquals(rootResource.getName(), getSubscriber(), resource, kind);
				return true;
			}
		}, depth, true);
	}
	
	private void assertAllSyncEquals(IResource[] resources, int kind, int depth) throws CoreException {
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			assertAllSyncEquals(resource, kind, depth);
		}
	}
	
	/* (non-Javadoc)
	 * 
	 * Override to check that the proper sync state is achieved.
	 * 
	 * @see org.eclipse.team.tests.ccvs.core.EclipseTest#setContentsAndEnsureModified(org.eclipse.core.resources.IFile)
	 */
	protected void setContentsAndEnsureModified(IFile file) throws CoreException, TeamException {
		// The delta will indicate to any interested parties that the sync state of the
		// file has changed
		super.setContentsAndEnsureModified(file);
		assertSyncEquals("Setting contents: ", file, SyncInfo.OUTGOING | SyncInfo.CHANGE);
	}
	
	private void assertSyncEquals(String string, IProject project, String[] strings, boolean refresh, int[] kinds) throws CoreException, TeamException {
		assertSyncEquals(string, getSubscriber(), project, strings, refresh, kinds);
	}
	
	private void assertSyncEquals(IProject project, String[] strings, boolean refresh, int[] kinds) throws CoreException, TeamException {
		assertSyncEquals(getName(), getSubscriber(), project, strings, refresh, kinds);
	}
	
	private void assertSyncEquals(String message, IResource resource, int syncKind) throws TeamException {
		assertSyncEquals(message, getSubscriber(), resource, syncKind);
		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.EclipseTest#addResources(org.eclipse.core.resources.IResource[])
	 */
	protected void addResources(IResource[] resources) throws TeamException, CVSException, CoreException {
		// first, get affected children
		IResource[] affectedChildren = collect(resources, new ResourceCondition() {
			public boolean matches(IResource resource) throws CoreException, TeamException {
				ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
				return (!cvsResource.isManaged() && !cvsResource.isIgnored());
			}
		}, IResource.DEPTH_INFINITE);
		// also get affected parents
		IResource[] affectedParents = collectAncestors(resources, new ResourceCondition() {
			public boolean matches(IResource resource) throws CoreException, TeamException {
				if (resource.getType() == IResource.PROJECT) return false;
				ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
				return (!cvsResource.isManaged() && !cvsResource.isIgnored());
			}
		});
		Set affected = new HashSet();
		affected.addAll(Arrays.asList(affectedChildren));
		affected.addAll(Arrays.asList(affectedParents));
		
		registerSubscriberListener();
		super.addResources(resources);
		TeamDelta[] changes = deregisterSubscriberListener();
		assertSyncChangesMatch(changes, (IResource[]) affected.toArray(new IResource[affected.size()]));
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (resource.getType() == IResource.FILE) {
				assertSyncEquals("Add", resource, SyncInfo.OUTGOING | SyncInfo.ADDITION);
			} else {
				// TODO: a folder should be in sync but isn't handled properly
				assertSyncEquals("Add", resource, SyncInfo.IN_SYNC);
			}
			
		}
	}

	private void registerSubscriberListener() throws TeamException {
		registerSubscriberListener(getSubscriber());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.EclipseTest#deleteResources(org.eclipse.core.resources.IResource[])
	 */
	protected void deleteResources(IResource[] resources) throws TeamException, CoreException {
		IResource[] affected = collect(resources, new ResourceCondition(), IResource.DEPTH_INFINITE);
		registerSubscriberListener();
		super.deleteResources(resources);
		TeamDelta[] changes = deregisterSubscriberListener();
		assertSyncChangesMatch(changes, affected);
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			// After deletion, folders should be in-sync while files should be outgoing deletions
			if (resource.getType() == IResource.FILE) {
				assertSyncEquals("Delete", resource, SyncInfo.OUTGOING | SyncInfo.DELETION);
			} else {
				assertSyncEquals("Delete", resource, SyncInfo.IN_SYNC);
			}
		}
	}
	
	private TeamDelta[] deregisterSubscriberListener() throws TeamException {
		return deregisterSubscriberListener(getSubscriber());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.EclipseTest#commitResources(org.eclipse.core.resources.IResource[])
	 */
	protected void commitResources(IResource[] resources, int depth) throws TeamException, CVSException, CoreException {
		IResource[] affected = collect(resources, new ResourceCondition() {
				public boolean matches(IResource resource) throws CoreException, TeamException {
					ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
					return (!cvsResource.isFolder() && cvsResource.isManaged() && cvsResource.isModified(DEFAULT_MONITOR));
				}
			}, IResource.DEPTH_INFINITE);
		registerSubscriberListener();
		super.commitResources(resources, depth);
		TeamDelta[] changes = deregisterSubscriberListener();
		assertSyncChangesMatch(changes, affected);
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (resource.exists())
				assertSyncEquals("Commit", resource, SyncInfo.IN_SYNC);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.tests.ccvs.core.EclipseTest#unmanageResources(org.eclipse.core.resources.IResource[])
	 */
	protected void unmanageResources(IResource[] resources) throws CoreException, TeamException {
		IResource[] affected = collect(resources, new ResourceCondition() {
				public boolean matches(IResource resource) throws CoreException, TeamException {
					ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
					return (cvsResource.isManaged());
				}
			}, IResource.DEPTH_INFINITE);
		registerSubscriberListener();
		super.unmanageResources(resources);
		TeamDelta[] changes = deregisterSubscriberListener();
		assertSyncChangesMatch(changes, affected);
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (resource.exists())
				assertSyncEquals("Unmanage", resource, SyncInfo.IN_SYNC);
		}
	}
	
	/******************************************************************
	 * Subscriber Actions: commit/update and overide and commit/update
	 * These actions are those contributed to the Synchronize View by
	 * the CVS plugin.
	 ******************************************************************/
	private SyncInfo[] createSyncInfos(IResource[] resources) throws TeamException {
		return createSyncInfos(getSubscriber(), resources);
	}
	
	public IResource[] update(IContainer container, String[] hierarchy) throws CoreException, TeamException, InvocationTargetException, InterruptedException {
		return runSubscriberAction(new TestUpdateAction(), container, hierarchy);
	}
	
	public IResource[] commit(IContainer container, String[] hierarchy) throws CoreException, TeamException {
		return runSubscriberAction(new TestCommitAction(), container, hierarchy);
	}

	public IResource[] overrideAndUpdate(IContainer container, String[] hierarchy, boolean shouldPrompt) throws CoreException, TeamException {
		TestOverrideAndUpdate action = new TestOverrideAndUpdate();
		IResource[] resources = runSubscriberAction(action, container, hierarchy);
		assertTrue(shouldPrompt == action.isPrompted());			
		return resources;
	}
	
	public IResource[] overrideAndCommit(IContainer container, String[] hierarchy, boolean shouldPrompt) throws CoreException, TeamException {
		TestOverrideAndCommit action = new TestOverrideAndCommit();
		IResource[] resources = runSubscriberAction(action, container, hierarchy);
		assertTrue(shouldPrompt == action.isPrompted());			
		return resources;
	}
	
	private IResource[] runSubscriberAction(CVSSubscriberAction action, IContainer container, String[] hierarchy) throws CoreException {
		IResource[] resources = getResources(container, hierarchy);
		SyncInfo[] syncResources = createSyncInfos(resources);
		try {
			action.getRunnable(new SyncInfoSet(syncResources)).run(DEFAULT_MONITOR);
			return resources;
		} catch (InvocationTargetException e) {
			throw CVSException.wrapException(e);
		} catch (InterruptedException e) {
			fail("Operation was interupted");
		}
		return new IResource[0];
	}

	/******************************************************************
	 * Tests
	 ******************************************************************/
	
	/*
	 * Perform a simple test that checks for the different types of incoming changes
	 */
	public void testIncomingChanges() throws IOException, TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a test project
		IProject project = createProject("testIncomingChanges", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Checkout and modify a copy
		IProject copy = checkoutCopy(project, "-copy");
		setContentsAndEnsureModified(copy.getFile("folder1/a.txt"));
		addResources(copy, new String[] { "folder2/folder3/add.txt" }, false);
		deleteResources(copy, new String[] {"folder1/b.txt"}, false);
		commitProject(copy);

		// Get the sync tree for the project
		assertSyncEquals("testIncomingChanges", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.INCOMING | SyncInfo.CHANGE,
				SyncInfo.INCOMING | SyncInfo.DELETION,
				SyncInfo.INCOMING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION});
				
		// Catch up to the incoming changes
		update(
			project, 
			new String[] {
				"folder1/a.txt", 
				"folder1/b.txt", 
				"folder2/", 
				"folder2/folder3/", 
				"folder2/folder3/add.txt"});
		
		// Verify that we are in sync (except for "folder1/b.txt", which was deleted)
		assertSyncEquals("testIncomingChanges", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC});
		
		// Ensure "folder1/b.txt" was deleted
		assertDeleted("testIncomingChanges", project, new String[] {"folder1/b.txt"});
				
		// Verify that the copy equals the original
		assertEquals(project, copy);
	}

	/*
	 * Perform a simple test that checks for the different types of outgoing changes
	 */
	public void testOutgoingChanges() throws TeamException, CoreException {
		// Create a test project (which commits it as well)
		IProject project = createProject("testOutgoingChanges", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Make some modifications
		setContentsAndEnsureModified(project.getFile("folder1/a.txt"));
		addResources(project, new String[] { "folder2/folder3/add.txt" }, false);
		deleteResources(project, new String[] {"folder1/b.txt"}, false);

		// Get the sync tree for the project
		assertSyncEquals("testOutgoingChanges", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.OUTGOING | SyncInfo.CHANGE,
				SyncInfo.OUTGOING | SyncInfo.DELETION,
				SyncInfo.IN_SYNC, /* adding a folder creates it remotely */
				SyncInfo.IN_SYNC, /* adding a folder creates it remotely */
				SyncInfo.OUTGOING | SyncInfo.ADDITION});
				
		// Commit the changes
		commit(project, new String[] {"folder1/a.txt", "folder1/b.txt", "folder2/folder3/add.txt"});
		
		// Ensure we're in sync
		assertSyncEquals("testOutgoingChanges", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC});
				
		// Ensure deleted resource "folder1/b.txt" no longer exists
		assertDeleted("testOutgoingChanges", project, new String[] {"folder1/b.txt"});
	}
	
	public void testOverideAndUpdateSimple() throws CVSException, CoreException, IOException {
		 IProject project = createProject("testOutgoingChangesOverideAndUpdate", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
	
		 // Make some modifications
		 setContentsAndEnsureModified(project.getFile("folder1/a.txt"));
		 addResources(project, new String[] { "folder2/folder3/add.txt" }, false);
		 deleteResources(project, new String[] {"folder1/b.txt"}, false);

		 // Get the sync tree for the project
		 assertSyncEquals("testOutgoingChangesOverwrite", project, 
			 new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			 true, new int[] {
				 SyncInfo.IN_SYNC,
				 SyncInfo.IN_SYNC,
				 SyncInfo.OUTGOING | SyncInfo.CHANGE,
				 SyncInfo.OUTGOING | SyncInfo.DELETION,
				 SyncInfo.IN_SYNC, /* adding a folder creates it remotely */
				 SyncInfo.IN_SYNC, /* adding a folder creates it remotely */
				 SyncInfo.OUTGOING | SyncInfo.ADDITION});
			
		 // Revert the changes
		 overrideAndUpdate(project, new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, true /* should prompt */);
	
		 // Ensure we're in sync
		 assertSyncEquals("testOutgoingChangesOverwrite", project, 
			 new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			 true, new int[] {
				 SyncInfo.IN_SYNC,
				 SyncInfo.IN_SYNC,
				 SyncInfo.IN_SYNC,
				 SyncInfo.IN_SYNC,
				 SyncInfo.IN_SYNC,
				 SyncInfo.IN_SYNC});
			
		 // Ensure deleted resource is re-created
		 assertExistsInWorkspace("testOutgoingChangesOverwrite", project.getFile(new Path("folder1/b.txt")));

		// Test conflicting changes
		IProject copy = checkoutCopy(project, "-copy");
		appendText(copy.getFile("file1.txt"), "prefix" + eol, true);
		setContentsAndEnsureModified(copy.getFile("folder1/a.txt"), "Use a custom string to avoid intermitant errors!");
		commitProject(copy);

		// Make the same modifications to the original (We need to test both M and C!!!)
		appendText(project.getFile("file1.txt"), eol + "postfix", false); // This will test merges (M)
		setContentsAndEnsureModified(project.getFile("folder1/a.txt"));

		// Get the sync tree for the project
		assertSyncEquals("testFileConflict", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, 
			true, new int[] {
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
				SyncInfo.IN_SYNC,
				SyncInfo.CONFLICTING | SyncInfo.CHANGE });
		
		// Revert the changes
		overrideAndUpdate(project, new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, true /* should prompt */);

		assertSyncEquals("testFileConflict", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC});
	}
	
	/*
	 * Perform a simple test that checks for the different types of outgoing changes
	 */
	public void testOverrideOutgoingChanges() throws IOException, TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a test project (which commits it as well)
		IProject project = createProject("testOverrideOutgoingChanges", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		// Checkout a copy for later verification
		IProject original = checkoutCopy(project, "-copy");
		
		// Make some modifications
		setContentsAndEnsureModified(project.getFile("folder1/a.txt"));
		addResources(project, new String[] { "folder2/folder3/add.txt" }, false);
		deleteResources(project, new String[] {"folder1/b.txt"}, false);

		// Get the sync tree for the project
		assertSyncEquals("testOverrideOutgoingChanges", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.OUTGOING | SyncInfo.CHANGE,
				SyncInfo.OUTGOING | SyncInfo.DELETION,
				SyncInfo.IN_SYNC, /* adding a folder creates it remotely */
				SyncInfo.IN_SYNC, /* adding a folder creates it remotely */
				SyncInfo.OUTGOING | SyncInfo.ADDITION});
				
		// Override the changes
		overrideAndUpdate(
			project, 
			new String[] {
				"folder1/a.txt", 
				"folder1/b.txt", 
				"folder2/folder3/add.txt"},
			true /* should prompt */);
		
		// Ensure added resources no longer exist
		assertDeleted("testOverrideOutgoingChanges", project, new String[] {"folder2/", "folder2/folder3/","folder2/folder3/add.txt"});
		
		// Ensure other resources are in sync
		assertSyncEquals("testOverrideOutgoingChanges", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt", "folder2/"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC});
		
		// Verify that the original has reverted to its original contents
		assertEquals(project, original);
	}
	
	/*
	 * Perform a test that checks for outgoing changes that are CVS questionables (no add or remove)
	 */
	public void testOutgoingQuestionables() throws TeamException, CoreException {
		// Create a test project (which commits it as well)
		IProject project = createProject("testIncomingChanges", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Create a new file without adding it to version control
		buildResources(project, new String[] {"folder2/folder3/add.txt"}, false);
		
		// Delete a file without an explicit cvs remove
		// NOTE: This will result in an implicit cvs remove
		IFile file = project.getFile("folder1/b.txt");
		file.delete(true, DEFAULT_MONITOR);

		// Get the sync tree for the project
		assertSyncEquals("testOutgoingQuestionables", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.OUTGOING | SyncInfo.DELETION,
				SyncInfo.OUTGOING | SyncInfo.ADDITION,
				SyncInfo.OUTGOING | SyncInfo.ADDITION,
				SyncInfo.OUTGOING | SyncInfo.ADDITION});
				
		commit(project, new String[] {"folder1/b.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"});
		
		// Ensure we are in sync
		assertSyncEquals("testOutgoingQuestionables", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC});
				
		// Ensure "folder1/b.txt" was deleted
		assertDeleted("testOutgoingQuestionables", project, new String[] {"folder1/b.txt"});
	}
	
	/*
	 * Test simple file conflicts
	 */
	public void testFileConflict() throws IOException, TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a test project (which commits it as well)
		IProject project = createProject("testFileConflict", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Set the contents of file1.txt to ensure proper merging
		setContentsAndEnsureModified(project.getFile("file1.txt"), "Use a custom string" + eol + " to ensure proper merging");
		commitProject(project);
		
		// Checkout a copy and make some modifications
		IProject copy = checkoutCopy(project, "-copy");
		appendText(copy.getFile("file1.txt"), "prefix" + eol, true);
		setContentsAndEnsureModified(copy.getFile("folder1/a.txt"), "Use a custom string to avoid intermitant errors!");
		commitProject(copy);

		// Make the same modifications to the original (We need to test both M and C!!!)
		appendText(project.getFile("file1.txt"), eol + "postfix", false); // This will test merges (M)
		setContentsAndEnsureModified(project.getFile("folder1/a.txt"));

		// Get the sync tree for the project
		assertSyncEquals("testFileConflict", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, 
			true, new int[] {
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
				SyncInfo.IN_SYNC,
				SyncInfo.CONFLICTING | SyncInfo.CHANGE });
		
		// Catch up to the file1.txt conflict using UPDATE
		update(
			project,
			new String[] {"file1.txt"});
								 
		assertSyncEquals("testFileConflict", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, 
			true, new int[] {
				SyncInfo.OUTGOING | SyncInfo.CHANGE,
				SyncInfo.IN_SYNC,
				SyncInfo.CONFLICTING | SyncInfo.CHANGE });
				
		// Release the folder1/a.txt conflict by merging and then committing
		commit(project, new String[] {"file1.txt", "folder1/a.txt"});
		
		assertSyncEquals("testFileConflict", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC });
	}

	/*
	 * Test conflicts involving additions
	 */
	public void testAdditionConflicts() throws TeamException, CoreException, InvocationTargetException, InterruptedException {
		
		// CASE 1: The user adds (using CVS add) a remotely added file
		//     (a) catchup is simply get?
		//     (b) release must do a merge
		// CASE 2: The user adds (but not using cvs add) a remotely added file
		//     (a) catchup is simply get?
		//     (b) release must do a merge
		// CASE 3: The user adds a remotely added then deleted file
		//     catchup is not applicable
		//     release is normal
		
		// Create a test project (which commits it as well) and add an uncommited resource
		IProject project = createProject("testAdditionConflicts", new String[] { "file.txt"});
		addResources(project, new String[] { "add1a.txt", "add1b.txt" }, false);
		addResources(project, new String[] { "add3.txt" }, false);
		buildResources(project, new String[] {"add2a.txt", "add2b.txt"}, false);
		
		// Checkout a copy, add the same resource and commit
		IProject copy = checkoutCopy(project, "-copy");
		addResources(copy, new String[] { "add1a.txt", "add1b.txt", "add2a.txt", "add2b.txt", "add3.txt"}, true);
		deleteResources(copy, new String[] { "add3.txt"}, true);

		// Get the sync tree for the project
		assertSyncEquals("testAdditionConflicts", project, 
			new String[] { "file.txt", "add1a.txt", "add1b.txt", "add2a.txt", "add2b.txt", "add3.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.CONFLICTING | SyncInfo.ADDITION,
				SyncInfo.CONFLICTING | SyncInfo.ADDITION,
				SyncInfo.CONFLICTING | SyncInfo.ADDITION,
				SyncInfo.CONFLICTING | SyncInfo.ADDITION,
				SyncInfo.OUTGOING | SyncInfo.ADDITION });
		
		// Commit conflicting add1b.txt and add2b.txt and outgoing add3.txt
		commit(project, new String[]{"add1b.txt", "add2b.txt", "add3.txt"});

		assertSyncEquals("testAdditionConflicts", project, 
			new String[] { "file.txt", "add1b.txt", "add2b.txt", "add3.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC });
				
		// Catch-up to conflicting cases using UPDATE
		overrideAndUpdate(
			project,
			new String[] {"add1a.txt", "add2a.txt"},
			true /* should prompt */);

		
		assertSyncEquals("testAdditionConflicts", project, 
			new String[] { "add1a.txt", "add2a.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC });
	}
	
	/*
	 * Test conflicts involving deletions
	 */
	public void testDeletionConflicts() throws TeamException, CoreException, InvocationTargetException, InterruptedException {
		
		// CASE 1: The user deletes a remotely modified file
		//    (a) catchup must do an update
		//    (b) release must do a merge
		// CASE 2: The user deletes (and removes) a remotely modified file	
		//    (a) catchup must do an unmanage and update
		//    (b) release must do a merge
		// CASE 3: The user modified a remotely deleted file
		//    (a) catchup must do an unmanage and local delete
		//    (b) release must do a merge
		// CASE 4: The user deletes a remotely deleted file
		//    (a) catchup can update (or unmanage?)
		//    (b) release must unmanage
		// CASE 5: The user deletes (and removes) a remotely deleted file
		//    (a) catchup can update (or unmanage?)
		//    (b) release must unmanage
		
		// Perform the test case for case A first
		
		// Create a test project (which commits it as well) and delete the resource without committing
		IProject project = createProject("testDeletionConflictsA", new String[] { "delete1.txt", "delete2.txt", "delete3.txt", "delete4.txt", "delete5.txt"});
		IFile file = project.getFile("delete1.txt"); // WARNING: This does a "cvs remove"!!!
		file.delete(false, DEFAULT_MONITOR);
		deleteResources(project, new String[] {"delete2.txt"}, false);
		setContentsAndEnsureModified(project.getFile("delete3.txt"));
		file = project.getFile("delete4.txt");
		file.delete(false, DEFAULT_MONITOR);
		deleteResources(project, new String[] {"delete5.txt"}, false);
		
		// Checkout a copy and commit the deletion
		IProject copy = checkoutCopy(project, "-copy");
		setContentsAndEnsureModified(copy.getFile("delete1.txt"));
		setContentsAndEnsureModified(copy.getFile("delete2.txt"));
		deleteResources(copy, new String[] {"delete3.txt", "delete4.txt", "delete5.txt"}, false);
		commitProject(copy);
		
		// Get the sync tree for the project
		assertSyncEquals("testDeletionConflictsA", project, 
			new String[] { "delete1.txt", "delete2.txt", "delete3.txt", "delete4.txt", "delete5.txt"}, 
			true, new int[] {
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC });
				
		// Catch up to remote changes.
		overrideAndUpdate(
			project, 
			new String[] {
				"delete1.txt", 
				"delete2.txt", 
				"delete3.txt", 
				"delete4.txt", 
				"delete5.txt"},
			true /* should be prompted */);
		
		assertSyncEquals("testDeletionConflictsA", project, 
			new String[] { "delete1.txt", "delete2.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC });
		
		assertDeleted("testDeletionConflictsA", project, new String[] {"delete3.txt", "delete4.txt", "delete5.txt"});
		
		// Now redo the test case for case B
		
		// Create a test project (which commits it as well) and delete the resource without committing
		project = createProject("testDeletionConflictsB", new String[] { "delete1.txt", "delete2.txt", "delete3.txt", "delete4.txt", "delete5.txt"});
		file = project.getFile("delete1.txt");
		file.delete(false, DEFAULT_MONITOR);
		deleteResources(project, new String[] {"delete2.txt"}, false);
		setContentsAndEnsureModified(project.getFile("delete3.txt"));
		file = project.getFile("delete4.txt");
		file.delete(false, DEFAULT_MONITOR);
		deleteResources(project, new String[] {"delete5.txt"}, false);
		
		// Checkout a copy and commit the deletion
		copy = checkoutCopy(project, "-copy");
		setContentsAndEnsureModified(copy.getFile("delete1.txt"));
		setContentsAndEnsureModified(copy.getFile("delete2.txt"));
		deleteResources(copy, new String[] {"delete3.txt", "delete4.txt", "delete5.txt"}, false);
		commitProject(copy);

		// Get the sync tree for the project
		assertSyncEquals("testDeletionConflictsB", project, 
			new String[] { "delete1.txt", "delete2.txt", "delete3.txt", "delete4.txt", "delete5.txt"}, 
			true, new int[] {
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC });

		// Release the resources
		commit(project, new String[] { "delete1.txt", "delete2.txt", "delete3.txt", "delete4.txt", "delete5.txt"});
		
		assertSyncEquals("testDeletionConflictsB", project, 
			new String[] { "delete3.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC });
		
		assertDeleted("testDeletionConflictsB", project, new String[] {"delete1.txt", "delete2.txt", "delete4.txt", "delete5.txt"});
	}
	
	/*
	 * Test the creation and sync of an empty local project that has remote contents
	 */
	public void testSyncOnEmptyProject() throws TeamException, CoreException {
		IProject project = createProject(new String[] { "file1.txt", "folder1/file2.txt"});
		
		// Make a new folder and map it to the same project
		IProject newProject = getUniqueTestProject("empty-" + getName());
		FolderSyncInfo info = CVSWorkspaceRoot.getCVSFolderFor(project).getFolderSyncInfo();
		CVSWorkspaceRoot.getCVSFolderFor(newProject).setFolderSyncInfo(info);
		RepositoryProvider.map(newProject, CVSProviderPlugin.getTypeId());
		
		// Assert that all resources are incoming additions
		assertSyncEquals(newProject, 
				new String[] { "file1.txt", "folder1/", "folder1/file2.txt"}, 
				true, new int[] {
					SyncInfo.INCOMING | SyncInfo.ADDITION,
					SyncInfo.INCOMING | SyncInfo.ADDITION,
					SyncInfo.INCOMING | SyncInfo.ADDITION});
	}
	
	/*
	 * Test syncing on a folder that has been deleted from the server
	 */
	public void testSyncOnDeletedFolder() throws TeamException {
	}
	
	/*
	 * Test syncing on a folder that is empty on the server and has been pruned, then added locally
	 */
	public void testSyncOnPrunedFolder() throws TeamException {
	}
	
	/*
	 * Test sync involving pruned directories
	 */
	public void testSyncWithPruning() throws TeamException {
	}
	
	/*
	 * Test a conflict with an incomming foler addition and an unmanaqged lcoal folder
	 */
	public void testFolderConflict()  throws TeamException, CoreException, InvocationTargetException, InterruptedException {
		
		// Create a test project (which commits it as well) and delete the resource without committing
		IProject project = createProject("testFolderConflict", new String[] { "file.txt"});
		
		// Checkout a copy and add some folders
		IProject copy = checkoutCopy(project, "-copy");
		addResources(copy, new String[] {"folder1/file.txt", "folder2/file.txt"}, true);
		
		// Add a folder to the original project (but not using cvs)
		IResource[] resources = buildResources(project, new String[] {"folder1/"});
		((IFolder)resources[0]).create(false, true, DEFAULT_MONITOR);
		
		assertSyncEquals("testFolderConflict", project, 
			new String[] { "file.txt", "folder1/", "folder1/file.txt", "folder2/", "folder2/file.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.CONFLICTING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION});
				
		update(
			project, 
			new String[] {"folder1/"});
	
		assertSyncEquals("testFolderConflict", project, 
			new String[] { "file.txt", "folder1/", "folder1/file.txt", "folder2/", "folder2/file.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.INCOMING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION});
	}
	 
	/*
	 * Test that a deleted file can still be deleted through the team provider
	 */
	public void testOutgoingDeletion() throws TeamException, CoreException {
		
		// Create a test project (which commits it as well)
		IProject project = createProject("testOutgoingDeletion", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Delete a file
		IFile file = project.getFile("folder1/b.txt");
		file.delete(true, DEFAULT_MONITOR); // WARNING: As of 2002/03/05, this is equivalent to a cvs remove

		// Get the sync tree for the project
		assertSyncEquals("testOutgoingDeletion", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.OUTGOING | SyncInfo.DELETION});
				
		// Commit the deletion
		commit(project , new String[] {"folder1/b.txt"});
		
		// Get the sync tree again for the project and ensure others aren't effected
		assertSyncEquals("testOutgoingDeletion", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC});
				
		// Assert that deletion no longer appears in remote tree
		assertDeleted("testOutgoingDeletion", project, new String[] {"folder1/b.txt"});
	}
	
	/*
	 * Test catching up to an incoming addition
	 */
	public void testIncomingAddition() throws TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a test project
		IProject project = createProject("testIncomingAddition", new String[] { "file1.txt", "folder1/", "folder1/a.txt"});
		
		// Checkout and modify a copy
		IProject copy = checkoutCopy(project, "-copy");
		addResources(copy, new String[] { "folder1/add.txt" }, true);

		// Get the sync tree for the project
		assertSyncEquals("testIncomingAddition", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.INCOMING | SyncInfo.ADDITION});
		
		// Catch up to the addition by updating
		update(
			project, 
			new String[] {"folder1/add.txt"});
		
		// Get the sync tree again for the project and ensure the added resource is in sync
		assertSyncEquals("testIncomingAddition", project, 
			new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC});
	}
	
	/* 
	 * Test changes using a granularity of contents
	 */
	 public void testGranularityContents() throws TeamException, CoreException, IOException {
		// Create a test project (which commits it as well)
		IProject project = createProject(new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Checkout a copy and make some modifications
		IProject copy = checkoutCopy(project, "-copy");
		appendText(copy.getFile("file1.txt"), "same text", false);
		setContentsAndEnsureModified(copy.getFile("folder1/a.txt"), " unique text"); // whitespace difference
		commitProject(copy);

		// Make the same modifications to the original
		appendText(project.getFile("file1.txt"), "same text", false);
		setContentsAndEnsureModified(project.getFile("folder1/a.txt"), "unique text"); // whitespace difference
		
		// Get the sync tree for the project
		String oldId = getSubscriber().getCurrentComparisonCriteria().getId();
		try {
			getSubscriber().setCurrentComparisonCriteria(ContentComparisonCriteria.ID_DONTIGNORE_WS);
			assertSyncEquals("testGranularityContents", project, 
				new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, 
				true, new int[] {
					SyncInfo.IN_SYNC,
					SyncInfo.IN_SYNC,
					SyncInfo.CONFLICTING | SyncInfo.CHANGE });
			getSubscriber().setCurrentComparisonCriteria(ContentComparisonCriteria.ID_IGNORE_WS);
			// TODO: Should not need to reset after a comparison criteria change (bug 46678)
			getSyncInfoSource().reset(getSubscriber());
			assertSyncEquals("testGranularityContents", project, 
					new String[] { "file1.txt", "folder1/", "folder1/a.txt"}, 
					true, new int[] {
						SyncInfo.IN_SYNC,
						SyncInfo.IN_SYNC,
						SyncInfo.IN_SYNC });
		} finally {
			getSubscriber().setCurrentComparisonCriteria(oldId);
		}

	 }
	 
	 public void testSyncOnBranch() throws TeamException, CoreException, IOException {
	 	
		// Create a test project and a branch
		IProject project = createProject(new String[] { "file1.txt", "file2.txt", "file3.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		tagProject(project, branch, false /* force */);
		getProvider(project).update(new IResource[] {project}, Command.NO_LOCAL_OPTIONS, branch, true /*createBackups*/, DEFAULT_MONITOR);

		// Checkout and modify a copy
		IProject copy = checkoutCopy(project, branch);
		addResources(copy, new String[] {"addition.txt", "folderAddition/", "folderAddition/new.txt"}, true);
		deleteResources(copy, new String[] {"folder1/b.txt"}, true);
		changeResources(copy, new String[] {"file1.txt", "file2.txt"}, true);
		
		// Sync on the original and assert the result equals the copy
		assertSyncEquals(project, new String[] { 
				"file1.txt", 
				"file2.txt", 
				"file3.txt", 
				"folder1/", 
				"folder1/a.txt", 
				"folder1/b.txt",
				"addition.txt", 
				"folderAddition/", 
				"folderAddition/new.txt"}, 
			true, 
			new int[] {
				SyncInfo.INCOMING | SyncInfo.CHANGE,
				SyncInfo.INCOMING | SyncInfo.CHANGE,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.INCOMING | SyncInfo.DELETION,
				SyncInfo.INCOMING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION,
				SyncInfo.INCOMING | SyncInfo.ADDITION});
	 }
	 
	public void testRenameProject() throws TeamException, CoreException, IOException {
		String[] resourceNames = new String[] { "changed.txt", "folder1/", "folder1/a.txt" };
		int[] inSync = new int[] {SyncInfo.IN_SYNC, SyncInfo.IN_SYNC, SyncInfo.IN_SYNC};
		IProject project = createProject("testRenameProject", new String[] { "changed.txt", "folder1/", "folder1/a.txt" });
		
		assertSyncEquals("sync should be in sync", project, resourceNames, true, inSync);
		IProjectDescription desc = project.getDescription();
		String newName = project.getName() + "_renamed";
		desc.setName(newName);
		project.move(desc, false, null);
		project = ResourcesPlugin.getWorkspace().getRoot().getProject(newName);
		assertTrue(project.exists());
		assertSyncEquals("sync should be in sync", project, resourceNames, true, inSync);
	}
	
	public void testDeleteProject() throws CoreException, IOException, TeamException, InterruptedException {
		String[] resourceNames = new String[] { "deleted.txt", "file1.txt", "folder1/", "folder1/a.txt" };
		int[] inSync = new int[] {SyncInfo.IN_SYNC, SyncInfo.IN_SYNC, SyncInfo.IN_SYNC, SyncInfo.IN_SYNC};
		IProject project = createProject("testDeleteProject", resourceNames);
		assertSyncEquals("sync should be in sync", project, resourceNames, true, inSync);

		// Make some modifications
		setContentsAndEnsureModified(project.getFile("folder1/a.txt"));
		addResources(project, new String[] { "folder2/folder3/add.txt" }, false);
		deleteResources(project, new String[] {"deleted.txt"}, false);
		
		// Get the sync tree for the project
		assertSyncEquals("testOutgoingChanges", project, 
			new String[] { "file1.txt", "folder1/", "deleted.txt", "folder1/a.txt", "folder2/", "folder2/folder3/", "folder2/folder3/add.txt"}, 
			true, new int[] {
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.OUTGOING | SyncInfo.DELETION,
				SyncInfo.OUTGOING | SyncInfo.CHANGE,
				SyncInfo.IN_SYNC, /* adding a folder creates it remotely */
				SyncInfo.IN_SYNC, /* adding a folder creates it remotely */
				SyncInfo.OUTGOING | SyncInfo.ADDITION});
				
		project.delete(true, false, DEFAULT_MONITOR);
		
		assertProjectRemoved(getWorkspaceSubscriber(), project);
	}
	
	public void testFolderDeletion() throws TeamException, CoreException {
		
		IProject project = createProject("testFolderDeletion", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt", "folder1/folder2/file.txt"});
		
		// Delete a folder and ensure that the file is managed but doesn't exist
		// (Special behavior is provider by the CVS move/delete hook but this is not part of CVS core)
		project.getFolder("folder1").delete(false, false, null);
		ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor(project.getFolder("folder1"));
		assertTrue("Deleted folder not in proper state", ! folder.exists() && folder.isManaged() && folder.isCVSFolder());
		
		// The files should show up as outgoing deletions
		assertSyncEquals("testFolderDeletion sync check", project,
						 new String[] { "folder1/", "folder1/a.txt", "folder1/folder2/", "folder1/folder2/file.txt"},
						 true, new int[] { SyncInfo.IN_SYNC,
									  SyncInfo.OUTGOING | SyncInfo.DELETION,
									  SyncInfo.IN_SYNC,
									  SyncInfo.OUTGOING | SyncInfo.DELETION});
		
		// commit folder1/a.txt
		commit(project, new String[] { "folder1/a.txt" });
		
		// Resync and verify that above file is gone and others remain the same
		assertSyncEquals("testFolderDeletion sync check", project,
						 new String[] { "folder1/", "folder1/folder2/", "folder1/folder2/file.txt"},
						 true, new int[] { SyncInfo.IN_SYNC,
									  SyncInfo.IN_SYNC,
									  SyncInfo.OUTGOING | SyncInfo.DELETION});
		assertDeleted("testFolderDeletion", project, new String[] {"folder1/a.txt"});
		
		// Commit folder1/folder2/file.txt
		commit(project, new String[] { "folder1/folder2/file.txt" });
		
		// Resync and verify that all are deleted
		assertDeleted("testFolderDeletion", project, new String[] {"folder1/", "folder1/folder2/", "folder1/folder2/file.txt"});
	}
	/**
	  * There is special handling required when building a sync tree for a tag when there are undiscovered folders
	  * that only contain other folders.
	  */
	 public void testTagRetrievalForFolderWithNoFile() throws TeamException, CoreException {
		IProject project = createProject("testTagRetrievalForFolderWithNoFile", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt"});
		// Checkout, branch and modify a copy
		IProject copy = checkoutCopy(project, "-copy");
		CVSTag version = new CVSTag("v1", CVSTag.BRANCH);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		getProvider(copy).makeBranch(new IResource[] {copy}, version, branch, true, DEFAULT_MONITOR);
		addResources(copy, new String[] {"folder2/folder3/a.txt"}, true);
		
		// Fetch the tree corresponding to the branch using the original as the base.
		// XXX This will fail for CVSNT with directory pruning on
		refresh(getSubscriber(), project);
	 }
	 
	 public void testIgnoredResource() throws CoreException, TeamException {
		// Create a test project (which commits it as well)
		IProject project = createProject("testIgnoredResource", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Create a new file without adding it to version control
		buildResources(project, new String[] {"ignored.txt"}, false);
	 	
		// Get the sync tree for the project
		assertSyncEquals("testIgnoredResource", project, 
			new String[] { "ignored.txt"}, 
			true, new int[] {SyncInfo.OUTGOING | SyncInfo.ADDITION});
			
		IFile ignores = project.getFile(".cvsignore");
		ignores.create(new ByteArrayInputStream("ignored.txt".getBytes()), false, DEFAULT_MONITOR);
		addResources(new IResource[] {ignores});
		
		assertSyncEquals("testIgnoredResource", project, 
			new String[] { "ignored.txt", ".cvsignore"}, 
			true, new int[] {
				SyncInfo.IN_SYNC, 
				SyncInfo.OUTGOING | SyncInfo.ADDITION});
	 }

	public void testRenameUnshared() throws CoreException, TeamException {
	   // Create a test project (which commits it as well)
	   IProject project = createProject("testRenameUnshared", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
	   // Create a new file without adding it to version control
	   buildResources(project, new String[] {"oldName.txt"}, false);
	 	
	   // Get the sync tree for the project
	   assertSyncEquals("testRenameUnshared", project, 
		   new String[] { "oldName.txt" }, 
		   true, new int[] {SyncInfo.OUTGOING | SyncInfo.ADDITION});
			
	   IFile rename = project.getFile("oldName.txt");
	   rename.move(new Path("newName.txt"), false, false, DEFAULT_MONITOR);
	
	   assertDeleted("testRenameUnshared", project, new String[] {"oldName.txt"});

	   assertSyncEquals("testRenameUnshared", project, 
		   new String[] { "newName.txt"}, 
		   true, new int[] {
			   SyncInfo.OUTGOING | SyncInfo.ADDITION});
	}
	
	public void testOutgoingEmptyFolder() throws CoreException, TeamException {
		// Create a test project (which commits it as well)
		IProject project = createProject("testOutgoingEmptyFolder", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});

		// Create an empty folder without adding it to version control
		buildResources(project, new String[] {"folder2/"}, false);
		
		assertSyncEquals("testOutgoingEmptyFolder", project, 
			new String[] { "folder2/" }, 
			true, new int[] {
				SyncInfo.OUTGOING | SyncInfo.ADDITION});
				
		commit(project, new String[] { "folder2" });
		
		assertSyncEquals("testOutgoingEmptyFolder", project, 
			new String[] { "folder2/" }, 
			true, new int[] {
				SyncInfo.IN_SYNC});
				
		// Ensure that the folder still exists (i.e. wasn't pruned)
		assertTrue("Folder should still exist", project.getFolder("folder2").exists());
	}
	
	public void testDisconnectingProject() throws CoreException, TeamException, InterruptedException {
		// Create a test project (which commits it as well)
		IProject project = createProject("testDisconnect", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		CVSTeamProvider provider = (CVSTeamProvider)RepositoryProvider.getProvider(project);
		RepositoryProvider.unmap(project);
		assertProjectRemoved(getWorkspaceSubscriber(), project);
	}
	
	/*
	 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=40221
	 */
	public void testConflictingFolderDeletion() throws TeamException, CoreException {
		// Create a test project (which commits it as well)
		IProject project = createProject("testConflictingFolderDeletion", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		// Checkout a copy
		IProject copy = checkoutCopy(project, "-copy");
		
		// Delete a folder in both projects and checkin one of the deletions
		deleteResources(project, new String[] { "folder1/" }, false /* checkin */);
		deleteResources(copy, new String[] { "folder1/" }, true /* checkin */);
		
		// The files should show up as outgoing deletions
		assertSyncEquals("testConflictingFolderDeletion sync check", project,
			 new String[] { "folder1/", "folder1/a.txt", "folder1/b.txt"},
			 true, new int[] { 
			 	SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC, /* conflicting deletions are handled automatically */
				SyncInfo.IN_SYNC});
	}
	
	public void testOverrideAndUpdateOutgoingAddition() throws TeamException, CoreException {
		// Create a test project (which commits it as well)
		IProject project = createProject(new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		IFolder newFolder = project.getFolder("newFolder");
		newFolder.create(false, true, null);
		buildResources(newFolder, new String[] {"newFile"}, false);
		overrideAndUpdate(project, new String[] {"newFolder", "newFolder/newFile"}, true);
		assertDoesNotExistInFileSystem(newFolder);
	}
	
	public void testProjectClose() throws TeamException, CoreException {
		IProject project = createProject(new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		
		setContentsAndEnsureModified(project.getFile("file1.txt"));
		assertSyncEquals("testProjectClose sync check", project,
				new String[] { "file1.txt"},
				true, new int[] { 
							  SyncInfo.OUTGOING | SyncInfo.CHANGE,
					});
		
		project.close(null);
		assertProjectRemoved(getWorkspaceSubscriber(), project);
	}
	
	public void testUpdateBinaryConflict() throws TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a shared project with a binary file
		IProject project = createProject(new String[] { "binary.gif"});
		assertIsBinary(project.getFile("binary.gif"));
		
		// Checkout a copy, modify the binary file and commit
		IProject copy = checkoutCopy(project, "-copy");
		assertIsBinary(copy.getFile("binary.gif"));
		setContentsAndEnsureModified(copy.getFile("binary.gif"));
		commitProject(copy);
		
		// Modify the same binary file and ensure sync is correct
		setContentsAndEnsureModified(project.getFile("binary.gif"));
		assertSyncEquals("testProjectClose sync check", project,
			new String[] { "binary.gif"},
			true, new int[] { 
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
		});
		
		// Perform an update and ensure the binary conflict is skipped
		update(project, new String[] { "binary.gif"});
		assertSyncEquals("testProjectClose sync check", project,
			new String[] { "binary.gif"},
			true, new int[] { 
				SyncInfo.CONFLICTING | SyncInfo.CHANGE,
		});
	}

	private void assertIsBinary(IFile local) throws CVSException {
		ICVSFile file = CVSWorkspaceRoot.getCVSFileFor((IFile)local);
		byte[] syncBytes = file.getSyncBytes();
		if (syncBytes != null) {
			assertTrue(ResourceSyncInfo.isBinary(syncBytes));
		}
	}
	
	public void testNestedMarkAsMerged() throws CoreException, InvocationTargetException, InterruptedException {
		// Create a project and checkout a copy
		IProject project = createProject(new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
		IProject copy = checkoutCopy(project, "-copy");
		// Add the same resources to both projects to create conflicting additions
		buildResources(project, new String[] { "folder2/", "folder2/file.txt", "folder2/file2.txt"}, false);
		addResources(copy, new String[] { "folder2/", "folder2/file.txt", "folder2/file2.txt"}, true);
		assertSyncEquals("testNestedMarkAsMerged sync check", project,
				new String[] { "folder2/", "folder2/file.txt", "folder2/file.txt"},
				true, new int[] { 
					SyncInfo.CONFLICTING | SyncInfo.ADDITION,
					SyncInfo.CONFLICTING | SyncInfo.ADDITION,
					SyncInfo.CONFLICTING | SyncInfo.ADDITION
				});
		markAsMerged(getSubscriber(), project, new String[] {"folder2/file.txt"});
		assertSyncEquals("testNestedMarkAsMerged sync check", project,
				new String[] { "folder2/", "folder2/file.txt", "folder2/file2.txt"},
				true, new int[] { 
					SyncInfo.IN_SYNC,
					SyncInfo.OUTGOING | SyncInfo.CHANGE,
					SyncInfo.CONFLICTING | SyncInfo.ADDITION
		});
	}
	
	public void testMarkAsMergedOnBinaryFile() throws TeamException, CoreException, InvocationTargetException, InterruptedException {
		// Create a project and checkout a copy
		IProject project = createProject(new String[] { "file1.txt"});
		IProject copy = checkoutCopy(project, "-copy");
		// Add the same binary file to both projects to create a conflicting addition
		buildResources(project, new String[] {"binary.gif"}, false);
		addResources(copy, new String[] {"binary.gif"}, true);
		assertIsBinary(copy.getFile("binary.gif"));
		assertSyncEquals("testMarkAsMergedOnBinaryFile sync check", project,
				new String[] {"binary.gif"},
				true, new int[] { 
						SyncInfo.CONFLICTING | SyncInfo.ADDITION
		});
		markAsMerged(getSubscriber(), project, new String[] {"binary.gif"});
		assertSyncEquals("testMarkAsMergedOnBinaryFile sync check", project,
				new String[] {"binary.gif"},
				true, new int[] { 
				SyncInfo.OUTGOING | SyncInfo.CHANGE
		});
		assertIsBinary(project.getFile("binary.gif"));
		// Unmanage the file and do it again
		// This tests the case were the contents are already cached locally
		CVSWorkspaceRoot.getCVSFileFor(project.getFile("binary.gif")).unmanage(DEFAULT_MONITOR);
		assertSyncEquals("testMarkAsMergedOnBinaryFile sync check", project,
				new String[] {"binary.gif"},
				true, new int[] { 
				SyncInfo.CONFLICTING | SyncInfo.ADDITION
		});
		markAsMerged(getSubscriber(), project, new String[] {"binary.gif"});
		assertSyncEquals("testMarkAsMergedOnBinaryFile sync check", project,
				new String[] {"binary.gif"},
				true, new int[] { 
				SyncInfo.OUTGOING | SyncInfo.CHANGE
		});
		assertIsBinary(project.getFile("binary.gif"));
	}
}
