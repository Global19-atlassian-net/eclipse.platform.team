package org.eclipse.team.tests.ccvs.core.compatible;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.ccvs.core.ICVSFile;
import org.eclipse.team.ccvs.core.ICVSFolder;
import org.eclipse.team.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.tests.ccvs.core.CVSClientException;
import org.eclipse.team.tests.ccvs.core.CommandLineCVSClient;
import org.eclipse.team.tests.ccvs.core.EclipseCVSClient;
import org.eclipse.team.tests.ccvs.core.JUnitTestCase;


/**
 * This is a TestCase that does provide the possibility
 * to run tests on both the reference reference-client and the
 * client provided by us, and to check on equal results
 * in files and messages to the consol.
 * 
 * No own tests should be placed here, instead you should
 * instanciate this testcase in order to make your test from
 * another suite.
 * The class is final, because you need to be able to open
 * two (or more) different enviorments to test certain things.
 */
public final class SameResultEnv extends JUnitTestCase {
	private IProject referenceProject;
	private ICVSFolder referenceRoot;
	private IProject eclipseProject;
	private ICVSFolder eclipseRoot;

	private boolean ignoreExceptions;

	public SameResultEnv(String arg) {
		super(arg);
	}
	
	/**
	 * Always to be called in the setUp of the testCase that wants to 
	 * use the same-result Enviorment.
	 */
	public void setUp() throws Exception {
		super.setUp();
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		// setup reference client test project
		referenceProject = root.getProject(getName() + "-reference");
		referenceProject.delete(true /*deleteContent*/, true /*force*/, null);
		mkdirs(referenceProject);
		referenceRoot = CVSWorkspaceRoot.getCVSFolderFor(referenceProject);
		
		// setup eclipse client test project
		eclipseProject = root.getProject(getName() + "-eclipse");
		eclipseProject.delete(true /*deleteContent*/, true /*force*/, null);
		mkdirs(eclipseProject);
		eclipseRoot = CVSWorkspaceRoot.getCVSFolderFor(eclipseProject);

		// By default, exceptions are not ignored.
		// Specific test cases can choose to ignore exceptions
		ignoreExceptions = false;
	}
	
	/**
	 * Always to be called in the tearDown of the testCase that wants to 
	 * use the same-result Enviorment.
	 */
	public void tearDown() throws Exception {
		// we deliberately don't clean up test projects to simplify debugging
		super.tearDown();
	}
	
	/**
	 * Helper method.
	 * Calls execute(command, EMPTY_ARGS, localOptions, arguments, pathRelativeToRoot)
	 */				
	public void execute(String command, String[] localOptions, String[] arguments, String pathRelativeToRoot)
		throws CVSException {
		execute(command, EMPTY_ARGS, localOptions, arguments, pathRelativeToRoot);
	}

	/**
	 * Helper method.
	 * Calls execute(command, EMPTY_ARGS, localOptions, arguments, "")
	 */				
	public void execute(String command, String[] localOptions, String[] arguments)
		throws CVSException {
		execute(command, EMPTY_ARGS, localOptions, arguments, "");
	}

