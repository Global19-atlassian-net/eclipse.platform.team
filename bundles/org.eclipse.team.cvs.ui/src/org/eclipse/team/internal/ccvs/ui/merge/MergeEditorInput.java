package org.eclipse.team.internal.ccvs.ui.merge;

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
import org.eclipse.team.internal.ccvs.core.resources.CVSRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.sync.CVSSyncCompareInput;
import org.eclipse.team.ui.sync.SyncView;

public class MergeEditorInput extends CVSSyncCompareInput {
	CVSTag start;
	CVSTag end;
	
	public MergeEditorInput(IResource[] resources, CVSTag start, CVSTag end) {
		super(resources);
		this.start = start;
		this.end = end;
	}
	public Viewer createDiffViewer(Composite parent) {
		Viewer viewer = super.createDiffViewer(parent);
		getViewer().syncModeChanged(SyncView.SYNC_MERGE);
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
				IRemoteResource base = CVSWorkspaceRoot.getRemoteTree(resource, start, Policy.subMonitorFor(monitor, 50));
				IRemoteResource remote = CVSWorkspaceRoot.getRemoteTree(resource, end, Policy.subMonitorFor(monitor, 50));
				trees[i] = new CVSRemoteSyncElement(true /*three way*/, resource, base, remote);				 
			}
		} finally {
			monitor.done();
		}
		return trees;
	}
	public CVSTag getStartTag() {
		return start;
	}
	public CVSTag getEndTag() {
		return end;
	}
	public String getTitle() {
		return Policy.bind("MergeEditorInput.title", start.getName(), end.getName()); //$NON-NLS-1$
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