package org.eclipse.team.internal.ccvs.ui.sync;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
 
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.structuremergeviewer.DiffContainer;
import org.eclipse.compare.structuremergeviewer.DiffElement;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.ILocalSyncElement;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.core.sync.IRemoteSyncElement;
import org.eclipse.team.core.sync.RemoteSyncElement;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ILogEntry;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.ui.CVSLightweightDecorator;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.HistoryView;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.IHelpContextIds;
import org.eclipse.team.internal.ccvs.ui.OverlayIcon;
import org.eclipse.team.internal.ccvs.ui.OverlayIconCache;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.merge.OverrideUpdateMergeAction;
import org.eclipse.team.internal.ccvs.ui.merge.UpdateMergeAction;
import org.eclipse.team.internal.ccvs.ui.merge.UpdateWithForcedJoinAction;
import org.eclipse.team.internal.ui.sync.CatchupReleaseViewer;
import org.eclipse.team.internal.ui.sync.ChangedTeamContainer;
import org.eclipse.team.internal.ui.sync.ITeamNode;
import org.eclipse.team.internal.ui.sync.MergeResource;
import org.eclipse.team.internal.ui.sync.SyncView;
import org.eclipse.team.internal.ui.sync.TeamFile;
import org.eclipse.ui.help.WorkbenchHelp;

public class CVSCatchupReleaseViewer extends CatchupReleaseViewer {
	// Actions
	private UpdateSyncAction updateAction;
	private ForceUpdateSyncAction forceUpdateAction;
	
	private CommitSyncAction commitAction;
	private ForceCommitSyncAction forceCommitAction;
	
	private UpdateMergeAction updateMergeAction;
	private UpdateWithForcedJoinAction updateWithJoinAction;
	private OverrideUpdateMergeAction forceUpdateMergeAction;

	private IgnoreAction ignoreAction;
	private HistoryAction showInHistory;

	private Action confirmMerge;
	private AddSyncAction addAction;
	
	private Action selectAdditions;
	private Image conflictImage;
	
	private static class DiffOverlayIcon extends OverlayIcon {
		private static final int HEIGHT = 16;
		private static final int WIDTH = 22;
		public DiffOverlayIcon(Image baseImage, ImageDescriptor[] overlays, int[] locations) {
			super(baseImage, overlays, locations, new Point(WIDTH, HEIGHT));
		}
		protected void drawOverlays(ImageDescriptor[] overlays, int[] locations) {
			Point size = getSize();
			for (int i = 0; i < overlays.length; i++) {
				ImageDescriptor overlay = overlays[i];
				ImageData overlayData = overlay.getImageData();
				switch (locations[i]) {
					case TOP_LEFT:
						drawImage(overlayData, 0, 0);			
						break;
					case TOP_RIGHT:
						drawImage(overlayData, size.x - overlayData.width, 0);			
						break;
					case BOTTOM_LEFT:
						drawImage(overlayData, 0, size.y - overlayData.height);			
						break;
					case BOTTOM_RIGHT:
						drawImage(overlayData, size.x - overlayData.width, size.y - overlayData.height);			
						break;
				}
			}
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
				currentRevision = baseFile != null ? baseFile.getRevision(): null;
			} catch(TeamException e) {
				CVSUIPlugin.log(e.getStatus());
			}
			if (remoteFile != null) {
				view.showHistory(remoteFile, currentRevision);
			} else if (baseFile != null) {
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
		// set F1 help
		WorkbenchHelp.setHelp(this.getControl(), IHelpContextIds.CATCHUP_RELEASE_VIEWER);
	}
	
	private static class Decoration implements IDecoration {
		public String prefix, suffix;
		public ImageDescriptor overlay;

