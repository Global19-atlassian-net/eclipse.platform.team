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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.tests.ccvs.core.CVSTestSetup;

/**
 * Tests the CVSMergeSubscriber
 */
public class CVSCompareSubscriberTest extends CVSSyncSubscriberTest {

	public static Test suite() {
		String testName = System.getProperty("eclipse.cvs.testName");
		if (testName == null) {
			TestSuite suite = new TestSuite(CVSCompareSubscriberTest.class);
			return new CVSTestSetup(suite);
		} else {
			return new CVSTestSetup(new CVSCompareSubscriberTest(testName));
		}
	}

	public CVSCompareSubscriberTest() {
		super();
	}

	public CVSCompareSubscriberTest(String name) {
		super(name);
	}

	/**
	 * Test the basic changes that can occur when comparing the local workspace to a remote
	 * lineup.
	 */
	public void testStandardChanges() throws InvocationTargetException, InterruptedException, CVSException, CoreException, IOException {
		// Create a test project
		IProject project = createProject("testCompareChanges", new String[]{"file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt", "folder2/", "folder2/deleted.txt"});
		// Checkout and branch a copy
		CVSTag tag = new CVSTag("v1", CVSTag.VERSION);
		tagProject(project, tag, true);
		// Modify the workspace
		addResources(project, new String[]{"addition.txt", "folderAddition/", "folderAddition/new.txt"}, true);
		deleteResources(project, new String[]{"folder1/a.txt"}, true);
		deleteResources(project, new String[] {"folder2/"}, true);
		// modify file1 - make two revisions
		appendText(project.getFile("file1.txt"), "Appended text 1", false);
		commitProject(project);
		appendText(project.getFile("file1.txt"), "Appended text 2", false);
		commitProject(project);
		// modify file2 in both branch and head and ensure it's merged properly 
		appendText(project.getFile("file2.txt"), "appened text", false);
		commitProject(project);
		// create a merge subscriber
		CVSCompareSubscriber subscriber = getSyncInfoSource().createCompareSubscriber(project, tag);
		// check the sync states
		assertSyncEquals("testIncomingChanges", subscriber, project, 
				new String[]{
				"file1.txt", 
				"file2.txt", 
				"folder1/",
				"folder1/b.txt",
				"folder1/a.txt", 
				"addition.txt", 
				"folderAddition/", 
				"folderAddition/new.txt", 
				"folder2/",
				"folder2/deleted.txt"}, true, 
				new int[]{
				SyncInfo.CHANGE, 
				SyncInfo.CHANGE, 
				SyncInfo.IN_SYNC,
				SyncInfo.IN_SYNC,
				SyncInfo.ADDITION, 
				SyncInfo.DELETION, 
				SyncInfo.DELETION, 
				SyncInfo.DELETION,
				SyncInfo.ADDITION,
				SyncInfo.ADDITION});
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		getSyncInfoSource().tearDown();
		super.tearDown();
	}
}
