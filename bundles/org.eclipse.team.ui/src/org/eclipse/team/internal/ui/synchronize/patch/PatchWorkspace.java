/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.synchronize.patch;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.compare.internal.core.patch.*;
import org.eclipse.compare.internal.patch.*;
import org.eclipse.compare.structuremergeviewer.*;
import org.eclipse.core.internal.runtime.AdapterManager;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IAdaptable;

// TODO: extend PatchDiffNode, update navigatorContent triggerPoints when done
public class PatchWorkspace extends DiffNode implements IAdaptable {

	private static PatchWorkspace instance;
	private IWorkspaceRoot root;
	private WorkspacePatcher patcher;
	
	private PatchWorkspace(IWorkspaceRoot root, WorkspacePatcher patcher) {
		super(null, Differencer.NO_CHANGE);
		this.root = root;
		this.patcher = patcher;
	}
	
	public static PatchWorkspace getInstance() {
	      return instance;
	}
	
	// this should be called before any getInstance
	// TODO: don't use singleton!
	public static Object create(IWorkspaceRoot root, WorkspacePatcher patcher) {
		instance = new PatchWorkspace(root, patcher);
		return instance;
	}
	
	public WorkspacePatcher getPatcher() {
		return patcher;
	}
	
	public IResource getResource() {
		return root;
	}

	public String getName() {
		return "Patch Root Workspace"; //$NON-NLS-1$
	}

	public IDiffContainer getParent() {
		return null;
	}

	public IDiffElement[] getChildren() {
		/*
		 * Create a complete tree of patch model objects - elements of the
		 * patch, but return only top-level ones: PatchProjectDiffNode(s) for a
		 * workspace patch or FileDiffResult(s) otherwise. See
		 * org.eclipse.compare.internal
		 * .patch.PatchCompareEditorInput.buildTree()
		 */
		IDiffElement[] children;
		if (getPatcher().isWorkspacePatch()) {
			children = processProjects(getPatcher().getDiffProjects());
		} else {
			children = processDiffs(getPatcher().getDiffs());
		}
		return children;
	}

	// see org.eclipse.compare.internal.patch.PatchCompareEditorInput.processDiffs(FilePatch2[])
	private IDiffElement[] processDiffs(FilePatch2[] diffs) { 
		List result = new ArrayList();
		for (int i = 0; i < diffs.length; i++) {
			result.addAll(processDiff(diffs[i], this));
		}
		return (IDiffElement[]) result.toArray(new IDiffElement[result.size()]);
	}
	
	// see org.eclipse.compare.internal.patch.PatchCompareEditorInput.processProjects(DiffProject[])
	private IDiffElement[] processProjects(DiffProject[] diffProjects) {
		List result = new ArrayList();
		for (int i = 0; i < diffProjects.length; i++) {
			PatchProjectDiffNode projectNode = new PatchProjectDiffNode(this, diffProjects[i], getPatcher().getConfiguration());
			result.add(projectNode);
			FilePatch2[] diffs = diffProjects[i].getFileDiffs();
			for (int j = 0; j < diffs.length; j++) {
				FilePatch2 fileDiff = diffs[j];
				processDiff(fileDiff, projectNode);
			}
		}
		return (IDiffElement[]) result.toArray(new IDiffElement[result.size()]);
	}
	
	// see org.eclipse.compare.internal.patch.PatchCompareEditorInput.processDiff(FilePatch2, DiffNode)
	private List/*<IDiffElement>*/ processDiff(FilePatch2 diff, DiffNode parent) {
		List result = new ArrayList();
		FileDiffResult diffResult = getPatcher().getDiffResult(diff);
		PatchFileDiffNode node = PatchFileDiffNode.createDiffNode(parent, diffResult);
		result.add(node);
		HunkResult[] hunkResults = diffResult.getHunkResults();
		for (int i = 0; i < hunkResults.length; i++) {
			HunkResult hunkResult = hunkResults[i];
			/*HunkDiffNode hunkDiffNode =*/ HunkDiffNode.createDiffNode(node, hunkResult, false, true, false);
			// result.add(hunkDiffNode);
		}
		return result;
	}
	
	// cannot extend PlatformObject (already extends DiffNode) so implement
	// IAdaptable
	public Object getAdapter(Class adapter) {
		return AdapterManager.getDefault().getAdapter(this, adapter);
	}
}
