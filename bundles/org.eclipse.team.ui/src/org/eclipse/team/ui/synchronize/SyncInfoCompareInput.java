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
package org.eclipse.team.ui.synchronize;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.compare.*;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.internal.core.Assert;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.internal.ui.synchronize.LocalResourceTypedElement;
import org.eclipse.team.internal.ui.synchronize.SyncInfoModelElement;
import org.eclipse.ui.*;
import org.eclipse.ui.progress.UIJob;

/**
 * A {@link SyncInfo} editor input used as input to a two-way or three-way 
 * compare viewer. It defines methods for accessing the three sides for the 
 * compare, and a name and image which is used when displaying the three way input
 * in an editor. This input can alternatly be used to show compare results in 
 * a dialog by calling {@link CompareUI#openCompareDialog()}.
 * <p>
 * Supports saving the local resource that is changed in the editor.
 * </p><p>
 * This class cannot be subclassed by clients.
 * </p>
 * @see SyncInfo
 * @since 3.0
 */
public final class SyncInfoCompareInput extends CompareEditorInput implements IResourceChangeListener {

	private MyDiffNode node;
	private String description;
	private IResource resource;
	private IWorkbenchPart part;
	
	// Hook the compare input into it's containing workbench parts lifecycle.
	private IPartListener partListener = new IPartListener() {
		public void partActivated(IWorkbenchPart part) {
		}
		public void partBroughtToTop(IWorkbenchPart part) {
		}
		public void partClosed(IWorkbenchPart part) {
			if(SyncInfoCompareInput.this.part == part) {
				dispose();
			}
		}
		public void partDeactivated(IWorkbenchPart part) {
		}
		public void partOpened(IWorkbenchPart part) {
		}
	};

	/*
	 * This class exists so that we can force the text merge viewers to update by
	 * calling #fireChange when we save the compare input to disk. The side
	 * effect is that the compare viewers will be updated to reflect the new changes
	 * that have been made. Compare doesn't do this by default.
	 */
	private static class MyDiffNode extends SyncInfoModelElement {
		public MyDiffNode(IDiffContainer parent, SyncInfo info) {
			super(parent, info);
		}
		public void fireChange() {
			super.fireChange();
		}
	}
	
	/**
	 * Creates a compare editor input based on an existing <code>SyncInfo</code>.
	 * 
	 * @param description a description of the context of this sync info. This
	 * is displayed to the user.
	 * @param sync the <code>SyncInfo</code> used as the base for the compare input.
	 */
	public SyncInfoCompareInput(String description, SyncInfo sync) {
		super(getDefaultCompareConfiguration());
		Assert.isNotNull(sync);
		Assert.isNotNull(description);
		this.description = description;
		this.resource = sync.getLocal();
		this.node = new MyDiffNode(null, sync);
		initializeContentChangeListeners();
	}
	
	public void init(IWorkbenchPart part) {
		Assert.isNotNull(part);
		this.part = part;
		part.getSite().getPage().addPartListener(partListener);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
	}
	
	private static CompareConfiguration getDefaultCompareConfiguration() {
		CompareConfiguration cc = new CompareConfiguration();
		//cc.setProperty(CompareConfiguration.USE_OUTLINE_VIEW, true);
		return cc;
	}

	private void initializeContentChangeListeners() {
		ITypedElement te = node.getLeft();
		if (te instanceof IContentChangeNotifier) {
			((IContentChangeNotifier) te).addContentChangeListener(new IContentChangeListener() {
				public void contentChanged(IContentChangeNotifier source) {
					try {
						saveChanges(new NullProgressMonitor());
					} catch (CoreException e) {
					}
				}
			});
		}
	}
	
	/**
	 * Note that until the compare editor inputs can be part of the compare editors lifecycle we
	 * can't register as a listener because there is no dispose() method to remove the listener.
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		if (delta != null) {
			IResourceDelta resourceDelta = delta.findMember(resource.getFullPath());
			if (resourceDelta != null) {
				if (part != null && part instanceof IReusableEditor) {
						UIJob job = new UIJob("") { //$NON-NLS-1$
							public IStatus runInUIThread(IProgressMonitor monitor) {
								if(! isSaveNeeded()) {
									node.update(node.getSyncInfo());
								}
								return Status.OK_STATUS;
							}
						};
						job.setSystem(true);
						job.schedule();
				}
			}
		}
	}
	
	public void dispose() {
		if(part != null) {
			part.getSite().getPage().removePartListener(partListener);
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
			part = null;
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.compare.CompareEditorInput#getTitleImage()
	 */
	public Image getTitleImage() {
		ImageRegistry reg = TeamUIPlugin.getPlugin().getImageRegistry();
		Image image = reg.get(ITeamUIImages.IMG_SYNC_VIEW);
		if (image == null) {
			image = getImageDescriptor().createImage();
			reg.put(ITeamUIImages.IMG_SYNC_VIEW, image);
		}
		return image;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.compare.CompareEditorInput#prepareInput(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected Object prepareInput(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		// update the title now that the remote revision number as been fetched
		// from the server
		setTitle(getTitle());
		Utils.updateLabels(node.getSyncInfo(), getCompareConfiguration());
		try {
			node.cacheContents(monitor);
		} catch (TeamException e) {
			throw new InvocationTargetException(e);
		}
		return node;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.compare.CompareEditorInput#getTitle()
	 */
	public String getTitle() {
		return Policy.bind("SyncInfoCompareInput.title", node.getName()); //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.IEditorInput#getImageDescriptor()
	 */
	public ImageDescriptor getImageDescriptor() {
		return TeamUIPlugin.getImageDescriptor(ITeamUIImages.IMG_SYNC_VIEW);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.IEditorInput#getToolTipText()
	 */
	public String getToolTipText() {
		return Policy.bind("SyncInfoCompareInput.tooltip", description, node.getResource().getFullPath().toString()); //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object other) {
		if (other == this)
			return true;
		if (other instanceof SyncInfoCompareInput) {
			return getSyncInfo().equals(((SyncInfoCompareInput) other).getSyncInfo());
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see CompareEditorInput#saveChanges(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void saveChanges(IProgressMonitor pm) throws CoreException {
		super.saveChanges(pm);
		if (node != null) {
			try {
				commit(pm, node);
			} finally {
				node.fireChange();
				setDirty(false);
			}
		}
	}

	private static void commit(IProgressMonitor pm, DiffNode node) throws CoreException {
		ITypedElement left = node.getLeft();
		if (left instanceof LocalResourceTypedElement)
			 ((LocalResourceTypedElement) left).commit(pm);

		ITypedElement right = node.getRight();
		if (right instanceof LocalResourceTypedElement)
			 ((LocalResourceTypedElement) right).commit(pm);
	}

	public SyncInfo getSyncInfo() {
		return node.getSyncInfo();
	}
}