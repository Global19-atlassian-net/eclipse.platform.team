package org.eclipse.team.internal.ccvs.core.util;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.resources.CVSEntryLineTag;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.NotifyInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;

/*
 * This is a helper class that knows the format of the CVS metafiles. It
 * provides a bridge between the CVS metafile formats and location to the
 * Eclipse CVS client ResourceSyncInfo and FolderSyncInfo types.
 */
public class SyncFileWriter {

	// the famous CVS meta directory name
	public static final String CVS_DIRNAME = "CVS"; //$NON-NLS-1$

	// CVS meta files located in the CVS subdirectory
	public static final String REPOSITORY = "Repository"; //$NON-NLS-1$
	public static final String ROOT = "Root"; //$NON-NLS-1$
	public static final String STATIC = "Entries.Static";	 //$NON-NLS-1$
	public static final String TAG = "Tag";	 //$NON-NLS-1$
	public static final String ENTRIES = "Entries"; //$NON-NLS-1$
	//private static final String PERMISSIONS = "Permissions"; //$NON-NLS-1$
	public static final String ENTRIES_LOG="Entries.Log"; //$NON-NLS-1$
	public static final String NOTIFY = "Notify"; //$NON-NLS-1$
	
	// the local workspace file that contains pattern for ignored resources
	public static final String IGNORE_FILE = ".cvsignore"; //$NON-NLS-1$

	// Some older CVS clients may of added a line to the entries file consisting
	// of only a 'D'. It is safe to ingnore these entries.	
	private static final String FOLDER_TAG="D"; //$NON-NLS-1$
	
	// Command characters found in the Entries.log file
	private static final String ADD_TAG="A "; //$NON-NLS-1$
	private static final String REMOVE_TAG="R "; //$NON-NLS-1$	

