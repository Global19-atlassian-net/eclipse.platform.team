/**********************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.team.internal.ccvs.core.filesystem;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFile;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFolder;

public class CVSURI {

	private static final String SCHEME = "cvs";
	private final ICVSRepositoryLocation repository;
	private final IPath path;
	private final CVSTag tag;

	public static CVSURI fromUri(URI uri) {
		try {
			ICVSRepositoryLocation repository = getRepository(uri);
			IPath path = getPath(uri);
			CVSTag tag = getTag(uri);
			return new CVSURI(repository, path, tag);
		} catch (CVSException e) {
			CVSProviderPlugin.log(e);
			throw new IllegalArgumentException(NLS.bind("Invalid uri {0}: {1}", new String[] {uri.toString(), e.getMessage()}));
		}
	}

	private static CVSTag getTag(URI uri) {
		String f = uri.getFragment();
		int i = f.indexOf(',');
		if (i == -1) {
			return CVSTag.DEFAULT;
		}
		String name = f.substring(i + 1);
		return new CVSTag(name, CVSTag.BRANCH);
	}

	private static IPath getPath(URI uri) {
		String path = uri.getFragment();
		int i = path.indexOf(',');
		if (i != -1) {
			path = path.substring(0, i);
		}
		return new Path(path);
	}

	private static ICVSRepositoryLocation getRepository(URI uri) throws CVSException {
		String ssp = uri.getSchemeSpecificPart();
		if (!ssp.startsWith(":")) {
			ssp = ":" + ssp;
		}
		return CVSRepositoryLocation.fromString(ssp);
	}
	
	public CVSURI(ICVSRepositoryLocation repository, IPath path, CVSTag tag) {
		this.repository = repository;
		this.path = path;
		this.tag = tag;
	}
	
	public CVSURI append(String name) {
		return new CVSURI(repository, path.append(name), tag);
	}

	public CVSURI append(IPath childPath) {
		return new CVSURI(repository, path.append(childPath), tag);
	}
	
	public String getLastSegment() {
		return path.lastSegment();
	}

	public URI toURI() {
		try {
			String fragment = path.toString();
			if (tag != null && tag.getType() != CVSTag.HEAD) {
				fragment += ","+tag.getName();
			}
			return new URI(SCHEME, repository.getLocation(false), fragment);
		} catch (URISyntaxException e) {
			throw new Error(e.getMessage());
		}
	}

	public boolean isRepositoryRoot() {
		return path.segmentCount() == 0;
	}

	public CVSURI removeLastSegment() {
		return new CVSURI(repository, path.removeLastSegments(1), tag);
	}

	public ICVSRemoteFolder getParentFolder() {
		return removeLastSegment().toFolder();
	}

	public String getRepositoryName() {
		return repository.toString();
	}

	public ICVSRemoteFolder toFolder() {
		return new RemoteFolder(null, repository, path.toString(), tag);
	}
	
	public ICVSRemoteFile toFile() {
		// TODO: What about keyword mode?
		return RemoteFile.create(path.toString(), repository);
	}

}
