package org.eclipse.team.ccvs.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;

 /**
  * This interface represents a remote folder in a repository. It provides
  * access to the members (remote files and folders) of a remote folder
  * 
  * Clients are not expected to implement this interface.
  */
public interface ICVSRemoteFolder extends ICVSRemoteResource {

	/**
	 * Allows a client to change the context of a remote folder handle.  For
	 * example, if a remote folder was created with the HEAD context (e.g. can
	 * be used to browse the main branch) use this method to change the
	 * context to another branch tag or to a version tag.
	 */
	public void setTag(CVSTag tagName);
	
	/**
	 * Return the context of this handle. The returned tag can be a branch or
	 * version tag.
	 */
	public CVSTag getTag();
}