		/**
		 * @see org.eclipse.jface.viewers.IDecoration#addPrefix(java.lang.String)
		 */
		public void addPrefix(String prefix) {
			this.prefix = prefix;
		}
		/**
		 * @see org.eclipse.jface.viewers.IDecoration#addSuffix(java.lang.String)
		 */
		public void addSuffix(String suffix) {
			this.suffix = suffix;
		}
		/**
		 * @see org.eclipse.jface.viewers.IDecoration#addOverlay(org.eclipse.jface.resource.ImageDescriptor)
		 */
		public void addOverlay(ImageDescriptor overlay) {
			this.overlay = overlay;
		}
	}
	
	private Image getConflictImage() {
		if(conflictImage != null)
			return conflictImage;
		final ImageDescriptor conflictDescriptor = CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_MERGEABLE_CONFLICT);
		conflictImage = conflictDescriptor.createImage();
		return conflictImage;
	}
		

	private void initializeLabelProvider() {
		final LabelProvider oldProvider = (LabelProvider)getLabelProvider();
		
		
		setLabelProvider(new LabelProvider() {
			private OverlayIconCache iconCache = new OverlayIconCache();
			
			public void dispose() {
				iconCache.disposeAll();
				oldProvider.dispose();
				conflictImage.dispose();
			}
			
			public Image getImage(Object element) {
				Image image = oldProvider.getImage(element);

				if (! (element instanceof ITeamNode))
					return image;
				
				ITeamNode node = (ITeamNode)element;
				IResource resource = node.getResource();

				if (! resource.exists())
					return image;
					
				CVSTeamProvider provider = (CVSTeamProvider)RepositoryProvider.getProvider(resource.getProject(), CVSProviderPlugin.getTypeId());
				List overlays = new ArrayList();
				List locations = new ArrayList();
				
				// use the default cvs image decorations
				ImageDescriptor resourceOverlay = CVSLightweightDecorator.getOverlay(node.getResource(),false, provider);
				
				int kind = node.getKind();
				boolean conflict = (kind & IRemoteSyncElement.AUTOMERGE_CONFLICT) != 0;

				if(resourceOverlay != null) {
					overlays.add(resourceOverlay);
					locations.add(new Integer(OverlayIcon.BOTTOM_RIGHT));
				}
				
				if(conflict) {
					overlays.add(CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_MERGEABLE_CONFLICT));
					locations.add(new Integer(OverlayIcon.TOP_LEFT));
				}

				if (overlays.isEmpty()) {
					return image;
				}

				//combine the descriptors and return the resulting image
				Integer[] integers = (Integer[])locations.toArray(new Integer[locations.size()]);
				int[] locs = new int[integers.length];
				for (int i = 0; i < integers.length; i++) {
					locs[i] = integers[i].intValue();
				}
				
				return iconCache.getImageFor(new DiffOverlayIcon(image,
					(ImageDescriptor[]) overlays.toArray(new ImageDescriptor[overlays.size()]),
					locs));
			}

			public String getText(Object element) {
				String label = oldProvider.getText(element);
				if (! (element instanceof ITeamNode))
					return label;
					
				ITeamNode node = (ITeamNode)element;					
				IResource resource = node.getResource();
				
				if (! resource.exists())
					return label;
						
				// use the default text decoration preferences
				Decoration decoration = new Decoration();

				CVSLightweightDecorator.decorateTextLabel(resource, decoration, false /*don't show dirty*/, false /*don't show revisions*/);
				label = decoration.prefix + label + decoration.suffix;
				
				if (CVSUIPlugin.getPlugin().getPreferenceStore().getBoolean(ICVSUIConstants.PREF_SHOW_SYNCINFO_AS_TEXT)) {
					int syncKind = node.getKind();
					if (syncKind != ILocalSyncElement.IN_SYNC) {
						String syncKindString = RemoteSyncElement.kindToString(syncKind);
						label = Policy.bind("CVSCatchupReleaseViewer.labelWithSyncKind", label, syncKindString); //$NON-NLS-1$
					}
				}
				return label;
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
				manager.add(new Separator());
				confirmMerge.setEnabled(confirmMerge.isEnabled());				
				manager.add(confirmMerge);
				break;
			case SyncView.SYNC_OUTGOING:
				addAction.update(SyncView.SYNC_OUTGOING);
				manager.add(addAction);
				commitAction.update(SyncView.SYNC_OUTGOING);
				manager.add(commitAction);
				forceCommitAction.update(SyncView.SYNC_OUTGOING);
				manager.add(forceCommitAction);
				ignoreAction.update();
				manager.add(ignoreAction);
				manager.add(new Separator());
				confirmMerge.setEnabled(confirmMerge.isEnabled());				
				manager.add(confirmMerge);
				selectAdditions.setEnabled(selectAdditions.isEnabled());				
				manager.add(selectAdditions);
				break;
			case SyncView.SYNC_BOTH:
				addAction.update(SyncView.SYNC_BOTH);
				manager.add(addAction);
				commitAction.update(SyncView.SYNC_BOTH);
				manager.add(commitAction);
				updateAction.update(SyncView.SYNC_BOTH);
				manager.add(updateAction);
				manager.add(new Separator());
				forceCommitAction.update(SyncView.SYNC_BOTH);
				manager.add(forceCommitAction);
				forceUpdateAction.update(SyncView.SYNC_BOTH);
				manager.add(forceUpdateAction);				
				manager.add(new Separator());
				confirmMerge.setEnabled( confirmMerge.isEnabled());				
				manager.add(confirmMerge);
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
		addAction = new AddSyncAction(diffModel, this, Policy.bind("CVSCatchupReleaseViewer.addAction"), shell); //$NON-NLS-1$
		
		// Show in history view
		showInHistory = new HistoryAction(Policy.bind("CVSCatchupReleaseViewer.showInHistory")); //$NON-NLS-1$
		addSelectionChangedListener(showInHistory);
		
		selectAdditions = new Action(Policy.bind("CVSCatchupReleaseViewer.Select_&Outgoing_Additions_1"), null) { //$NON-NLS-1$
			public boolean isEnabled() {
				DiffNode node = diffModel.getDiffRoot();
				IDiffElement[] elements = node.getChildren();
				for (int i = 0; i < elements.length; i++) {
					IDiffElement element = elements[i];
					if (element instanceof ITeamNode) {
						CVSSyncSet set = new CVSSyncSet(new StructuredSelection(element));
						try {
							if (set.hasNonAddedChanges()) return true;
						} catch (CVSException e) {
							// Log the error and enable the menu item
							CVSUIPlugin.log(e.getStatus());
							return true;
						}
					} else {
						// unanticipated situation, just enable the action
						return true;
					}
				}
				return false;
			}
			public void run() {
				List additions = new ArrayList();
				DiffNode root = diffModel.getDiffRoot();
				visit(root, additions);
				setSelection(new StructuredSelection(additions));
			}
			private void visit(IDiffElement node, List additions) {
				try {
					if (node instanceof TeamFile) {
						TeamFile file = (TeamFile)node;
						if (file.getChangeDirection() == IRemoteSyncElement.OUTGOING) {
							if (file.getChangeType() == IRemoteSyncElement.ADDITION) {
								ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(file.getResource());
								if (cvsResource.isManaged()) return;
								additions.add(node);
							}
						}
						return;
					}
					if (node instanceof ChangedTeamContainer) {
						ChangedTeamContainer container = (ChangedTeamContainer)node;
						if (container.getChangeDirection() == IRemoteSyncElement.OUTGOING) {
							if (container.getChangeType() == IRemoteSyncElement.ADDITION) {
								ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(container.getResource());
								if (!((ICVSFolder)cvsResource).isCVSFolder()) {
									additions.add(node);
								}
							}
						}
						
					}
					if (node instanceof DiffContainer) {
						IDiffElement[] children = ((DiffContainer)node).getChildren();
						for (int i = 0; i < children.length; i++) {
							visit(children[i], additions);
						}
					}
				} catch (TeamException e) {
					CVSUIPlugin.log(e.getStatus());
				}
			}
		};
		
		// confirm merge
		confirmMerge = new Action(Policy.bind("CVSCatchupReleaseViewer.confirmMerge"), null) { //$NON-NLS-1$
			public void run() {
				ISelection s = getSelection();
				if (!(s instanceof IStructuredSelection) || s.isEmpty()) {
					return;
				}
				List needsMerge = new ArrayList();
				for (Iterator it = ((IStructuredSelection)s).iterator(); it.hasNext();) {
					final Object element = it.next();
					if(element instanceof DiffElement) {
						mergeRecursive((IDiffElement)element, needsMerge);
					}
				}
				TeamFile[] files = (TeamFile[]) needsMerge.toArray(new TeamFile[needsMerge.size()]);
				if(files.length != 0) {
					try {
						for (int i = 0; i < files.length; i++) {		
							TeamFile teamFile = (TeamFile)files[i];
							CVSUIPlugin.getPlugin().getRepositoryManager().merged(new IRemoteSyncElement[] {teamFile.getMergeResource().getSyncElement()});
							teamFile.merged();
						}
					} catch(TeamException e) {
						CVSUIPlugin.openError(getControl().getShell(), null, null, e);
					}
				}
				refresh();
				diffModel.updateStatusLine();
			}
			 
			public boolean isEnabled() {
				ISelection s = getSelection();
				if (!(s instanceof IStructuredSelection) || s.isEmpty()) {
					return false;
				}
				for (Iterator it = ((IStructuredSelection)s).iterator(); it.hasNext();) {
					Object element = (Object) it.next();
					if(element instanceof TeamFile) {
						TeamFile file = (TeamFile)element;						
						int direction = file.getChangeDirection();
						int type = file.getChangeType();
						if(direction == IRemoteSyncElement.INCOMING ||
						   direction == IRemoteSyncElement.CONFLICTING) {
							continue;
						}
					}
					return false;
				}
				return true;
			}
		};
	}
	
	protected void mergeRecursive(IDiffElement element, List needsMerge) {
		if (element instanceof DiffContainer) {
			DiffContainer container = (DiffContainer)element;
			IDiffElement[] children = container.getChildren();
			for (int i = 0; i < children.length; i++) {
				mergeRecursive(children[i], needsMerge);
			}
		} else if (element instanceof TeamFile) {
			TeamFile file = (TeamFile)element;
			needsMerge.add(file);			
		}
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
								if (logEntry != null)
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
				CVSUIPlugin.openError(getControl().getShell(), null, null, e);
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
				CVSUIPlugin.openError(getControl().getShell(), null, null, e);
				config.setRightLabel(Policy.bind("CVSCatchupReleaseViewer.commonFile", name)); //$NON-NLS-1$
			}
		} else {
			config.setAncestorLabel(Policy.bind("CVSCatchupReleaseViewer.noCommonFile")); //$NON-NLS-1$
		}
		
		IResource local = syncTree.getLocal();
		if (local != null) {
			if (!local.exists()) {
				config.setLeftLabel(Policy.bind("CVSCatchupReleaseViewer.No_workspace_file_1")); //$NON-NLS-1$
			} else {
				ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile)local);
				ResourceSyncInfo info = null;
				try {
					info = cvsFile.getSyncInfo();
					name = local.getName();
					String revision = null;
					if (info != null) {
						revision = info.getRevision();
						if (info.isAdded() || info.isDeleted()) {
							revision = null;
						}
					}
					if (revision != null) {
						config.setLeftLabel(Policy.bind("CVSCatchupReleaseViewer.commonFileRevision", name, revision)); //$NON-NLS-1$
					} else {
						config.setLeftLabel(Policy.bind("CVSCatchupReleaseViewer.commonFile", name)); //$NON-NLS-1$
					}
				} catch (CVSException e) {
					CVSUIPlugin.openError(getControl().getShell(), null, null, e);
					config.setLeftLabel(Policy.bind("CVSCatchupReleaseViewer.commonFile", name)); //$NON-NLS-1$				
				}
			}
		}
	}
}
