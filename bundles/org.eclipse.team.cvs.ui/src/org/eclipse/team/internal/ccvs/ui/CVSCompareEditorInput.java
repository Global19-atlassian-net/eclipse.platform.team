package org.eclipse.team.internal.ccvs.ui;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
 
import java.lang.reflect.InvocationTargetException;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.team.ccvs.core.CVSTag;
import org.eclipse.team.ccvs.core.CVSTeamProvider;
import org.eclipse.team.ccvs.core.ICVSFile;
import org.eclipse.team.ccvs.core.ICVSRemoteFolder;
import org.eclipse.team.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.ccvs.core.ICVSResource;
import org.eclipse.team.core.ITeamProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;

/**
 * A compare input for performing naive comparisons between
 * resources and resource editions.
 */
public class CVSCompareEditorInput extends CompareEditorInput {
	private ITypedElement left;
	private ITypedElement right;
	private ITypedElement ancestor;	
	
	// comparison constants
	private static final int NODE_EQUAL = 0;
	private static final int NODE_NOT_EQUAL = 1;
	private static final int NODE_UNKNOWN = 2;
	
	// provider for the project being compared
	CVSTeamProvider cvsProvider = null;
	
	/**
	 * Creates a new CVSCompareEditorInput.
	 */
	public CVSCompareEditorInput(ITypedElement left, ITypedElement right) {
		this(left, right, null);
	}
	
	/**
	 * Creates a new CVSCompareEditorInput.
	 */
	public CVSCompareEditorInput(ITypedElement left, ITypedElement right, ITypedElement ancestor) {
		super(new CompareConfiguration());
		this.left = left;
		this.right = right;
		this.ancestor = ancestor;
	}
	
	/**
	 * Overridden to create the CVSDiffTreeViewer to have the proper popup actions
	 */
	//public Viewer createDiffViewer(Composite parent) {
	//	return new CVSDiffTreeViewer(parent, this);
	//}

	/**
	 * Returns the label for the given input element.
	 */
	private String getLabel(ITypedElement element) {
		if (element instanceof ResourceNode) {
			return Policy.bind("CVSCompareEditorInput.workspace", element.getName());
		}
		if (element instanceof ResourceEditionNode) {
			ICVSRemoteResource edition = ((ResourceEditionNode)element).getRemoteResource();
			ICVSResource resource = (ICVSResource)edition;
			try {
				if (edition.isContainer()) {
					CVSTag tag = ((ICVSRemoteFolder)edition).getTag();
					if (tag == null) {
						return Policy.bind("CVSCompareEditorInput.inHead", edition.getName());
					} else if (tag.getType() == CVSTag.BRANCH) {
						return Policy.bind("CVSCompareEditorInput.inBranch", new Object[] {edition.getName(), tag.getName()});
					} else {
						return Policy.bind("CVSCompareEditorInput.repository", new Object[] {edition.getName(), tag.getName()});
					}
				} else {
					return Policy.bind("CVSCompareEditorInput.repository", new Object[] {edition.getName(), resource.getSyncInfo().getRevision()});
				}
			} catch (TeamException e) {
				handle(e);
				// Fall through and get the default label
			}
		}
		return element.getName();
	}
	
	/**
	 * Returns the label for the given input element.
	 */
	private String getVersionLabel(ITypedElement element) {
		if (element instanceof ResourceNode) {
			return Policy.bind("CVSCompareEditorInput.workspaceLabel");
		}
		if (element instanceof ResourceEditionNode) {
			ICVSRemoteResource edition = ((ResourceEditionNode)element).getRemoteResource();
			ICVSResource resource = (ICVSResource)edition;
			try {
				if (edition.isContainer()) {
					CVSTag tag = ((ICVSRemoteFolder)resource).getTag();
					if (tag == null) {
						return Policy.bind("CVSCompareEditorInput.headLabel");
					} else if (tag.getType() == CVSTag.BRANCH) {
						return Policy.bind("CVSCompareEditorInput.branchLabel", tag.getName());
					} else {
						return tag.getName();
					}
				} else {
					return resource.getSyncInfo().getRevision();
				}
			} catch (TeamException e) {
				handle(e);
				// Fall through and get the default label
			}
		}
		return element.getName();
	}
	