	// file and folder patterns that are ignored by default by the CVS server on import.
	public static final String[] PREDEFINED_IGNORE_PATTERNS = {
		"CVS", ".#*", "#*", ",*", "_$*", "*~", "*$", "*.a", "*.bak", "*.BAK",  //$NON-NLS-1$  //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
		"*.elc", "*.exe", "*.ln", "*.o", "*.obj", "*.olb", "*.old", "*.orig", "*.rej", "*.so", //$NON-NLS-1$  //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
		"*.Z", ".del-*", ".make.state", ".nse_depinfo", "CVS.adm", //$NON-NLS-1$  //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		"cvslog.*", "RCS", "RCSLOG", "SCCS", "tags", "TAGS"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		
	// file and folder patterns that are ignored by default by the CVS server on import.
	public static final String[] BASIC_IGNORE_PATTERNS = {"CVS", ".#*"}; //$NON-NLS-1$ //$NON-NLS-2$

	// key for saving the mod stamp for each writen meta file
	public static final QualifiedName MODSTAMP_KEY = new QualifiedName("org.eclipse.team.cvs.core", "meta-file-modtime"); //$NON-NLS-1$ //$NON-NLS-2$
	
	/**
	 * Reads the CVS/Entries, CVS/Entries.log and CVS/Permissions files from the
	 * specified folder and returns ResourceSyncInfo instances for the data stored therein.
	 * If the folder does not have a CVS subdirectory then <code>null</code> is returned.
	 */
	public static ResourceSyncInfo[] readAllResourceSync(IContainer parent) throws CVSException {
		IFolder cvsSubDir = getCVSSubdirectory(parent);
		if (! cvsSubDir.exists()) return null;

		// process Entries file contents
		String[] entries = readLines(cvsSubDir.getFile(ENTRIES));
		if (entries == null) return null;
		Map infos = new TreeMap();
		for (int i = 0; i < entries.length; i++) {
			String line = entries[i];
			if(!FOLDER_TAG.equals(line) && !"".equals(line)) { //$NON-NLS-1$
				ResourceSyncInfo info = new ResourceSyncInfo(line, null, null);
				infos.put(info.getName(), info);			
			}
		}
		
		// process Entries.log file contents
		String[] entriesLog = readLines(cvsSubDir.getFile(ENTRIES_LOG));
		if (entriesLog != null) {
			for (int i = 0; i < entriesLog.length; i++) {
				String line = entriesLog[i];
				if (line.startsWith(ADD_TAG)) {
					line = line.substring(ADD_TAG.length());
					ResourceSyncInfo info = new ResourceSyncInfo(line, null, null);
					infos.put(info.getName(), info);
				} else if (line.startsWith(REMOVE_TAG)) {
					line = line.substring(REMOVE_TAG.length());
					ResourceSyncInfo info = new ResourceSyncInfo(line, null, null);
					infos.remove(info.getName());
				}
			}
		}
		
		// XXX no longer processes CVS/Permissions (was never written) -- should we?
		
		return (ResourceSyncInfo[])infos.values().toArray(new ResourceSyncInfo[infos.size()]);
	}
	
	/**
	 * Writes the CVS/Entries, CVS/Entries.log and CVS/Permissions files to the
	 * specified folder using the data contained in the specified ResourceSyncInfo instance.
	 * If the folder does not have a CVS subdirectory then <code>null</code> is returned.
	 */
	public static void writeAllResourceSync(IContainer parent, ResourceSyncInfo[] infos) throws CVSException {
		try {
			IFolder cvsSubDir = createCVSSubdirectory(parent);
			
			// format file contents
			String[] entries = new String[infos.length];
			for (int i = 0; i < infos.length; i++) {
				ResourceSyncInfo info = infos[i];
				entries[i] = info.getEntryLine();
			}
	
			// write Entries
			writeLines(cvsSubDir.getFile(ENTRIES), entries);
			
			// delete Entries.log
			cvsSubDir.getFile(ENTRIES_LOG).delete(IResource.NONE, null);
		} catch(CoreException e) {
			throw CVSException.wrapException(e);
		}
	}
		
	/**
	 * Reads the CVS/Root, CVS/Repository, CVS/Tag, and CVS/Entries.static files from
	 * the specified folder and returns a FolderSyncInfo instance for the data stored therein.
	 * If the folder does not have a CVS subdirectory then <code>null</code> is returned.
	 */
	public static FolderSyncInfo readFolderSync(IContainer folder) throws CVSException {
		IFolder cvsSubDir = getCVSSubdirectory(folder);
		if (! cvsSubDir.exists()) return null;
		
		// read CVS/Root
		String root = readFirstLine(cvsSubDir.getFile(ROOT));
		if (root == null) return null;
		
		// read CVS/Repository
		String repository = readFirstLine(cvsSubDir.getFile(REPOSITORY));
		if (repository == null) return null;
		
		// read CVS/Tag
		String tag = readFirstLine(cvsSubDir.getFile(TAG));
		CVSTag cvsTag = (tag != null) ? new CVSEntryLineTag(tag) : null;

		// read Entries.Static
		String staticDir = readFirstLine(cvsSubDir.getFile(STATIC));
		boolean isStatic = (staticDir != null);
		
		// return folder sync
		return new FolderSyncInfo(repository, root, cvsTag, isStatic);		
	}
	
	/**
	 * Writes the CVS/Root, CVS/Repository, CVS/Tag, and CVS/Entries.static files to the
	 * specified folder using the data contained in the specified FolderSyncInfo instance.
	 */
	public static void writeFolderSync(IContainer folder, FolderSyncInfo info) throws CVSException {
		try {
			IFolder cvsSubDir = createCVSSubdirectory(folder);
	
			// write CVS/Root
			writeLines(cvsSubDir.getFile(ROOT), new String[] {info.getRoot()});
			
			// write CVS/Repository
			writeLines(cvsSubDir.getFile(REPOSITORY), new String[] {info.getRepository()});
			
			// write CVS/Tag
			IFile tagFile = cvsSubDir.getFile(TAG);
			if (info.getTag() != null) {
				writeLines(tagFile, new String[] {info.getTag().toEntryLineFormat(false)});
			} else {
				if(tagFile.exists()) {
					tagFile.delete(IResource.NONE, null);
				}
			}
			
			// write CVS/Entries.Static
			IFile staticFile = cvsSubDir.getFile(STATIC);
			if(info.getIsStatic()) {
				// the existance of the file is all that matters
				writeLines(staticFile, new String[] {""}); //$NON-NLS-1$
			} else {
				if(staticFile.exists()) {
					staticFile.delete(IResource.NONE, null);
				}
			}
		} catch(CoreException e) {
			throw CVSException.wrapException(e);
		}
	}

	/**
	 * Returns all .cvsignore entries for the specified folder.
	 */
	public static String[] readCVSIgnoreEntries(IContainer folder) throws CVSException {
		IFile ignoreFile = folder.getFile(new Path(IGNORE_FILE));
		if (ignoreFile != null) {
			return readLines(ignoreFile);
		}
		return null;
	}
	
	/**
	 * Writes all entries to the specified folder's .cvsignore file, overwriting any
	 * previous edition of the file.
	 */
	public static void writeCVSIgnoreEntries(IContainer folder, String[] patterns) throws CVSException {
		IFile ignoreFile = folder.getFile(new Path(IGNORE_FILE));
		writeLines(ignoreFile, patterns);
	}	

	/**
	 * Delete folder sync is equilavent to removing the CVS subdir.
	 */
	public static void deleteFolderSync(IContainer folder) throws CVSException {		
		try {
			getCVSSubdirectory(folder).delete(IResource.NONE, null);
		} catch(CoreException e) {
			throw CVSException.wrapException(e);
		}
	}

	/**
	 * Reads the CVS/Notify file from the specified folder and returns NotifyInfo instances 
	 * for the data stored therein. If the folder does not have a CVS subdirectory then <code>null</code> is returned.
	 */
	public static NotifyInfo[] readAllNotifyInfo(IContainer parent) throws CVSException {
		IFolder cvsSubDir = getCVSSubdirectory(parent);
		if (! cvsSubDir.exists()) return null;

		// process Notify file contents
		String[] entries = readLines(cvsSubDir.getFile(NOTIFY));
		if (entries == null) return null;
		Map infos = new TreeMap();
		for (int i = 0; i < entries.length; i++) {
			String line = entries[i];
			if(!"".equals(line)) { //$NON-NLS-1$
				NotifyInfo info = new NotifyInfo(parent, line);
				infos.put(info.getName(), info);			
			}
		}
		
		return (NotifyInfo[])infos.values().toArray(new NotifyInfo[infos.size()]);
	}
	
	/**
	 * Writes the CVS/Notify file to the specified folder using the data contained in the 
	 * specified NotifyInfo instances. A CVS subdirectory must already exist (an exception 
	 * is thrown if it doesn't).
	 */
	public static void writeAllNotifyInfo(IContainer parent, NotifyInfo[] infos) throws CVSException {
		// get the CVS directory
		IFolder cvsSubDir = getCVSSubdirectory(parent);
		// write lines will throw an exception if the CVS directoru does not exist
		
		if (infos.length == 0) {
			// if there are no notify entries, delete the notify file
			try {
				IFile notifyFile = cvsSubDir.getFile(NOTIFY);
				if(notifyFile.exists()) {
					notifyFile.delete(IResource.NONE, null);
				}
			} catch (CoreException e) {
				throw CVSException.wrapException(e);
			}
		} else {
			// format file contents
			String[] entries = new String[infos.length];
			for (int i = 0; i < infos.length; i++) {
				NotifyInfo info = infos[i];
				entries[i] = info.getNotifyLine();
			}
	
			// write Notify entries
			writeLines(cvsSubDir.getFile(NOTIFY), entries);
		}
	}
		
	/**
	 * Returns the CVS subdirectory for this folder.
	 */
	private static IFolder getCVSSubdirectory(IContainer folder) throws CVSException {
		return folder.getFolder(new Path(CVS_DIRNAME));
	}
	
	/**
	 * Creates and makes team-private and returns a CVS subdirectory in this folder.
	 */
	private static IFolder createCVSSubdirectory(IContainer folder) throws CVSException {
		try {
			final IFolder cvsSubDir = getCVSSubdirectory(folder);
			if (! cvsSubDir.exists()) {
				// important to have both the folder creation and setting of team-private in the
				// same runnable so that the team-private flag is set before other delta listeners 
				// sees the CVS folder creation.
				ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
					public void run(IProgressMonitor monitor) throws CoreException {
						cvsSubDir.create(false /*don't force*/, true /*make local*/, null);
						cvsSubDir.setTeamPrivateMember(true);
					} 
				}, null);
			}
			return cvsSubDir;
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
	}

