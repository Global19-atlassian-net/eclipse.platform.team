package org.eclipse.team.internal.ccvs.ui;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ICVSRunnable;
import org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener;
import org.eclipse.team.internal.ccvs.core.client.Command.KSubstOption;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.team.ui.TeamImages;
import org.eclipse.ui.IDecoratorManager;

public class CVSLightweightDecorator
	extends LabelProvider
	implements ILightweightLabelDecorator, IResourceChangeListener, IResourceStateChangeListener {

	// Images cached for better performance
	private static ImageDescriptor dirty;
	private static ImageDescriptor checkedIn;
	private static ImageDescriptor checkedOut;
	private static ImageDescriptor merged;
	private static ImageDescriptor newResource;

	/*
	 * Define a cached image descriptor which only creates the image data once
	 */
	public static class CachedImageDescriptor extends ImageDescriptor {
		ImageDescriptor descriptor;
		ImageData data;
		public CachedImageDescriptor(ImageDescriptor descriptor) {
			this.descriptor = descriptor;
		}
		public ImageData getImageData() {
			if (data == null) {
				data = descriptor.getImageData();
			}
			return data;
		}
	}

	static {
		dirty = new CachedImageDescriptor(TeamImages.getImageDescriptor(ISharedImages.IMG_DIRTY_OVR));
		checkedIn = new CachedImageDescriptor(TeamImages.getImageDescriptor(ISharedImages.IMG_CHECKEDIN_OVR));
		//checkedOut = new CachedImageDescriptor(TeamImages.getImageDescriptor(ISharedImages.IMG_CHECKEDOUT_OVR));
		checkedOut = new CachedImageDescriptor(TeamImages.getImageDescriptor(ISharedImages.IMG_CHECKEDIN_OVR));
		merged = new CachedImageDescriptor(CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_MERGED));
		newResource = new CachedImageDescriptor(CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_QUESTIONABLE));
	}

	public CVSLightweightDecorator() {
		CVSProviderPlugin.addResourceStateChangeListener(this);
		
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.PRE_AUTO_BUILD);
	}

	// Keep track of deconfigured projects
	private Set deconfiguredProjects = new HashSet();

	public static boolean isDirty(final ICVSResource cvsFile) {
		try {
			final boolean[] isDirty = new boolean[] {false};
			cvsFile.getParent().run(new ICVSRunnable() {
				public void run(IProgressMonitor monitor) throws CVSException {
					// file is dirty or file has been merged by an update
					if(!cvsFile.isIgnored()) {
						isDirty[0] = cvsFile.isModified();
					}
				}
			}, ICVSFolder.READ_ONLY, null);
			return isDirty[0];
		} catch (CVSException e) {
			//if we get an error report it to the log but assume dirty
			CVSUIPlugin.log(e.getStatus());
			return true;
		}
	}

	public static boolean isDirty(IResource resource) {

		// No need to decorate non-existant resources
		if (!resource.exists()) return false;

		return isDirty(CVSWorkspaceRoot.getCVSResourceFor(resource));

	}
	
	/*
	 * Answers null if a provider does not exist or the provider is not a CVS provider. These resources
	 * will be ignored by the decorator.
	 */
	private CVSTeamProvider getCVSProviderFor(IResource resource) {
		RepositoryProvider p =
			RepositoryProvider.getProvider(
				resource.getProject(),
				CVSProviderPlugin.getTypeId());
		if (p == null) {
			return null;
		}
		return (CVSTeamProvider) p;
	}

	/**
	 * Returns the resource for the given input object, or
	 * null if there is no resource associated with it.
	 *
	 * @param object  the object to find the resource for
	 * @return the resource for the given object, or null
	 */
	private IResource getResource(Object object) {
		if (object instanceof IResource) {
			return (IResource) object;
		}
		if (object instanceof IAdaptable) {
			return (IResource) ((IAdaptable) object).getAdapter(
				IResource.class);
		}
		return null;
	}
	/**
	 * @see org.eclipse.jface.viewers.ILightweightLabelDecorator#decorate(java.lang.Object, org.eclipse.jface.viewers.IDecoration)
	 */
	public void decorate(Object element, IDecoration decoration) {
		
		IResource resource = getResource(element);
		if (resource == null || resource.getType() == IResource.ROOT)
			return;
			
		CVSTeamProvider cvsProvider = getCVSProviderFor(resource);
		if (cvsProvider == null)
			return;


		// if the resource is ignored return an empty decoration. This will
		// force a decoration update event and clear the existing CVS decoration.
		ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
		try {
			if (cvsResource.isIgnored()) {
				return;
			}
		} catch (CVSException e) {
			// The was an exception in isIgnored. Don't decorate
			//todo should log this error
			return;
		}

		// determine a if resource has outgoing changes (e.g. is dirty).
		IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();
		boolean isDirty = false;
		boolean computeDeepDirtyCheck =
			store.getBoolean(ICVSUIConstants.PREF_CALCULATE_DIRTY);
		int type = resource.getType();
		if (type == IResource.FILE || computeDeepDirtyCheck) {
			isDirty = CVSLightweightDecorator.isDirty(resource);
		}
		
		decorateTextLabel(resource, decoration, isDirty, true);
		
		ImageDescriptor overlay = getOverlay(resource, isDirty, cvsProvider);
		if(overlay != null) { //actually sending null arg would work but this makes logic clearer
			decoration.addOverlay(overlay);
		}
	}

