package org.eclipse.team.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFileModificationValidator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.core.internal.Policy;

public class FileModificationValidator implements IFileModificationValidator {
	private static final Status OK = new Status(Status.OK, TeamPlugin.ID, Status.OK, "OK", null);
	
	/*
	 * @see IFileModificationValidator#validateEdit(IFile[], Object)
	 */
	public IStatus validateEdit(IFile[] files, Object context) {
		// To do: hash the files by provider and only call each provider once.
		IStatus[] result = new IStatus[files.length];
		// Optimization so we don't create a new IFile[] each time
		IFile[] fileArray = new IFile[1];
		for (int i = 0; i < files.length; i++) {
			IFile file = files[i];
			RepositoryProvider provider = RepositoryProviderType.getProvider(file.getProject());
			if (provider == null) {
				result[i] = OK;
			} else {
				fileArray[0] = file;
				IFileModificationValidator validator = provider.getFileModificationValidator();
				if(validator == null)
					return OK;
				result[i] = validator.validateEdit(fileArray, context);
			}
		}
		if (result.length == 1) {
			return result[0];
		} 
		return new MultiStatus(TeamPlugin.ID, 0, result, Policy.bind("FileModificationValidator.validateEdit"), null);
	}

	/*
	 * @see IFileModificationValidator#validateSave(IFile)
	 */
	public IStatus validateSave(IFile file) {
		RepositoryProvider provider = RepositoryProviderType.getProvider(file.getProject());
		if (provider == null) {
			return OK;
		}
		IFileModificationValidator validator = provider.getFileModificationValidator();
		if(validator == null)
			return OK;
		return validator.validateSave(file);
	}
}