	/**
	 * Runs a command twice, once in the reference environments, once
	 * in the eclipse environment.  Compares the resulting resources
	 * on disk, but not console output.
	 */
	public void execute(String command,
		String[] globalOptions, String[] localOptions, String[] arguments,
		String pathRelativeToRoot) throws CVSException {
		
		// run with reference client
		boolean referenceClientException = false;
		try {
			File localRoot = referenceProject.getLocation().toFile();
			if (pathRelativeToRoot.length() != 0) {
				localRoot = new File(localRoot, pathRelativeToRoot);
			}
			CommandLineCVSClient.execute(
				CompatibleTestSetup.referenceClientRepository.getLocation(),
				localRoot, command, globalOptions, localOptions, arguments);
		} catch (CVSClientException e) {
			if (! ignoreExceptions) throw e;
			referenceClientException = true;
		} finally {
			try {
				referenceProject.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (CoreException e) {
				fail("CoreException during refreshLocal: " + e.getMessage());
			}
		}
		
		// run with Eclipse client
		boolean eclipseClientException = false;
		try {
			ICVSFolder localRoot = eclipseRoot;
			IPath path = new Path(pathRelativeToRoot);
			while (path.segmentCount() != 0) {
				localRoot = localRoot.getFolder(path.segment(0));
				path = path.removeFirstSegments(1);
			}
			EclipseCVSClient.execute(
				CompatibleTestSetup.eclipseClientRepository, localRoot,
				command, globalOptions, localOptions, arguments);
		} catch (CVSClientException e) {
			if (! ignoreExceptions) throw e;
			eclipseClientException = true;
		}
		assertEquals(referenceClientException, eclipseClientException);
		assertConsistent();
	}	

	/**
	 * Deletes files on the both of the cvs-servers.
	 */
	public void magicDeleteRemote(String remoteName) throws CVSException {
		super.magicDeleteRemote(CompatibleTestSetup.referenceClientRepository, remoteName);
		super.magicDeleteRemote(CompatibleTestSetup.eclipseClientRepository, remoteName);		
	}
	
	/**
	 * Set up both of the repos on the cvs-server(s) with the standard
	 * file-structure:
	 * project
	 *   a.txt
	 *   f1
	 *     b.txt
	 *     c.txt
	 */
	public void magicSetUpRepo(String projectName)
		throws IOException, CoreException, CVSException {
		magicSetUpRepo(projectName, new String[]{"a.txt","f1/b.txt","f1/c.txt"});
	}
	
	/**
	 * Set up both of the repos on the cvs-server(s) with a filestructre
	 * resulting for your input in the parameter createResources.
	 */
	public void magicSetUpRepo(String projectName, String[] createResources)
		throws IOException, CoreException, CVSException {
		magicDeleteRemote(projectName);

		IProject projectRoot = workspaceRoot.getProject(projectName + "-setup-tmp");
		mkdirs(projectRoot);
		createRandomFile(projectRoot, createResources);
		
		String[] lOptions = new String[]{"-m","msg"};
		String[] args = new String[]{projectName,"a","b"};
	
		magicDeleteRemote(CompatibleTestSetup.referenceClientRepository, projectName);
		EclipseCVSClient.execute(CompatibleTestSetup.referenceClientRepository, CVSWorkspaceRoot.getCVSFolderFor(projectRoot),
			"import", EMPTY_ARGS, lOptions, args);
			
		magicDeleteRemote(CompatibleTestSetup.eclipseClientRepository, projectName);
		EclipseCVSClient.execute(CompatibleTestSetup.eclipseClientRepository, CVSWorkspaceRoot.getCVSFolderFor(projectRoot),
			"import", EMPTY_ARGS, lOptions, args);

		projectRoot.delete(false /*force*/, null);
	}

	/**
	 * Create a file with random-content in both, the reference client and 
	 * the eclipse-client.
	 * 
	 * @param relativeFileName is the relative path as allways in the 
	           class used for access
	 */
	public void createRandomFile(String relativeFileName)
		throws IOException, CoreException {
		String[] contents = new String[] { createRandomContent() };
		writeToFile(relativeFileName, contents);
	}

	/**
	 * Call createRandomFile for every element of the array
	 * 
	 * @see SameResultEnv#createRandomFile(String)
	 */
	public void createRandomFile(String[] relativeFileNames,
		String pathRelativeToRoot) throws CoreException, IOException {
		if (pathRelativeToRoot == null) {
			pathRelativeToRoot = "";
		} else if (! pathRelativeToRoot.endsWith("/")) {
			pathRelativeToRoot += "/";
		}
		for (int i = 0; i < relativeFileNames.length; i++) {
			createRandomFile(pathRelativeToRoot + relativeFileNames[i]);
		}
	}
	
	/**
	 * Read from the file (check that we have acctually got the same
	 * content in both versions
	 */
	public String[] readFromFile(String relativeFileName)
		throws IOException, CoreException {
		IFile referenceFile = referenceProject.getFile(relativeFileName);
		String[] content1 = super.readFromFile(referenceFile);
		IFile eclipseFile = eclipseProject.getFile(relativeFileName);
		String[] content2 = super.readFromFile(eclipseFile);
		assertEqualsArrays(content1,content2);
		return content1;
	}
	
	/**
	 * Delete a file / folder from both directories.
	 */
	public void deleteFile(String relativeFileName) throws CoreException {
		IResource referenceFile, eclipseFile;
		if (relativeFileName.length() != 0) {
			referenceFile = referenceProject.findMember(relativeFileName);
			eclipseFile = eclipseProject.findMember(relativeFileName);
		} else {
			referenceFile = referenceProject;
			eclipseFile = eclipseProject;
		}
		assertEquals(referenceFile != null, eclipseFile != null);
		if (referenceFile == null) return;
		assertEquals(referenceFile.exists(), eclipseFile.exists());
		referenceFile.delete(true, null);
		eclipseFile.delete(true, null);
	}
	
	/**
	 * Creates a folder (and its parents if needed) in both environments.
	 */
	public void mkdirs(String relativeFolderName) throws CoreException {
		IFolder referenceFolder = referenceProject.getFolder(relativeFolderName);
		IFolder eclipseFolder = eclipseProject.getFolder(relativeFolderName);
		assertEquals(referenceFolder.exists(), eclipseFolder.exists());
		mkdirs(referenceFolder);
		mkdirs(eclipseFolder);
	}
	
	/**
	 * Append a String to an file (acctally to both of the files, that are going
	 * to have the same content)
	 */
	public void appendToFile(String relativeFileName, String[] contents)
		throws IOException, CoreException {
		// Wait a second so that the timestamp will change for sure
		waitMsec(1500);

		IFile referenceFile = referenceProject.getFile(relativeFileName);
		appendToFile(referenceFile, contents);
		IFile eclipseFile = eclipseProject.getFile(relativeFileName);
		appendToFile(eclipseFile, contents);		
	}
	
	/**
	 * Write to the file (acctally to both of the files, that are going
	 * to have the same content)
	 */
	public void writeToFile(String relativeFileName, String[] contents)
		throws IOException, CoreException {
		IFile referenceFile = referenceProject.getFile(relativeFileName);
		writeToFile(referenceFile, contents);
		IFile eclipseFile = eclipseProject.getFile(relativeFileName);
		writeToFile(eclipseFile, contents);
	}		

	/**
	 * Checks whether the two directories inside the environment
	 * are equal and therefore the state valid.
	 */
	public void assertConsistent() throws CVSException {
		assertEquals(referenceRoot, eclipseRoot);
	}	

	/**
	 * Deep compare of two ManagedResources (most likly folders).
	 * Passwords are ignored.
	 * 
	 * @param ignoreTimestamp if true timestamps of
	           files are ignored for the comparison
	 */
	public static void assertEquals(ICVSResource mResource1, 
										ICVSResource mResource2) 
										throws CVSException {
		
		assertEquals(mResource1.isFolder(), mResource2.isFolder());
		assertEquals(mResource1.isManaged() , mResource2.isManaged());
		assertEquals(mResource1.exists(), mResource2.exists());
		
		if (!mResource1.exists()) {
			return;
		}
		
		if (mResource1.isFolder()) {
			assertEquals((ICVSFolder)mResource1,(ICVSFolder)mResource2);
		} else {
			assertEquals((ICVSFile)mResource1,(ICVSFile)mResource2);
		}	
	}	
	
	/**
	 * Assert that two CVSFile's are equal by comparing the content
	 * and the metainformation out of the ResourceSync.
	 */
	private static void assertEquals(ICVSFile mFile1, ICVSFile mFile2) throws CVSException {
		
		if (mFile1.getName().equals(".project")) return;
		
		// Check the permissions on disk
		assertEquals(mFile1.isReadOnly(), mFile2.isReadOnly());
					
		// Compare the content of the files
		try {
			InputStream in1 = mFile1.getInputStream();
			InputStream in2 = mFile2.getInputStream();
			byte[] buffer1 = new byte[(int)mFile1.getSize()];
			byte[] buffer2 = new byte[(int)mFile2.getSize()];
			// This is not the right way to do it, because the Stream
			// may read less than the whoole file
			in1.read(buffer1);
			in2.read(buffer2);
			in1.close();
			in2.close();
			assertEquals("Length differs for file " + mFile1.getName(), buffer1.length, buffer2.length);
			assertEquals("Contents differs for file " + mFile1.getName(), new String(buffer1),new String(buffer2));
		} catch (IOException e) {
			throw new CVSException("Error in TestCase");
		}

		// We can not do the ceck, because the reference client does
		// check out dirty files ?!?
		// assertEquals(mFile1.isDirty(),mFile2.isDirty());
		
		assertEquals(mFile1.getSyncInfo() == null,mFile2.getSyncInfo() == null);
		if (mFile1.getSyncInfo() == null) {
			return;
		}
		
		ResourceSyncInfo info1 = mFile1.getSyncInfo();
		ResourceSyncInfo info2 = mFile2.getSyncInfo();
		
		assertEquals(info1.getKeywordMode(), info2.getKeywordMode());
		assertEquals(info1.getTag(), info2.getTag());
		assertEquals(info1.getName(), info2.getName());
		assertEquals(info1.getRevision(), info2.getRevision());
		
		// Ensure that timestamps are written in ISO C asctime() format and if timestamp
		// has a conflict marker then both should have the marker. Also ensure that timestamps
		// are written using same timezone.
		//assertTimestampEquals(info1.getTimeStamp(), info2.getTimeStamp());
		
		// We are not able to check for the permissions, as the reference-client doesn't save them
	}

	private static void assertTimestampEquals(String timestamp1, String timestamp2) {
		//try {			
			//EntryFileDateFormat timestampFormat = new EntryFileDateFormat();
			//boolean merge1 = timestamp1.indexOf(ResourceSyncInfo.RESULT_OF_MERGE_CONFLICT) != -1;
			//boolean merge2 = timestamp2.indexOf(ResourceSyncInfo.RESULT_OF_MERGE_CONFLICT) != -1;
			//boolean dummy1 = timestamp1.indexOf(ResourceSyncInfo.DUMMY_TIMESTAMP) != -1;
			//boolean dummy2 = timestamp2.indexOf(ResourceSyncInfo.DUMMY_TIMESTAMP) != -1;
			//assertEquals("both timestamps should show same conflict state", merge1, merge2);
			//assertEquals("both timestamps should show same dummy state", dummy1, dummy2);
			//if(!merge1 && !dummy1) {
			//	long time1 = timestampFormat.toDate(timestamp1).getTime();
			//	long time2 = timestampFormat.toDate(timestamp2).getTime();
				/* timestamp tests don't seem to work on some systems.
				long difference = Math.abs(time1 - time2);
				assertTrue("timestamps should be in same timezone:" + timestamp1 + ":" + timestamp2, difference < (5*60*1000)); // 5 minutes
				*/
			//}
		//} catch(ParseException e) {			
		//	fail("timestamps in CVS/Entry file are not in ISO C asctime format:" + timestamp1 + ":" + timestamp2);
		//}
	}
	
	/**
	 * Assert that two CVSFile's are equal. First the 
	 * metainformation out of the FolderSync for this 
	 * folder is compared, then the amount of children is 
	 * checked and finally the recussion is started to
	 * compare the children of this folder.
	 */
	private static void assertEquals(ICVSFolder mFolder1, 
										ICVSFolder mFolder2) 
										throws CVSException {

		assertEquals(mFolder1.isCVSFolder(),mFolder2.isCVSFolder());
		
		if (mFolder1.isCVSFolder()) {
			String root1 = removePassword(mFolder1.getFolderSyncInfo().getRoot());
			String root2 = removePassword(mFolder2.getFolderSyncInfo().getRoot());
			root1 = root1.substring(0,root1.lastIndexOf("@"));
			root2 = root2.substring(0,root2.lastIndexOf("@"));
			assertEquals(root1,root2);
			
			assertEquals(mFolder1.getFolderSyncInfo().getRepository(),mFolder2.getFolderSyncInfo().getRepository());
			assertEquals(mFolder1.getFolderSyncInfo().getIsStatic(),mFolder2.getFolderSyncInfo().getIsStatic());
			assertEquals(mFolder1.getFolderSyncInfo().getTag(),mFolder2.getFolderSyncInfo().getTag());
		}
		
		ICVSResource[] resourceList1;
		ICVSResource[] resourceList2;
		boolean fileFound;
		
		resourceList1 = mFolder1.getFiles();
		resourceList2 = mFolder2.getFiles();
		assertEquals(resourceList1.length,resourceList2.length);
		for (int i=0; i<resourceList1.length; i++) {
			fileFound = false;
			for (int j=0; j<resourceList2.length; j++) {
				if (resourceList1[i].getName().equals(resourceList2[j].getName())) {
					assertEquals(resourceList1[i], resourceList2[j]);
					fileFound = true;
					break;
				}
			}
			assertTrue("File " + resourceList1[i].getName() + " not found in the list",fileFound);
		}
		
		resourceList1 = mFolder1.getFolders();
		resourceList2 = mFolder2.getFolders();
		assertEquals(resourceList1.length,resourceList2.length);
		for (int i=0; i<resourceList1.length; i++) {
			fileFound = false;
			for (int j=0; j<resourceList2.length; j++) {
				if (resourceList1[i].getName().equals(resourceList2[j].getName())) {
					assertEquals(resourceList1[i], resourceList2[j]);
					fileFound = true;
					break;
				}
			}
			assertTrue("Folder " + resourceList1[i].getName() + " not found in the list",fileFound);
		}
	}
	
	/**
	 * Sets whether Exceptions that are thrown in the execution of both of the clients are
	 * catched or thrown to the upper level. If the exceptions are catched the result of the 
	 * reference-client and this client are compared as if the execution succseded.
	 */
	public void setIgnoreExceptions(boolean ignoreExceptions) {
		this.ignoreExceptions = ignoreExceptions;
	}
	
	/**
	 * returns ":pserver:nkrambro@fiji:/home/nkrambro/repo"
	 *         when you insert ":pserver:nkrambro:password@fiji:/home/nkrambro/repo"
	 */
	public static String removePassword(String root) {
		StringTokenizer tok = new StringTokenizer(root, ":@", true);
		StringBuffer filteredRoot = new StringBuffer();
		int colonCounter = 3;
		while (tok.hasMoreTokens()) {
			String token = tok.nextToken();
			if ("@".equals(token)) colonCounter = -1;
			if (":".equals(token)) {
				if (--colonCounter == 0) continue; // skip colon
			}
			if (colonCounter == 0) continue; // skip password
			filteredRoot.append(token);
		}
		return filteredRoot.toString();
	}
}