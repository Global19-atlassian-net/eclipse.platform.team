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
package org.eclipse.team.tests.ccvs.core.provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.tests.ccvs.core.CVSTestSetup;
import org.eclipse.team.tests.ccvs.core.EclipseTest;

/**
 * Test isModified on file, folders and projects.
 */
public class IsModifiedTests extends EclipseTest {

	/**
	 * Constructor for CVSProviderTest
	 */
	public IsModifiedTests() {
		super();
	}

	/**
	 * Constructor for CVSProviderTest
	 */
	public IsModifiedTests(String name) {
		super(name);
	}

	public static Test suite() {
		TestSuite suite = new TestSuite(IsModifiedTests.class);
		//return new CVSTestSetup(suite);
		return new CVSTestSetup(new IsModifiedTests("testFolderDeletions"));
	}

	/*
	 * Assert that the modification state of the provided resources matches the
	 * provided state and that the other are the opposite state.
	 */
	private void assertModificationState(IContainer container, String[] resources, final boolean modified) throws CVSException {
		final ICVSFolder rootFolder = CVSWorkspaceRoot.getCVSFolderFor(container);
		final List resourceList = new ArrayList();
		if (resources != null) {
			for (int i = 0; i < resources.length; i++) {
				String string = resources[i];
				resourceList.add(new Path(string));
			}
		}
		rootFolder.accept(new ICVSResourceVisitor() {
			public void visitFile(ICVSFile file) throws CVSException {
				assertModificationState(file);
			}
			public void visitFolder(ICVSFolder folder) throws CVSException {
				// find the deepest mistake
				folder.acceptChildren(this);
				assertModificationState(folder);	
			}
			public void assertModificationState(ICVSResource resource) throws CVSException {
				IPath relativePath = new Path(resource.getRelativePath(rootFolder));
				if (modified) {
					assertTrue(resource.getIResource().getFullPath().toString() 
						+ " should be modified but isn't",
						resource.isModified() == resourceList.contains(relativePath));
				} else {
					assertTrue(resource.getIResource().getFullPath().toString()
						+ " should not be modified but it is",
						!resource.isModified() != resourceList.contains(relativePath));
				}
			}
		});
	}
	
	/**
	 * Assert that a project (and all it's children) is clean after it is
	 * created and shared.
	 * 
	 * @see org.eclipse.team.tests.ccvs.core.EclipseTest#createProject(java.lang.String, java.lang.String)
	 */
	protected IProject createProject(String prefix, String[] resources) throws CoreException, TeamException {
		IProject project = super.createProject(prefix, resources);
		assertModificationState(project, null, false);
		return project;
	}


