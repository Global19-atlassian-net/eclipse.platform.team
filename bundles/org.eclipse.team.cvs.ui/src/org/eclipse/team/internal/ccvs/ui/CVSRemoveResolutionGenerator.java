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

package org.eclipse.team.internal.ccvs.ui;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFileState;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.MutableResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.AddDeleteMoveListener;
import org.eclipse.ui.IMarkerResolution;

/**
 * Generate marker resoultions for a cvs remove marker
 */
public class CVSRemoveResolutionGenerator extends CVSAbstractResolutionGenerator {
	IMarkerResolution commitDeletion =new IMarkerResolution() {
		public String getLabel() {
			return Policy.bind("CVSRemoveResloutionGenerator.Commit_Deletion_to_CVS_1"); //$NON-NLS-1$
		}
		public void run(IMarker marker) {
			try {
				final IContainer parent = (IContainer)marker.getResource();
				final String childName = (String)marker.getAttribute(AddDeleteMoveListener.NAME_ATTRIBUTE);
				ICVSFile mFile = CVSWorkspaceRoot.getCVSFileFor(parent.getFile(new Path(childName)));
				final TeamException[] exception = new TeamException[] {null};
				CVSRemoveResolutionGenerator.this.run(new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor)throws InvocationTargetException, InterruptedException {
						try {
							((CVSTeamProvider)RepositoryProvider.getProvider(parent.getProject())).checkin(new IResource[] {parent.getFile(new Path(childName))}, IResource.DEPTH_ZERO, monitor);
						} catch (TeamException e) {
							exception[0] = e;
						}
					}
				});
				if (exception[0] != null) {
					throw exception[0];
				}
				marker.delete();
			} catch (TeamException e) {
				handle(e, null, null);
			} catch (CoreException e) {
				handle(e, null, null);
			} catch (InvocationTargetException e) {
				handle(e, null, null);
			}  catch (InterruptedException e) {
				// do nothing
			}
		}
	};

	IMarkerResolution undoDeletionLocal = new IMarkerResolution() {
		public String getLabel() {
			return Policy.bind("CVSRemoveResloutionGenerator.Undo_Deletion_from_Local_History_2"); //$NON-NLS-1$
		}
		public void run(IMarker marker) {
			try {
				final IContainer parent = (IContainer)marker.getResource();
				final String childName = (String)marker.getAttribute(AddDeleteMoveListener.NAME_ATTRIBUTE);
				final IFile file = parent.getFile(new Path(childName));
				final ICVSFile mFile = CVSWorkspaceRoot.getCVSFileFor(parent.getFile(new Path(childName)));
				
				boolean recreated = false;
				IFileState[] history = file.getHistory(null);
				for (int i = 0; i < history.length; i++) {
					IFileState state = history[i];
					if (state.exists()) {
						file.create(state.getContents(), false, null);
						mFile.setTimeStamp(new Date(state.getModificationTime()));
						recreated = true;
						break;
					}
				}
				
				if (recreated) {
					if (mFile.isManaged()) {
						ResourceSyncInfo info = mFile.getSyncInfo();
						if (info.isDeleted()) {
							MutableResourceSyncInfo deletedInfo = info.cloneMutable();
							deletedInfo.setDeleted(false);
							mFile.setSyncInfo(deletedInfo, ICVSFile.UNKNOWN);
						}
					}
					marker.delete();
				} else {
					throw new CVSException(Policy.bind("CVSRemoveResloutionGenerator.No_local_history_available._Try_undoing_from_the_server_3")); //$NON-NLS-1$
				}
			} catch (TeamException e) {
				handle(e, null, null);
			} catch (CoreException e) {
				handle(e, null, null);
			}
		}
	};
		
	IMarkerResolution undoDeletion = new IMarkerResolution() {
		public String getLabel() {
			return Policy.bind("CVSRemoveResloutionGenerator.Undo_Deletion_from_CVS_Server_4"); //$NON-NLS-1$
		}
		public void run(IMarker marker) {
			try {
				final IContainer parent = (IContainer)marker.getResource();
				final String childName = (String)marker.getAttribute(AddDeleteMoveListener.NAME_ATTRIBUTE);
				final IFile file = parent.getFile(new Path(childName));
				final ICVSFile mFile = CVSWorkspaceRoot.getCVSFileFor(parent.getFile(new Path(childName)));
				
				if (mFile.isManaged()) {
					ResourceSyncInfo info = mFile.getSyncInfo();
					if (info.isDeleted()) {
						MutableResourceSyncInfo deletedInfo = info.cloneMutable();
						deletedInfo.setDeleted(false);
						mFile.setSyncInfo(deletedInfo, ICVSFile.UNKNOWN);
					}
				}
				
				final TeamException[] exception = new TeamException[] {null};
				CVSRemoveResolutionGenerator.this.run(new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor)throws InvocationTargetException, InterruptedException {
						try {
							CVSTeamProvider provider = (CVSTeamProvider)RepositoryProvider.getProvider(parent.getProject());
							provider.update(new IResource[] { parent.getFile(new Path(childName)) },
								Command.NO_LOCAL_OPTIONS, null, true /*createBackups*/, monitor);
						} catch (TeamException e) {
							exception[0] = e;
						}
					}
				});
				if (exception[0] != null) {
					throw exception[0];
				}			
				marker.delete();
			} catch (TeamException e) {
				handle(e, null, null);
			} catch (CoreException e) {
				handle(e, null, null);
			} catch (InvocationTargetException e) {
				handle(e, null, null);
			}  catch (InterruptedException e) {
				// do nothing
			}
		}
	};
	
	/*
	 * @see IMarkerResolutionGenerator#getResolutions(IMarker)
	 */
	public IMarkerResolution[] getResolutions(IMarker marker) {
		return new IMarkerResolution[] {
			commitDeletion, undoDeletionLocal, undoDeletion
		};
	}
}
