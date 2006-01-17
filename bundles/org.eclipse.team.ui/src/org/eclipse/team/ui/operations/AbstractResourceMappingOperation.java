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
package org.eclipse.team.ui.operations;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.*;
import org.eclipse.core.resources.mapping.ModelProvider;
import org.eclipse.core.runtime.*;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.diff.*;
import org.eclipse.team.core.mapping.*;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.mapping.DefaultResourceMappingMerger;
import org.eclipse.team.ui.TeamOperation;
import org.eclipse.ui.IWorkbenchPart;

/**
 * An operation for performing model provider based operations.
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is a guarantee neither that this API will
 * work nor that it will remain the same. Please do not use this API without
 * consulting with the Platform/Team team.
 * </p>
 * 
 * @since 3.2
 */
public abstract class AbstractResourceMappingOperation extends TeamOperation {

	/**
	 * Create the operation
	 * @param part the workbench part from which the operation was
	 * launched or <code>null</code>
	 */
	public AbstractResourceMappingOperation(IWorkbenchPart part) {
		super(part);
	}
	
	/**
	 * A convenience method that performs a headless merge of the
	 * elements in the given context. The merge is performed by obtaining
	 * the {@link IResourceMappingMerger} for the model providers in the context's
	 * scope.
	 * @param context the merge context
	 * @param monitor a progress monitor
	 * @return <code>true</code> if all elements where merged
	 * TODO should return more useful information
	 * @throws CoreException
	 */
	protected boolean performMerge(final IMergeContext context, IProgressMonitor monitor) throws CoreException {
		final ModelProvider[] providers = sortByExtension(context.getScope().getModelProviders());
		final List failedMerges = new ArrayList();
		context.run(new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {	
				monitor.beginTask(null, IProgressMonitor.UNKNOWN);
				for (int i = 0; i < providers.length; i++) {
					ModelProvider provider = providers[i];
					if (!performMerge(provider, context, Policy.subMonitorFor(monitor, IProgressMonitor.UNKNOWN))) {
						failedMerges.add(provider);
					}
				}
				monitor.done();
			}
		}, null /* scheduling rule */, IResource.NONE, monitor);
		return failedMerges.isEmpty();
	}
	
	private ModelProvider[] sortByExtension(ModelProvider[] providers) {
		List result = new ArrayList();
		for (int i = 0; i < providers.length; i++) {
			ModelProvider providerToInsert = providers[i];
			int index = result.size();
			for (int j = 0; j < result.size(); j++) {
				ModelProvider provider = (ModelProvider) result.get(j);
				if (extendsProvider(providerToInsert, provider)) {
					index = j;
					break;
				}
			}
			result.add(index, providerToInsert);
		}
		return (ModelProvider[]) result.toArray(new ModelProvider[result.size()]);
	}

	private boolean extendsProvider(ModelProvider providerToInsert, ModelProvider provider) {
		String[] extended = providerToInsert.getDescriptor().getExtendedModels();
		// First search immediate dependents
		for (int i = 0; i < extended.length; i++) {
			String id = extended[i];
			if (id.equals(provider.getDescriptor().getId())) {
				return true;
			}
		}
		return false;
	}
	/**
	 * Merge all the mappings that come from the given provider. By default,
	 * an automatic merge is attempted. After this, a manual merge (i.e. with user
	 * intervention) is attempted on any mappings that could not be merged
	 * automatically.
	 * @param provider the model provider IDocumentProviderExtension5 
	 * @param mappings the mappings to be merged
	 * @param monitor a progress monitor
	 * @throws CoreException
	 */
	protected boolean performMerge(ModelProvider provider, IMergeContext mergeContext, IProgressMonitor monitor) throws CoreException {
		IResourceMappingMerger merger = getMerger(provider);
		IStatus status = merger.merge(mergeContext, monitor);
		if (!status.isOK()) {
			if (status.getCode() == IMergeStatus.CONFLICTS) {
				return false;
			} else {
				throw new TeamException(status);
			}
		}
		return true;
	}
	
	/**
	 * Validate the merge by obtaining the {@link IResourceMappingMerger} for the
	 * given provider.
	 * @param provider the model provider
	 * @param context the merge context
	 * @param monitor a progress monitor
	 * @return the status obtained from the merger for the provider
	 */
	protected IStatus validateMerge(ModelProvider provider, IMergeContext context, IProgressMonitor monitor) {
		IResourceMappingMerger merger = getMerger(provider);
		return merger.validateMerge(context, monitor);
	}
	
	/**
	 * Return the auto-merger associated with the given model provider
	 * view the adaptable mechanism.
	 * If the model provider does not have a merger associated with
	 * it, a default merger that performs the merge at the file level
	 * is returned.
	 * @param provider the model provider of the elements to be merged
	 * (must not be <code>null</code>)
	 * @return a merger
	 */
	protected IResourceMappingMerger getMerger(ModelProvider provider) {
		Assert.isNotNull(provider);
		Object o = provider.getAdapter(IResourceMappingMerger.class);
		if (o instanceof IResourceMappingMerger) {
			return (IResourceMappingMerger) o;	
		}
		return new DefaultResourceMappingMerger(provider);
	}
	
	/**
	 * Return whether the given diff tree contains any deltas that match the given filter.
	 * @param tree the diff tree
	 * @param filter the diff node filter
	 * @param monitor a progress monitor
	 * @return whether the given diff tree contains any deltas that match the given filter
	 */
	protected boolean hasChangesMatching(IDiffTree tree, final FastDiffNodeFilter filter) {
		final CoreException found = new CoreException(Status.OK_STATUS);
		try {
			tree.accept(ResourcesPlugin.getWorkspace().getRoot().getFullPath(), new IDiffVisitor() {
				public boolean visit(IDiffNode delta) throws CoreException {
					if (filter.select(delta)) {
						throw found;
					}
					return false;
				}
			
			}, IResource.DEPTH_INFINITE);
		} catch (CoreException e) {
			if (e == found)
				return true;
			TeamUIPlugin.log(e);
		}
		return false;
	}

}
