package org.eclipse.team.internal.ccvs.ui.sync;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
 
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.core.sync.IRemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ILogEntry;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.ui.CVSDecoration;
import org.eclipse.team.internal.ccvs.ui.CVSDecorationRunnable;
import org.eclipse.team.internal.ccvs.ui.CVSDecoratorConfiguration;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.HistoryView;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.OverlayIcon;
import org.eclipse.team.internal.ccvs.ui.OverlayIconCache;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.merge.OverrideUpdateMergeAction;
import org.eclipse.team.internal.ccvs.ui.merge.UpdateMergeAction;
import org.eclipse.team.internal.ccvs.ui.merge.UpdateWithForcedJoinAction;
import org.eclipse.team.ui.sync.CatchupReleaseViewer;
import org.eclipse.team.ui.sync.ITeamNode;
import org.eclipse.team.ui.sync.MergeResource;
import org.eclipse.team.ui.sync.SyncView;
import org.eclipse.team.ui.sync.TeamFile;

public class CVSCatchupReleaseViewer extends CatchupReleaseViewer {
	// Actions
	private UpdateSyncAction updateAction;
	private ForceUpdateSyncAction forceUpdateAction;
	private CommitSyncAction commitAction;
	private ForceCommitSyncAction forceCommitAction;
	private UpdateMergeAction updateMergeAction;
	private UpdateWithForcedJoinAction updateWithJoinAction;
	private IgnoreAction ignoreAction;
	private HistoryAction showInHistory;
	private OverrideUpdateMergeAction forceUpdateMergeAction;
	
	private static class DiffOverlayIcon extends OverlayIcon {
		private static final int HEIGHT = 16;
		private static final int WIDTH = 22;
		public DiffOverlayIcon(Image baseImage, ImageDescriptor overlay) {
			super(baseImage, new ImageDescriptor[] { overlay }, new Point(WIDTH, HEIGHT));
		}
		protected void drawOverlays(ImageDescriptor[] overlays) {
			ImageDescriptor overlay = overlays[0];
			ImageData overlayData = overlay.getImageData();
			drawImage(overlayData, WIDTH - overlayData.width, (HEIGHT - overlayData.height) / 2);
		}
	}
	
	private static class HistoryAction extends Action implements ISelectionChangedListener {
		IStructuredSelection selection;
		public HistoryAction(String label) {
			super(label);
		}
		public void run() {
			if (selection.isEmpty()) {
				return;
			}
			HistoryView view = HistoryView.openInActivePerspective();
			if (view == null) {
				return;
			}
			ITeamNode node = (ITeamNode)selection.getFirstElement();
			IRemoteSyncElement remoteSyncElement = ((TeamFile)node).getMergeResource().getSyncElement();
			ICVSRemoteFile remoteFile = (ICVSRemoteFile)remoteSyncElement.getRemote();
			IResource local = remoteSyncElement.getLocal();
			ICVSRemoteFile baseFile = (ICVSRemoteFile)remoteSyncElement.getBase();
			
			// can only show history if remote exists or local has a base.
			String currentRevision = null;
			try {
				currentRevision = baseFile!=null ? baseFile.getRevision(): null;
			} catch(TeamException e) {
				CVSUIPlugin.log(e.getStatus());
			}
			if(remoteFile!=null) {
				view.showHistory(remoteFile, currentRevision);
			} else if(baseFile!=null) {
				view.showHistory(baseFile, currentRevision);
			}
		}
		public void selectionChanged(SelectionChangedEvent event) {
			ISelection selection = event.getSelection();
			if (!(selection instanceof IStructuredSelection)) {
				setEnabled(false);
				return;
			}
			IStructuredSelection ss = (IStructuredSelection)selection;
			if (ss.size() != 1) {
				setEnabled(false);
				return;
			}
			ITeamNode first = (ITeamNode)ss.getFirstElement();
			if (first instanceof TeamFile) {
				// can only show history on elements that have a remote file
				this.selection = ss;
				IRemoteSyncElement remoteSyncElement = ((TeamFile)first).getMergeResource().getSyncElement();
				if(remoteSyncElement.getRemote() != null || remoteSyncElement.getBase() != null) {
					setEnabled(true);
				} else {
					setEnabled(false);
				}
			} else {
				this.selection = null;
				setEnabled(false);
			}
		}
	}
	
	public CVSCatchupReleaseViewer(Composite parent, CVSSyncCompareInput model) {
		super(parent, model);
		initializeActions(model);
		initializeLabelProvider();
	}
	
