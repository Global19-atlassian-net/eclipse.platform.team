/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.synchronize;

import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.*;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.team.ui.synchronize.IActionContribution;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.ui.IActionBars;

/**
 * Manages the models that can be displayed by a synchronize page
 */
public abstract class SynchronizeModelManager implements IActionContribution {
	
	private ISynchronizeModelProvider modelProvider;
	private List toggleModelProviderActions;
	private ISynchronizePageConfiguration configuration;
	private StructuredViewerAdvisor advisor;
	
	/**
	 * Action that allows changing the model providers supported by this advisor.
	 */
	private class ToggleModelProviderAction extends Action implements IPropertyChangeListener {
		private ISynchronizeModelProviderDescriptor descriptor;
		protected ToggleModelProviderAction(ISynchronizeModelProviderDescriptor descriptor) {
			super(descriptor.getName(), Action.AS_RADIO_BUTTON);
			setImageDescriptor(descriptor.getImageDescriptor());
			setToolTipText(descriptor.getName());
			this.descriptor = descriptor;
			update();
			configuration.addPropertyChangeListener(this);
		}

		public void run() {
			ISynchronizeModelProvider mp = getActiveModelProvider();
			IStructuredSelection selection = null;
			if(mp != null) {
				if(mp.getDescriptor().getId().equals(descriptor.getId())) return;	
				selection = (IStructuredSelection)configuration.getSite().getSelectionProvider().getSelection();	
			}
			internalPrepareInput(descriptor.getId(), null);
			setInput();
			if(selection != null) {
				setSelection(selection.toArray(), true);
			}
		}
		
		public void update() {
			ISynchronizeModelProvider mp = getActiveModelProvider();
			if(mp != null) {
				setChecked(mp.getDescriptor().getId().equals(descriptor.getId()));
			}
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
		 */
		public void propertyChange(PropertyChangeEvent event) {
			if (event.getProperty().equals(ISynchronizePageConfiguration.P_MODEL)) {
				update();
			}
		}
	}
	
	public SynchronizeModelManager(StructuredViewerAdvisor advisor, ISynchronizePageConfiguration configuration) {
		this.advisor = advisor;
		this.configuration = configuration;
		configuration.addActionContribution(this);
	}
	
	/**
	 * Return the list of supported model providers for this advisor.
	 * @param viewer
	 * @return
	 */
	protected abstract ISynchronizeModelProviderDescriptor[] getSupportedModelProviders();
	
	/**
	 * Get the model provider that will be used to create the input
	 * for the adviser's viewer.
	 * @return the model provider
	 */
	protected abstract ISynchronizeModelProvider createModelProvider(String id);
	
	protected ISynchronizeModelProvider getActiveModelProvider() {
		return modelProvider;
	}
	
	protected Object internalPrepareInput(String id, IProgressMonitor monitor) {
		if(modelProvider != null) {
			modelProvider.dispose();
		}
		modelProvider = createModelProvider(id);		
		return modelProvider.prepareInput(monitor);
	}
	
	/**
	 * Gets a new selection that contains the view model objects that
	 * correspond to the given objects. The advisor will try and
	 * convert the objects into the appropriate viewer objects. 
	 * This is required because the model provider controls the actual 
	 * model elements in the viewer and must be consulted in order to
	 * understand what objects can be selected in the viewer.
	 * <p>
	 * This method does not affect the selection of the viewer itself.
	 * It's main purpose is for testing and should not be used by other
	 * clients.
	 * </p>
	 * @param object the objects to select
	 * @return a selection corresponding to the given objects
	 */
	public ISelection getSelection(Object[] objects) {
		if (modelProvider != null) {
	 		Object[] viewerObjects = new Object[objects.length];
			for (int i = 0; i < objects.length; i++) {
				viewerObjects[i] = modelProvider.getMapping(objects[i]);
			}
			return new StructuredSelection(viewerObjects);
		} else {
			return StructuredSelection.EMPTY;
		}
	}
	
	/**
	 * Sets a new selection for this viewer and optionally makes it visible. The advisor will try and
	 * convert the objects into the appropriate viewer objects. This is required because the model
	 * provider controls the actual model elements in the viewer and must be consulted in order to
	 * understand what objects can be selected in the viewer.
	 * 
	 * @param object the objects to select
	 * @param reveal <code>true</code> if the selection is to be made visible, and
	 *                  <code>false</code> otherwise
	 */
	protected void setSelection(Object[] objects, boolean reveal) {
		ISelection selection = getSelection(objects);
		if (!selection.isEmpty()) {
			advisor.setSelection(selection, reveal);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.IActionContribution#initialize(org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration)
	 */
	public void initialize(ISynchronizePageConfiguration configuration) {
		ISynchronizeModelProviderDescriptor[] providers = getSupportedModelProviders();
		// We only need switching of layouts if there is more than one model provider
		if (providers.length > 1) {
			toggleModelProviderActions = new ArrayList();
			for (int i = 0; i < providers.length; i++) {
				final ISynchronizeModelProviderDescriptor provider = providers[i];
				toggleModelProviderActions.add(new ToggleModelProviderAction(provider));
			}
		}
		// The input may of been set already. In that case, don't change it and
		// simply assign it to the view.
		if(modelProvider == null) {
			internalPrepareInput(null, null);
		}
		setInput();
	}
	
	/**
	 * Set the input of the viewer
	 */
	protected void setInput() {
		configuration.setProperty(ISynchronizePageConfiguration.P_MODEL, modelProvider.getModelRoot());
		advisor.setInput(modelProvider);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.IActionContribution#setActionBars(org.eclipse.ui.IActionBars)
	 */
	public void setActionBars(IActionBars actionBars) {
		// TODO: add to group
		IToolBarManager toolbar = actionBars.getToolBarManager();
		IMenuManager menu = actionBars.getMenuManager();
		IContributionManager contribManager = null;
		if(menu != null) {
			MenuManager layout = new MenuManager(Policy.bind("action.layout.label")); //$NON-NLS-1$
			menu.add(layout);	
			contribManager = layout;
		} else if(toolbar != null) {
			contribManager = toolbar;
		}
		
		if (toggleModelProviderActions != null && contribManager != null) {
			if (toolbar != null) {
				toolbar.add(new Separator());
				for (Iterator iter = toggleModelProviderActions.iterator(); iter.hasNext();) {
					contribManager.add((Action) iter.next());
				}
				toolbar.add(new Separator());
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.IActionContribution#fillContextMenu(org.eclipse.jface.action.IMenuManager)
	 */
	public void fillContextMenu(IMenuManager manager) {
		// No context menu entries
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.IActionContribution#dispose()
	 */
	public void dispose() {
		if(modelProvider != null) {
			modelProvider.dispose();
		}
		configuration.removeActionContribution(this);
	}
	
	/**
	 * @return Returns the configuration.
	 */
	public ISynchronizePageConfiguration getConfiguration() {
		return configuration;
	}
}