	/*
	 * Returns a guess of the resource name being compared, for display
	 * in the title.
	 */
	private String guessResourceName() {
		if (left != null) {
			return left.getName();
		}
		if (right != null) {
			return right.getName();
		}
		if (ancestor != null) {
			return ancestor.getName();
		}
		return "";
	}
	
	/**
	 * Handles a random exception and sanitizes it into a reasonable
	 * error message.  
	 */
	private void handle(Exception e) {
		// create a status
		Throwable t = e;
		// unwrap the invocation target exception
		if (t instanceof InvocationTargetException) {
			t = ((InvocationTargetException)t).getTargetException();
		}
		IStatus error;
		if (t instanceof CoreException) {
			error = ((CoreException)t).getStatus();
		} else if (t instanceof TeamException) {
			error = ((TeamException)t).getStatus();
		} else {
			error = new Status(IStatus.ERROR, CVSUIPlugin.ID, 1, Policy.bind("internal"), t);
		}
		setMessage(error.getMessage());
		if (!(t instanceof TeamException)) {
			CVSUIPlugin.log(error);
		}
	}
	
	/**
	 * Sets up the title and pane labels for the comparison view.
	 */
	private void initLabels() {
		CompareConfiguration cc = (CompareConfiguration) getCompareConfiguration();
	
		String leftLabel = getLabel(left);
		cc.setLeftLabel(leftLabel);
		cc.setLeftImage(left.getImage());
	
		String rightLabel = getLabel(right);
		cc.setRightLabel(rightLabel);
		cc.setRightImage(right.getImage());
	
		String title;
		if (ancestor != null) {
			cc.setAncestorLabel(getLabel(ancestor));
			cc.setAncestorImage(ancestor.getImage());
			title = Policy.bind("CVSCompareEditorInput.titleAncestor", new Object[] {guessResourceName(), getVersionLabel(ancestor), getVersionLabel(left), getVersionLabel(right)} );
		} else {
			String leftName = null;
			if (left != null) leftName = left.getName();
			String rightName = null;
			if (right != null) rightName = right.getName();
			boolean differentNames = false;
			if (leftName != null && !leftName.equals(rightName)) {
				title = Policy.bind("CVSCompareEditorInput.titleNoAncestorDifferent", new Object[] {leftName, getVersionLabel(left), rightName, getVersionLabel(right)} ); 
			} else {
				title = Policy.bind("CVSCompareEditorInput.titleNoAncestor", new Object[] {guessResourceName(), getVersionLabel(left), getVersionLabel(right)} );
			}
		}
		setTitle(title);
	}
	
	/* (Non-javadoc)
	 * Method declared on CompareEditorInput
	 */
	public boolean isSaveNeeded() {
		return false;
	}

