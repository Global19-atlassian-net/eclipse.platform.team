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

import org.eclipse.compare.structuremergeviewer.*;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.*;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.ui.model.IWorkbenchAdapter;

public abstract class AdaptableDiffNode extends DiffNode implements IAdaptable {

	public static final String BUSY_PROPERTY = TeamUIPlugin.ID + ".busy"; //$NON-NLS-1$
	public static final String PROPAGATED_CONFLICT_PROPERTY = TeamUIPlugin.ID + ".conflict"; //$NON-NLS-1$
	
	/*
	 * Internal flags bits for stroing properties in the flags variable
	 */
	private static final int BUSY_FLAG = 1;
	private static final int PROPAGATED_CONFLICT_FLAG = 2;

	// Instance variable containing the flags for this node
	private int flags;
	private ListenerList listeners;
	
	public AdaptableDiffNode(IDiffContainer parent, int kind) {
		super(parent, kind);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		return Platform.getAdapterManager().getAdapter(this, adapter);
	}

	public synchronized void addPropertyChangeListener(IPropertyChangeListener listener) {
		if (listeners == null) {
			listeners = new ListenerList();
		}
		listeners.add(listeners);
	}
	
	public synchronized void removePropertyChangeListener(IPropertyChangeListener listener) {
		if (listeners != null) {
			listeners.remove(listener);
			if (listeners.isEmpty()) {
				listeners = null;
			}
		}
	}
	
	/**
	 * Return whether this node has the given property set.
	 * @param propertyName the flag to test
	 * @return <code>true</code> if the property is set
	 */
	public boolean getProperty(String propertyName) {
		return (getFlags() & getFlag(propertyName)) > 0;
	}
	
	/**
	 * Add the flag to the flags for this node
	 * @param propertyName the flag to add
	 */
	public void setProperty(String propertyName, boolean value) {
		if (value) {
			if (!getProperty(propertyName)) {
				int flag = getFlag(propertyName);
				flags |= flag;
				firePropertyChange(propertyName);
			}
		} else {
			if (getProperty(propertyName)) {
				int flag = getFlag(propertyName);
				flags ^= flag;
				firePropertyChange(propertyName);
			}
		}
	}
	
	public void setPropertyToRoot(String propertyName, boolean value) {
		if (value) {
			addToRoot(propertyName);
		} else {
			removeToRoot(propertyName);
		}
	}
	
	
	public ImageDescriptor getImageDescriptor(Object object) {
		IResource resource = getResource();
		if(resource != null) {
			IWorkbenchAdapter adapter = (IWorkbenchAdapter)((IAdaptable) resource).getAdapter(IWorkbenchAdapter.class);
			return adapter.getImageDescriptor(resource);
		}
		return null;
	}
	
	public abstract IResource getResource();

	private void addToRoot(String flag) {
		setProperty(flag, true);
		AdaptableDiffNode parent = (AdaptableDiffNode)getParent();
		if (parent != null) {
			if (parent.getProperty(flag)) return;
			parent.addToRoot(flag);
		}
	}

	private void firePropertyChange(String propertyName) {
		Object[] allListeners;
		synchronized(this) {
			if (listeners == null) return;
			allListeners = listeners.getListeners();
		}
		boolean set = getProperty(propertyName);
		final PropertyChangeEvent event = new PropertyChangeEvent(this, propertyName, Boolean.valueOf(!set), Boolean.valueOf(set));
		for (int i = 0; i < allListeners.length; i++) {
			Object object = allListeners[i];
			if (object instanceof IPropertyChangeListener) {
				final IPropertyChangeListener listener = (IPropertyChangeListener)object;
				Platform.run(new ISafeRunnable() {
					public void handleException(Throwable exception) {
						// Exceptions logged by the platform
					}
					public void run() throws Exception {
						listener.propertyChange(event);
					}
				});
			}
		}
	}
	
	private int getFlag(String propertyName) {
		if (propertyName == BUSY_PROPERTY) {
			return BUSY_FLAG;
		} else if (propertyName == PROPAGATED_CONFLICT_PROPERTY) {
			return PROPAGATED_CONFLICT_FLAG;
		}
		return 0;
	}
	
	private int getFlags() {
		return flags;
	}
	
	private boolean hasChildWithFlag(String flag) {
		IDiffElement[] childen = getChildren();
		for (int i = 0; i < childen.length; i++) {
			IDiffElement element = childen[i];
			if (((AdaptableDiffNode)element).getProperty(flag)) {
				return true;
			}
		}
		return false;
	}
	
	private void removeToRoot(String flag) {
		setProperty(flag, false);
		AdaptableDiffNode parent = (AdaptableDiffNode)getParent();
		if (parent != null) {
			// If the parent doesn't have the tag, no recalculation is required
			// Also, if the parent still has a child with the tag, no recalculation is needed
			if (parent.getProperty(flag) && !parent.hasChildWithFlag(flag)) {
				// The parent no longer has the flag so propogate the reclaculation
				parent.removeToRoot(flag);
			}
		}
	}
}