	private void initializeLabelProvider() {
		final ImageDescriptor conflictDescriptor = CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_MERGEABLE_CONFLICT);
		final ImageDescriptor questionableDescriptor = CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_QUESTIONABLE);
		final LabelProvider oldProvider = (LabelProvider)getLabelProvider();
		setLabelProvider(new LabelProvider() {
			private OverlayIconCache iconCache = new OverlayIconCache();
			
			public void dispose() {
				iconCache.disposeAll();
				oldProvider.dispose();
			}
			public Image getImage(Object element) {
				Image image = oldProvider.getImage(element);
				if (element instanceof ITeamNode) {
					ITeamNode node = (ITeamNode)element;
					int kind = node.getKind();
					if ((kind & IRemoteSyncElement.AUTOMERGE_CONFLICT) != 0) {
						return iconCache.getImageFor(new DiffOverlayIcon(image, conflictDescriptor));
					}
					if (kind == (IRemoteSyncElement.OUTGOING | IRemoteSyncElement.ADDITION)) {
						IResource resource = node.getResource();
						if (resource.getType() == IResource.FILE) {
							try {
								ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile) resource);
								if (cvsFile.getSyncInfo() == null) {
									return iconCache.getImageFor(new DiffOverlayIcon(image, questionableDescriptor));
								}
							} catch (TeamException e) {
								ErrorDialog.openError(getControl().getShell(), null, null, e.getStatus());
								// Fall through and return the default image
							}
						}
					}
				}
				return image;
			}
			public String getText(Object element) {
				if (element instanceof ITeamNode) {					
					ITeamNode node = (ITeamNode)element;					
					IResource resource = node.getResource();
					if (resource.exists()) {
						
						// use the default text decoration preferences
						CVSDecoration decoration = CVSDecorationRunnable.computeTextLabelFor(resource, false /*don't show dirty*/);
						String format = decoration.getFormat();
						Map bindings = decoration.getBindings();
						
						// don't show the revision number, it will instead be shown in 
						// the label for the remote/base/local files editors
						bindings.remove(CVSDecoratorConfiguration.FILE_REVISION);
						
						bindings.put(CVSDecoratorConfiguration.RESOURCE_NAME, oldProvider.getText(element));
						return CVSDecoratorConfiguration.bind(format, bindings);
					}
				}								
				return oldProvider.getText(element);
			}
		});
	}
	
	protected void fillContextMenu(IMenuManager manager) {
		super.fillContextMenu(manager);
		if (showInHistory != null) {
			manager.add(showInHistory);
		}
		manager.add(new Separator());
		switch (getSyncMode()) {
			case SyncView.SYNC_INCOMING:
				updateAction.update(SyncView.SYNC_INCOMING);
				manager.add(updateAction);
				forceUpdateAction.update(SyncView.SYNC_INCOMING);
				manager.add(forceUpdateAction);
				break;
			case SyncView.SYNC_OUTGOING:
				commitAction.update(SyncView.SYNC_OUTGOING);
				manager.add(commitAction);
				forceCommitAction.update(SyncView.SYNC_OUTGOING);
				manager.add(forceCommitAction);
				ignoreAction.update();
				manager.add(ignoreAction);
				break;
			case SyncView.SYNC_BOTH:
				commitAction.update(SyncView.SYNC_BOTH);
				manager.add(commitAction);
				updateAction.update(SyncView.SYNC_BOTH);
				manager.add(updateAction);
				manager.add(new Separator());
				forceCommitAction.update(SyncView.SYNC_BOTH);
				manager.add(forceCommitAction);
				forceUpdateAction.update(SyncView.SYNC_BOTH);
				manager.add(forceUpdateAction);				
				break;
			case SyncView.SYNC_MERGE:
				updateMergeAction.update(SyncView.SYNC_INCOMING);
				forceUpdateMergeAction.update(SyncView.SYNC_INCOMING);
				updateWithJoinAction.update(SyncView.SYNC_INCOMING);
				manager.add(updateMergeAction);
				manager.add(forceUpdateMergeAction);
				manager.add(updateWithJoinAction);
				break;
		}
	}
	
	/**
	 * Creates the actions for this viewer.
	 */
	private void initializeActions(final CVSSyncCompareInput diffModel) {
		Shell shell = getControl().getShell();
		commitAction = new CommitSyncAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.commit"), shell); //$NON-NLS-1$
		forceCommitAction = new ForceCommitSyncAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.forceCommit"), shell); //$NON-NLS-1$
		updateAction = new UpdateSyncAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.update"), shell); //$NON-NLS-1$
		forceUpdateAction = new ForceUpdateSyncAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.forceUpdate"), shell); //$NON-NLS-1$
		updateMergeAction = new UpdateMergeAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.update"), shell); //$NON-NLS-1$
		ignoreAction = new IgnoreAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.ignore"), shell); //$NON-NLS-1$
		updateWithJoinAction = new UpdateWithForcedJoinAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.mergeUpdate"), shell); //$NON-NLS-1$
		forceUpdateMergeAction = new OverrideUpdateMergeAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.forceUpdate"), shell); //$NON-NLS-1$
		
		// Show in history view
		showInHistory = new HistoryAction(Policy.bind("CVSCatchupReleaseViewer.showInHistory")); //$NON-NLS-1$
		addSelectionChangedListener(showInHistory);	
	}
	
	/**
	 * Provide CVS-specific labels for the editors.
	 */
	protected void updateLabels(MergeResource resource) {
		CompareConfiguration config = getCompareConfiguration();
		String name = resource.getName();
		config.setLeftLabel(Policy.bind("CVSCatchupReleaseViewer.workspaceFile", name)); //$NON-NLS-1$
	
		IRemoteSyncElement syncTree = resource.getSyncElement();
		IRemoteResource remote = syncTree.getRemote();
		if (remote != null) {
			try {
				final ICVSRemoteFile remoteFile = (ICVSRemoteFile)remote;
				String revision = remoteFile.getRevision();
				final String[] author = new String[] { "" }; //$NON-NLS-1$
				try {
					CVSUIPlugin.runWithProgress(getTree().getShell(), true /*cancelable*/,
						new IRunnableWithProgress() {
						public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
							try {
								ILogEntry logEntry = remoteFile.getLogEntry(monitor);
								author[0] = logEntry.getAuthor();
							} catch (TeamException e) {
								throw new InvocationTargetException(e);
							}
						}
					});
				} catch (InterruptedException e) { // ignore cancellation
				} catch (InvocationTargetException e) {
					Throwable t = e.getTargetException();
					if (t instanceof TeamException) {
						throw (TeamException) t;
					}
					// should not get here
				}
				config.setRightLabel(Policy.bind("CVSCatchupReleaseViewer.repositoryFileRevision", new Object[] {name, revision, author[0]})); //$NON-NLS-1$
			} catch (TeamException e) {
				ErrorDialog.openError(getControl().getShell(), null, null, e.getStatus());
				config.setRightLabel(Policy.bind("CVSCatchupReleaseViewer.repositoryFile", name)); //$NON-NLS-1$
			}
		} else {
			config.setRightLabel(Policy.bind("CVSCatchupReleaseViewer.noRepositoryFile")); //$NON-NLS-1$
		}
	
		IRemoteResource base = syncTree.getBase();
		if (base != null) {
			try {
				String revision = ((ICVSRemoteFile)base).getRevision();
				config.setAncestorLabel(Policy.bind("CVSCatchupReleaseViewer.commonFileRevision", new Object[] {name, revision} )); //$NON-NLS-1$
			} catch (TeamException e) {
				ErrorDialog.openError(getControl().getShell(), null, null, e.getStatus());
				config.setRightLabel(Policy.bind("CVSCatchupReleaseViewer.commonFile", name)); //$NON-NLS-1$
			}
		} else {
			config.setAncestorLabel(Policy.bind("CVSCatchupReleaseViewer.noCommonFile")); //$NON-NLS-1$
		}
		
		IResource local = syncTree.getLocal();
		if(local != null) {
			ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile)local);
			ResourceSyncInfo info = null;
			try {
				info = cvsFile.getSyncInfo();
				name = local.getName();
				String revision = null;
				if(info != null) {
					revision = info.getRevision();
					if(info.isAdded() || info.isDeleted()) {
						revision = null;
					}
				}
				if(revision != null) {
					config.setLeftLabel(Policy.bind("CVSCatchupReleaseViewer.commonFileRevision", name, revision)); //$NON-NLS-1$
				} else {
					config.setLeftLabel(Policy.bind("CVSCatchupReleaseViewer.commonFile", name)); //$NON-NLS-1$
				}
			} catch(CVSException e) {
				ErrorDialog.openError(getControl().getShell(), null, null, e.getStatus());
				config.setLeftLabel(Policy.bind("CVSCatchupReleaseViewer.commonFile", name)); //$NON-NLS-1$				
			}
		}
	}
}
