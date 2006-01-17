/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.mapping;

import java.util.*;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.registry.TeamContentProviderManager;
import org.eclipse.team.internal.ui.synchronize.AbstractTreeViewerAdvisor;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.mapping.SynchronizationStateTester;
import org.eclipse.team.ui.operations.ModelSynchronizeParticipant;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.navigator.*;
import org.eclipse.ui.navigator.internal.CommonSorter;
import org.eclipse.ui.part.IPageSite;

/**
 * Provides a Common Navigator based viewer for use by a {@link ModelSynchronizePage}.
 */
public class CommonViewerAdvisor extends AbstractTreeViewerAdvisor implements INavigatorContentServiceListener {

	private static final class NavigableCommonViewer extends CommonViewer implements ITreeViewerAccessor {
		private NavigableCommonViewer(String id, Composite parent, int style) {
			super(id, parent, style);
		}
		protected ILabelProvider wrapLabelProvider(ILabelProvider provider) {
			// Don't wrap since we don't want any decoration
			return provider;
		}
		public void createChildren(TreeItem item) {
			super.createChildren(item);
		}
		public void openSelection() {
			fireOpen(new OpenEvent(this, getSelection()));
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.ui.navigator.CommonViewer#init()
		 */
		protected void init() {
			super.init();
			setSorter(new CommonSorter(getNavigatorContentService()));
		}
	}

	public static final String TEAM_NAVIGATOR_CONTENT = "org.eclipse.team.ui.navigatorViewer"; //$NON-NLS-1$
	
	private Set extensions = new HashSet();
	private Map properties = new HashMap();
	
	private NavigatorActionService actionService;
	
	/**
	 * Create a common viewer
	 * @param parent the parent composite of the common viewer
	 * @param configuration the configuration for the viewer
	 * @return a newly created common viewer
	 */
	private static CommonViewer createViewer(Composite parent, ISynchronizePageConfiguration configuration) {
		CommonViewer v = new NavigableCommonViewer(configuration.getViewerId(), parent, SWT.NONE);
		v.getNavigatorContentService().activateExtensions(TeamContentProviderManager.getInstance().getContentProviderIds(), true);
		configuration.getSite().setSelectionProvider(v);
		return v;
	}
	
