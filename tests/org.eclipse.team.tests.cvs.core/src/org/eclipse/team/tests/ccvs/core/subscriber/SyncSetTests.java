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

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.SyncInfo;
import org.eclipse.team.internal.ui.synchronize.sets.SyncInfoSet;
import org.eclipse.team.tests.ccvs.core.CVSTestSetup;


public class SyncSetTests extends CVSSyncSubscriberTest {
	
	public SyncSetTests() {
		super();
	}
	
	public SyncSetTests(String name) {
		super(name);
	}

	public static Test suite() {
		String testName = System.getProperty("eclipse.cvs.testName");
		if (testName == null) {
			TestSuite suite = new TestSuite(SyncSetTests.class);
			return new CVSTestSetup(suite);
		} else {
			return new CVSTestSetup(new SyncSetTests(testName));
		}
	}

	class TestSyncInfo extends SyncInfo {
		protected int calculateKind(IProgressMonitor progress) throws TeamException {
				return 0;
		}
		public TestSyncInfo() throws TeamException {
			super(ResourcesPlugin.getWorkspace().getRoot(), null, null, null, null);
		}
	}

	/**
	 * Test that ensures that SyncSet can be modified concurrently. This is a quick test
	 * that doesn't validate the actual contents of the sync set.
	 */
	public void testConcurrentAccessToSyncSet() throws Throwable {
		final SyncInfoSet set = new SyncInfoSet();
		final boolean[] done = {false};
		final IStatus[] error = {null};
		
		for(int numJobs = 0; numJobs < 10; numJobs++) {		
			Job job = new Job("SyncSetTests" + numJobs) {
				public IStatus run(IProgressMonitor monitor) {
					while(! done[0]) {
						try {
							set.add(new TestSyncInfo());
							set.getOutOfSyncDescendants(ResourcesPlugin.getWorkspace().getRoot());
							set.getSyncInfo(ResourcesPlugin.getWorkspace().getRoot());
							set.members();
						} catch (Exception e) {
							error[0] = new Status(IStatus.ERROR, "this", 1, "", e);
							return error[0];						
						}
					}
					return Status.OK_STATUS;
				}
			};
			
			job.addJobChangeListener(new JobChangeAdapter() {
				public void done(IJobChangeEvent event) {
					if(event.getResult() != Status.OK_STATUS) {
						error[0] = event.getResult();
					}
				}
			});		
			
			job.schedule();
		}
		
		for(int i = 0; i < 10000; i++) {
			set.add(new TestSyncInfo());
			set.getOutOfSyncDescendants(ResourcesPlugin.getWorkspace().getRoot());
			set.getSyncInfo(ResourcesPlugin.getWorkspace().getRoot());
			set.members();
			set.members(ResourcesPlugin.getWorkspace().getRoot());
			set.clear();		
		}
		done[0] = true;
		if(error[0] != null) {
			throw error[0].getException();
		}	
	}
}
