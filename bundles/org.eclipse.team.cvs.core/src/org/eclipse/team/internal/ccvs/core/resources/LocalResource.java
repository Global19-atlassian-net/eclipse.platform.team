package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.io.File;

import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.core.IIgnoreInfo;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.FileNameMatcher;
import org.eclipse.team.internal.ccvs.core.util.FileUtil;
import org.eclipse.team.internal.ccvs.core.util.SyncFileUtil;
import org.eclipse.team.internal.ccvs.core.util.Util;

/**
 * Represents handles to CVS resource on the local file system. Synchronization
 * information is taken from the CVS subdirectories. 
 * 
 * @see LocalFolder
 * @see LocalFile
 */
public abstract class LocalResource implements ICVSResource {

	 // The seperator that must be used when creating CVS resource paths. Never use
	 // the platform default seperator since it is not compatible with CVS resources.
	protected static final String SEPARATOR = Session.SERVER_SEPARATOR;
	protected static final String CURRENT_LOCAL_FOLDER = Session.CURRENT_LOCAL_FOLDER;
		
	/**
	 * The local file represented by this handle.
	 */
	File ioResource;
	
	/**
	 * A local handle 
	 */
	protected LocalResource(File ioResource) {
		Assert.isNotNull(ioResource);
		this.ioResource = ioResource;
	}
	
	/**
	 * Get the extention of the path of resource
	 * relative to the path of root
	 * 
	 * @throws CVSException if root is not a root-folder of resource
	 */
	public String getRelativePath(ICVSFolder root) 
		throws CVSException {
		
		LocalResource rootFolder;
		String result;
		
		try {
			rootFolder = (LocalResource)root;
		} catch (ClassCastException e) {
			throw new CVSException(Policy.bind("LocalResource.invalidResourceClass"),e); //$NON-NLS-1$
		}
		
		result = Util.getRelativePath(rootFolder.getPath(),getPath()); 
		return result;	
	}

	/**
	 * Do a DEEP delete.
	 * @see ICVSResource#delete()
	 */
	public void delete() {
		FileUtil.deepDelete(ioResource);
		// XXX Should we clear the cache in all cases?
		// XXX If not, should we provide a boolean parameter as a choice
	}

	/**
	 * @see ICVSResource#exists()
	 */
	public boolean exists() {
		return ioResource.exists();
	}

	/**
	 * @see ICVSResource#getParent()
	 */
	public ICVSFolder getParent() {
		File parentFile = ioResource.getParentFile();
		if (parentFile == null) return null;
		return new LocalFolder(parentFile);
	}

	/**
	 * @see ICVSResource#getName()
	 */
	public String getName() {
		return ioResource.getName();
	}

	/**
	 * @see ICVSResource#isIgnored()
	 */
	public boolean isIgnored() {
		// a managed resource is never ignored
		if(isManaged()) {
			return false;
		}
		
		// initialize matcher with global ignores and basic CVS ignore patterns
		IIgnoreInfo[] ignorePatterns = TeamPlugin.getManager().getGlobalIgnore();
		FileNameMatcher matcher = new FileNameMatcher(SyncFileUtil.BASIC_IGNORE_PATTERNS);
		for (int i = 0; i < ignorePatterns.length; i++) {
			IIgnoreInfo info = ignorePatterns[i];
			if(info.getEnabled()) {
				matcher.register(info.getPattern(), "true"); //$NON-NLS-1$
			}
		}
		
		// 1. check CVS default patterns and global ignores
		boolean ignored = matcher.match(ioResource.getName());
		
		// 2. check .cvsignore file
		if(!ignored) {
			ignored = CVSProviderPlugin.getSynchronizer().isIgnored(ioResource);		
		}
		
		// 3. check the parent
		if(!ignored) {
			ICVSFolder parent = getParent();
			if(parent==null) return false;
			return parent.isIgnored();
		} else {
			return ignored;
		}
	}

	public void setIgnored() throws CVSException {
		CVSProviderPlugin.getSynchronizer().setIgnored(ioResource, null);
	}
	
	public void setIgnoredAs(String pattern) throws CVSException {
		CVSProviderPlugin.getSynchronizer().setIgnored(ioResource, pattern);		
	}

	/**
	 * @see ICVSResource#isManaged()
	 */
	public boolean isManaged() {
		try {
			return getSyncInfo() != null;
		} catch(CVSException e) {
			return false;
		}
	}
			
	/**
	 * Two ManagedResources are equal, if there cvsResources are
	 * equal (and that is, if the point to the same file)
	 */
	public boolean equals(Object obj) {
		
		if (!(obj instanceof LocalResource)) {
			return false;
		} else {
			return getPath().equals(((LocalResource) obj).getPath());
		}
	}
			
	/*
	 * @see ICVSResource#getPath()
	 */
	public String getPath() {
		return ioResource.getAbsolutePath();
	}	
	
	/*
	 * @see ICVSResource#isFolder()
	 */
	public boolean isFolder() {
		return false;
	}
	
	/*
	 * @see ICVSResource#getSyncInfo()
	 */
	public ResourceSyncInfo getSyncInfo() throws CVSException {
		return CVSProviderPlugin.getSynchronizer().getResourceSync(ioResource);
	}

	/*
	 * @see ICVSResource#setSyncInfo(ResourceSyncInfo)
	 */
	public void setSyncInfo(ResourceSyncInfo info) throws CVSException {
		CVSProviderPlugin.getSynchronizer().setResourceSync(ioResource, info);		
	}
	
	/*
	 * Implement the hashcode on the underlying strings, like it is done in the equals.
	 */
	public int hashCode() {
		return getPath().hashCode();
	}	
	
	/*
	 * Give the pathname back
	 */
	public String toString() {
		return getPath();
	}
	
	public File getLocalFile() {
		return ioResource;
	}
}