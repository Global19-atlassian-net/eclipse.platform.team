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
package org.eclipse.team.internal.ccvs.ui.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.compare.CompareUI;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.RDiff;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.client.listeners.RDiffSummaryListener;
import org.eclipse.team.internal.ccvs.core.resources.FileContentCachingService;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFile;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFolderTree;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.ui.CVSCompareEditorInput;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.ResourceEditionNode;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.ui.IWorkbenchPage;

/**
 * Compare the two versions of given remote folders obtained from the two tags specified.
 */
public class RemoteCompareOperation extends RemoteOperation  implements RDiffSummaryListener.IFileDiffListener {

	private CVSTag left;
	private CVSTag right;
	
	private RemoteFolderTree leftTree, rightTree;

	/**
	 * Compare two versions of the given remote resource.
	 * @param shell
	 * @param remoteResource the resource whose tags are being compared
	 * @param left the earlier tag (not null)
	 * @param right the later tag (not null)
	 */
	public RemoteCompareOperation(Shell shell, ICVSRemoteResource remoteResource, CVSTag tag) {
		super(shell, new ICVSRemoteResource[] {remoteResource});
		Assert.isNotNull(tag);
		this.right = tag;
		if (remoteResource.isContainer()) {
			this.left = ((ICVSRemoteFolder)remoteResource).getTag();
		} else {
			try {
				this.left = remoteResource.getSyncInfo().getTag();
			} catch (CVSException e) {
				// This shouldn't happen but log it just in case
				CVSProviderPlugin.log(e);
			}
		}
		if (this.left == null) {
			this.left = CVSTag.DEFAULT;
		}
	}

	/*
	 * This command only supports the use of a single resource
	 */
	private ICVSRemoteResource getRemoteResource() {
		return getRemoteResources()[0];
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CVSOperation#execute(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void execute(IProgressMonitor monitor) throws CVSException {
		leftTree = rightTree = null;
		boolean fetchContents = CVSUIPlugin.getPlugin().getPluginPreferences().getBoolean(ICVSUIConstants.PREF_CONSIDER_CONTENTS);
		monitor.beginTask(getTaskName(), 50 + (fetchContents ? 100 : 0));
		try {
			ICVSRemoteResource resource = getRemoteResource();
			IStatus status = buildTrees(resource, Policy.subMonitorFor(monitor, 50));
			if (status.isOK() && fetchContents) {
				fetchFileContents(leftTree, Policy.subMonitorFor(monitor, 50));
				fetchFileContents(rightTree, Policy.subMonitorFor(monitor, 50));
			}
			collectStatus(status);
			openCompareEditor(leftTree, rightTree);
		} finally {
			monitor.done();
		}
	}

	private void fetchFileContents(RemoteFolderTree tree, IProgressMonitor monitor) throws CVSException {
		String[] filePaths = getFilePaths(tree);
		if (filePaths.length > 0) {
			FileContentCachingService.fetchFileContents(tree, filePaths, monitor);
		}
		
	}

	private String[] getFilePaths(RemoteFolderTree tree) {
		ICVSRemoteResource[] children = tree.getChildren();
		List result = new ArrayList();
		for (int i = 0; i < children.length; i++) {
			ICVSRemoteResource resource = children[i];
			if (resource.isContainer()) {
				result.addAll(Arrays.asList(getFilePaths((RemoteFolderTree)resource)));
			} else {
				result.add(resource.getRepositoryRelativePath());
			}
		}
		return (String[]) result.toArray(new String[result.size()]);
	}

	/*
	 * Build the two trees uses the reponses from "cvs rdiff -s ...".
	 */
	private IStatus buildTrees(ICVSRemoteResource resource, IProgressMonitor monitor) throws CVSException {
		// Initialize the resulting trees
		leftTree = new RemoteFolderTree(null, resource.getRepository(), ICVSRemoteFolder.REPOSITORY_ROOT_FOLDER_NAME, left);
		rightTree = new RemoteFolderTree(null, resource.getRepository(), ICVSRemoteFolder.REPOSITORY_ROOT_FOLDER_NAME, right);
		Command.QuietOption oldOption= CVSProviderPlugin.getPlugin().getQuietness();
		Session session = new Session(resource.getRepository(), leftTree, false);
		try {
			monitor.beginTask(getTaskName(), 100);
			CVSProviderPlugin.getPlugin().setQuietness(Command.VERBOSE);
			session.open(Policy.subMonitorFor(monitor, 10));
			IStatus status = Command.RDIFF.execute(session,
					Command.NO_GLOBAL_OPTIONS,
					getLocalOptions(),
					new ICVSResource[] { resource },
					new RDiffSummaryListener(this),
					Policy.subMonitorFor(monitor, 90));
			return status;
		} finally {
			try {
				session.close();
			} finally {
				CVSProviderPlugin.getPlugin().setQuietness(oldOption);
			}
			monitor.done();
		}
	}

