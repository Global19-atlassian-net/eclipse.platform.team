package org.eclipse.team.tests.ccvs.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import junit.framework.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.team.ccvs.core.CVSStatus;
import org.eclipse.team.ccvs.core.ICVSFolder;
import org.eclipse.team.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.Command.GlobalOption;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;

public class EclipseCVSClient {
	private static final HashMap commandPool = new HashMap();
	static {
		commandPool.put("update", Command.UPDATE);
		commandPool.put("co", Command.CHECKOUT);
		commandPool.put("ci", Command.COMMIT);
		commandPool.put("import", Command.IMPORT);
		commandPool.put("add", Command.ADD);
		commandPool.put("remove", Command.REMOVE);
		commandPool.put("status", Command.STATUS);
		commandPool.put("log", Command.LOG);
		commandPool.put("tag", Command.TAG);
		commandPool.put("rtag", Command.RTAG);
		commandPool.put("admin", Command.ADMIN);
		commandPool.put("diff", Command.DIFF);
	}
	
	public static void execute(
		ICVSRepositoryLocation cvsRepositoryLocation, ICVSFolder cvsLocalRoot,
		String command, String[] globalOptions, String[] localOptions,
		String[] arguments) throws CVSException {
		// test arguments
		Assert.assertNotNull(cvsRepositoryLocation);
		Assert.assertNotNull(cvsLocalRoot);
		Assert.assertNotNull(command);
		Assert.assertNotNull(globalOptions);
		Assert.assertNotNull(localOptions);
		Assert.assertNotNull(arguments);
		Assert.assertTrue(cvsLocalRoot.exists());

		// get command instance
		Command cvsCommand = (Command) commandPool.get(command);
			
		// get global options
		List globals = new ArrayList();
		for (int i = 0; i < globalOptions.length; i++) {
			globals.add(new CustomGlobalOption(globalOptions[i]));
		}
		GlobalOption[] cvsGlobalOptions = (GlobalOption[]) globals.toArray(new GlobalOption[globals.size()]);
		
		// get local options
		List locals = new ArrayList();
		for (int i = 0; i < localOptions.length; i++) {
			String option = localOptions[i];
			String argument = null;
			if ((i < localOptions.length - 1) && (localOptions[i + 1].charAt(0) != '-')) {
				argument = localOptions[++i];
			}
			locals.add(new CustomLocalOption(option, argument));
		}
		LocalOption[] cvsLocalOptions = (LocalOption[]) locals.toArray(new LocalOption[locals.size()]);
		
		// execute command
		IProgressMonitor monitor = new NullProgressMonitor();
		Session session = new Session(cvsRepositoryLocation, cvsLocalRoot);
		try {
			session.open(monitor);
			IStatus status = cvsCommand.execute(session,
				cvsGlobalOptions, cvsLocalOptions, arguments, null, monitor);
			if (status.getCode() == CVSStatus.SERVER_ERROR) {
				throw new CVSClientException("Eclipse client returned non-ok status: " + status);
			}
		} finally {
			session.close();
		}
	}

	private static class CustomGlobalOption extends GlobalOption {
		public CustomGlobalOption(String option) {
			super(option);
		}
	}

	private static class CustomLocalOption extends LocalOption {
		public CustomLocalOption(String option, String arg) {
			super(option, arg);
		}
	}
}
