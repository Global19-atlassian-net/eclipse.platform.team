package org.eclipse.team.internal.ccvs.ui.wizards;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.CVSTeamProvider;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ccvs.ui.RepositoryManager;

public class BranchWizard extends Wizard {
	BranchWizardPage mainPage;
	IResource[] resources;
	
	public BranchWizard() {
		setNeedsProgressMonitor(true);
		setWindowTitle(Policy.bind("BranchWizard.title")); //$NON-NLS-1$
	}
	
	public void addPages() {
		boolean allResourcesSticky = areAllResourcesSticky(resources);
		String versionName = "";		
		try {
			if(allResourcesSticky) {
				IResource stickyResource = resources[0];									
				if(stickyResource.getType()==IResource.FILE) {
					ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile)stickyResource);
					versionName = cvsFile.getSyncInfo().getTag().getName();
				} else {
					ICVSFolder cvsFolder = CVSWorkspaceRoot.getCVSFolderFor((IContainer)stickyResource);
					versionName = cvsFolder.getFolderSyncInfo().getTag().getName();
				}
			}
		} catch(CVSException e) {
			CVSUIPlugin.log(e.getStatus());
			versionName = "";
		}
		mainPage = new BranchWizardPage("versionPage", Policy.bind("BranchWizard.createABranch"), allResourcesSticky, versionName, CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_WIZBAN_BRANCH)); //$NON-NLS-1$ //$NON-NLS-2$
		addPage(mainPage);
	}
	public boolean performFinish() {
		final boolean[] result = new boolean[] {false};
		try {
			getContainer().run(false, false, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException {
					try {
						String tagString = mainPage.getBranchTag();
						boolean update = mainPage.getUpdate();
						String versionString = mainPage.getVersionTag();
						CVSTag rootVersionTag = null;
						final CVSTag branchTag = new CVSTag(tagString, CVSTag.BRANCH);
						if (versionString != null) {
							rootVersionTag = new CVSTag(versionString, CVSTag.VERSION);
						}
												
						// For non-projects determine if the tag being loaded is the same as the resource's parent
						// If it's not, warn the user that they will have strange sync behavior
						if (update) {
							for (int i = 0; i < resources.length; i++) {
								IResource resource = resources[i];
								if (resource.getType() != IResource.PROJECT) {
									ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
									CVSTag parentTag = cvsResource.getParent().getFolderSyncInfo().getTag();
									if (!equalTags(branchTag, parentTag)) {
										final Shell shell = getShell();
										final boolean[] result = new boolean[] { false };
										shell.getDisplay().syncExec(new Runnable() {
											public void run() {
												result[0] = MessageDialog.openQuestion(getShell(), Policy.bind("question"), Policy.bind("BranchWizard.mixingTags", branchTag.getName())); //$NON-NLS-1$ //$NON-NLS-2$
											}
										});
										if (!result[0]) return;										
									}
								}
								
							}
						}
										
						RepositoryManager manager = CVSUIPlugin.getPlugin().getRepositoryManager();
						Hashtable table = getProviderMapping(resources);
						Set keySet = table.keySet();
						monitor.beginTask("", keySet.size() * 1000); //$NON-NLS-1$
						MultiStatus status = new MultiStatus(CVSUIPlugin.ID, IStatus.INFO, Policy.bind("BranchWizard.errorTagging"), null); //$NON-NLS-1$
						Iterator iterator = keySet.iterator();
						while (iterator.hasNext()) {
							IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 1000);
							CVSTeamProvider provider = (CVSTeamProvider)iterator.next();
							List list = (List)table.get(provider);
							IResource[] providerResources = (IResource[])list.toArray(new IResource[list.size()]);											
							ICVSRepositoryLocation root = provider.getCVSWorkspaceRoot().getRemoteLocation();
							try {
								if (!areAllResourcesSticky(resources)) {													
									// version everything in workspace with the root version tag specified in dialog
									provider.makeBranch(providerResources, rootVersionTag, branchTag, update, true, subMonitor);
								} else {
									// all resources are versions, use that version as the root of the branch
									provider.makeBranch(providerResources, null, branchTag, update, true, subMonitor);										
								}
								if (rootVersionTag != null || update) {
									for (int i = 0; i < providerResources.length; i++) {
										ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(providerResources[i]);
										if (rootVersionTag != null) {
											manager.addVersionTags(cvsResource, new CVSTag[] { rootVersionTag });
										}
										if (update) {
											manager.addBranchTags(cvsResource, new CVSTag[] { branchTag });
										}
									}
								}
							} catch (TeamException e) {
								status.merge(e.getStatus());
							}
						}
						if (!status.isOK()) {
							ErrorDialog.openError(getShell(), null, null, status);
						}
						result[0] = true;
					} catch (CVSException e) {
						throw new InvocationTargetException(e);
					} finally {
						monitor.done();
					}
				}
			});
		} catch (InterruptedException e) {
			return true;
		} catch (InvocationTargetException e) {
			Throwable target = e.getTargetException();
			if (target instanceof CVSException) {
				ErrorDialog.openError(getShell(), null, null, ((CVSException)target).getStatus());
				return false;
			}
			if (target instanceof RuntimeException) {
				throw (RuntimeException)target;
			}
			if (target instanceof Error) {
				throw (Error)target;
			}
		}
		return result[0];
	}
	public void setResources(IResource[] resources) {
		this.resources = resources;
	}
	private Hashtable getProviderMapping(IResource[] resources) {
		Hashtable result = new Hashtable();
		for (int i = 0; i < resources.length; i++) {
			RepositoryProvider provider = RepositoryProvider.getProvider(resources[i].getProject(), CVSProviderPlugin.getTypeId());
			List list = (List)result.get(provider);
			if (list == null) {
				list = new ArrayList();
				result.put(provider, list);
			}
			list.add(resources[i]);
		}
		return result;
	}
	
	private boolean areAllResourcesSticky(IResource[] resources) {
		for (int i = 0; i < resources.length; i++) {
			if(!hasStickyTag(resources[i])) return false;
		}
		return true;
	}
	
	private boolean hasStickyTag(IResource resource) {
		try {
			ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);			
			CVSTag tag;
			if(cvsResource.isFolder()) {
				FolderSyncInfo folderInfo = ((ICVSFolder)cvsResource).getFolderSyncInfo();
				tag = folderInfo.getTag();
			} else {
				ResourceSyncInfo info = cvsResource.getSyncInfo();
				tag = info.getTag();
			}
			if(tag!=null) {
				int tagType = tag.getType();
				if(tagType==tag.VERSION) {
					return true;
				}
			}
		} catch(CVSException e) {
			CVSUIPlugin.log(e.getStatus());
			return false;
		}
		return false;
	}
	
	protected boolean equalTags(CVSTag tag1, CVSTag tag2) {
		if (tag1 == null) tag1 = CVSTag.DEFAULT;
		if (tag2 == null) tag2 = CVSTag.DEFAULT;
		return tag1.equals(tag2);
	}
}
