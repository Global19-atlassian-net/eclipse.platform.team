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
package org.eclipse.team.internal.ccvs.core.client.listeners;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.team.internal.ccvs.core.CVSAnnotateBlock;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.client.CommandOutputListener;

public class AnnotateListener extends CommandOutputListener {

/**
 * Handle output from the CVS Annotate command.
 */	
	ByteArrayOutputStream aStream = new ByteArrayOutputStream();
	List blocks = new ArrayList();
	int lineNumber;
	String error;
	
	/**
	 * @return
	 */
	public String getError() {
		return error;
	}

	public IStatus messageLine(String line, ICVSRepositoryLocation location, ICVSFolder commandRoot, IProgressMonitor monitor) {

		CVSAnnotateBlock aBlock = new CVSAnnotateBlock(line, lineNumber++);
		if (!aBlock.isValid()) {
			error = line;
		}
		
		/**
		 * Make sure all lines have a line terminator.
		 */
		try {
			aStream.write(line.substring(aBlock.getSourceOffset()).getBytes());
			if (!(line.endsWith("\r") || line.endsWith("\r\n"))) {
				aStream.write(System.getProperty("line.separator").getBytes());
			}
		} catch (IOException e) {
		}
		add(aBlock);
		return OK;
	}
	
	public InputStream getContents() {
		return new ByteArrayInputStream(aStream.toByteArray());
	}
	
	public List getCvsAnnotateBlocks() {
		return blocks;
	}
	/**
	 * Add an annotate block to the receiver merging this block with the
	 * previous block if it is part of the same change.
	 * @param aBlock
	 */
	private void add(CVSAnnotateBlock aBlock) {
		
		int size = blocks.size();
		if (size == 0) {
			blocks.add(aBlock);
		} else {
			CVSAnnotateBlock lastBlock = (CVSAnnotateBlock) blocks.get(size - 1);
			if (lastBlock.getRevision().equals(aBlock.getRevision())) {
				lastBlock.setEndLine(aBlock.getStartLine());
			} else {
				blocks.add(aBlock);
			}
		}
	}

	/**
	 * @return
	 */
	public boolean hasError() {
		return (error != null);
	}

}
