/*******************************************************************************
 * Copyright (c) 2000, 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.core.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Command.GlobalOption;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;

public class RTag extends RemoteCommand {
	/*** Local options: specific to tag ***/
	public static final LocalOption CREATE_BRANCH = Tag.CREATE_BRANCH;

	/**
	 * Makes a -r or -D option for a tag.
	 * Valid for: checkout export history rdiff update
	 */
	public static LocalOption makeTagOption(CVSTag tag) {
		int type = tag.getType();
		switch (type) {
			case CVSTag.BRANCH:
			case CVSTag.VERSION:
			case CVSTag.HEAD:
				return new LocalOption("-r", tag.getName()); //$NON-NLS-1$
			case CVSTag.DATE:
				return new LocalOption("-D", tag.getName()); //$NON-NLS-1$
			default:
				// Unknow tag type!!!
				throw new IllegalArgumentException();
		}
	}
	
	protected String getRequestId() {
		return "rtag"; //$NON-NLS-1$
	}

	protected ICVSResource[] computeWorkResources(Session session, LocalOption[] localOptions,
		String[] arguments) throws CVSException {
		if (arguments.length < 2) throw new IllegalArgumentException();
		return super.computeWorkResources(session, localOptions, arguments);
	}
	
	public IStatus execute(Session session, GlobalOption[] globalOptions,
		LocalOption[] localOptions, CVSTag sourceTag, CVSTag tag, String[] arguments,
		IProgressMonitor monitor) throws CVSException {
		
		if(tag.getType() != CVSTag.VERSION && tag.getType() != CVSTag.BRANCH) {
			throw new CVSException(new CVSStatus(IStatus.ERROR, Policy.bind("Tag.notVersionOrBranchError"))); //$NON-NLS-1$
		}
		
		// Add the source tag to the local options
		List modifiedLocalOptions = new ArrayList(localOptions.length + 1);
		if (sourceTag==null) sourceTag = CVSTag.DEFAULT;
		modifiedLocalOptions.addAll(Arrays.asList(localOptions));
		modifiedLocalOptions.add(makeTagOption(sourceTag));
		
		// Add the CREATE_BRANCH option for a branch tag
		if (tag.getType() == tag.BRANCH) {
			if ( ! CREATE_BRANCH.isElementOf(localOptions)) {
				modifiedLocalOptions.add(CREATE_BRANCH);
			}
		}
		
		// Add the tag name to the start of the arguments
		String[] newArguments = new String[arguments.length + 1];
		newArguments[0] = tag.getName();
		System.arraycopy(arguments, 0, newArguments, 1, arguments.length);
		
		return execute(session, globalOptions, 
			(LocalOption[]) modifiedLocalOptions.toArray(new LocalOption[modifiedLocalOptions.size()]), 
			newArguments, null, monitor);
	}
}