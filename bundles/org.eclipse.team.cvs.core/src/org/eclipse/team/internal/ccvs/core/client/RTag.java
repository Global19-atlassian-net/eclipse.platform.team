package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.team.ccvs.core.CVSStatus;
import org.eclipse.team.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Command.GlobalOption;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.resources.ICVSResource;

public class RTag extends Command {
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
	
	protected RTag() { }
	protected String getCommandId() {
		return "rtag"; //$NON-NLS-1$
	}

	protected ICVSResource[] computeWorkResources(Session session, LocalOption[] localOptions,
		String[] arguments) throws CVSException {
		if (arguments.length < 2) throw new IllegalArgumentException();
		return new ICVSResource[0];
	}

	protected void sendLocalResourceState(Session session, GlobalOption[] globalOptions,
		LocalOption[] localOptions, ICVSResource[] resources, IProgressMonitor monitor)
		throws CVSException {
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