package org.eclipse.team.internal.ccvs.ui.repo;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.ui.model.CVSTagElement;

public class RepositorySorter extends ViewerSorter {
	public int category(Object element) {
		if (element instanceof ICVSRemoteFolder) {
			return 1;
		}
		if (element instanceof ICVSRemoteFile) {
			return 2;
		}
		if (element instanceof CVSTagElement) {
			CVSTagElement tagElement = (CVSTagElement)element;
			if (tagElement.getTag().getType() == CVSTag.HEAD) {
				return 0;
			} else if (tagElement.getTag().getType() == CVSTag.BRANCH) {
				return 4;
			} else if (tagElement.getTag().getType() == CVSTag.VERSION) {
				return 5;
			} else {
				return 6;
			}
		}
		return 0;
	}

	public int compare(Viewer viewer, Object o1, Object o2) {
		int cat1 = category(o1);
		int cat2 = category(o2);
		if (cat1 != cat2) return cat1 - cat2;
		
		if (o1 instanceof CVSTagElement && o2 instanceof CVSTagElement) {
			CVSTag tag1 = ((CVSTagElement)o1).getTag();
			CVSTag tag2 = ((CVSTagElement)o2).getTag();
			if (tag1.getType() == CVSTag.BRANCH) {
				return tag1.compareTo(tag2);
			} else {
				return -1 * tag1.compareTo(tag2);
			}
		}
		
		if (o1 instanceof ICVSRepositoryLocation && o2 instanceof ICVSRepositoryLocation) {
			return ((ICVSRepositoryLocation)o1).getLocation().compareTo(((ICVSRepositoryLocation)o2).getLocation());
		}
		
		return super.compare(viewer, o1, o2);
	}
}

