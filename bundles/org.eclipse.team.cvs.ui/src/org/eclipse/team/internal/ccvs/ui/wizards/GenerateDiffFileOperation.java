package org.eclipse.team.internal.ccvs.ui.wizards;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.ccvs.core.CVSTeamProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.Policy;

/**
 * An operation to run the CVS diff operation on a set of resources. The result
 * of the diff is written to a file. If there are no differences found, the
 * user is notified and the output file is not created.
 */
public class GenerateDiffFileOperation implements IRunnableWithProgress {

	private File outputFile;
	private IResource[] resources;
	private Shell shell;
	private LocalOption[] options;
	private boolean toClipboard;

	GenerateDiffFileOperation(IResource[] resources, File file, boolean toClipboard, LocalOption[] options, Shell shell) {
		this.resources = resources;
		this.outputFile = file;
		this.shell = shell;
		this.options = options;
		this.toClipboard = toClipboard;
	}

	/**
	 * @see IRunnableWithProgress#run(IProgressMonitor)
	 */
	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		MultiStatus result = new MultiStatus(CVSUIPlugin.ID, 1, Policy.bind("GenerateCVSDiff.error"), null);
		try {
			if (resources == null)
				return;

			monitor.beginTask("", resources.length * 500);
			monitor.setTaskName(
			Policy.bind("GenerateCVSDiff.working"));
			
			OutputStream os;
			if(toClipboard) {
				os = new ByteArrayOutputStream();
			} else {
				os = new FileOutputStream(outputFile);
			}
			try {
				for (int i = 0; i < resources.length; i++) {
					IResource resource = resources[i];
					CVSTeamProvider provider = (CVSTeamProvider)TeamPlugin.getManager().getProvider(resource);
					provider.diff(new IResource[] {resource}, options, new PrintStream(os), new SubProgressMonitor(monitor, 500));
				}
			} finally {
				os.close();
			}

			boolean emptyDiff = false;
			
			if(toClipboard) {				
				ByteArrayOutputStream baos = (ByteArrayOutputStream)os;
				if(baos.size() == 0) {
					emptyDiff = true;
				} else {
					TextTransfer plainTextTransfer = TextTransfer.getInstance();
					Clipboard clipboard= new Clipboard(shell.getDisplay());		
					clipboard.setContents(
						new String[]{baos.toString()}, 
						new Transfer[]{plainTextTransfer});	
				}
			} else {
				if(outputFile.length() == 0) {
					emptyDiff = true;
					outputFile.delete();
				}	
			}

			//check for empty diff and report			
			if (emptyDiff) {
				MessageDialog.openInformation(
					shell,
					Policy.bind("GenerateCVSDiff.noDiffsFoundTitle"),
					Policy.bind("GenerateCVSDiff.noDiffsFoundMsg"));
			}
		} catch (TeamException e) {
			throw new InvocationTargetException(e);
		} catch(IOException e) {
			throw new InvocationTargetException(e);
		} finally {
			monitor.done();
		}
	}
}