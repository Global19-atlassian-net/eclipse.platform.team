package org.eclipse.team.internal.ccvs.ui.repo;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.Properties;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.wizards.NewLocationWizard;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * Called from Welcome page only.
 */
public class NewCVSAnonEclipseConnection extends Action {
	public void run() {
		Shell shell;
		IWorkbenchWindow window = CVSUIPlugin.getPlugin().getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			shell = window.getShell();
		} else {
			Display display = Display.getCurrent();
			shell = new Shell(display);
		}
		Properties p = new Properties();
		p.setProperty("connection", "pserver"); //$NON-NLS-1$ //$NON-NLS-2$
		p.setProperty("user", "anonymous"); //$NON-NLS-1$ //$NON-NLS-2$
		p.setProperty("host", "dev.eclipse.org"); //$NON-NLS-1$ //$NON-NLS-2$
		p.setProperty("root", "/home/eclipse"); //$NON-NLS-1$ //$NON-NLS-2$
		NewLocationWizard wizard = new NewLocationWizard(p);
		WizardDialog dialog = new WizardDialog(shell, wizard);
		dialog.open();
	}
}