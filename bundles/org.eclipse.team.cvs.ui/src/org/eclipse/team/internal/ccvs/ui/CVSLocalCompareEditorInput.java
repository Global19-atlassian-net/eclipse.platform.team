package org.eclipse.team.internal.ccvs.ui;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.core.sync.IRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.resources.CVSCompareSyncElement;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.sync.CVSSyncCompareInput;
import org.eclipse.team.ui.sync.SyncView;

public class CVSLocalCompareEditorInput extends CVSSyncCompareInput {
	CVSTag[] tags;
	
	public CVSLocalCompareEditorInput(IResource[] resources, CVSTag[] tags) {
		super(resources);
		this.tags = tags;
	}
	
	public CVSLocalCompareEditorInput(IResource[] resources, CVSTag tag) {
		super(resources);
		this.tags = new CVSTag[] {tag};
	}
	
	public Viewer createDiffViewer(Composite parent) {
		Viewer viewer = super.createDiffViewer(parent);
		getViewer().syncModeChanged(SyncView.SYNC_COMPARE);
		return viewer;
	}
	
	protected IRemoteSyncElement[] createSyncElements(IProgressMonitor monitor) throws TeamException {
		IResource[] resources = getResources();
		IRemoteSyncElement[] trees = new IRemoteSyncElement[resources.length];
		int work = 100 * resources.length;
		monitor.beginTask(null, work);
		try {
			for (int i = 0; i < trees.length; i++) {
				IResource resource = resources[i];
				CVSTag tag = i > tags.length ? tags[0] : tags[i];
				IRemoteResource remote = CVSWorkspaceRoot.getRemoteTree(resource, tag, Policy.subMonitorFor(monitor, 50));
				trees[i] = new CVSCompareSyncElement(resource, remote);				 
			}
		} finally {
			monitor.done();
		}
		//getViewer().resetFilters();
		return trees;
	}
	
	public String getTitle() {
		return "CVS Compare [" + tags[0].getName() +"]";
	}
	
	public boolean isSaveNeeded() {
		return false;
	}
	
	protected void contentsChanged(ICompareInput source) {
	}
	
	/*
	 * @see SyncCompareInput#getSyncGranularity()
	 */
	protected int getSyncGranularity() {
		// we have to perform content comparison since files in different branches
		// may have different revisions but the same contents. Consider these files
		// for merge purposes as equal.
		return IRemoteSyncElement.GRANULARITY_CONTENTS;
	}
}