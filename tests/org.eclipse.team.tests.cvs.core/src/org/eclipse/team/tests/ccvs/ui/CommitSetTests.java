/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.tests.ccvs.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.ui.subscriber.CommitSetManager;
import org.eclipse.team.tests.ccvs.core.EclipseTest;

/**
 * Tests for CVS commit sets
 */
public class CommitSetTests extends EclipseTest {

	private List addedSets = new ArrayList();
	private List removedSets = new ArrayList();
	private IChangeSetChangeListener listener = new IChangeSetChangeListener() {
        public void setAdded(ChangeSet set) {
            addedSets.add(set);
        }
        public void setRemoved(ChangeSet set) {
            removedSets.add(set);
        }
        public void titleChanged(ChangeSet set) {
            // TODO Auto-generated method stub

        }
        public void defaultSetChanged(ChangeSet oldDefault, ChangeSet set) {
            // TODO Auto-generated method stub
            
        }
        public void resourcesChanged(ChangeSet set, IResource[] resources) {
            // TODO Auto-generated method stub
            
        }
    };

    public static Test suite() {
		return suite(CommitSetTests.class);
	}
	
    public CommitSetTests() {
        super();
    }
    
    public CommitSetTests(String name) {
        super(name);
    }
    
    /**
     * Create a commit set and verify that it was created and contains the supplied files
     * @param title the title of the new set
     * @param files the files for the new set
     * @return the newly create commit set
     * @throws TeamException
     */
    protected ChangeSet createCommitSet(String title, IFile[] files, boolean manageSet) throws TeamException {
        assertIsModified(getName(), files);
        SubscriberChangeSetManager manager = CommitSetManager.getInstance();
        ChangeSet set = manager.createSet(title, files);
        assertEquals("Not all files were asdded to the set", files.length, set.getResources().length);
        if (manageSet) {
	        manager.add(set);
	        waitForSetAddedEvent(set);
        }
        return set;
    }


    /**
     * Commit the files in the given set to the repository 
     * and ensure that the files are in the proper state
     * @param set the commit set
     * @throws CVSException
     */
    protected void commit(ChangeSet set) throws CoreException {
        boolean isManaged = setIsManaged(set);
        commitResources(set.getResources(), IResource.DEPTH_ZERO);
        if (isManaged) {
	        // Committing the set should remove it from the manager
            waitForSetRemovedEvent(set);
        }
    }
    
    private boolean setIsManaged(ChangeSet set) {
        return CommitSetManager.getInstance().contains(set);
    }

    private void waitForSetAddedEvent(ChangeSet set) {
        int count = 0;
        while (count < 5) {
	        if (addedSets.contains(set)) {
	            addedSets.remove(set);
	            return;
	        }
	        try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // keep going
            }
	        count++;
        }
        fail("Did not receive expected set added event");
    }

    private void waitForSetRemovedEvent(ChangeSet set) {
        int count = 0;
        while (count < 5) {
	        if (removedSets.contains(set)) {
	            removedSets.remove(set);
	            return;
	        }
	        try {
                while (Display.getCurrent().readAndDispatch()) {}
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // keep going
            }
	        count++;
        }
        fail("Did not receive expected set removed event");
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.core.tests.harness.EclipseWorkspaceTest#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
        CommitSetManager.getInstance().addListener(listener);
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.team.tests.ccvs.core.EclipseTest#tearDown()
     */
    protected void tearDown() throws Exception {
        super.tearDown();
        CommitSetManager.getInstance().removeListener(listener);
    }
    
    /**
     * Test a simple commit of a commit set. The set being committed is not managed.
     * @throws CoreException
     * @throws IOException
     * @throws TeamException
     */
    public void testSimpleCommit() throws TeamException, CoreException, IOException {
        IProject project = createProject(new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
        setContentsAndEnsureModified(project.getFile("changed.txt"));
        deleteResources(project, new String[] { "deleted.txt" }, false /* don't commit */);
        addResources(project, new String[] { "added.txt" }, false /* don't commit */);
        
        IFile[] files = new IFile[] { 
                project.getFile("changed.txt"), 
                project.getFile("deleted.txt"),
                project.getFile("added.txt")};
        ChangeSet set = createCommitSet("testSimpleCommit", files, false /* do not manage the set */);
        commit(set);
        assertLocalStateEqualsRemote(project);
    }
    
    /**
     * Test a managed commit of a commit set. The set being committed is managed
     * and should be removed once the commit succeeds.
     * @throws CoreException
     * @throws IOException
     * @throws TeamException
     */
    public void testManagedCommit() throws TeamException, CoreException, IOException {
        IProject project = createProject(new String[] { "changed.txt", "deleted.txt", "folder1/", "folder1/a.txt" });
        setContentsAndEnsureModified(project.getFile("changed.txt"));
        deleteResources(project, new String[] { "deleted.txt" }, false /* don't commit */);
        addResources(project, new String[] { "added.txt" }, false /* don't commit */);
        
        IFile[] files = new IFile[] { 
                project.getFile("changed.txt"), 
                project.getFile("deleted.txt"),
                project.getFile("added.txt")};
        ChangeSet set = createCommitSet("testSimpleCommit", files, true /* manage the set */);
        commit(set);
        assertLocalStateEqualsRemote(project);
    }
}
