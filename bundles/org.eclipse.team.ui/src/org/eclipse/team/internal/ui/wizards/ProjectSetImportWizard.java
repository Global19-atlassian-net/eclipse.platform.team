/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.wizards;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import javax.xml.parsers.*;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.team.core.*;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.team.ui.UIProjectSetSerializationContext;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ProjectSetImportWizard extends Wizard implements IImportWizard {
	ImportProjectSetMainPage mainPage;
	public static String lastFile;

	public ProjectSetImportWizard() {
		setNeedsProgressMonitor(true);
		setWindowTitle(Policy.bind("ProjectSetImportWizard.Project_Set_1")); //$NON-NLS-1$
	}
	
	public void addPages() {
		mainPage = new ImportProjectSetMainPage("projectSetMainPage", Policy.bind("ProjectSetImportWizard.Import_a_Project_Set_3"), TeamUIPlugin.getImageDescriptor(ISharedImages.IMG_PROJECTSET_IMPORT_BANNER)); //$NON-NLS-1$ //$NON-NLS-2$
		mainPage.setFileName(lastFile);
		addPage(mainPage);
	}
	public boolean performFinish() {
		
		// check if the desired working set exists
		final String workingSetName = mainPage.getWorkingSetName();
		if (workingSetName != null) {
			IWorkingSet existingSet = TeamUIPlugin.getPlugin().getWorkbench().getWorkingSetManager().getWorkingSet(workingSetName);
			if (existingSet != null && 
				!MessageDialog.openConfirm(getShell(), Policy.bind("ProjectSetImportWizard.workingSetExistsTitle"), Policy.bind("ProjectSetImportWizard.workingSetExistsMessage", workingSetName))) //$NON-NLS-1$ //$NON-NLS-2$
					return false;
		}
		
		final boolean[] result = new boolean[] {false};
		try {
			getContainer().run(true, true, new WorkspaceModifyOperation() {
				public void execute(IProgressMonitor monitor) throws InvocationTargetException {
					InputStreamReader reader = null;
					try {
						String filename = mainPage.getFileName();
						lastFile = filename;
						reader = new InputStreamReader(new FileInputStream(filename), "UTF-8"); //$NON-NLS-1$
						
						SAXParserFactory factory = SAXParserFactory.newInstance();
						SAXParser parser = factory.newSAXParser();
						ProjectSetContentHandler handler = new ProjectSetContentHandler();
						InputSource source = new InputSource(reader);
						parser.parse(source, handler);
						
						Map map = handler.getReferences();
						List newProjects = new ArrayList();
						if (map.size() == 0 && handler.isVersionOne()) {
							IProjectSetSerializer serializer = Team.getProjectSetSerializer("versionOneSerializer"); //$NON-NLS-1$
							if (serializer != null) {
								IProject[] projects = serializer.addToWorkspace(new String[0], filename, getShell(), monitor);
								if (projects != null)
									newProjects.addAll(Arrays.asList(projects));
							}
						} else {
							UIProjectSetSerializationContext context = new UIProjectSetSerializationContext(getShell());
							Iterator it = map.keySet().iterator();
							while (it.hasNext()) {
								String id = (String)it.next();
								List references = (List)map.get(id);
								RepositoryProviderType providerType = RepositoryProviderType.getProviderType(id);
								ProjectSetCapability serializer = providerType.getProjectSetCapability();
								ProjectSetCapability.ensureBackwardsCompatible(providerType, serializer);
								if (serializer != null) {
									IProject[] projects = serializer.addToWorkspace((String[])references.toArray(new String[references.size()]), context, monitor);
									if (projects != null)
										newProjects.addAll(Arrays.asList(projects));
								}
							}
						}
						if (workingSetName != null)
							createWorkingSet(workingSetName, (IProject[]) newProjects.toArray(new IProject[newProjects.size()]));
						result[0] = true;
					} catch (IOException e) {
						throw new InvocationTargetException(e);
					} catch (SAXException e) {
						throw new InvocationTargetException(e);
					} catch (TeamException e) {
						throw new InvocationTargetException(e);
					} catch (ParserConfigurationException e) {
						throw new InvocationTargetException(e);
					} finally {
						if (reader != null) {
							try {
								reader.close();
							} catch (IOException e) {
								throw new InvocationTargetException(e);
							}
						}
					}
				}
			});
		} catch (InterruptedException e) {
			return true;
		} catch (InvocationTargetException e) {
			Throwable target = e.getTargetException();
			if (target instanceof TeamException) {
				ErrorDialog.openError(getShell(), null, null, ((TeamException)target).getStatus());
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
	
	/* private */ void createWorkingSet(String workingSetName, IProject[] projects) {
		IWorkingSetManager manager = TeamUIPlugin.getPlugin().getWorkbench().getWorkingSetManager();
		IWorkingSet oldSet = manager.getWorkingSet(workingSetName);
		if (oldSet == null) {
			IWorkingSet newSet = manager.createWorkingSet(workingSetName, projects);
			manager.addWorkingSet(newSet);
		}else {
			oldSet.setElements(projects);
		}	
	}
	
	public void init(IWorkbench workbench, IStructuredSelection selection) {
	}
}
