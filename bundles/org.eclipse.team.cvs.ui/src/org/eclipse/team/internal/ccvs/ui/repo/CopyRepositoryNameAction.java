/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.repo;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.ui.actions.CVSAction;

public class CopyRepositoryNameAction extends CVSAction {
	protected boolean isEnabled() throws TeamException {
		return true;
	}
	public void execute(IAction action) {
		ICVSRepositoryLocation[] locations = getSelectedRepositories();
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < locations.length; i++) {
			buffer.append(locations[i].getLocation());
			if (i < locations.length - 1) buffer.append("\n"); //$NON-NLS-1$
		}
		copyToClipbard(Display.getDefault(), buffer.toString());
	}
	protected ICVSRepositoryLocation[] getSelectedRepositories() {
		ArrayList repositories = null;
		if (!selection.isEmpty()) {
			repositories = new ArrayList();
			Iterator elements = ((IStructuredSelection)selection).iterator();
			while (elements.hasNext()) {
				Object next = getAdapter(elements.next(), ICVSRepositoryLocation.class);
				if (next instanceof ICVSRepositoryLocation) {
					repositories.add(next);
					continue;
				}
			}
		}
		if (repositories != null && !repositories.isEmpty()) {
			ICVSRepositoryLocation[] result = new ICVSRepositoryLocation[repositories.size()];
			repositories.toArray(result);
			return result;
		}
		return new ICVSRepositoryLocation[0];
	}
	private void copyToClipbard(Display display, String str) {
		Clipboard clipboard = new Clipboard(display);
		clipboard.setContents(new String[] { str },	new Transfer[] { TextTransfer.getInstance()});			
		clipboard.dispose();
	}
}
