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
package org.eclipse.team.ui.synchronize.viewers;

import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.structuremergeviewer.*;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.SyncInfoSet;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.synchronize.LocalResourceTypedElement;
import org.eclipse.team.internal.ui.synchronize.RemoteResourceTypedElement;

/**
 * A diff node used to display the synchronization state for resources described by
 * existing {@link SyncInfo} objects. The synchronization state for a node can
 * change after it has been created. Since it implements the <code>ITypedElement</code>
 * and <code>ICompareInput</code> interfaces it can be used directly to
 * display the compare result in a <code>DiffTreeViewer</code> and as the
 * input to any other compare/merge viewer.
 * <p>
 * You can access the {@link SyncInfoSet} this node was created from for quick access
 * to the underlying sync state model.
 * </p>
 * <p>
 * TODO: mention node builders and syncinfocompareinput and syncinfodifftree viewer
 * Clients typically use this class as is, but may subclass if required.
 * @see DiffTreeViewer
 * @see Differencer
 */
public class SyncInfoModelElement extends SynchronizeModelElement {
		
	private ITypedElement ancestor;
	private SyncInfo info;
	
	/**
	 * Construct a <code>SyncInfoModelElement</code> for the given resource. The {@link SyncInfoSet} 
	 * that contains sync states for this resource must also be provided. This set is used
	 * to access the underlying sync state model that is the basis for this node this helps for
	 * providing quick access to the logical containment
	 * 
	 * @param set The set associated with the diff tree veiwer
	 * @param resource The resource for the node
	 */
	public SyncInfoModelElement(IDiffContainer parent, SyncInfo info) {
		super(parent);
		this.info = info;
		// update state
		setKind(info.getKind());		
		// local
		setLeft(createLocalTypeElement(info));
		// remote
		setRight(createRemoteTypeElement(info));	
		// base
		setAncestor(createBaseTypeElement(info));
			
		fireChange();
	}

	public void update(SyncInfo info) {
		this.info = info;
		// update state
		setKind(info.getKind());		
		// never have to update the local, it's always the workspace resource
		// remote
		RemoteResourceTypedElement rightEl = (RemoteResourceTypedElement)getRight(); 
		if(rightEl == null && info.getRemote() != null) {
			setRight(createRemoteTypeElement(info));
		} else {
			rightEl.update(info.getRemote());
		}
		// base
		RemoteResourceTypedElement ancestorEl = (RemoteResourceTypedElement)getRight(); 
		if(ancestorEl == null && info.getBase() != null) {
			setAncestor(createBaseTypeElement(info));
		} else {
			ancestorEl.update(info.getBase());
		}
		
		fireChange();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.compare.structuremergeviewer.DiffElement#getKind()
	 */
	public int getKind() {
		SyncInfo info = getSyncInfo();
		if (info != null) {
			return info.getKind();
		} else {
			return SyncInfo.IN_SYNC;
		}
	}
	
	/**
	 * We have to track the base because <code>DiffNode</code> doesn't provide a
	 * setter. See:
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=52261
	 */
	public void setAncestor(ITypedElement ancestor) {
		this.ancestor = ancestor;
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.compare.structuremergeviewer.DiffNode#getAncestor()
	 */
	public ITypedElement getAncestor() {
		return this.ancestor;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.compare.structuremergeviewer.DiffNode#getName()
	 */
	public String getName() {
		IResource resource = getResource();
		if(resource != null) {
			return resource.getName();
		} else {
			return super.getName();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if(adapter == SyncInfo.class) {
			return getSyncInfo();
		}
		return super.getAdapter(adapter);
	}
	
	/**
	 * Helper method that returns the resource associated with this node. A node is not
	 * required to have an associated local resource.
	 * @return the resource associated with this node or <code>null</code> if the local
	 * contributor is not a resource.
	 */
	public IResource getResource() {
		ITypedElement element = getLeft();
		if(element instanceof ResourceNode) {
			return ((ResourceNode)element).getResource();
		}
		return null;
	}
	
	/**
	 * Return true if the receiver's Subscriber and Resource are equal to that of object.
	 * @param object The object to test
	 * @return true has the same subsriber and resource
	 */
	public boolean equals(Object object) {
		return this==object;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		IResource resource = getResource();
		if (resource == null) {
			return super.hashCode();
		}
		return resource.hashCode();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return getResource() != null ? getResource().getFullPath().toString() : getName();
	}
	
	/**
	 * Cache the contents for the base and remote.
	 * @param monitor
	 */
	public void cacheContents(IProgressMonitor monitor) throws TeamException {
		ITypedElement base = getAncestor();
		ITypedElement remote = getRight();
		int work = Math.min((remote== null ? 0 : 50) + (base == null ? 0 : 50), 10);
		monitor.beginTask(null, work);
		try {
			if (base != null && base instanceof RemoteResourceTypedElement) {
				((RemoteResourceTypedElement)base).cacheContents(Policy.subMonitorFor(monitor, 50));
			}
			if (remote != null && remote instanceof RemoteResourceTypedElement) {
				((RemoteResourceTypedElement)remote).cacheContents(Policy.subMonitorFor(monitor, 50));
			}
		} finally {
			monitor.done();
		}
	}
	
	public SyncInfo getSyncInfo() {
		return info;
	}

	/**
	 * Create an ITypedElement for the given local resource. The returned ITypedElement
	 * will prevent editing of outgoing deletions.
	 */
	private static ITypedElement createTypeElement(final IResource resource, final int kind) {
		if(resource != null) {
			return new LocalResourceTypedElement(resource) {
				public boolean isEditable() {
						if(! resource.exists() && SyncInfo.getDirection(kind) == SyncInfo.OUTGOING && SyncInfo.getChange(kind) == SyncInfo.DELETION) {
							return false;
						}
						return super.isEditable();
					}
				};
		}
		return null;
	}
	
	/**
	 * Create an ITypedElement for the given remote resource. The contents for the remote resource
	 * will be retrieved from the given IStorage which is a local cache used to buffer the remote contents
	 */
	protected static ITypedElement createTypeElement(IResourceVariant remoteResource) {
		return new RemoteResourceTypedElement(remoteResource);
	}

	protected static ITypedElement createRemoteTypeElement(SyncInfo info) {
		if(info != null && info.getRemote() != null) {
			return createTypeElement(info.getRemote());
		}
		return null;
	}

	protected static ITypedElement createLocalTypeElement(SyncInfo info) {
		if(info != null && info.getLocal() != null) {
			return createTypeElement(info.getLocal(), info.getKind());
		}
		return null;
	}

	protected static ITypedElement createBaseTypeElement(SyncInfo info) {
		if(info != null && info.getBase() != null) {
			return createTypeElement(info.getBase());
		}
		return null;
	}
}