//todo the showRevisions flag is temp, a better solution is DecoratorStrategy classes which have most the code below
	public static void decorateTextLabel(IResource resource, IDecoration decoration, boolean isDirty, boolean showRevisions) {
		try {
			Map bindings = new HashMap(3);
			String format = ""; //$NON-NLS-1$
			IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();

			// if the resource does not have a location then return. This can happen if the resource
			// has been deleted after we where asked to decorate it.
			if(resource.getLocation() == null) {
				return;
			}

			int type = resource.getType();

			if (type == IResource.FOLDER) {
				format = store.getString(ICVSUIConstants.PREF_FOLDERTEXT_DECORATION);
			} else if (type == IResource.PROJECT) {
				format = store.getString(ICVSUIConstants.PREF_PROJECTTEXT_DECORATION);
			} else {
				format = store.getString(ICVSUIConstants.PREF_FILETEXT_DECORATION);
			}

			if (isDirty) {
				bindings.put(CVSDecoratorConfiguration.DIRTY_FLAG, store.getString(ICVSUIConstants.PREF_DIRTY_FLAG));
			}

			CVSTag tag = getTagToShow(resource);
			if (tag != null) {
				bindings.put(CVSDecoratorConfiguration.RESOURCE_TAG, tag.getName());
			}

			if (type != IResource.FILE) {
				ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor((IContainer) resource);
				FolderSyncInfo folderInfo = folder.getFolderSyncInfo();
				if (folderInfo != null) {
					ICVSRepositoryLocation location = CVSProviderPlugin.getPlugin().getRepository(folderInfo.getRoot());
					bindings.put(CVSDecoratorConfiguration.REMOTELOCATION_HOST, location.getHost());
					bindings.put(CVSDecoratorConfiguration.REMOTELOCATION_METHOD, location.getMethod().getName());
					bindings.put(CVSDecoratorConfiguration.REMOTELOCATION_USER, location.getUsername());
					bindings.put(CVSDecoratorConfiguration.REMOTELOCATION_ROOT, location.getRootDirectory());
					bindings.put(CVSDecoratorConfiguration.REMOTELOCATION_REPOSITORY, folderInfo.getRepository());
				}
			} else {
				format = store.getString(ICVSUIConstants.PREF_FILETEXT_DECORATION);
				ICVSFile file = CVSWorkspaceRoot.getCVSFileFor((IFile) resource);
				ResourceSyncInfo fileInfo = file.getSyncInfo();
				if (fileInfo != null) {
					if (fileInfo.isAdded()) {
						bindings.put(CVSDecoratorConfiguration.ADDED_FLAG, store.getString(ICVSUIConstants.PREF_ADDED_FLAG));
					} else {
						if(showRevisions)
							bindings.put(CVSDecoratorConfiguration.FILE_REVISION, fileInfo.getRevision());
					}
					KSubstOption option = fileInfo.getKeywordMode() != null ?
						fileInfo.getKeywordMode() :
						KSubstOption.fromFile((IFile) resource);
					bindings.put(CVSDecoratorConfiguration.FILE_KEYWORD, option.getShortDisplayText());
				} else {
					// only show the type that cvs will use when comitting the file
					KSubstOption option = KSubstOption.fromFile((IFile) resource);
					bindings.put(CVSDecoratorConfiguration.FILE_KEYWORD, option.getShortDisplayText());
				}
			}
		
		CVSDecoratorConfiguration.decorate(decoration, format, bindings);
			
		} catch (CVSException e) {
			CVSUIPlugin.log(e.getStatus());
			return;
		}
	}

	/**
	 * Only show the tag if the resources tag is different than the parents. Or else, tag
	 * names will clutter the text decorations.
	 */
	protected static CVSTag getTagToShow(IResource resource) throws CVSException {
		ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
		CVSTag tag = null;

		// for unmanaged resources don't show a tag since they will be added in
		// the context of their parents tag. For managed resources only show tags
		// if different than parent.
		boolean managed = false;

		if(cvsResource.isFolder()) {
			FolderSyncInfo folderInfo = ((ICVSFolder)cvsResource).getFolderSyncInfo();
			if(folderInfo != null) {
				tag = folderInfo.getTag();
				managed = true;
			}
		} else {
			ResourceSyncInfo info = ((ICVSFile)cvsResource).getSyncInfo();
			if(info != null) {
				tag = info.getTag();
				managed = true;
			}
		}

		ICVSFolder parent = cvsResource.getParent();
		if(parent != null && managed) {
			FolderSyncInfo parentInfo = parent.getFolderSyncInfo();
			if(parentInfo != null) {
				CVSTag parentTag = parentInfo.getTag();
				parentTag = (parentTag == null ? CVSTag.DEFAULT : parentTag);
				tag = (tag == null ? CVSTag.DEFAULT : tag);
				// must compare tags by name because CVS doesn't do a good job of
				// using  T and N prefixes for folders and files.
				if( parentTag.getName().equals(tag.getName())) {
					tag = null;
				}
			}
		}
		return tag;
	}
	
	/* Determine and return the overlay icon to use.
	 * We only get to use one, so if many are applicable at once we chose the
	 * one we think is the most important to show.
	 * Return null if no overlay is to be used.
	 */	
	public static ImageDescriptor getOverlay(IResource resource, boolean isDirty, CVSTeamProvider provider) {

		// for efficiency don't look up a pref until its needed
		IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();
		boolean showNewResources = store.getBoolean(ICVSUIConstants.PREF_SHOW_NEWRESOURCE_DECORATION);

		// show newResource icon
		if (showNewResources) {
			ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
			try {
				if (cvsResource.exists()) {
					boolean isNewResource = false;
					if (cvsResource.isFolder()) {
						if (!((ICVSFolder)cvsResource).isCVSFolder()) {
							isNewResource = true;
						}
					} else if (!cvsResource.isManaged()) {
						isNewResource = true;
					}
					if (isNewResource) {
						return newResource;
					}
				}
			} catch (CVSException e) {
				CVSUIPlugin.log(e.getStatus());
				return null;
			}
		}
		
		boolean showDirty = store.getBoolean(ICVSUIConstants.PREF_SHOW_DIRTY_DECORATION);

		// show dirty icon
		if(showDirty && isDirty) {
			 return dirty;
		}
				
		boolean showAdded = store.getBoolean(ICVSUIConstants.PREF_SHOW_ADDED_DECORATION);

		if (showAdded && resource.getType() == IResource.FILE) {
			try {
				if (resource.getLocation() != null) {
					ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile) resource);
					ResourceSyncInfo info = cvsFile.getSyncInfo();
					// show merged icon if file has been merged but has not been edited (e.g. on commit it will be ignored)
					if (info != null && info.isNeedsMerge(cvsFile.getTimeStamp())) {
						return merged;
					// show added icon if file has been added locally.
					} else if (info != null && info.isAdded()) {
						return checkedOut;
					}
				}
			} catch (CVSException e) {
				CVSUIPlugin.log(e.getStatus());
				return null;
			}
		}

		boolean showHasRemote = store.getBoolean(ICVSUIConstants.PREF_SHOW_HASREMOTE_DECORATION);
		
		// Simplest is that is has remote.
		if (showHasRemote && CVSWorkspaceRoot.hasRemote(resource)) {
			return checkedIn;
		}

		//nothing matched
		return null;

	}

	/*
	 * Add resource and its parents to the List
	 */
	 
	private void addWithParents(IResource resource, List resources) {
		IResource current = resource;

		while (current.getType() != IResource.ROOT) {
			resources.add(current);
			current = current.getParent();
		}
	}
	
	/*
	 * Return the CVSLightweightDecorator instance that is currently enabled.
	 * Return null if we don't have a decorator or its not enabled.
	 */	 
	/* package */ static CVSLightweightDecorator getActiveCVSDecorator() {
		IDecoratorManager manager = CVSUIPlugin.getPlugin().getWorkbench().getDecoratorManager();
		if (manager.getEnabled(CVSUIPlugin.DECORATOR_ID))
			return (CVSLightweightDecorator) manager.getLightweightLabelDecorator(CVSUIPlugin.DECORATOR_ID);
		return null;
	}
	/*
	 * Perform a blanket refresh of all CVS decorations
	 */
	 public static void refresh() {
		CVSLightweightDecorator activeDecorator = getActiveCVSDecorator();

		if(activeDecorator == null)
			return;	//nothing to do, our decorator isn't active

		//update all displaying of our decorator;
		activeDecorator.fireLabelProviderChanged(new LabelProviderChangedEvent(activeDecorator));
	}

	/*
	 * Update the decorators for every resource in project
	 */
	 
	public void refresh(IProject project) {
		final List resources = new ArrayList();
		try {
			project.accept(new IResourceVisitor() {
				public boolean visit(IResource resource) {
					resources.add(resource);
					return true;
				}
			});
			postLabelEvent(new LabelProviderChangedEvent(this, resources.toArray()));
		} catch (CoreException e) {
			CVSProviderPlugin.log(e.getStatus());
		}
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceSyncInfoChanged(org.eclipse.core.resources.IResource[])
	 */
	public void resourceSyncInfoChanged(IResource[] changedResources) {
		resourceStateChanged(changedResources);
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceModificationStateChanged(org.eclipse.core.resources.IResource[])
	 */
	public void resourceModified(IResource[] changedResources) {
		resourceStateChanged(changedResources);
	}
	
	/**
	 * @see org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org.eclipse.core.resources.IResourceChangeEvent)
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			final List changedResources = new ArrayList();
			event.getDelta().accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) throws CoreException {
					IResource resource = delta.getResource();

					if(resource.getType() == IResource.ROOT) {
						// continue with the delta
						return true;
					}

					if (resource.getType() == IResource.PROJECT) {
						// deconfigure if appropriate (see CVSDecorator#projectDeconfigured(IProject))
						// do this even if there is a provider (this is required since old projects may still have a cvs nature)
						if (deconfiguredProjects.contains(resource)) {
							deconfiguredProjects.remove(resource);
							refresh((IProject)resource);
						}
						// If there is no CVS provider, don't continue
						if (RepositoryProvider.getProvider((IProject)resource, CVSProviderPlugin.getTypeId()) == null) {
							return false;
						}
					}

					return true;
				}
			});
			resourceStateChanged((IResource[])changedResources.toArray(new IResource[changedResources.size()]));
			changedResources.clear();
		} catch (CoreException e) {
			CVSProviderPlugin.log(e.getStatus());
		}
	}
	/**
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceStateChanged(org.eclipse.core.resources.IResource[])
	 */
	public void resourceStateChanged(IResource[] changedResources) {
		// add depth first so that update thread processes parents first.
		//System.out.println(">> State Change Event");
		List resourcesToUpdate = new ArrayList();

		boolean showingDeepDirtyIndicators = CVSUIPlugin.getPlugin().getPreferenceStore().getBoolean(ICVSUIConstants.PREF_CALCULATE_DIRTY);

		for (int i = 0; i < changedResources.length; i++) {
			IResource resource = changedResources[i];

			if(showingDeepDirtyIndicators) {
				addWithParents(resource, resourcesToUpdate);
			} else {
				resourcesToUpdate.add(resource);
			}
		}

		postLabelEvent(new LabelProviderChangedEvent(this, resourcesToUpdate.toArray()));
	}
	
	/**
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#projectConfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectConfigured(IProject project) {
	}
	/**
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#projectDeconfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectDeconfigured(IProject project) {
	}

	/**
	 * Post the label event to the UI thread
	 *
	 * @param events  the events to post
	 */
	private void postLabelEvent(final LabelProviderChangedEvent event) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				fireLabelProviderChanged(event);
			}
		});
	}
}