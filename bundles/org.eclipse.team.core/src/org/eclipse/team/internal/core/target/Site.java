/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.team.internal.core.target;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.core.Assert;

/**
 * A <code>Site</code> is a place where resources can be deployed and 
 * retrieved via a target provider.
 * 
 * @see ISiteFactory
 */
public abstract class Site {
	
	//The location of the site's resources:
	private URL rootUrl;

	/**
	 * Answers a <code>TargetProvider</code> instance for the given path at 
	 * this site.
	 */
	public abstract TargetProvider newProvider(IPath intrasitePath) 
		throws TeamException;
	
	/**
	 * Answers the type identifier for this site. For example:
	 * <blockquote><pre>
	 * org.eclipse.team.target.webdav
	 * </pre></blockquote>
	 * 
	 * @return string identifier for this site
	 */
	public abstract String getType();
	
	/**
	 * Answers a string that can be displayed to the user that represents
	 * this site. For example:
	 * <blockquote><pre>
	 * http://usename@www.mysite.com/dav (WebDav)
 	 * </pre></blockquote>
	 */
	public String getDisplayName() {
		return getURL().toExternalForm();
	}

	/**
	 * Writes the state of this site such that the corresponding concrete
	 * <code>ISiteFactory</code> class can restore the site.
	 * 
	 * @param os the object stream into which to write it's state
	 */
	public abstract void writeObject(ObjectOutputStream os);
	
	/**
	 * Returns a handle to the remote resource that represents this site
	 * on the server.
	 * 
	 * @return a remote handle to this site that may or may not exist
	 */
	public IRemoteTargetResource getRemoteResource() throws TeamException {
		return newProvider(Path.EMPTY).getRemoteResource();
	}
		
	/**
	 * Compares two Sites. The result is <code>true</code> if and only if 
	 * the argument is not <code>null</code> and is a Site object that 
	 * represents the same Site as this object. Two Site objects are equal 
	 * if they have the same types and URLs.
	 * 
	 * @param other the Site to compare with
	 * 
	 * @return <code>true</code> if the Sites are the same; <code>false</code>
	 * otherwise
	 * 
	 * @see Object#equals(Object)
	 */
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof Site)) return false;
		Site location = (Site)other;
		if (!getType().equals(location.getType())) return false;
		URL url = getURL();
		if (url == null) return super.equals(other);
		return url.equals(location.getURL());
	}
	
	public int hashCode() {
		URL url = getURL();
		if (url == null) return super.hashCode();
		return url.hashCode();
	}

	/**
	 * Debugging helper
	 * 
	 * @see Object#toString()
	 */
	public String toString() {
		return getDisplayName();
	}
	
	/**
	 * Should be called whenever a site is being deleted from the workspace.
	 * This method removes the encrypted login info stored for the site.
	 * @throws TeamException
	 */
	public abstract void dispose() throws TeamException;

	/**
	 * Answers the location of this site as a URL. For example:
	 * <blockquote><pre>
	 * http://www.mysite.com:14356/dav
	 * </pre></blockquote>
	 * 
	 * @return URL location of this site
	 */
	public URL getURL() {
		return rootUrl;
	}

	/**
	 * Sets the rootUrl.
	 * @param rootUrl The rootUrl to set
	 */
	protected void setURL(URL rootUrl) {
		this.rootUrl = rootUrl;
	}

}