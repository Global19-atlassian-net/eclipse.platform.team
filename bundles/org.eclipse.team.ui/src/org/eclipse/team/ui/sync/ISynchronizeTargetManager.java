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
package org.eclipse.team.ui.sync;

/**
 * Manages synchronization targets.
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 * @since 3.0 
 */
public interface ISynchronizeTargetManager {
	
	/**
	 * Registers the given listener for console notifications. Has
	 * no effect if an identical listener is already registered.
	 * 
	 * @param listener listener to register
	 */
	public void addConsoleListener(ISynchronizeTargetListener listener);
	
	/**
	 * Deregisters the given listener for console notifications. Has
	 * no effect if an identical listener is not already registered.
	 * 
	 * @param listener listener to deregister
	 */
	public void removeConsoleListener(ISynchronizeTargetListener listener);

	/**
	 * Adds the given consoles to the console manager. Has no effect for
	 * equivalent consoles already registered. The consoles will be added
	 * to any existing console views.
	 * 
	 * @param consoles consoles to add
	 */
	public void addConsoles(ISynchronizeTarget[] consoles);
	
	/**
	 * Removes the given consoles from the console manager. If the consoles are
	 * being displayed in any console views, the associated pages will be closed.
	 * 
	 * @param consoles consoles to remove
	 */
	public void removeConsoles(ISynchronizeTarget[] consoles);
	
	/**
	 * Returns a collection of consoles registered with the console manager.
	 * 
	 * @return a collection of consoles registered with the console manager
	 */
	public ISynchronizeTarget[] getConsoles();
}
