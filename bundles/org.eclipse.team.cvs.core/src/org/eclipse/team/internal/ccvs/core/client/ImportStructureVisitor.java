package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFile;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.resources.ICVSResourceVisitor;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.FileNameMatcher;

/**
 * The ImportStructureVisitor sends the content of the folder it is
 * used on to the server. It constructs the locations of the resources
 * because the resources do not yet have a remote-location.<br>
 * It can also ignore certain files and decides wether to send
 * a file in binary or text mode due to a specification that is passed 
 * as a "wrapper" argument.
 */
class ImportStructureVisitor implements ICVSResourceVisitor {
	
	private static final String KEYWORD_OPTION = "-k"; //$NON-NLS-1$
	private static final String QUOTE = "'"; //$NON-NLS-1$
	
	protected Session session;
	private Set sentFolders;
	protected IProgressMonitor monitor;
	private String[] wrappers;
	
	private FileNameMatcher ignoreMatcher;
	private FileNameMatcher wrapMatcher;
	
	/**
	 * Constructor for ImportStructureVisitor.
	 * @param requestSender
	 * @param mRoot
	 * @param monitor
	 */
	public ImportStructureVisitor(Session session, 
		String[] wrappers, IProgressMonitor monitor) {

		this.session = session;
		this.monitor = monitor;
		this.wrappers = wrappers;
		wrapMatcher = initWrapMatcher(wrappers);
	}
	

	/**
	 * Inits the wrapMatcher, that is responsible to find out
	 * whether a file is to be send as a binary (on an import)
	 * or not.
	 * 
	 * Takes wrappers of this format:
	 *   *.class -k 'o'
	 * 
	 * and inits the FileNameMatcher to give
	 *   -ko back if you call it with match("somename.class")
	 * 
	 * ignores all wrappers, that do not contain -k
	 */
	private FileNameMatcher initWrapMatcher(String[] wrappers) {
		
		FileNameMatcher wrapMatcher;
		
		if (wrappers == null) {
			return null;
		}
		
		wrapMatcher = new FileNameMatcher();
		
		for (int i = 0; i < wrappers.length; i++) {
			
			if (wrappers[i].indexOf(KEYWORD_OPTION) == -1) {
				continue;
			}
			
			StringTokenizer st = new StringTokenizer(wrappers[i]);
			String pattern = st.nextToken();
			String option = st.nextToken();
			// get rid of the quotes
			StringTokenizer quoteSt = 
				new StringTokenizer(st.nextToken(),QUOTE);
			option += quoteSt.nextToken();
			
			wrapMatcher.register(pattern,option);
		}
		
		return wrapMatcher;
	}	
	
	/**
	 * @see ICVSResourceVisitor#visitFile(IManagedFile)
	 */
	public void visitFile(ICVSFile mFile) throws CVSException {
		if (ignoreMatcher != null && ignoreMatcher.match(mFile.getName())) {
			return;
		}
		
		String mode;
		if (wrapMatcher != null) {
			mode = wrapMatcher.getMatch(mFile.getName());
		} else {
			mode = ""; //$NON-NLS-1$
		}
		boolean binary = mode != null && mode.indexOf(ResourceSyncInfo.BINARY_TAG) != -1;
		session.sendModified(mFile, binary, monitor);
	}

	/**
	 * @see ICVSResourceVisitor#visitFolder(ICVSFolder)
	 */
	public void visitFolder(ICVSFolder mFolder) throws CVSException {
		
		if (ignoreMatcher != null && ignoreMatcher.match(mFolder.getName())) {
			return;
		}
		
		session.sendConstructedDirectory(mFolder.getRelativePath(session.getLocalRoot()));
		mFolder.acceptChildren(this);
	}

}