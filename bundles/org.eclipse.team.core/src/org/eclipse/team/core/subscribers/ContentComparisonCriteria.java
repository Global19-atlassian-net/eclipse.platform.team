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

import java.io.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.*;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.core.TeamPlugin;

/**
 * A content comparison criteria that knows how to compare the content of
 * <code>IStorage</code> and <code>IRemoteResource</code> objects. The
 * content comparison can be configured to ignore or consider whitespace.
 * 
 * @see org.eclipse.team.core.subscribers.ComparisonCriteria
 */
public class ContentComparisonCriteria implements IComparisonCriteria {

	private boolean ignoreWhitespace = false;

	public ContentComparisonCriteria(boolean ignoreWhitespace) {
		this.ignoreWhitespace = ignoreWhitespace;
	}

	/**
	 * Helper methods for comparisons that returns true if the resource
	 * contents are the same.
	 * 
	 * If timestampDiff is true then the timestamps don't differ and there's no
	 * point checking the contents.
	 */
	public boolean compare(Object e1, Object e2) {
		InputStream is1 = null;
		InputStream is2 = null;
		try {
			is1 = getContents(e1, new NullProgressMonitor());
			is2 = getContents(e2, new NullProgressMonitor());
			return contentsEqual(is1, is2, shouldIgnoreWhitespace());
		} catch(TeamException e) {
			TeamPlugin.log(e);
			return false;
		} finally {
			try {
				try {
					if (is1 != null) {
						is1.close();
					}
				} finally {
					if (is2 != null) {
						is2.close();
					}
				}
			} catch (IOException e) {
				// Ignore
			}
		}
	}

	protected boolean shouldIgnoreWhitespace() {
		return ignoreWhitespace;
	}

	/**
	 * Returns <code>true</code> if both input streams byte contents is
	 * identical.
	 * 
	 * @param input1
	 *                   first input to contents compare
	 * @param input2
	 *                   second input to contents compare
	 * @return <code>true</code> if content is equal
	 */
	private boolean contentsEqual(InputStream is1, InputStream is2, boolean ignoreWhitespace) {
		try {
			if (is1 == is2)
				return true;

			if (is1 == null && is2 == null) // no byte contents
				return true;

			if (is1 == null || is2 == null) // only one has
														 // contents
				return false;

			while (true) {
				int c1 = is1.read();
				while (shouldIgnoreWhitespace() && isWhitespace(c1))
					c1 = is1.read();
				int c2 = is2.read();
				while (shouldIgnoreWhitespace() && isWhitespace(c2))
					c2 = is2.read();
				if (c1 == -1 && c2 == -1)
					return true;
				if (c1 != c2)
					break;

			}
		} catch (IOException ex) {
		} finally {
			try {
				try {
					if (is1 != null) {
						is1.close();
					}
				} finally {
					if (is2 != null) {
						is2.close();
					}
				}
			} catch (IOException e) {
				// Ignore
			}
		}
		return false;
	}

	private boolean isWhitespace(int c) {
		if (c == -1)
			return false;
		return Character.isWhitespace((char) c);
	}

	private InputStream getContents(Object resource, IProgressMonitor monitor) throws TeamException {
		try {
			if (resource instanceof IFile) {
				return new BufferedInputStream(((IFile) resource).getContents());
			} else if(resource instanceof ISubscriberResource) {
				ISubscriberResource remote = (ISubscriberResource)resource;
				if (!remote.isContainer()) {
					return new BufferedInputStream(remote.getStorage(monitor).getContents());
				}
			}
			return null;
		} catch (CoreException e) {
			throw TeamException.asTeamException(e);
		}
	}
}