	/*
	 * Reads the first line of the specified file.
	 * Returns null if the file does not exist, or the empty string if it is blank.
	 */
	private static String readFirstLine(IFile file) throws CVSException {
		if (! file.exists()) return null;
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(file.getContents()));
			try {
				String line = reader.readLine();
				if (line == null) return ""; //$NON-NLS-1$
				return line;
			} finally {
				reader.close();
			}
		} catch (IOException e) {
			throw CVSException.wrapException(e);
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
	}
	
	/*
	 * Reads all lines of the specified file.
	 * Returns null if the file does not exist.
	 */
	private static String[] readLines(IFile file) throws CVSException {
		if (! file.exists()) return null;
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(file.getContents()));
			List fileContentStore = new ArrayList();
			try {
				String line;
				while ((line = reader.readLine()) != null) {
					fileContentStore.add(line);
				}
				return (String[]) fileContentStore.toArray(new String[fileContentStore.size()]);
			} finally {
				reader.close();
			}
		} catch (IOException e) {
			throw CVSException.wrapException(e);
		}  catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
	}
	
	/*
	 * Writes all lines to the specified file, using linefeed terminators for
	 * compatibility with other CVS clients.
	 */
	private static void writeLines(final IFile file, final String[] contents) throws CVSException {
		try {
			// The creation of sync files has to be in a runnable in order for the resulting delta
			// to include the MODSTAMP value. If not in a runnable then create/setContents
			// will trigger a delta and the SyncFileWriter change listener won't know that the delta
			// was a result of our own creation.
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					try {
						ByteArrayOutputStream os = new ByteArrayOutputStream();
						writeLinesToStreamAndClose(os, contents);
						if(!file.exists()) {
							file.create(new ByteArrayInputStream(os.toByteArray()), IResource.NONE /*don't keep history and don't force*/, null);
						} else {
							file.setContents(new ByteArrayInputStream(os.toByteArray()), IResource.NONE /*don't keep history and don't force*/, null);
						}			
						file.setSessionProperty(MODSTAMP_KEY, new Long(file.getModificationStamp()));
					} catch(CVSException e) {
						throw new CoreException(e.getStatus());
					}
				}
			}, null);
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
	}
	
	private static void writeLinesToStreamAndClose(OutputStream os, String[] contents) throws CVSException {
		try {
			try {
				for (int i = 0; i < contents.length; i++) {
					os.write(contents[i].getBytes()); // XXX should we specify a character encoding?
					os.write(0x0A); // newline byte
				}
			} finally {
				os.close();
			}
		} catch (IOException e) {
			throw CVSException.wrapException(e);
		}
	}
}