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
package org.eclipse.team.tests.ccvs.core.provider;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.Team;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Command.KSubstOption;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.tests.ccvs.core.CVSTestSetup;
import org.eclipse.team.tests.ccvs.core.EclipseTest;

/*
 * This class tests both the CVSProvider and the CVSTeamProvider
 */
public class CVSProviderTest extends EclipseTest {

	/**
	 * Constructor for CVSProviderTest
	 */
	public CVSProviderTest() {
		super();
	}

	/**
	 * Constructor for CVSProviderTest
	 */
	public CVSProviderTest(String name) {
		super(name);
	}

	public static Test suite() {
		String testName = System.getProperty("eclipse.cvs.testName");
		if (testName == null) {
			TestSuite suite = new TestSuite(CVSProviderTest.class);
			return new CVSTestSetup(suite);
		} else {
			return new CVSTestSetup(new CVSProviderTest(testName));
		}
	}
	
	public void testAdd() throws TeamException, CoreException {
		
		// Test add with cvsignores
		/*
		IProject project = createProject("testAdd", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
		IFile file = project.getFile(".cvsignore");
		file.create(new ByteArrayInputStream("ignored.txt".getBytes()), false, null);
		file = project.getFile("ignored.txt");
		file.create(new ByteArrayInputStream("some text".getBytes()), false, null);
		file = project.getFile("notignored.txt");
		file.create(new ByteArrayInputStream("some more text".getBytes()), false, null);
		file = project.getFile("folder1/.cvsignore");
		file.create(new ByteArrayInputStream("ignored.txt".getBytes()), false, null);
		file = project.getFile("folder1/ignored.txt");
		file.create(new ByteArrayInputStream("some text".getBytes()), false, null);
		file = project.getFile("folder1/notignored.txt");
		file.create(new ByteArrayInputStream("some more text".getBytes()), false, null);
		
		getProvider(project).add(new IResource[] {project}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		
		assertTrue( ! CVSWorkspaceRoot.getCVSResourceFor(project.getFile("ignored.txt")).isManaged());
		assertTrue( ! CVSWorkspaceRoot.getCVSResourceFor(project.getFile("folder1/ignored.txt")).isManaged());
		
		assertTrue(CVSWorkspaceRoot.getCVSResourceFor(project.getFile("notignored.txt")).isManaged());
		assertTrue(CVSWorkspaceRoot.getCVSResourceFor(project.getFile("folder1/notignored.txt")).isManaged());
		assertTrue(CVSWorkspaceRoot.getCVSResourceFor(project.getFile(".cvsignore")).isManaged());
		assertTrue(CVSWorkspaceRoot.getCVSResourceFor(project.getFile("folder1/.cvsignore")).isManaged());
		*/
	}
	
