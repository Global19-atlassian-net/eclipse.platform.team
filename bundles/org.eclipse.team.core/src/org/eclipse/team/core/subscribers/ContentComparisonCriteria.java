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
package org.eclipse.team.core.subscribers;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.internal.core.Policy;

/**
 * TimestampComparisonCriteria
 */
public class ContentComparisonCriteria extends ComparisonCriteria {

	private boolean ignoreWhitespace = false;

	public String getName() {
		return "Comparing content"  + (ignoreWhitespace ? " ignore whitespace": "");
	}

	public String getId() {
		return "org.eclipse.team.comparisoncriteria.content" + (ignoreWhitespace ? ".ignore": "");
	}
	
	public ContentComparisonCriteria(ComparisonCriteria[] preConditions, boolean ignoreWhitespace) {
		super(preConditions);
		this.ignoreWhitespace = ignoreWhitespace;
	}
	
	/**
	 * Helper methods for comparisons that returns true if the resource contents are the same.
	 * 
	 * If timestampDiff is true then the timestamps don't differ and there's no point checking the
	 * contents.
	 */
	public boolean compare(Object e1, Object e2, IProgressMonitor monitor) throws TeamException {
		try {
			monitor.beginTask(null, 100);
			if(checkPreConditions(e1, e2, Policy.subMonitorFor(monitor, 10))) {
				return true;
			}

			return contentsEqual(
				getContents(e1, Policy.subMonitorFor(monitor, 45)), 
				getContents(e2, Policy.subMonitorFor(monitor, 45)),
				shouldIgnoreWhitespace());
		} finally {
			monitor.done();
		}
	}

	protected boolean shouldIgnoreWhitespace() {
		return ignoreWhitespace;
	}

	/**
	 * Returns <code>true</code> if both input streams byte contents is identical. 
	 *
	 * @param input1 first input to contents compare
	 * @param input2 second input to contents compare
	 * @return <code>true</code> if content is equal
	 */
	private boolean contentsEqual(InputStream is1, InputStream is2, boolean ignoreWhitespace) {
		if (is1 == is2)
			return true;

		if (is1 == null && is2 == null) // no byte contents
			return true;

		try {
			if (is1 == null || is2 == null) // only one has contents
				return false;
	
			while (true) {
				int c1 = is1.read();
				while (shouldIgnoreWhitespace() && isWhitespace(c1)) c1 = is1.read();
				int c2 = is2.read();
				while (shouldIgnoreWhitespace() && isWhitespace(c2)) c2 = is2.read();
				if (c1 == -1 && c2 == -1)
					return true;
				if (c1 != c2)
					break;

			}
		} catch (IOException ex) {
		} finally {
			if (is1 != null) {
				try {
					is1.close();
				} catch (IOException ex) {
				}
			}
			if (is2 != null) {
				try {
					is2.close();
				} catch (IOException ex) {
				}
			}
		}
		return false;
	}
	
	private boolean isWhitespace(int c) {
		if (c == -1) return false;
		return Character.isWhitespace((char)c);
	}

	private InputStream getContents(Object resource, IProgressMonitor monitor) throws TeamException {
			try {
				if (resource instanceof IStorage) {
					return new BufferedInputStream(((IStorage) resource).getContents());
				} else if(resource instanceof IRemoteResource) {
					IRemoteResource remote = (IRemoteResource)resource;
					if (!remote.isContainer()) {
						return new BufferedInputStream(remote.getContents(monitor));
					}
				}
				return null;
			} catch (CoreException e) {
				throw new TeamException(e);
			}
		}
}