	/**
	 * Create the advisor using the given configuration
	 * @param configuration the configuration
	 */
	public CommonViewerAdvisor(Composite parent, ISynchronizePageConfiguration configuration) {
		super(configuration);
		CommonViewer viewer = CommonViewerAdvisor.createViewer(parent, configuration);
		GridData data = new GridData(GridData.FILL_BOTH);
		viewer.getControl().setLayoutData(data);
        viewer.getNavigatorContentService().addListener(this);
        initializeViewer(viewer);
		IBaseLabelProvider provider = viewer.getLabelProvider();
		if (provider instanceof DecoratingLabelProvider) {
			DecoratingLabelProvider dlp = (DecoratingLabelProvider) provider;
			DecorationContext decorationContext = new DecorationContext();
			decorationContext.putProperty(SynchronizationStateTester.PROP_TESTER, new SynchronizationStateTester() {
				public boolean isStateDecorationEnabled() {
					return false;
				}
			});
			dlp.setDecorationContext(decorationContext);
		}
        viewer.setInput(getInitialInput());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.StructuredViewerAdvisor#initializeViewer(org.eclipse.jface.viewers.StructuredViewer)
	 */
	public void initializeViewer(StructuredViewer viewer) {
		createActionService((CommonViewer)viewer, getConfiguration());
		super.initializeViewer(viewer);
	}
	
	private void createActionService(CommonViewer viewer, ISynchronizePageConfiguration configuration) {
		ICommonViewerSite commonSite = createCommonViewerSite(viewer, configuration);
		actionService = new NavigatorActionService(commonSite, viewer, viewer.getNavigatorContentService());
	}

	private ICommonViewerSite createCommonViewerSite(CommonViewer viewer, ISynchronizePageConfiguration configuration) {
		IWorkbenchSite site = configuration.getSite().getWorkbenchSite();
		if (site instanceof IEditorSite) {
			IEditorSite es = (IEditorSite) site;
			return CommonViewerSiteFactory.createCommonViewerSite(es);
		}
		if (site instanceof IViewSite) {
			IViewSite vs = (IViewSite) site;
			return CommonViewerSiteFactory.createCommonViewerSite(vs);
		}
		if (site instanceof IPageSite) {
			IPageSite ps = (IPageSite) site;
			return CommonViewerSiteFactory.createCommonViewerSite(configuration.getViewerId(), ps);
		}
		return CommonViewerSiteFactory.createCommonViewerSite(configuration.getViewerId(), TeamUIPlugin.getActivePage(), new IMenuRegistration() {
			public void registerContextMenu(String menuId, MenuManager menuManager,
					ISelectionProvider selectionProvider) {
				// Do nothing since dialogs can't have object contributions
			}
		}, viewer, configuration.getSite().getActionBars());
	}

	private Object getInitialInput() {
		return ((ModelSynchronizeParticipant)getConfiguration().getParticipant()).getContext();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.navigator.internal.extensions.INavigatorContentServiceListener#onLoad(org.eclipse.ui.navigator.internal.extensions.NavigatorContentExtension)
	 */
	public void onLoad(INavigatorContentExtension anExtension) {
		extensions.add(anExtension);
		anExtension.getStateModel().setProperty(TeamUI.RESOURCE_MAPPING_SCOPE, getParticipant().getContext().getScope());
		anExtension.getStateModel().setProperty(TeamUI.SYNCHRONIZATION_PAGE_CONFIGURATION, getConfiguration());
		if (getParticipant().getContext() != null) {
			anExtension.getStateModel().setProperty(TeamUI.SYNCHRONIZATION_CONTEXT, getParticipant().getContext());
		}
		for (Iterator iter = properties.keySet().iterator(); iter.hasNext();) {
			String element = (String) iter.next();
			Object value = properties.get(element);
			if (value instanceof Integer) {
				Integer integer = (Integer) value;
				anExtension.getStateModel().setIntProperty(element, integer.intValue());
			}
		}
	}

	private ModelSynchronizeParticipant getParticipant() {
		return (ModelSynchronizeParticipant)getConfiguration().getParticipant();
	}

	/**
	 * Set the given property for all active extensions.
	 * @param property the property
	 * @param value the value
	 */
	public void setExtentionProperty(String property, int value) {
		properties.put(property, new Integer(value));
		for (Iterator iter = extensions.iterator(); iter.hasNext();) {
			INavigatorContentExtension extension = (INavigatorContentExtension) iter.next();
			extension.getStateModel().setIntProperty(property, value);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.StructuredViewerAdvisor#getContextMenuId(org.eclipse.jface.viewers.StructuredViewer)
	 */
	protected String getContextMenuId(StructuredViewer viewer) {
		return ((CommonViewer)viewer).getNavigatorContentService().getViewerDescriptor().getPopupMenuId();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.StructuredViewerAdvisor#registerContextMenu(org.eclipse.jface.viewers.StructuredViewer, org.eclipse.jface.action.MenuManager)
	 */
	protected void registerContextMenu(StructuredViewer viewer, MenuManager menuMgr) {
		actionService.prepareMenuForPlatformContributions(menuMgr,
				viewer, false);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.StructuredViewerAdvisor#fillContextMenu(org.eclipse.jface.viewers.StructuredViewer, org.eclipse.jface.action.IMenuManager)
	 */
	protected void fillContextMenu(StructuredViewer viewer, IMenuManager manager) {
		if (manager instanceof CommonMenuManager) {
			CommonMenuManager cmm = (CommonMenuManager) manager;
			cmm.clearHandlers();
		}
		ISelection selection = getViewer().getSelection();
		actionService.setContext(new ActionContext(selection));
		actionService.fillContextMenu(manager);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.StructuredViewerAdvisor#dispose()
	 */
	public void dispose() {
		actionService.dispose();
		super.dispose();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.StructuredViewerAdvisor#updateActionBars(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	protected void updateActionBars(IStructuredSelection selection) {
		super.updateActionBars(selection);
		if (!getConfiguration().getSite().isModal()) {
			actionService.setContext(new ActionContext(selection));
			actionService.fillActionBars(getConfiguration().getSite().getActionBars());
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.StructuredViewerAdvisor#createContextMenuManager(java.lang.String)
	 */
	protected MenuManager createContextMenuManager(String targetID) {
		return new CommonMenuManager(targetID);
	}

}
