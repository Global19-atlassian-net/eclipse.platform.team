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
package org.eclipse.team.internal.ui.synchronize;

import java.util.*;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.team.ui.synchronize.viewers.ISynchronizeModelElement;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * A label provider that decorates viewers showing 
 * {@link ISynchronizeModelElement}.
 * 
 * @since 3.0
 */
public class SynchronizeModelElementLabelProvider extends LabelProvider implements IColorProvider {

	// Cache for folder images that have been overlayed with conflict icon
	private Map fgImageCache;
	
	// Contains direction images
	CompareConfiguration compareConfig = new CompareConfiguration();
	
	// Used as the base label provider for retreiving image and text from
	// the workbench adapter.
	private WorkbenchLabelProvider workbenchLabelProvider = new WorkbenchLabelProvider();

	public SynchronizeModelElementLabelProvider() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorProvider#getForeground(java.lang.Object)
	 */
	public Color getForeground(Object element) {
		if (element instanceof ISynchronizeModelElement) {
			ISynchronizeModelElement node = (ISynchronizeModelElement)element;
			if(node.getProperty(ISynchronizeModelElement.BUSY_PROPERTY)) {
				return Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorProvider#getBackground(java.lang.Object)
	 */
	public Color getBackground(Object element) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.LabelProvider#getImage(java.lang.Object)
	 */
	public Image getImage(Object element) {
		Image base = workbenchLabelProvider.getImage(element);
		if (base != null) {
			if (element instanceof DiffNode) {
				DiffNode syncNode = (DiffNode) element;
				int kind = syncNode.getKind();
				Image decoratedImage;
				decoratedImage = getCompareImage(base, kind);
				
					// The reason we still overlay the compare image is to
					// ensure that the image width for all images shown in the viewer
					// are consistent.
					return propagateConflicts(decoratedImage, syncNode);
				
			}
		}
		return base;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
	 */
	public String getText(Object element) {
		String base = workbenchLabelProvider.getText(element);
		if (element instanceof DiffNode) {
			if (TeamUIPlugin.getPlugin().getPreferenceStore().getBoolean(IPreferenceIds.SYNCVIEW_VIEW_SYNCINFO_IN_LABEL)) {
				// if the folder is already conflicting then don't bother
				// propagating the conflict
				int kind = ((DiffNode) element).getKind();
				if (kind != SyncInfo.IN_SYNC) {
					String syncKindString = SyncInfo.kindToString(kind);
					return Policy.bind("TeamSubscriberSyncPage.labelWithSyncKind", base, syncKindString); //$NON-NLS-1$ 
				}
			}
		}
		return base;
	}

	protected Image getCompareImage(Image base, int kind) {
		switch (kind & SyncInfo.DIRECTION_MASK) {
			case SyncInfo.OUTGOING :
				kind = (kind & ~SyncInfo.OUTGOING) | SyncInfo.INCOMING;
				break;
			case SyncInfo.INCOMING :
				kind = (kind & ~SyncInfo.INCOMING) | SyncInfo.OUTGOING;
				break;
		}
		return compareConfig.getImage(base, kind);
	}

	private Image propagateConflicts(Image base, DiffNode element) {
		// if the folder is already conflicting then don't bother propagating
		// the conflict
		List overlays = new ArrayList();
		List locations = new ArrayList();
		
		int kind = element.getKind();
		if ((kind & SyncInfo.DIRECTION_MASK) != SyncInfo.CONFLICTING) {
			if (hasDecendantConflicts(element)) {
				overlays.add(TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_CONFLICT_OVR));
				locations.add(new Integer(OverlayIcon.BOTTOM_RIGHT));
			}
		}
			if(hasErrorMarker(element)) {
				overlays.add(TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_ERROR_OVR));
				locations.add(new Integer(OverlayIcon.BOTTOM_LEFT));
			} else if(hasWarningMarker(element)) {
				overlays.add(TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_WARNING_OVR));
				locations.add(new Integer(OverlayIcon.BOTTOM_LEFT));
			}
			
			if(! overlays.isEmpty()) {
				ImageDescriptor[] overlayImages = (ImageDescriptor[]) overlays.toArray(new ImageDescriptor[overlays.size()]);
				int[] locationInts = new int[locations.size()];
				for (int i = 0; i < locations.size(); i++) {
						locationInts[i] =((Integer) locations.get(i)).intValue();
				}
				ImageDescriptor overlay = new OverlayIcon(base, overlayImages, locationInts, new Point(base.getBounds().width, base.getBounds().height));
				if (fgImageCache == null) {
					fgImageCache = new HashMap(10);
				}
				Image conflictDecoratedImage = (Image) fgImageCache.get(overlay);
				if (conflictDecoratedImage == null) {
					conflictDecoratedImage = overlay.createImage();
					fgImageCache.put(overlay, conflictDecoratedImage);
				}
				return conflictDecoratedImage;
			}
		
		return base;
	}
	
	/**
	 * Return whether this diff node has descendant conflicts in the view in which it appears.
	 * @return whether the node has descendant conflicts
	 */
	private boolean hasDecendantConflicts(DiffNode node) {
		if(node instanceof ISynchronizeModelElement) {
			return ((ISynchronizeModelElement)node).getProperty(ISynchronizeModelElement.PROPAGATED_CONFLICT_PROPERTY);
		}
		return false;
	}
	
	/**
	 * Return whether this diff node has descendant conflicts in the view in which it appears.
	 * @return whether the node has descendant conflicts
	 */
	private boolean hasErrorMarker(DiffNode node) {
		if(node instanceof ISynchronizeModelElement) {
			return ((ISynchronizeModelElement)node).getProperty(ISynchronizeModelElement.PROPAGATED_ERROR_MARKER_PROPERTY);
		}
		return false;
	}
	
	/**
	 * Return whether this diff node has descendant conflicts in the view in which it appears.
	 * @return whether the node has descendant conflicts
	 */
	private boolean hasWarningMarker(DiffNode node) {
		if(node instanceof ISynchronizeModelElement) {
			return ((ISynchronizeModelElement)node).getProperty(ISynchronizeModelElement.PROPAGATED_WARNING_MARKER_PROPERTY);
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IBaseLabelProvider#dispose()
	 */
	public void dispose() {
		compareConfig.dispose();
		if (fgImageCache != null) {
			Iterator it = fgImageCache.values().iterator();
			while (it.hasNext()) {
				Image element = (Image) it.next();
				element.dispose();
			}
		}
	}
}