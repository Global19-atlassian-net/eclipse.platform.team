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
package org.eclipse.team.tests.ccvs.core.subscriber;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ui.synchronize.*;
import org.eclipse.team.tests.ccvs.ui.SynchronizeViewTestAdapter;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.ui.PartInitException;

/**
 * Tests the change set mode of the synchronize view
 */
public class CVSChangeSetTests extends CVSSyncSubscriberTest {

	public static Test suite() {
	    return suite(CVSChangeSetTests.class);
	}
	
	public CVSChangeSetTests() {
		super();
	}
	
	public CVSChangeSetTests(String name) {
		super(name);
	}
	
    private void assertIncomingChangesInSets(IFile[][] files, String[] messages) throws CoreException {
        // Get the workspace subscriber which also creates a participant and page in the sync view
        Subscriber workspaceSubscriber = getWorkspaceSubscriber();
        enableChangeSets(workspaceSubscriber);
        refresh(workspaceSubscriber);
        ISynchronizeModelElement root = getModelRoot(workspaceSubscriber);
        for (int i = 0; i < messages.length; i++) {
            String message = messages[i];
            ChangeSetDiffNode node = getCommitSetFor(root, message);
            assertNotNull("The commit set for '" + message + "' is not in the sync view", node);
            List filesInSet = new ArrayList();
            getFileChildren(node, filesInSet);
            assertTrue("The number of files in the set do not match the expected number", files[i].length == filesInSet.size());
            for (int j = 0; j < files[i].length; j++) {
                IFile file = files[i][j];
                assertTrue("File " + file.getFullPath() + " is not in the set", filesInSet.contains(file));
            }
        }
    }
    
    /**
     * Adds IFiles to the list
     */
    private void getFileChildren(ISynchronizeModelElement node, List list) {
        IResource resource = node.getResource();
        if (resource != null && resource.getType() == IResource.FILE) {
            list.add(resource);
        }
        IDiffElement[] children = node.getChildren();
        for (int i = 0; i < children.length; i++) {
            IDiffElement child = children[i];
            getFileChildren((ISynchronizeModelElement)child, list);
        }
        return;
    }

    private ChangeSetDiffNode getCommitSetFor(ISynchronizeModelElement root, String message) {
        IDiffElement[] children = root.getChildren();
        for (int i = 0; i < children.length; i++) {
            IDiffElement element = children[i];
            if (element instanceof ChangeSetDiffNode) {
                ChangeSetDiffNode node = (ChangeSetDiffNode)element;
                if (node.getSet().getComment().equals(message)) {
                    return node;
                }
            }
        }
        return null;
    }