	/* (non-Javadoc)
	 * Method declared on CompareEditorInput
	 */
	protected Object prepareInput(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		boolean threeWay = ancestor != null;
		if (right == null || left == null) {
			setMessage(Policy.bind("CVSCompareEditorInput.different"));
			return null;
		}
		
		initLabels();
	
		Differencer d = new Differencer() {
			protected boolean contentsEqual(Object input1, Object input2) {
				int compare = teamEqual(input1, input2);
				if (compare == NODE_EQUAL) {
					return true;
				}
				if (compare == NODE_NOT_EQUAL) {
					return false;
				}
				//revert to slow content comparison
				return super.contentsEqual(input1, input2);
			}
			protected void updateProgress(IProgressMonitor progressMonitor, Object node) {
				super.updateProgress(progressMonitor, node);
				progressMonitor.worked(1);
			}
			protected Object[] getChildren(Object input) {
				if (input instanceof IStructureComparator) {
					Object[] children= ((IStructureComparator)input).getChildren();
					if (children != null)
						return children;
				}
				return null;
			}
		};
		
		try {
			monitor.beginTask(Policy.bind("CVSCompareEditorInput.comparing"), 30);
			
			// get the CVS provider if one exists
			if (left instanceof ResourceNode) {
				IResource resource = ((ResourceNode)left).getResource();
				ITeamProvider p = TeamPlugin.getManager().getProvider(resource);
				if(p != null && p instanceof CVSTeamProvider) {
					cvsProvider = (CVSTeamProvider)p;
				}
			}
			
			// do the diff	
			IProgressMonitor sub = new SubProgressMonitor(monitor, 10);
			try {
				sub.beginTask(Policy.bind("CVSCompareEditorInput.comparing"), 100);
				return d.findDifferences(threeWay, sub, null, ancestor, left, right);
			} finally {
				sub.done();
			}
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
		} catch (RuntimeException e) {
			handle(e);
			return null;	
		} finally {
			monitor.done();
		}
	}
	
	/**
	 * Compares two nodes to determine if they are equal.  Returns NODE_EQUAL
	 * of they are the same, NODE_NOT_EQUAL if they are different, and
	 * NODE_UNKNOWN if comparison was not possible.
	 */
	protected int teamEqual(Object left, Object right) {
		
		// calculate the type for the left contribution
		ICVSRemoteResource leftEdition = null;
		if (left instanceof ResourceEditionNode) {
			leftEdition = ((ResourceEditionNode)left).getRemoteResource();
		} else if (left instanceof ResourceNode) {
			IResource resource = ((ResourceNode)left).getResource();
			try {
				ICVSResource element = CVSWorkspaceRoot.getCVSResourceFor(resource);
				if (resource.getType() == IResource.FILE) {
					if (((ICVSFile) element).isDirty()) return NODE_NOT_EQUAL;
				}
				if(cvsProvider==null) {
					return NODE_UNKNOWN;
				}
				leftEdition = CVSWorkspaceRoot.getRemoteResourceFor(resource);
			} catch(CVSException e) {
				return NODE_UNKNOWN;
			}
		}
		
		// calculate the type for the right contribution
		ICVSRemoteResource rightEdition = null;
		if (right instanceof ResourceEditionNode)
			rightEdition = ((ResourceEditionNode)right).getRemoteResource();
		
		
		// compare them
			
		if (leftEdition == null || rightEdition == null) {
			return NODE_UNKNOWN;
		}
		// if they're both non-files, they're the same
		if (leftEdition.isContainer() && rightEdition.isContainer()) {
			return NODE_EQUAL;
		}
		// if they have different types, they're different
		if (leftEdition.isContainer() != rightEdition.isContainer()) {
			return NODE_NOT_EQUAL;
		}
		
		if (!leftEdition.getRepository().getLocation().equals(rightEdition.getRepository().getLocation())) {
			return NODE_UNKNOWN;
		}
		try {
			ResourceSyncInfo leftInfo = ((ICVSResource)leftEdition).getSyncInfo();
			ResourceSyncInfo rightInfo = ((ICVSResource)rightEdition).getSyncInfo();
			
			if (leftEdition.getRepositoryRelativePath().equals(rightEdition.getRepositoryRelativePath()) &&
				leftInfo.getRevision().equals(rightInfo.getRevision())) {
				return NODE_EQUAL;
			} else {
				// Optimized for most common case. There are actually cases where a file is merged from
				// one branch to another where the revision numbers are different but the file is the same.
				// We do not handle this case properly.
				return NODE_NOT_EQUAL;
			}
		} catch (TeamException e) {
			handle(e);
			return NODE_UNKNOWN;
		}
	}
}