package org.eclipse.team.internal.ccvs.ui.merge;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.ccvs.core.CVSTag;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.core.sync.RemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Update;
import org.eclipse.team.internal.ccvs.core.resources.CVSRemoteSyncElement;
import org.eclipse.team.internal.ccvs.ui.RepositoryManager;
import org.eclipse.team.internal.ccvs.ui.sync.CVSSyncCompareInput;
import org.eclipse.team.internal.ccvs.ui.sync.UpdateSyncAction;
import org.eclipse.team.ui.sync.ITeamNode;

/*
 * To be done:
 * 1. add another action that allows a force merge merging since we can't tell the manual vs automatic conflicts when building the sync tree.
 * 2. fix progress monitoring
 */
public class UpdateMergeAction extends UpdateSyncAction {
	public UpdateMergeAction(CVSSyncCompareInput model, ISelectionProvider sp, String label, Shell shell) {
		super(model, sp, label, shell);
	}
		
	/*
	 * @see UpdateSyncAction#runUpdateDeep(IProgressMonitor, List, RepositoryManager)
 	 * incoming-change
 	 * incoming-deletion
	 */
	protected void runUpdateDeep(ITeamNode[] nodes, RepositoryManager manager, IProgressMonitor monitor) throws TeamException {		
		ITeamNode[] incoming = removeOutgoing(nodes);
		for (int i = 0; i < incoming.length; i++) {
			CVSRemoteSyncElement element = getSyncElementFrom(incoming[i]);
			if(element!=null) {
				makeRemoteLocal(element);
			}
		}
	}
		
	/*
	 * @see UpdateSyncAction#runUpdateIgnoreLocalShallow(IProgressMonitor, List, RepositoryManager)
	 * incoming-addition
	 * incoming-conflict (no-merge)
	 */
	protected void runUpdateIgnoreLocalShallow(ITeamNode[] nodes, RepositoryManager manager, IProgressMonitor monitor)	throws TeamException {
		runUpdateDeep(nodes, manager, monitor);
	}

	/*
	 * @see UpdateSyncAction#runUpdateShallow(ITeamNode[], RepositoryManager, IProgressMonitor)
	 */
	protected void runUpdateShallow(ITeamNode[] nodes, RepositoryManager manager, IProgressMonitor monitor) throws TeamException {	
		CVSTag startTag = ((MergeEditorInput)getDiffModel()).getStartTag();
		CVSTag endTag = ((MergeEditorInput)getDiffModel()).getEndTag();
	
		Command.LocalOption[] options = new Command.LocalOption[] {
			Command.DO_NOT_RECURSE,
			Update.makeArgumentOption(Update.JOIN, startTag.getName()),
			Update.makeArgumentOption(Update.JOIN, endTag.getName()) };

		// run a join update using the start and end tags and the join points
		manager.update(getIResourcesFrom(nodes), options, false, monitor);
	}

	private ITeamNode[] removeOutgoing(ITeamNode[] nodes) {
		List incomingNodes = new ArrayList();
		for (int i = 0; i < nodes.length; i++) {
			// filter out pseudo conflicts
			if((nodes[i].getKind() & RemoteSyncElement.PSEUDO_CONFLICT) == 0) {
				switch((nodes[i].getKind() & RemoteSyncElement.DIRECTION_MASK)) {
					case RemoteSyncElement.OUTGOING: break; // ignore
					case RemoteSyncElement.INCOMING:
					case RemoteSyncElement.CONFLICTING:
						incomingNodes.add(nodes[i]);
				}
			}
		}
		return (ITeamNode[])incomingNodes.toArray(new ITeamNode[incomingNodes.size()]);
	}
	
	private void makeRemoteLocal(CVSRemoteSyncElement element) throws CVSException {
		IRemoteResource remote = element.getRemote();
		IResource local = element.getLocal();
		try {
			if(remote==null) {
				local.delete(false, null);
			} else {
				if(remote.isContainer()) {
					if(!local.exists()) {
						((IFolder)local).create(false /*don't force*/, true /*local*/, null);
					}
				} else {
					IFile localFile = (IFile)local;
					if(local.exists()) {
						localFile.setContents(remote.getContents(new NullProgressMonitor()), false /*don't force*/, true /*keep history*/, null);
					} else {
						localFile.create(remote.getContents(new NullProgressMonitor()), false /*don't force*/, null);
					}
				}
			}
		} catch(CoreException e) {
			throw new CVSException("Problems merging remote resources into workspace", e);
		} catch(TeamException e) {
			throw new CVSException("Problems merging remote resources into workspace", e);
		}
	}	
}