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
package org.eclipse.team.internal.ccvs.ui.operations;

import java.util.*;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.core.*;

/**
 * A specialized Replace operation that will update managed resources and
 * unmanaged resources that are conflicting additions (so that the remote is fetched)
 */
public class OverrideAndUpdateOperation extends ReplaceOperation {

	private IResource[] conflictingAdditions;

	public OverrideAndUpdateOperation(Shell shell, IResource[] allResources, IResource[] conflictingAdditions, CVSTag tag, boolean recurse) {
		super(shell, allResources, tag, recurse);
		this.conflictingAdditions = conflictingAdditions;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.ReplaceOperation#getResourcesToUpdate(org.eclipse.team.internal.ccvs.core.ICVSResource[])
	 */
	protected ICVSResource[] getResourcesToUpdate(ICVSResource[] resources) throws CVSException {
		// Add the conflicting additions to the list of resources to update
		Set update = new HashSet();
		ICVSResource[] conflicts = getCVSArguments(conflictingAdditions);
		update.addAll(Arrays.asList(conflicts));
		update.addAll(Arrays.asList(super.getResourcesToUpdate(resources)));
		return (ICVSResource[]) update.toArray(new ICVSResource[update.size()]);
	}

}