    private void refresh(Subscriber workspaceSubscriber) throws TeamException {
        workspaceSubscriber.refresh(workspaceSubscriber.roots(), IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
    }

    private void enableChangeSets(Subscriber workspaceSubscriber) throws PartInitException {
        ISynchronizeParticipant participant = SynchronizeViewTestAdapter.getParticipant(workspaceSubscriber);
        SubscriberParticipantPage page = (SubscriberParticipantPage)SynchronizeViewTestAdapter.getSyncViewPage(participant);
        ChangeSetModelManager manager = (ChangeSetModelManager)page.getConfiguration().getProperty(SynchronizePageConfiguration.P_MODEL_MANAGER);
        manager.setCommitSetsEnabled(true);
        page.getConfiguration().setMode(ISynchronizePageConfiguration.BOTH_MODE);
    }

    /*
     * Wait until all the background handlers have settled and then return the root element in the sync view
     */
    private ISynchronizeModelElement getModelRoot(Subscriber workspaceSubscriber) throws CoreException {
        SynchronizeViewTestAdapter.getCollector(workspaceSubscriber);
        ISynchronizeParticipant participant = SynchronizeViewTestAdapter.getParticipant(workspaceSubscriber);
        SubscriberParticipantPage page = (SubscriberParticipantPage)SynchronizeViewTestAdapter.getSyncViewPage(participant);
        ChangeSetModelManager manager = (ChangeSetModelManager)page.getConfiguration().getProperty(SynchronizePageConfiguration.P_MODEL_MANAGER);
        AbstractSynchronizeModelProvider provider = (AbstractSynchronizeModelProvider)manager.getActiveModelProvider();
        provider.waitForUpdateHandler(new IProgressMonitor() {
			public void beginTask(String name, int totalWork) {
			}
			public void done() {
			}
			public void internalWorked(double work) {
			}
			public boolean isCanceled() {
				return false;
			}
			public void setCanceled(boolean value) {
			}
			public void setTaskName(String name) {
			}
			public void subTask(String name) {
			}
			public void worked(int work) {
				while (Display.getCurrent().readAndDispatch()) {}
			}
		});
        return provider.getModelRoot();
    }

    private SubscriberChangeSetCollector getActiveChangeSetManager() {
        return CVSUIPlugin.getPlugin().getChangeSetManager();
    }
    
    /*
     * Assert that the given resources make up the given set both directly
     * and by what is displayed in the sync view.
     */
    private void assertInActiveSet(IResource[] resources, ActiveChangeSet set) throws CoreException {
        assertResourcesAreTheSame(resources, set.getResources());
        ISynchronizeModelElement root = getModelRoot(getActiveChangeSetManager().getSubscriber());
        ChangeSetDiffNode node = getChangeSetNodeFor(root, set);
        assertNotNull("Change set " + set.getTitle() + " did not appear in the sync view", node);
        IResource[] outOfSync = getOutOfSyncResources(node);
        assertResourcesAreTheSame(resources, outOfSync);
    }
    
    private ChangeSetDiffNode getChangeSetNodeFor(ISynchronizeModelElement root, ActiveChangeSet set) {
        IDiffElement[] children = root.getChildren();
        for (int i = 0; i < children.length; i++) {
            IDiffElement element = children[i];
            if (element instanceof ChangeSetDiffNode) {
                ChangeSetDiffNode node = (ChangeSetDiffNode)element;
                if (node.getSet() == set) {
                    return node;
                }
            }
        }
        return null;
    }

    private IResource[] getOutOfSyncResources(ISynchronizeModelElement element) {
        ArrayList arrayList = new ArrayList();
        getOutOfSync(element, arrayList);
        SyncInfo[] infos = (SyncInfo[]) arrayList.toArray(new SyncInfo[arrayList.size()]);
        IResource[] resources = getResources(infos);
        return resources;
    }

    private IResource[] getResources(SyncInfo[] infos) {
        IResource[] resources = new IResource[infos.length];
        for (int i = 0; i < resources.length; i++) {
            resources[i] = infos[i].getLocal();
        }
        return resources;
    }

    private void getOutOfSync(ISynchronizeModelElement node, List list) {
        SyncInfo info = getSyncInfo(node);
        if (info != null && info.getKind() != SyncInfo.IN_SYNC) {
            list.add(info);
        }
        IDiffElement[] children = node.getChildren();
        for (int i = 0; i < children.length; i++) {
            IDiffElement child = children[i];
            getOutOfSync((ISynchronizeModelElement)child, list);
        }
        return;
    }
    
    private SyncInfo getSyncInfo(ISynchronizeModelElement node) {
        if (node instanceof IAdaptable) {
            return (SyncInfo)((IAdaptable)node).getAdapter(SyncInfo.class);
        }
        return null;
    }

    private void assertResourcesAreTheSame(IResource[] resources1, IResource[] resources2) {
        assertEquals("The number of resources do not match the expected number", resources1.length, resources2.length);
        for (int i = 0; i < resources1.length; i++) {
            IResource resource = resources1[i];
            boolean found = false;
            for (int j = 0; j < resources2.length; j++) {
                IResource resource2 = resources2[j];
                if (resource2.equals(resource)) {
                    found = true;
                    break;
                }
            }
            assertTrue("Expected resource " + resource.getFullPath().toString() + " was not presebt", found);
        }
    }

    /*
     * Assert that the given resources make up the root set
     * displayed in the sync view. The root set is those 
     * resources that are not part of an active change set.
     */
    private void assertInRootSet(IResource[] resources) throws CoreException {
        ISynchronizeModelElement[] nodes = getNonChangeSetRoots(getModelRoot(getActiveChangeSetManager().getSubscriber()));
        List list = new ArrayList();
        for (int i = 0; i < nodes.length; i++) {
            ISynchronizeModelElement element = nodes[i];
            getOutOfSync(element, list);
        }
        IResource[] outOfSync = getResources((SyncInfo[]) list.toArray(new SyncInfo[list.size()]));
        assertResourcesAreTheSame(resources, outOfSync);
        
    }
    
    private ISynchronizeModelElement[] getNonChangeSetRoots(ISynchronizeModelElement modelRoot) {
        List result = new ArrayList();
        IDiffElement[] children = modelRoot.getChildren();
        for (int i = 0; i < children.length; i++) {
            IDiffElement element = children[i];
            if (!(element instanceof ChangeSetDiffNode)) {
                result.add(element);
            }
        }
        return (ISynchronizeModelElement[]) result.toArray(new ISynchronizeModelElement[result.size()]);
    }

    public void testSimpleCommit() throws CoreException {
	    IProject project = createProject(new String[] { "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
	    
	    // Modify a file in a copy
	    IProject copy = checkoutCopy(project, CVSTag.DEFAULT);
	    setContentsAndEnsureModified(copy.getFile("file1.txt"));
	    String message1 = "Commit 1";
	    commitResources(new IResource[] {copy}, IResource.DEPTH_INFINITE, message1);
	    assertIncomingChangesInSets(new IFile[][] {{ project.getFile("file1.txt") }}, new String[] {message1});
	    
	    // Modify the copy some more
	    setContentsAndEnsureModified(copy.getFile("file2.txt"));
	    setContentsAndEnsureModified(copy.getFile("folder1/a.txt"));
	    String message2 = "Commit 2";
	    commitResources(new IResource[] {copy}, IResource.DEPTH_INFINITE, message2);
	    assertIncomingChangesInSets(new IFile[][] {
	            { project.getFile("file1.txt") },
	            { project.getFile("file2.txt"), project.getFile("folder1/a.txt") }
	            }, new String[] {message1, message2});
	    
	    // Modify the copy some more
	    setContentsAndEnsureModified(copy.getFile("file2.txt"));
	    String message3 = "Commit 3";
	    commitResources(new IResource[] {copy}, IResource.DEPTH_INFINITE, message3);
	    assertIncomingChangesInSets(new IFile[][] {
	            { project.getFile("file1.txt") },
	            { project.getFile("folder1/a.txt") },
	            { project.getFile("file2.txt")}
	            }, new String[] {message1, message2, message3});
	}
	
    public void testSimpleActiveChangeSet() throws CoreException {
        IProject project = createProject(new String[] { "file1.txt", "file2.txt", "folder1/", "folder1/a.txt", "folder1/b.txt"});
        // Enable Change Sets
        enableChangeSets(getWorkspaceSubscriber());
	    // Add a folder and file
	    IFolder newFolder = project.getFolder("folder2");
        newFolder.create(false, true, null);
        IFile newFile = newFolder.getFile("file.txt");
        newFile.create(new ByteArrayInputStream("Hi There".getBytes()), false, null);
        // Create an active commit set and assert that it appears in the sync view
        SubscriberChangeSetCollector manager = getActiveChangeSetManager();
        ActiveChangeSet set = manager.createSet("test", new SyncInfo[0]);
        manager.add(set);
        assertInActiveSet(new IResource[] { }, set);
        assertInRootSet(new IResource[] {newFolder, newFile});
        // Add the new file to the set and assert that the file is in the set and the folder is still at the root
        set.add(new IResource[] { newFile });
        assertInActiveSet(new IResource[] { newFile }, set);
        assertInRootSet(new IResource[] {newFolder });
	    // Add the folder to the set
        set.add(new IResource[] { newFolder });
        assertInActiveSet(new IResource[] { newFolder, newFile }, set);
    }
}
