package org.eclipse.team.internal.ccvs.ui.model;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.progress.IPendingPlaceholder;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.model.IWorkbenchAdapter;

/**
 * The PendingUpdateAdapter is the object the represents an update about to
 * occur.
 */
public class PendingUpdateAdapter
 implements IWorkbenchAdapter, IAdaptable, IPendingPlaceholder {
	 public PendingUpdateAdapter() {
	 }
	
	 public Object getAdapter(Class adapter) {
	  if (adapter == IWorkbenchAdapter.class)
	   return this;
	  return null;
	 }
	
	 /* (non-Javadoc)
	  * @see org.eclipse.ui.model.IWorkbenchAdapter#getChildren(java.lang.Object)
	  */
	 public Object[] getChildren(Object o) {
	  return new Object[0];
	 }
	
	 /* (non-Javadoc)
	  * @see org.eclipse.ui.model.IWorkbenchAdapter#getImageDescriptor(java.lang.Object)
	  */
	 public ImageDescriptor getImageDescriptor(Object object) {
	  // XXX Auto-generated method stub
	  return null;
	 }
	
	 /* (non-Javadoc)
	  * @see org.eclipse.ui.model.IWorkbenchAdapter#getLabel(java.lang.Object)
	  */
	 public String getLabel(Object o) {
	  // XXX Auto-generated method stub
	  return "Pending...";
	 }
	
	 /* (non-Javadoc)
	  * @see org.eclipse.ui.model.IWorkbenchAdapter#getParent(java.lang.Object)
	  */
	 public Object getParent(Object o) {
	  // XXX Auto-generated method stub
	  return null;
	 }
}
