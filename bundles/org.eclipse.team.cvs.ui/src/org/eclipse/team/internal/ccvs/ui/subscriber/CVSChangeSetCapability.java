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
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.team.ui.synchronize.*;


public class CVSChangeSetCapability extends ChangeSetCapability {

    /* (non-Javadoc)
     * @see org.eclipse.team.ui.synchronize.ChangeSetCapability#supportsCheckedInChangeSets()
     */
    public boolean supportsCheckedInChangeSets() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.ui.synchronize.ChangeSetCapability#supportsActiveChangeSets()
     */
    public boolean supportsActiveChangeSets() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.ui.synchronize.ChangeSetCapability#createCheckedInChangeSetCollector(org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration)
     */
    public SyncInfoSetChangeSetCollector createCheckedInChangeSetCollector(ISynchronizePageConfiguration configuration) {
        return new CVSChangeSetCollector(configuration);
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.team.ui.synchronize.ChangeSetCapability#getActionGroup()
     */
    public SynchronizePageActionGroup getActionGroup() {
        return new CVSChangeSetActionGroup();
    }
}