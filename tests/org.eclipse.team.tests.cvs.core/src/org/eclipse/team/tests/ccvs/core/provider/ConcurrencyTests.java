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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.progress.IElementCollector;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.operations.FetchMembersOperation;
import org.eclipse.team.tests.ccvs.core.CVSTestSetup;
import org.eclipse.team.tests.ccvs.core.EclipseTest;
import org.eclipse.team.tests.ccvs.ui.unit.HeadlessCVSRunnableContext;

public class ConcurrencyTests extends EclipseTest {

	public ConcurrencyTests() {
		super();
	}

	public ConcurrencyTests(String name) {
		super(name);
	}

	public static Test suite() {
		String testName = System.getProperty("eclipse.cvs.testName");
		if (testName == null) {
			TestSuite suite = new TestSuite(ConcurrencyTests.class);
			return new CVSTestSetup(suite);
		} else {
			return new CVSTestSetup(new ConcurrencyTests(testName));
		}
	}
	
	public void testBackgroundMemberFetch() throws InterruptedException, TeamException, CoreException {
		IProject project = createProject("testBackgroundMemberFetch", new String[] { "file1.txt", "folder1/", "folder1/a.txt", "folder2/", "folder2/a.txt", "folder2/folder3/", "folder2/folder3/b.txt", "folder2/folder3/c.txt"});
		ICVSRemoteFolder folder = (ICVSRemoteFolder)CVSWorkspaceRoot.getRemoteResourceFor(project);
		final List result = new ArrayList(); 
		final boolean[] done = new boolean[] { false };
		IElementCollector collector = new IElementCollector() {
			public void add(Object element, IProgressMonitor monitor) {
				result.add(element);
			}
			public void add(Object[] elements, IProgressMonitor monitor) {
				result.addAll(Arrays.asList(elements));
			}
			public void done() {
				done[0] = true;
			}
		};
		IJobChangeListener listener = new JobChangeAdapter() {
			public void done(IJobChangeEvent event) {
				done[0] = true;
			}
		};
		FetchMembersOperation operation = new FetchMembersOperation(null, folder, collector);
		operation.setCVSRunnableContext(new HeadlessCVSRunnableContext(listener));
		operation.run();
		int count = 0;
		while (!done[0]) {
			Thread.sleep(1000);
			count++;
			if (count > 5) {
				fail("Fetch of memebers didn't complete in " + count + " seconds");
			}
		}
		assertTrue(result.size() == project.members().length);
		for (Iterator iter = result.iterator(); iter.hasNext();) {
			ICVSRemoteResource remote = (ICVSRemoteResource) iter.next();
			IResource local = project.findMember(remote.getName());
			assertNotNull(local);
		}
	}
}
