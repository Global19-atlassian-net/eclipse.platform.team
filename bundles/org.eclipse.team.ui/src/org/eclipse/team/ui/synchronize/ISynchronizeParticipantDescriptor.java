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

import org.eclipse.jface.resource.ImageDescriptor;

/**
 * A participant descriptor contains the content of the 
 * <code>synchronizeParticipants</code> extension section for 
 * for a registered participant type in the declaring plug-in's 
 * manifest (<code>plugin.xml</code>) file.
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 * @see ISynchronizeManager#getParticipantDescriptor(String)
 * @since 3.0
 */
public interface ISynchronizeParticipantDescriptor {
	/**
	 * Returns the name of this participant. This can be shown to the user.
	 * 
	 * @return the name of this participant. This can be shown to the user.
	 */
	public String getName();
	
	/**
	 * Returns a string describing this participant type.
	 * 
	 * @return a string describing this participant type.
	 */
	public String getDescription();
	
	/**
	 * Returns the unique id that identifies this participant type.
	 * 
	 * @return the unique id that identifies this participant type.
	 */
	public String getId();

	/**
	 * Returns the image descriptor for this participant type.
	 * 
	 * @return the image descriptor for this participant type.
	 */
	public ImageDescriptor getImageDescriptor();
	
	/**
	 * Returns <code>true</code> if the participant is static and 
	 * <code>false</code> otherwise. Static participants are created 
	 * automatically by the synchronize manager at startup whereas 
	 * not static participants are created by client code and registered 
	 * with the manager.
	 * 
	 * @return <code>true</code> if the participant is static and 
	 * <code>false</code> otherwise
	 */
	public boolean isStatic();

	/**
	 * Returns if this participant supports a global refresh action.
	 * 
	 * @return if this participant supports a global refresh action.
	 */
	public boolean doesSupportRefresh();
	
	/**
	 * Does this type of participant allow multiple instances.
	 *
	 */
	public boolean doesAllowMultiple();
	
	/**
	 * Can be persisted between sessions.
	 *
	 */
	public boolean isPersistent();
}