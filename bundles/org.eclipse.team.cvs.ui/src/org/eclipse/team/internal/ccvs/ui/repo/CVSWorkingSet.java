/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.repo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.XMLWriter;

public class CVSWorkingSet {
	private String name;
	// Map of ICVSRepositoryLocation -> Set (String folder paths that are repository relative)
	Map remotePaths = new HashMap();

	public CVSWorkingSet(String name) {
		this.name = name;
	}
	
	/**
	 * Returns the folders.
	 * @return ICVSRemoteFolder[]
	 */
	public ICVSRemoteFolder[] getFolders(IProgressMonitor monitor) throws CVSException {
		ICVSRepositoryLocation[] locations = getRepositoryLocations();
		List result = new ArrayList();
		for (int i = 0; i < locations.length; i++) {
			ICVSRepositoryLocation location = locations[i];
			ICVSRemoteFolder[] folders = getFoldersForTag(location, CVSTag.DEFAULT, monitor);
			result.addAll(Arrays.asList(folders));
		}
		return (ICVSRemoteFolder[]) result.toArray(new ICVSRemoteFolder[result.size()]);
	}

	/**
	 * Method getFoldersForTag.
	 * @param root
	 * @param tag
	 * @param monitor
	 * @return ICVSRemoteResource[]
	 */
	public ICVSRemoteFolder[] getFoldersForTag(ICVSRepositoryLocation location, CVSTag tag, IProgressMonitor monitor) throws CVSException {
		RepositoryRoot root = CVSUIPlugin.getPlugin().getRepositoryManager().getRepositoryRootFor(location);
		String[] paths = getRemotePaths(location);
		List result = new ArrayList();
		for (int i = 0; i < paths.length; i++) {
			String path = paths[i];
			result.add(root.getRemoteFolder(path, tag, monitor));
		}
		return (ICVSRemoteFolder[]) result.toArray(new ICVSRemoteFolder[result.size()]);
	}
	
	/**
	 * Returns the locations.
	 * @return ICVSRepositoryLocation[]
	 */
	public ICVSRepositoryLocation[] getRepositoryLocations() {
		Set locations = remotePaths.keySet();
		return (ICVSRepositoryLocation[]) locations.toArray(new ICVSRepositoryLocation[locations.size()]);
	}

	/**
	 * Returns the name.
	 * @return String
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the folders.
	 * @param folders The folders to set
	 */
	public void setFolders(ICVSRemoteFolder[] folders) throws CVSException {
		remotePaths = new HashMap();
		for (int i = 0; i < folders.length; i++) {
			ICVSRemoteFolder folder = folders[i];
			ICVSRepositoryLocation location = folder.getRepository();
			addRemotePath(location, RepositoryRoot.getRemotePathFor(folder));
		}
	}

	/**
	 * Sets the name.
	 * @param name The name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Write out the state of the receiver as XML on the given XMLWriter.
	 * 
	 * @param writer
	 * @throws IOException
	 */
	public void writeState(XMLWriter writer) throws IOException {

		HashMap attributes = new HashMap();

		attributes.clear();
		attributes.put(RepositoriesViewContentHandler.NAME_ATTRIBUTE, getName());
		writer.startTag(RepositoriesViewContentHandler.WORKING_SET_TAG, attributes, true);
		
		// for each module, write the moduel, tags and auto-refresh files.
		ICVSRepositoryLocation[] locations = getRepositoryLocations();
		for (int i = 0; i < locations.length; i++) {
			ICVSRepositoryLocation location = locations[i];
			attributes.clear();
			attributes.put(RepositoriesViewContentHandler.ID_ATTRIBUTE, location.getLocation());
			writer.startTag(RepositoriesViewContentHandler.REPOSITORY_TAG, attributes, true);
			String[] paths = getRemotePaths(location);
			for (int j = 0; j < paths.length; j++) {
				String path = paths[j];
				attributes.clear();
				String name = path;
				if (RepositoryRoot.isDefinedModuleName(path)) {
					name = RepositoryRoot.getDefinedModuleName(path);
					attributes.put(RepositoriesViewContentHandler.TYPE_ATTRIBUTE, RepositoriesViewContentHandler.DEFINED_MODULE_TYPE);
				}
				attributes.put(RepositoriesViewContentHandler.PATH_ATTRIBUTE, name);
				writer.startAndEndTag(RepositoriesViewContentHandler.MODULE_TAG, attributes, true);
			}
			writer.endTag(RepositoriesViewContentHandler.REPOSITORY_TAG);
		}
		writer.endTag(RepositoriesViewContentHandler.WORKING_SET_TAG);
	}
	
	/**
	 * Method getRemotePaths.
	 * @param location
	 * @return String[]
	 */
	private String[] getRemotePaths(ICVSRepositoryLocation location) {
		Set set = (Set)remotePaths.get(location);
		return (String[])set.toArray(new String[set.size()]);
	}
	
	/**
	 * Method addRemotePath.
	 * @param currentRemotePath
	 */
	void addRemotePath(ICVSRepositoryLocation location, String currentRemotePath) {
		Set set = (Set)remotePaths.get(location);
		if (set == null) {
			set = new HashSet();
			remotePaths.put(location, set);
		}
		set.add(currentRemotePath);
	}
	
}
