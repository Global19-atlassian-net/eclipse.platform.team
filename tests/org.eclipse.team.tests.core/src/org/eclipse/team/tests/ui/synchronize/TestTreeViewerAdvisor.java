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
package org.eclipse.team.tests.ui.synchronize;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.team.core.synchronize.SyncInfoTree;
import org.eclipse.team.internal.ui.synchronize.*;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.TreeViewerAdvisor;

public class TestTreeViewerAdvisor extends TreeViewerAdvisor {

	public TestTreeViewerAdvisor(Composite parent, ISynchronizePageConfiguration configuration) {
		super(parent, configuration);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.TreeViewerAdvisor#createModelManager(org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration)
	 */
	protected SynchronizeModelManager createModelManager(ISynchronizePageConfiguration configuration) {
		return  new SynchronizeModelManager(this, configuration) {
			protected ISynchronizeModelProvider createModelProvider(String id) {
				return new HierarchicalModelProvider(getSyncInfoSet());
			}
			protected ISynchronizeModelProviderDescriptor[] getSupportedModelProviders() {
				return new ISynchronizeModelProviderDescriptor[] {
						new HierarchicalModelProvider.HierarchicalModelProviderDescriptor()};
			}
			private SyncInfoTree getSyncInfoSet() {
				return (SyncInfoTree)getConfiguration().getProperty(ISynchronizePageConfiguration.P_SYNC_INFO_SET);
			}
		};
	}
}