	public void testDeleteHandling() throws TeamException, CoreException {
		
		IProject project = createProject("testDeleteHandling", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
		
		// Delete a file and ensure that it is an outgoing deletion
		project.getFile("deleted.txt").delete(false, false, null);
		ICVSFile file = CVSWorkspaceRoot.getCVSFileFor(project.getFile("deleted.txt"));
		assertTrue("File is not outgoing deletion", file.getSyncInfo().isDeleted());
		
		// Delete a folder and ensure that the file is managed but doesn't exist
		// (Special behavior is provider by the CVS move/delete hook but this is not part of CVS core)
		project.getFolder("folder1").delete(false, false, null);
		ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor(project.getFolder("folder1"));
		assertTrue("Deleted folder not in proper state", ! folder.exists() && folder.isManaged());
	}
	
	public void testCheckin() throws TeamException, CoreException, IOException {
		IProject project = createProject("testCheckin", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
		
		// Perform some operations on the project
		IResource[] newResources = buildResources(project, new String[] { "added.txt", "folder2/", "folder2/added.txt" }, false);
		setContentsAndEnsureModified(project.getFile("changed.txt"));
		getProvider(project).add(newResources, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		getProvider(project).delete(new IResource[] {project.getFile("deleted.txt")}, DEFAULT_MONITOR);
		assertIsModified("testDeepCheckin: ", newResources);
		assertIsModified("testDeepCheckin: ", new IResource[] {project.getFile("deleted.txt"), project.getFile("changed.txt")});
		getProvider(project).checkin(new IResource[] {project}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		assertLocalStateEqualsRemote(project);
	}
	
	public void testMoveHandling() throws TeamException, CoreException {
		IProject project = createProject("testMoveHandling", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
		
		// Move a file and ensure that it is an outgoing deletion at the source and unmanaged at the destination
		project.getFile("deleted.txt").move(new Path("moved.txt"), false, false, null);
		ICVSFile file = CVSWorkspaceRoot.getCVSFileFor(project.getFile("deleted.txt"));
		assertTrue("Source is not outgoing deletion", file.getSyncInfo().isDeleted());
		file = CVSWorkspaceRoot.getCVSFileFor(project.getFile("moved.txt"));
		assertTrue("Destination not in proper state", ! file.isManaged());
		
		// Move a folder and ensure the source is deleted
		project.getFolder("folder1").move(new Path("moved"), false, false, null);
		ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor(project.getFolder("folder1"));
		assertTrue("Deleted folder not in proper state", ! folder.exists() && folder.isManaged());
		folder = CVSWorkspaceRoot.getCVSFolderFor(project.getFolder("moved"));
		assertTrue("Moved folder should not be managed", ! folder.isManaged());
		assertTrue("Moved folder should not be a CVS folder", ! folder.isCVSFolder());
	}
	
	public void testUpdate() throws TeamException, CoreException, IOException {
		// Create a test project, import it into cvs and check it out
		IProject project = createProject("testUpdate", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });

		// Check the project out under a different name
		IProject copy = checkoutCopy(project, "-copy");
		
		// Perform some operations on the copy
		addResources(copy, new String[] { "added.txt", "folder2/", "folder2/added.txt" }, false);
		setContentsAndEnsureModified(copy.getFile("changed.txt"));
		getProvider(copy).delete(new IResource[] {copy.getFile("deleted.txt")}, DEFAULT_MONITOR);
		
		// Commit the copy and update the project
		getProvider(copy).checkin(new IResource[] {copy}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		getProvider(project).update(new IResource[] {project}, Command.NO_LOCAL_OPTIONS, null, true /*createBackups*/, DEFAULT_MONITOR);
		assertEquals(project, copy);
	}
	
	public void testVersionTag() throws TeamException, CoreException, IOException {
		
		// Create a test project, import it into cvs and check it out
		IProject project = createProject("testVersionTag", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
		
		// Perform some operations on the copy and commit
		IProject copy = checkoutCopy(project, "-copy");
		addResources(copy, new String[] { "added.txt", "folder2/", "folder2/added.txt" }, false);
		changeResources(copy, new String[] {"changed.txt"}, false);
		deleteResources(copy, new String[] {"deleted.txt"}, false);
		commitResources(copy, true);
		
		// Tag the original, checkout the tag and compare with original
		CVSTag v1Tag = new CVSTag("v1", CVSTag.VERSION);
		tagProject(project, v1Tag);
		IProject v1 = checkoutCopy(project, v1Tag);
		assertEquals(project, v1);
		
		// Update original to HEAD and compare with copy including tags
		updateProject(project, null, false);
		assertEquals(project, copy, false, true);
		
		// Update copy to v1 and compare with the copy (including tag)
		updateProject(copy, v1Tag, false);
		assertEquals(copy, v1, false, true);
		
		// Update copy back to HEAD and compare with project (including tag)
		updateProject(copy, CVSTag.DEFAULT, false);
		assertEquals(project, copy, false, true);
	}
	
	public void testMakeBranch() throws TeamException, CoreException, IOException {
		// Create a test project
		IProject project = createProject("testMakeBranch", new String[] { "file1.txt", "file2.txt", "file3.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});

		// Make some local modifications including "cvs adds" and "cvs removes"
		addResources(project, new String[] {"folder1/c.txt"}, false);
		deleteResources(project, new String[] {"folder1/b.txt"}, false);
		changeResources(project, new String[] {"file2.txt"}, false);
		
		// Make the branch including a pre-version
		CVSTag version = new CVSTag("v1", CVSTag.BRANCH);
		CVSTag branch = new CVSTag("branch1", CVSTag.BRANCH);
		getProvider(project).makeBranch(new IResource[] {project}, version, branch, true, DEFAULT_MONITOR);

		// Checkout a copy from the branch and version and compare
		IProject branchCopy = checkoutCopy(project, branch);
		IProject versionCopy = checkoutCopy(project, branch);
		assertEquals(branchCopy, versionCopy, true, false);
		
		// Commit the project, update the branch and compare
		commitProject(project);
		updateProject(branchCopy, null, false);
		assertEquals(branchCopy, project, false, true);
	}
	 
	public void testPruning() throws TeamException, CoreException, IOException {
		// Create a project with empty folders
		CVSProviderPlugin.getPlugin().setPruneEmptyDirectories(false);
		IProject project = createProject("testPruning", new String[] { "file.txt", "folder1/", "folder2/folder3/" });

		// Disable pruning, checkout a copy and ensure original and copy are the same
		IProject copy = checkoutCopy(project, "-copy");
		assertEquals(project, copy); 

		// Enable pruning, update copy and ensure emtpy folders are gone
		CVSProviderPlugin.getPlugin().setPruneEmptyDirectories(true);
		updateProject(copy, null, false);
		assertDoesNotExistInFileSystem(new IResource[] {copy.getFolder("folder1"), copy.getFolder("folder2"), copy.getFolder("folder2/folder3")});
		
		// Checkout another copy and ensure that the two copies are the same (with pruning enabled)
		IProject copy2 = checkoutCopy(project, "-copy2");
		assertEquals(copy, copy2); 
		
		// Disable pruning, update copy and ensure directories come back
		CVSProviderPlugin.getPlugin().setPruneEmptyDirectories(false);
		updateProject(copy, null, false);
		assertEquals(project, copy);
		
		// Enable pruning again since it's the default
		CVSProviderPlugin.getPlugin().setPruneEmptyDirectories(true);
	}

	public void testGet() throws TeamException, CoreException, IOException {
		
		// Create a project
		IProject project = createProject("testGet", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
		
		// Checkout a copy and modify locally
		IProject copy = checkoutCopy(project, "-copy");
		//addResources(copy, new String[] { "added.txt", "folder2/", "folder2/added.txt" }, false);
		deleteResources(copy, new String[] {"deleted.txt"}, false);
		setContentsAndEnsureModified(copy.getFile("changed.txt"));

		// get the remote conetns
		getProvider(copy).get(new IResource[] {copy}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		assertEquals(project, copy);
	}
	
	public void testReadOnly() throws TeamException, CoreException, IOException {
		IProject project = createProject("testReadOnly", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
		// Need to check the project out as read-only
	}
	
	public void testCleanLineDelimiters() throws TeamException, CoreException, IOException {
		// Create a project
		IProject project = getUniqueTestProject("testCleanLineDelimiters");
		IFile file = project.getFile("testfile");
		IProgressMonitor monitor = new NullProgressMonitor();

		// empty file
		setFileContents(file, "");
		CVSTeamProvider.cleanLineDelimiters(file, false, monitor);
		assertEqualsFileContents(file, "");
		CVSTeamProvider.cleanLineDelimiters(file, true, monitor);
		assertEqualsFileContents(file, "");
		
		// one byte
		setFileContents(file, "a");
		CVSTeamProvider.cleanLineDelimiters(file, false, monitor);
		assertEqualsFileContents(file, "a");
		CVSTeamProvider.cleanLineDelimiters(file, true, monitor);
		assertEqualsFileContents(file, "a");
		
		// single orphan carriage return (should be preserved)
		setFileContents(file, "\r");
		CVSTeamProvider.cleanLineDelimiters(file, false, monitor);
		assertEqualsFileContents(file, "\r");
		CVSTeamProvider.cleanLineDelimiters(file, true, monitor);
		assertEqualsFileContents(file, "\r");

		// single line feed
		setFileContents(file, "\n");
		CVSTeamProvider.cleanLineDelimiters(file, false, monitor);
		assertEqualsFileContents(file, "\n");
		CVSTeamProvider.cleanLineDelimiters(file, true, monitor);
		assertEqualsFileContents(file, "\r\n");
		
		// single carriage return line feed
		setFileContents(file, "\r\n");
		CVSTeamProvider.cleanLineDelimiters(file, true, monitor);
		assertEqualsFileContents(file, "\r\n");
		CVSTeamProvider.cleanLineDelimiters(file, false, monitor);
		assertEqualsFileContents(file, "\n");
		
		// mixed text with orphaned CR's
		setFileContents(file, "The \r\n quick brown \n fox \r\r\r\n jumped \n\n over \r\n the \n lazy dog.\r\n");
		CVSTeamProvider.cleanLineDelimiters(file, false, monitor);
		assertEqualsFileContents(file, "The \n quick brown \n fox \r\r\n jumped \n\n over \n the \n lazy dog.\n");
		setFileContents(file, "The \r\n quick brown \n fox \r\r\r\n jumped \n\n over \r\n the \n lazy dog.\r\n");
		CVSTeamProvider.cleanLineDelimiters(file, true, monitor);
		assertEqualsFileContents(file, "The \r\n quick brown \r\n fox \r\r\r\n jumped \r\n\r\n over \r\n the \r\n lazy dog.\r\n");
	}
	
	public void testKeywordSubstitution() throws TeamException, CoreException, IOException {
		testKeywordSubstitution(Command.KSUBST_BINARY); // -kb
		testKeywordSubstitution(Command.KSUBST_TEXT); // -ko
		testKeywordSubstitution(Command.KSUBST_TEXT_EXPAND); // -kkv
	}

	private void testKeywordSubstitution(KSubstOption ksubst) throws TeamException, CoreException, IOException {
		// setup some known file types
		Team.setAllTypes( new String[] {"xbin", "xtxt"}, new int[] {Team.BINARY, Team.TEXT});
		
		// create a test project
		IProject project = createProject("testKeywordSubstitution", new String[] { "dummy" });
		addResources(project, new String[] { "binary.xbin", "text.xtxt", "folder1/", "folder1/a.xtxt" }, true);
		addResources(project, new String[] { "added.xbin", "added.xtxt" }, false);
		assertHasKSubstOption(project, "binary.xbin", Command.KSUBST_BINARY);
		assertHasKSubstOption(project, "added.xbin", Command.KSUBST_BINARY);
		assertHasKSubstOption(project, "text.xtxt", CVSProviderPlugin.DEFAULT_TEXT_KSUBST_OPTION);
		assertHasKSubstOption(project, "folder1/a.xtxt", CVSProviderPlugin.DEFAULT_TEXT_KSUBST_OPTION);
		assertHasKSubstOption(project, "added.xtxt", CVSProviderPlugin.DEFAULT_TEXT_KSUBST_OPTION);
		
		// change keyword substitution
		Map map = new HashMap();
		map.put(project.getFile("binary.xbin"), ksubst);
		map.put(project.getFile("added.xbin"), ksubst);
		map.put(project.getFile("text.xtxt"), ksubst);
		map.put(project.getFile("folder1/a.xtxt"), ksubst);
		map.put(project.getFile("added.xtxt"), ksubst);
		
		waitMsec(1500);
		IStatus status = getProvider(project).setKeywordSubstitution(map, null, null);
		assertTrue("Status should be ok, was: " + status.toString(), status.isOK());
		assertHasKSubstOption(project, "binary.xbin", ksubst);
		assertHasKSubstOption(project, "text.xtxt", ksubst);
		assertHasKSubstOption(project, "folder1/a.xtxt", ksubst);
		assertHasKSubstOption(project, "added.xtxt", ksubst);
		assertHasKSubstOption(project, "added.xbin", ksubst);

		// verify that substitution mode changed remotely and "added.xtxt", "added.xbin" don't exist
		IProject copy = checkoutCopy(project, "-copy");
		assertHasKSubstOption(copy, "binary.xbin", ksubst);
		assertHasKSubstOption(copy, "text.xtxt", ksubst);
		assertHasKSubstOption(copy, "folder1/a.xtxt", ksubst);
		assertDoesNotExistInWorkspace(copy.getFile("added.xtxt"));
		assertDoesNotExistInWorkspace(copy.getFile("added.xbin"));
		
		// commit added files then checkout the copy again
		commitResources(project, new String[] { "added.xbin", "added.xtxt" });
		IProject copy2 = checkoutCopy(project, "-copy2");
		assertHasKSubstOption(copy2, "added.xtxt", ksubst);
		assertHasKSubstOption(copy2, "added.xbin", ksubst);
		
		// verify that local contents are up to date
		assertEquals(project, copy2);
	}
	
	public static void setFileContents(IFile file, String string) throws CoreException {
		InputStream is = new ByteArrayInputStream(string.getBytes());
		if (file.exists()) {
			file.setContents(is, false /*force*/, true /*keepHistory*/, null);
		} else {
			file.create(is, false /*force*/, null);
		}
	}
	public static String getFileContents(IFile file) throws CoreException, IOException {
		StringBuffer buf = new StringBuffer();
		Reader reader = new InputStreamReader(new BufferedInputStream(file.getContents()));
		try {
			int c;
			while ((c = reader.read()) != -1) buf.append((char) c);
		} finally {
			reader.close();
		}
		return buf.toString();
	}
	
	public static void assertEqualsFileContents(IFile file, String string) throws CoreException, IOException {
		String other = getFileContents(file);
		assertEquals(string, other);
	}
	
	public static void assertHasKSubstOption(IContainer container, String filename, KSubstOption ksubst)
		throws TeamException {
		IFile file = container.getFile(new Path(filename));
		ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor(file);
		ResourceSyncInfo info = cvsFile.getSyncInfo();
		assertEquals(ksubst, info.getKeywordMode());
	}
	
	public void testUnmap() throws CoreException, TeamException {
		// Create a project
		IProject project = createProject("testUnmap", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt", "folder1/", "folder1/folder2/deep.txt", "folder2/b.txt" });
		// delete a file and folder to create phantoms
		project.getFile("deleted.txt").delete(false, false, null);
		assertTrue(project.getFile("deleted.txt").isPhantom());
		project.getFolder("folder2").delete(false, false, null);
		assertTrue(project.getFolder("folder2").isPhantom());
		// unmap
		RepositoryProvider.unmap(project);
		// ensure that phantoms for the resoucrs no longer exist
		assertFalse(project.getFile("deleted.txt").isPhantom());
		assertFalse(project.getFolder("folder2").isPhantom());
		
		// Create a project
		project = createProject("testUnmap2", new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt", "folder1/", "folder1/folder2/deep.txt", "folder2/b.txt" });
		// delete a deep folder to create phantoms
		project.getFolder("folder1/folder2").delete(false, false, null);
		assertTrue(project.getFolder("folder1/folder2").isPhantom());
		// unmap
		RepositoryProvider.unmap(project);
		// ensure that phantoms for the resources no longer exist
		assertFalse(project.getFolder("folder1/folder2").isPhantom());
	}
	
	public void testForBinaryLinefeedCorruption() throws CoreException, TeamException, IOException {
		String EOL = "\n";
		IProject project = createProject("testForBinaryLinefeedCorruption", new String[] { "binaryFile" });
		ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor(project.getFile("binaryFile"));
		assertTrue(ResourceSyncInfo.isBinary(cvsFile.getSyncBytes()));
		setContentsAndEnsureModified(project.getFile("binaryFile"), "line 1" + EOL + "line 2");
		commitProject(project);
		
		// Checkout a copy and ensure the file was not corrupted
		IProject copy = checkoutCopy(project, "-copy");
		assertEquals(project, copy);
	}
	
	public void test33984CannotCommitAfterConflictsMergedLocally() throws CoreException, TeamException, IOException {
			String EOL = System.getProperty("line.separator");
			
			IProject project = createProject("test33984", new String[] { "a.txt", "b.txt" });
			setContentsAndEnsureModified(project.getFile("a.txt"), "line 1");
		    setContentsAndEnsureModified(project.getFile("b.txt"), ("line 1" + EOL + "line 2" + EOL + "line3"));

			Map kMode = new HashMap();
			kMode.put(project.getFile("a.txt"), Command.KSUBST_TEXT);
			kMode.put(project.getFile("b.txt"), Command.KSUBST_TEXT);
			getProvider(project).setKeywordSubstitution(kMode, "", null);
		    
			commitProject(project);
			

		
			// Checkout a copy and ensure the file was not corrupted
			IProject copy = checkoutCopy(project, "-copy");
			assertEquals(project, copy);
			
			// TEST 1: simulate modifying same file by different users
			// b.txt has non-conflicting changes 
			setContentsAndEnsureModified(copy.getFile("b.txt"), ("line 1a" + EOL + "line 2" + EOL + "line3"));
		    
			commitProject(copy);
			
			// user updates which would cause a merge with conflict, a commit should not be allowed
			
			setContentsAndEnsureModified(project.getFile("b.txt"), ("line 1" + EOL + "line 2" + EOL + "line3a"));
			updateProject(project, CVSTag.DEFAULT, false /* don't ignore local changes */);
			commitProject(project);

			// TEST 2: a.txt has conflicting changes
			setContentsAndEnsureModified(copy.getFile("a.txt"), "line 1dfgdfne3");
  
			commitProject(copy);
			
			// user updates which would cause a merge with conflict, a commit should not be allowed
			setContentsAndEnsureModified(project.getFile("a.txt"), "some other text");
			updateProject(project, CVSTag.DEFAULT, false /* don't ignore local changes */);
			try {
				commitProject(project);
				fail("should not be allowed to commit a resource with merged conflicts");
			} catch(TeamException e) {
			}
	}
}

