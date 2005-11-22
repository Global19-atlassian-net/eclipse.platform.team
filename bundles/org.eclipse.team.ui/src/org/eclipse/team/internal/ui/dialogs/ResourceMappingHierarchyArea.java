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
package org.eclipse.team.internal.ui.dialogs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.mapping.IResourceMappingScope;
import org.eclipse.team.ui.mapping.ISynchronizationContext;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.navigator.internal.extensions.INavigatorContentServiceListener;
import org.eclipse.ui.navigator.internal.extensions.NavigatorContentExtension;

public class ResourceMappingHierarchyArea extends DialogArea implements INavigatorContentServiceListener {

	private static final String TEAM_NAVIGATOR_CONTENT = "org.eclipse.team.ui.navigatorViewer"; //$NON-NLS-1$
	private String description;
    private CommonViewer viewer;
	private final IResourceMappingScope scope;
	private final ISynchronizationContext context;
    
    
    public static ResourceMappingHierarchyArea create(IResourceMappingScope scope, ISynchronizationContext context) {
        return new ResourceMappingHierarchyArea(scope, context);
    }
    
	private ResourceMappingHierarchyArea(IResourceMappingScope scope, ISynchronizationContext context) {
		this.scope = scope;
		this.context = context;
	}

	public void createArea(Composite parent) {
        Composite composite = createComposite(parent, 1, true);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        layout.verticalSpacing = 0;
        layout.horizontalSpacing = 0;
        composite.setLayout(layout);
        
        if (description != null)
            createWrappingLabel(composite, description, 1);
        
        viewer = new CommonViewer(TEAM_NAVIGATOR_CONTENT, composite, SWT.BORDER) {
        	protected org.eclipse.jface.viewers.ILabelProvider wrapLabelProvider(org.eclipse.jface.viewers.ILabelProvider provider) {
        		return provider;
        	};
        };
        GridData data = new GridData(GridData.FILL_BOTH);
        data.heightHint = 100;
        data.widthHint = 300;
        viewer.getControl().setLayoutData(data);
        viewer.getNavigatorContentService().addListener(this);
        viewer.setInput(ResourcesPlugin.getWorkspace().getRoot());
        viewer.refresh();
        Object[] objects = getRootModelObjects();
        viewer.setSelection(new StructuredSelection(objects), true);
    }

	private Object[] getRootModelObjects() {
		if (scope == null)
			return new Object[0];
		ResourceMapping[] mappings = scope.getMappings();
		List result = new ArrayList();
		for (int i = 0; i < mappings.length; i++) {
			ResourceMapping mapping = mappings[i];
			result.add(mapping.getModelObject());
		}
		return result.toArray(new Object[result.size()]);
	}

	public void setDescription(String string) {
        description = string;
    }

	/* (non-Javadoc)
	 * @see org.eclipse.ui.navigator.internal.extensions.INavigatorContentServiceListener#onLoad(org.eclipse.ui.navigator.internal.extensions.NavigatorContentExtension)
	 */
	public void onLoad(NavigatorContentExtension anExtension) {
		anExtension.getStateModel().setProperty(TeamUI.RESOURCE_MAPPING_SCOPE, scope);
		if (context != null) {
			anExtension.getStateModel().setProperty(TeamUI.SYNCHRONIZATION_CONTEXT, context);
		}
	}

}