	private LocalOption[] getLocalOptions() {
		return new LocalOption[] {RDiff.SUMMARY, RDiff.makeTagOption(left), RDiff.makeTagOption(right)};
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CVSOperation#getTaskName()
	 */
	protected String getTaskName() {
		return Policy.bind("RemoteCompareOperation.0", new Object[] {left.getName(), right.getName(), getRemoteResource().getRepositoryRelativePath()}); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.RDiffSummaryListener.IFileDiffListener#fileDiff(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void fileDiff(String remoteFilePath, String leftRevision, String rightRevision) {
		try {
			addFile(rightTree, right, new Path(remoteFilePath), rightRevision);
		} catch (CVSException e) {
			CVSUIPlugin.log(e);
		}
		try {
			addFile(leftTree, left, new Path(remoteFilePath), leftRevision);
		} catch (CVSException e) {
			CVSUIPlugin.log(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.RDiffSummaryListener.IFileDiffListener#newFile(java.lang.String, java.lang.String)
	 */
	public void newFile(String remoteFilePath, String rightRevision) {
		try {
			addFile(rightTree, right, new Path(remoteFilePath), rightRevision);
		} catch (CVSException e) {
			CVSUIPlugin.log(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.RDiffSummaryListener.IFileDiffListener#deletedFile(java.lang.String)
	 */
	public void deletedFile(String remoteFilePath) {
		try {
			addFile(leftTree, left, new Path(remoteFilePath), null);
		} catch (CVSException e) {
			CVSUIPlugin.log(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.RDiffSummaryListener.IFileDiffListener#directory(java.lang.String)
	 */
	public void directory(String remoteFolderPath) {
		try {
			getFolder(leftTree, left, new Path(remoteFolderPath), Path.EMPTY);
		} catch (CVSException e) {
			CVSUIPlugin.log(e);
		}
		try {
			getFolder(rightTree, right, new Path(remoteFolderPath), Path.EMPTY);
		} catch (CVSException e) {
			CVSUIPlugin.log(e);
		}
	}

	/* 
	 * Get the folder at the given path in the given tree, creating any missing folders as needed.
	 */
	private ICVSRemoteFolder getFolder(RemoteFolderTree tree, CVSTag tag, IPath remoteFolderPath, IPath parentPath) throws CVSException {
		if (remoteFolderPath.segmentCount() == 0) return tree;
		String name = remoteFolderPath.segment(0);
		ICVSResource child;
		IPath childPath = parentPath.append(name);
		if (tree.childExists(name)) {
			child = tree.getChild(name);
		}  else {
			child = new RemoteFolderTree(tree, tree.getRepository(), childPath.toString(), tag);
			((RemoteFolderTree)child).setChildren(new ICVSRemoteResource[0]);
			addChild(tree, (ICVSRemoteResource)child);
		}
		return getFolder((RemoteFolderTree)child, tag, remoteFolderPath.removeFirstSegments(1), childPath);
	}

	private void addChild(RemoteFolderTree tree, ICVSRemoteResource resource) {
		ICVSRemoteResource[] children = tree.getChildren();
		ICVSRemoteResource[] newChildren;
		if (children == null) {
			newChildren = new ICVSRemoteResource[] { resource };
		} else {
			newChildren = new ICVSRemoteResource[children.length + 1];
			System.arraycopy(children, 0, newChildren, 0, children.length);
			newChildren[children.length] = resource;
		}
		tree.setChildren(newChildren);
	}

	private void addFile(RemoteFolderTree tree, CVSTag tag, Path filePath, String revision) throws CVSException {
		RemoteFolderTree parent = (RemoteFolderTree)getFolder(tree, tag, filePath.removeLastSegments(1), Path.EMPTY);
		String name = filePath.lastSegment();
		ICVSRemoteFile file = new RemoteFile(parent, 0, name, revision, null, tag);
		addChild(parent, file);
	}
	
	/*
	 * Only intended to be overridden by test cases.
	 */
	protected void openCompareEditor(final ICVSRemoteFolder leftTree, final ICVSRemoteFolder rightTree) {
		if (leftTree == null || rightTree == null) return;
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				CompareUI.openCompareEditorOnPage(
						new CVSCompareEditorInput(new ResourceEditionNode(leftTree), new ResourceEditionNode(rightTree)),
						getTargetPage());
			}
		});
	}
	
	protected IWorkbenchPage getTargetPage() {
		return TeamUIPlugin.getActivePage();
	}
}