	public void testFileModifications() throws CoreException, TeamException {
		IProject project = createProject("testFileModifications", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
		// change two files, commit one and revert the other
		setContentsAndEnsureModified(project.getFile("changed.txt"));
		assertModificationState(project, new String[] {".", "changed.txt"}, true);
		setContentsAndEnsureModified(project.getFile(new Path("folder1/a.txt")));
		assertModificationState(project, new String[] {".", "changed.txt", "folder1/", "folder1/a.txt"}, true);
		commitResources(project, new String[] {"folder1/a.txt"});
		assertModificationState(project, new String[] {".", "changed.txt"}, true);
		getProvider(project).get(new IResource[] {project.getFile("changed.txt")}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
	}

	public void testFileDeletions() throws CoreException, TeamException {
		IProject project = createProject("testFileDeletions", new String[] { "changed.txt", "folder1/", "folder1/deleted.txt", "folder1/a.txt" });
		// delete and commit a file
		project.getFile("folder1/deleted.txt").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/deleted.txt"}, true);
		commitResources(project, new String[] {"folder1/deleted.txt"});
		assertModificationState(project, null, false);
		// modify, delete and revert a file
		setContentsAndEnsureModified(project.getFile("changed.txt"));
		assertModificationState(project, new String[] {".", "changed.txt"}, true);
		project.getFile("changed.txt").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "changed.txt"}, true);
		getProvider(project).get(new IResource[] {project.getFile("changed.txt")}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		// modify, delete and commit a file
		setContentsAndEnsureModified(project.getFile("changed.txt"));
		assertModificationState(project, new String[] {".", "changed.txt"}, true);
		project.getFile("changed.txt").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "changed.txt"}, true);
		commitResources(project, new String[] {"changed.txt"});
		assertModificationState(project, null, false);
		// delete, recreate and commit
		project.getFile("folder1/a.txt").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/a.txt"}, true);
		IResource[] addedResources = buildResources(project, new String[] {"folder1/a.txt"}, false);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/a.txt"}, true);
		commitResources(project, new String[] {"folder1/a.txt"});
		assertModificationState(project, null, false);
		
	}
	
	public void testFileAdditions() throws CoreException, TeamException {
		IProject project = createProject("testFileAdditions", new String[] { "changed.txt", "folder1/", "folder1/deleted.txt", "folder1/a.txt" });
		// create, add and commit a file
		IResource[] addedResources = buildResources(project, new String[] {"folder1/added.txt"}, false);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/added.txt"}, true);
		getProvider(project).add(addedResources, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/added.txt"}, true);
		commitResources(project, new String[] {"folder1/added.txt"});
		assertModificationState(project, null, false);
		// create, add and delete a file
		addResources(project, new String[] {"added.txt"}, false);
		assertModificationState(project, new String[] {".", "added.txt"}, true);
		project.getFile("added.txt").delete(false, false, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		// create and delete a file
		addedResources = buildResources(project, new String[] {"folder1/another.txt"}, false);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/another.txt"}, true);
		project.getFile("folder1/another.txt").delete(false, false, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		// create and ignore a file
		addedResources = buildResources(project, new String[] {"ignored.txt"}, false);
		assertModificationState(project, new String[] {".", "ignored.txt"}, true);
		project.getFile(".cvsignore").create(new ByteArrayInputStream("ignored.txt".getBytes()), false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", ".cvsignore"}, true);
		getProvider(project).add(new IResource[] {project.getFile(".cvsignore")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", ".cvsignore"}, true);
		commitResources(project, new String[] {".cvsignore"});
		assertModificationState(project, null, false);
		// delete the .cvsignore to see the modification come back
		project.getFile(".cvsignore").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "ignored.txt", ".cvsignore"}, true);
		commitResources(project, new String[] {".cvsignore"});
		assertModificationState(project, new String[] {".", "ignored.txt"}, true);
		// re-add the ignore and then delete the ignored
		project.getFile(".cvsignore").create(new ByteArrayInputStream("ignored.txt".getBytes()), false, DEFAULT_MONITOR);
		getProvider(project).add(new IResource[] {project.getFile(".cvsignore")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", ".cvsignore"}, true);
		commitResources(project, new String[] {".cvsignore"});
		assertModificationState(project, null, false);
		project.getFile("ignored.txt").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		// add the ignored file to version control
		buildResources(project, new String[] {"ignored.txt"}, false);
		assertModificationState(project, null, false);
		getProvider(project).add(new IResource[] {project.getFile("ignored.txt")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "ignored.txt"}, true);
		commitProject(project);
		assertModificationState(project, null, false);
	}
	
	public void testFileMoveAndCopy() throws CoreException, TeamException {
		IProject project = createProject("testFileMoveAndCopy", new String[] { "changed.txt", "folder1/", "folder2/", "folder1/a.txt" });
		// move a file
		project.getFile("folder1/a.txt").move(project.getFile("folder2/a.txt").getFullPath(), false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/a.txt", "folder2/", "folder2/a.txt"}, true);
		// commit the source
		commitResources(project, new String[] {"folder1/a.txt"});
		assertModificationState(project, new String[] {".", "folder2/", "folder2/a.txt"}, true);
		// copy the destination back to the source
		project.getFolder("folder1").create(false, true, DEFAULT_MONITOR);
		project.getFile("folder2/a.txt").copy(project.getFile("folder1/a.txt").getFullPath(), false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/a.txt", "folder2/", "folder2/a.txt"}, true);
		// add the source, delete the destination and commit
		getProvider(project).add(new IResource[] {project.getFile("folder1/a.txt")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		project.getFile("folder2/a.txt").delete(false, DEFAULT_MONITOR);
		commitProject(project);
		assertModificationState(project, null, false);
		// Do the above without committing the source
		project.getFile("folder1/a.txt").move(project.getFile("folder2/a.txt").getFullPath(), false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/a.txt", "folder2/", "folder2/a.txt"}, true);
		// copy the destination back to the source
		project.getFile("folder2/a.txt").copy(project.getFile("folder1/a.txt").getFullPath(), false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder2/", "folder2/a.txt"}, true);
		getProvider(project).add(new IResource[] {project.getFile("folder2/a.txt")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		commitProject(project);
		assertModificationState(project, null, false);
	}
	
	public void testFolderAdditions() throws CoreException, TeamException {
		IProject project = createProject("testFileAdditions", new String[] { "changed.txt", "folder1/", "folder1/deleted.txt", "folder1/a.txt" });
		// create a folder
		project.getFolder("folder1/folder2").create(false, true, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/folder2/"}, true);
		getProvider(project).add(new IResource[] {project.getFolder("folder1/folder2/")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		
		// create a folder
		project.getFolder("folder1/folder2/folder3").create(false, true, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/folder2/", "folder1/folder2/folder3"}, true);
		// add some children
		buildResources(project, new String[] {
			"folder1/folder2/folder3/add1.txt", 
			"folder1/folder2/folder3/add2.txt",
			"folder1/folder2/folder3/folder4/",
			"folder1/folder2/folder3/folder5/"}, false);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/folder2/", "folder1/folder2/folder3",
			"folder1/folder2/folder3/add1.txt",
			"folder1/folder2/folder3/add2.txt",
			"folder1/folder2/folder3/folder4/",
			"folder1/folder2/folder3/folder5/"}, true);
		// delete some children
		project.getFile("folder1/folder2/folder3/add2.txt").delete(false, DEFAULT_MONITOR);
		project.getFolder("folder1/folder2/folder3/folder5/").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/folder2/", "folder1/folder2/folder3",
			"folder1/folder2/folder3/add1.txt",
			"folder1/folder2/folder3/folder4/"}, true);
		// add to version control
		getProvider(project).add(new IResource[] {
			project.getFile("folder1/folder2/folder3/add1.txt"),
			project.getFolder("folder1/folder2/folder3/folder4/")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/folder2/", "folder1/folder2/folder3",
			"folder1/folder2/folder3/add1.txt"}, true);
		// commit
		commitResources(project, new String[] {"folder1/folder2/folder3/add1.txt"});
		assertModificationState(project, null, false);
		
		// create a folder
		project.getFolder("folder1/ignored").create(false, true, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/ignored/"}, true);
		// add some files
		buildResources(project, new String[] {"folder1/ignored/file.txt"}, false);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/ignored/", "folder1/ignored/file.txt"}, true);
		// ignore the folder
		project.getFile("folder1/.cvsignore").create(new ByteArrayInputStream("ignored".getBytes()), false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/.cvsignore"}, true);
		// XXX How can we ensure that the file info was flushed?
		getProvider(project).add(new IResource[] {project.getFile("folder1/.cvsignore")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/.cvsignore"}, true);
		commitResources(project, new String[] {"folder1/.cvsignore"});
		assertModificationState(project, null, false);
		// delete the .cvsignore to see the modification come back
		project.getFile("folder1/.cvsignore").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/.cvsignore", "folder1/ignored/", "folder1/ignored/file.txt"}, true);
		commitResources(project, new String[] {"folder1/.cvsignore"});
		assertModificationState(project, new String[] {".", "folder1/", "folder1/ignored/", "folder1/ignored/file.txt"}, true);
		// re-add the .cvsignore and then delete the ignored
		project.getFile("folder1/.cvsignore").create(new ByteArrayInputStream("ignored".getBytes()), false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/.cvsignore"}, true);
		getProvider(project).add(new IResource[] {project.getFile("folder1/.cvsignore")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		commitResources(project, new String[] {"folder1/.cvsignore"});
		assertModificationState(project, null, false);
		project.getFolder("folder/ignored").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		// add the ignored file to version control
		buildResources(project, new String[] {"folder1/ignored/file.txt"}, false);
		assertModificationState(project, null, false);
		getProvider(project).add(new IResource[] {project.getFile("folder1/ignored/file.txt")}, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/ignored/", "folder1/ignored/file.txt"}, true);
		commitProject(project);
		assertModificationState(project, null, false);
	}
	
	public void testFolderDeletions() throws CoreException, TeamException {
		IProject project = createProject("testFileAdditions", new String[] { "changed.txt", "folder1/", "folder1/deleted.txt", "folder1/a.txt" });
		// create a folder
		project.getFolder("folder1/folder2").create(false, true, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/folder2/"}, true);
		// delete the folder
		project.getFolder("folder1/folder2").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		
		// create a folder
		project.getFolder("folder1/folder2").create(false, true, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/folder2/"}, true);
		// add some children
		buildResources(project, new String[] {"folder1/folder2/file.txt"}, false);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/folder2/", "folder1/folder2/file.txt"}, true);
		// delete the folder
		project.getFolder("folder1/folder2").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		
		// delete a shared folder with files
		project.getFolder("folder1").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/deleted.txt", "folder1/a.txt"}, true);
		// recreate folders and files
		project.getFolder("folder1").create(false, true, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/deleted.txt", "folder1/a.txt"}, true);
		getProvider(project).get(new IResource[] {project.getFile("folder1/deleted.txt"), project.getFile("folder1/a.txt")}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		assertModificationState(project, null, false);
		
		// delete a shared folder with files
		project.getFolder("folder1").delete(false, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "folder1/", "folder1/deleted.txt", "folder1/a.txt"}, true);
		// commit file deletions
		commitProject(project);
		assertModificationState(project, null, false);
	}
	
	public void testFolderMoveAndCopy() {
	}
	
	public void testUpdate() throws TeamException, CoreException, IOException {
		// Create a test project, import it into cvs and check it out
		IProject project = createProject("testUpdate", new String[] { "changed.txt", "merged.txt", "deleted.txt", "folder1/", "folder1/a.txt" });

		// Check the project out under a different name
		IProject copy = checkoutCopy(project, "-copy");
		assertModificationState(copy, null, false);

		// Perform some operations on the copy and commit
		addResources(copy, new String[] { "added.txt", "folder2/", "folder2/added.txt" }, false);
		setContentsAndEnsureModified(copy.getFile("changed.txt"));
		setContentsAndEnsureModified(copy.getFile("merged.txt"));
		getProvider(copy).delete(new IResource[] {copy.getFile("deleted.txt")}, DEFAULT_MONITOR);
		assertModificationState(copy, new String[] {".", "added.txt", "folder2/", "folder2/added.txt", "changed.txt", "merged.txt", "deleted.txt"}, true);
		getProvider(copy).checkin(new IResource[] {copy}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		assertModificationState(copy, null, false);
		
		// update the project and check status
		setContentsAndEnsureModified(project.getFile("merged.txt"));
		getProvider(project).update(new IResource[] {project}, Command.NO_LOCAL_OPTIONS, null, true /*createBackups*/, DEFAULT_MONITOR);
		assertModificationState(project, new String[] {".", "merged.txt"}, true);
		// can't commit because of merge
		// commitProject(project);
		// assertModificationState(project, null, false);
	}
	
	public void testExternalModifications() {
	}
}

