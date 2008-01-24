/*******************************************************************************
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.compare.internal.patch;

import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.patch.PatchConfiguration;
import org.eclipse.compare.structuremergeviewer.Differencer;

public class HunkDiffNode extends PatchDiffNode {

	private final HunkResult result;

	public static HunkDiffNode createDiffNode(PatchFileDiffNode parent, HunkResult result, boolean fullContext) {
//		return new HunkDiffNode(result, parent, Differencer.CHANGE, getAncestorElement(result, result.isOK()), getLeftElement(result, fullContext), getRightElement(result, fullContext));
		return createDiffNode(parent, result, result.isOK(), fullContext, fullContext);
	}
	
	public static HunkDiffNode createDiffNode(PatchFileDiffNode parent, HunkResult result, boolean ancestorFullContext, boolean leftFullContext, boolean rightFullContext) {
		return new HunkDiffNode(result, parent, Differencer.CHANGE, getAncestorElement(result, ancestorFullContext), getLeftElement(result, leftFullContext), getRightElement(result, rightFullContext));
	}
	
	private static ITypedElement getRightElement(HunkResult result, boolean fullContext) {
		return new HunkTypedElement(result, true /* isResult */, fullContext);
	}

	private static ITypedElement getLeftElement(HunkResult result,
			boolean fullContext) {
//		if (fullContext && !result.isOK())
			return new UnmatchedHunkTypedElement(result);
//		return new HunkTypedElement(result, true /* before state */, fullContext);
	}

	private static ITypedElement getAncestorElement(HunkResult result, boolean fullContext) {
		//XXX (tzarna): temporarily commented out
//		if (!fullContext) {
			// Don't provide an ancestor if the hunk didn't match or we're not doing fullContext
//			return null;
//		}
		// Make the ancestor the same as the left so we have an incoming change
//		return new HunkTypedElement(result, false /* before state */, result.isOK());
		return new HunkTypedElement(result, false /* before state */, fullContext);
	}

	private HunkDiffNode(HunkResult result, PatchFileDiffNode parent, int kind, ITypedElement ancestor, ITypedElement left, ITypedElement right) {
		super(result.getHunk(), parent, kind, ancestor, left, right);
		this.result = result;
	}

	public HunkResult getHunkResult() {
		return result;
	}

	protected PatchConfiguration getConfiguration() {
		return result.getDiffResult().getConfiguration();
	}

	public boolean isManuallyMerged() {
		Object left = getLeft();
		if (left instanceof UnmatchedHunkTypedElement) {
			UnmatchedHunkTypedElement element = (UnmatchedHunkTypedElement) left;
			return element.isManuallyMerged();
		}
		return false;
	}
	
	public boolean isFuzzUsed() {
		return result.getFuzz() > 0;
	}
	
	public boolean isAllContextIgnored() {
		int fuzz = result.getFuzz();
		if (fuzz > 0) {
			String[] lines = result.getHunk().getLines();
			int contextLines = 0;
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				char c = line.charAt(0);
				if (c == ' ') {
					contextLines++;
				} else {
					if (contextLines > 0 && fuzz >= contextLines) {
						return true;
					}
					contextLines = 0;
				}
			}
			if (contextLines > 0 && fuzz >= contextLines) {
				return true;
			}
			
		}
		return false;
	}

}
