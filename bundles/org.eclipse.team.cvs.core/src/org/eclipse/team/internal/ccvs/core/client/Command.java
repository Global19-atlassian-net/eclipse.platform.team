package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProvider;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.ICVSRunnable;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.listeners.ICommandOutputListener;
import org.eclipse.team.internal.ccvs.core.client.listeners.IConsoleListener;

/**
 * Abstract base class for command requests.
 * Provides a framework for implementing command execution.
 */
public abstract class Command extends Request {
	/*** Command singleton instances ***/
	public final static Add ADD = new Add();
	public final static Admin ADMIN = new Admin();
	public final static Checkout CHECKOUT = new Checkout();
	public final static Commit COMMIT = new Commit();
	public final static Diff DIFF = new Diff();
	public final static Import IMPORT = new Import();
	public final static Log LOG = new Log();
	public final static Remove REMOVE = new Remove();
	public final static Status STATUS = new Status();
	public final static Tag TAG = new Tag();
	// The CUSTOM_TAG command has special handling for added and removed resources.
	// This behavior supports branching with local changes in the workspace
	public final static Tag CUSTOM_TAG = new Tag(true);
	public final static RTag RTAG = new RTag();
	public final static Update UPDATE = new Update();
	
	// Empty argument array
	public final static String[] NO_ARGUMENTS = new String[0];

	/*** Global options ***/
	// Empty global option array
	public static final GlobalOption[] NO_GLOBAL_OPTIONS = new GlobalOption[0];
	// Do not change file contents
	public static final GlobalOption DO_NOT_CHANGE = new GlobalOption("-n");  //$NON-NLS-1$
	// Do not record this operation into CVS command history
	public static final GlobalOption DO_NOT_LOG = new GlobalOption("-l");  //$NON-NLS-1$
	// Make new working files read-only
	public static final GlobalOption MAKE_READ_ONLY = new GlobalOption("-r"); //$NON-NLS-1$
	// Trace command execution
	public static final GlobalOption TRACE_EXECUTION = new GlobalOption("-t"); //$NON-NLS-1$

	/*** Global options: quietness ***/
	// Don't be quiet (normal verbosity)
	public static final QuietOption VERBOSE = new QuietOption(""); //$NON-NLS-1$
	// Be somewhat quiet (suppress informational messages)
	public static final QuietOption PARTLY_QUIET = new QuietOption("-q"); //$NON-NLS-1$
	// Be really quiet (silent but for serious problems)
	public static final QuietOption SILENT = new QuietOption("-Q"); //$NON-NLS-1$

	/*** Local options: common to many commands ***/
	// Empty local option array
	public static final LocalOption[] NO_LOCAL_OPTIONS = new LocalOption[0];
	// valid for: annotate checkout commit diff export log rdiff remove rtag status tag update  
	public static final LocalOption DO_NOT_RECURSE = new LocalOption("-l"); //$NON-NLS-1$	
	// valid for: checkout export update
	public static final LocalOption PRUNE_EMPTY_DIRECTORIES = new LocalOption("-P"); //$NON-NLS-1$
	// valid for: checkout export update
	public static final LocalOption MESSAGE_OPTION = new LocalOption("-m"); //$NON-NLS-1$

	/*** Local options: keyword substitution mode ***/
	// valid for: add admin checkout export import update
	private static final Map ksubstOptionMap = new HashMap();
	public static final KSubstOption KSUBST_BINARY = new KSubstOption("-kb"); //$NON-NLS-1$
	public static final KSubstOption KSUBST_TEXT = new KSubstOption("-ko"); //$NON-NLS-1$
	public static final KSubstOption KSUBST_TEXT_EXPAND = new KSubstOption("-kkv"); //$NON-NLS-1$
	public static final KSubstOption KSUBST_TEXT_EXPAND_LOCKER = new KSubstOption("-kkvl"); //$NON-NLS-1$
	public static final KSubstOption KSUBST_TEXT_VALUES_ONLY = new KSubstOption("-kv"); //$NON-NLS-1$
	public static final KSubstOption KSUBST_TEXT_KEYWORDS_ONLY = new KSubstOption("-kk"); //$NON-NLS-1$

	/*** Default command output listener ***/
	private static final ICommandOutputListener DEFAULT_OUTPUT_LISTENER =
		new ICommandOutputListener() {
			public IStatus messageLine(String line, ICVSFolder commandRoot, IProgressMonitor monitor) {
				return OK;
			}
			public IStatus errorLine(String line, ICVSFolder commandRoot, IProgressMonitor monitor) {
				return new CVSStatus(CVSStatus.ERROR, CVSStatus.ERROR_LINE, line);
			}

		};
	
	/**
	 * Prevents client code from instantiating us.
	 */
	protected Command() { }

	/**
	 * Provides the default command output listener which is used to accumulate errors.
	 * 
	 * Subclasses can override this method in order to properly interpret information
	 * received from the server.
	 */
	protected ICommandOutputListener getDefaultCommandOutputListener() {
		return DEFAULT_OUTPUT_LISTENER;
	}

	/**
	 * Sends the command's arguments to the server.
	 * [template method]
	 * <p>
	 * The default implementation sends all arguments.  Subclasses may override
	 * this method to provide alternate behaviour.
	 * </p>
	 * 
	 * @param session the CVS session
	 * @param arguments the arguments that were supplied by the caller of execute()
	 */
	protected void sendArguments(Session session, String[] arguments) throws CVSException {
		for (int i = 0; i < arguments.length; ++i) {
			session.sendArgument(arguments[i]);
		}
	}

	/**
	 * Describes the local resource state to the server prior to command execution.
	 * [template method]
	 * <p>
	 * Commands must override this method to inform the server about the state of
	 * local resources using the Entries, Modified, Unchanged, and Questionable
	 * requests as needed.
	 * </p>
	 * 
	 * @param session the CVS session
	 * @param globalOptions the global options for the command
	 * @param localOptions the local options for the command
	 * @param resources the resource arguments for the command
	 * @param monitor the progress monitor
	 */
	protected abstract void sendLocalResourceState(Session session, GlobalOption[] globalOptions,
		LocalOption[] localOptions, ICVSResource[] resources, IProgressMonitor monitor)
		throws CVSException;

	/**
	 * Cleans up after command execution.
	 * [template method] 
	 * <p>
	 * The default implementation is a no-op.  Subclasses may override this
	 * method to follow up command execution on the server with clean up
	 * operations on local resources.
	 * </p>
	 *
	 * @param session the CVS session
	 * @param globalOptions the global options for the command
	 * @param localOptions the local options for the command
	 * @param resources the resource arguments for the command
	 * @param monitor the progress monitor
	 * @param serverError true iff the server returned the "ok" response
	 */
	protected void commandFinished(Session session, GlobalOption[] globalOptions,
		LocalOption[] localOptions, ICVSResource[] resources, IProgressMonitor monitor,
		boolean serverError) throws CVSException {
	}

	/**
	 * Sends the local working directory path prior to command execution.
	 * [template method]
	 * <p>
	 * The default implementation sends the paths of local root directory
	 * (assuming it exists).  Subclasses may override this method to provide
	 * alternate behaviour.
	 * </p>
	 * 
	 * @param session the CVS session
	 */
	protected void sendLocalWorkingDirectory(Session session) throws CVSException {
		ICVSFolder localRoot = session.getLocalRoot();
		if (localRoot.isCVSFolder()) {
			session.sendLocalRootDirectory();
		} else {
			session.sendConstructedRootDirectory();
		}
	}

	/**
	 * Computes an array of ICVSResources corresponding to command arguments.
	 * [template method]
	 * <p>
	 * The default implementation assumes that all arguments supplied to the
	 * command represent resources in the local root that are to be manipulated.
	 * Subclasses must override this method if this assumption does not hold.
	 * </p>
	 * @param session the CVS session
	 * @param localOptions the command local options
	 * @param arguments the command arguments
	 * @return the resource arguments for the command
	 */
	protected ICVSResource[] computeWorkResources(Session session,
		LocalOption[] localOptions, String[] arguments) throws CVSException {
		ICVSFolder localRoot = session.getLocalRoot();

		if (arguments.length == 0) {
			// As a convenience, passing no arguments to the CVS command
			// implies the command will operate on the local root folder.
			return new ICVSResource[] { localRoot };
		} else {
			// Assume all arguments represent resources that are descendants
			// of the local root folder.
			ICVSResource[] resources = new ICVSResource[arguments.length];
			for (int i = 0; i < arguments.length; i++) {
				ICVSResource resource = localRoot.getChild(arguments[i]);				
				// file does not exist, it could have been deleted. It doesn't matter
				// which type we return since only the name of the resource is used
				// and sent to the server.
				if(resource==null) {
					if(localRoot.getName().length()==0) {
						// XXX returning a folder because it is the safest choice when
						// localRoot is a handle to the IWorkspaceRoot!
						resource = localRoot.getFolder(arguments[i]);
					} else {
						resource = localRoot.getFile(arguments[i]);
					}
				}
				resources[i] = resource;
			}
			return resources;
		}
	}

	/**
	 * Send an array of Resources.
	 * 
	 * @see Command#sendFileStructure(ICVSResource,IProgressMonitor,boolean,boolean,boolean)
	 */
	protected void sendFileStructure(Session session, ICVSResource[] resources,
		boolean emptyFolders, IProgressMonitor monitor) throws CVSException {
		checkResourcesManaged(resources);
		
		new FileStructureVisitor(session, emptyFolders, true, monitor).visit(session, resources);
	}

	/**
	 * Checks that all work resources are managed.
	 * 
	 * @param resources the resource arguments for the command
	 * @throws CVSException if some resources are not managed
	 */
	protected void checkResourcesManaged(ICVSResource[] resources) throws CVSException {
		for (int i = 0; i < resources.length; ++i) {
			ICVSFolder folder;
			if (resources[i].isFolder()) {
				folder = (ICVSFolder) resources[i];
			}
			else {
				folder = resources[i].getParent();
			}
			if (folder==null || (!folder.isCVSFolder() && folder.exists())) {
				throw new CVSException(Policy.bind("Command.argumentNotManaged", folder.getName()));//$NON-NLS-1$
			}
		}
	}
		
	/**
	 * Executes a CVS command.
	 * <p>
	 * Dispatches the commands, retrieves the results, and determines whether or
	 * not an error occurred.  A listener may be supplied to capture message text
	 * that would normally be written to the standard error and standard output
	 * streams of a command line CVS client.
	 * </p>
	 * @param session the open CVS session
	 * @param globalOptions the array of global options, or NO_GLOBAL_OPTIONS
	 * @param localOptions the array of local options, or NO_LOCAL_OPTIONS
	 * @param arguments the array of arguments (usually filenames relative to localRoot), or NO_ARGUMENTS
	 * @param listener the command output listener, or null to discard all messages
	 * @param monitor the progress monitor
	 * @return a status code indicating success or failure of the operation
	 * @throws CVSException if a fatal error occurs (e.g. connection timeout)
	 */
	public final IStatus execute(final Session session, final GlobalOption[] globalOptions,
		final LocalOption[] localOptions, final String[] arguments, final ICommandOutputListener listener,
		IProgressMonitor pm) throws CVSException {		
		final IStatus[] status = new IStatus[1];
		ICVSRunnable job = new ICVSRunnable() {
			public void run(IProgressMonitor monitor) throws CVSException {
				// update the global and local options
				GlobalOption[] gOptions = filterGlobalOptions(session, globalOptions);
				LocalOption[] lOptions = filterLocalOptions(session, gOptions, localOptions);
				
				// print the invocation string to the console
				if (session.isOutputToConsole() || Policy.DEBUG_CVS_PROTOCOL) {
					String line = constructCommandInvocationString(gOptions, lOptions, arguments);
					if (session.isOutputToConsole()) {
						IConsoleListener consoleListener = CVSProviderPlugin.getPlugin().getConsoleListener();
						if (consoleListener != null) consoleListener.commandInvoked(line);
					}
					if (Policy.DEBUG_CVS_PROTOCOL) System.out.println("CMD> " + line); //$NON-NLS-1$
				}
				
				// run the command
				try {
					status[0] = doExecute(session, gOptions, lOptions, arguments, listener, monitor);
					notifyConsoleOnCompletion(session, status[0], null);
				} catch (CVSException e) {
					notifyConsoleOnCompletion(session, null, e);
					throw e;
				} catch (RuntimeException e) {
					notifyConsoleOnCompletion(session, null, e);
					throw e;
				}
			}
		};
		session.getLocalRoot().run(job, pm);
		return status[0];
	}
	
	private void notifyConsoleOnCompletion(Session session, IStatus status, Exception exception) {
		if (session.isOutputToConsole()) {
			IConsoleListener consoleListener = CVSProviderPlugin.getPlugin().getConsoleListener();
			if (consoleListener != null) consoleListener.commandCompleted(status, exception);
		}
		if (Policy.DEBUG_CVS_PROTOCOL) {
			if (status != null) System.out.println("RESULT> " + status.toString()); //$NON-NLS-1$
			else System.out.println("RESULT> " + exception.toString()); //$NON-NLS-1$
		}
	}

	protected IStatus doExecute(Session session, GlobalOption[] globalOptions,
		LocalOption[] localOptions, String[] arguments, ICommandOutputListener listener,
		IProgressMonitor monitor) throws CVSException {
		ICVSResource[] resources = null;
		/*** setup progress monitor ***/
		monitor = Policy.monitorFor(monitor);
		monitor.beginTask(Policy.bind("Command.server"), 100); //$NON-NLS-1$
		Policy.checkCanceled(monitor);
		try {
			/*** prepare for command ***/
			// clear stale command state from previous runs
			session.setNoLocalChanges(DO_NOT_CHANGE.isElementOf(globalOptions));
			session.setModTime(null);

			/*** initiate command ***/
			// send global options
			for (int i = 0; i < globalOptions.length; i++) {
				globalOptions[i].send(session);
			}
			Policy.checkCanceled(monitor);
			// send local options
			for (int i = 0; i < localOptions.length; i++) {
				localOptions[i].send(session);
			}
			Policy.checkCanceled(monitor);
			// compute the work resources
			resources = computeWorkResources(session, localOptions, arguments);			
			Policy.checkCanceled(monitor);
			// send local working directory state contributes 25% of work
			sendLocalResourceState(session, globalOptions, localOptions,
				resources, Policy.subMonitorFor(monitor, 25));
			Policy.checkCanceled(monitor);
			// send arguments
			sendArguments(session, arguments);
			// send local working directory path
			sendLocalWorkingDirectory(session);

			// if no listener was provided, use the command's default in order to get error reporting
			if (listener == null) listener = getDefaultCommandOutputListener();

			/*** execute command and process responses ***/
			// Processing responses contributes 70% of work.
			IStatus status = executeRequest(session, listener, Policy.subMonitorFor(monitor, 70));

			// Finished adds last 5% of work.
			commandFinished(session, globalOptions, localOptions, resources, Policy.subMonitorFor(monitor, 5),
				status.getCode() != CVSStatus.SERVER_ERROR);
			return status;
		} finally {			
			monitor.done();
		}
	}
	
	/**
	 * Constucts the CVS command invocation string corresponding to the arguments.
	 * 
	 * @param globalOptions the global options
	 * @param localOption the local options
	 * @param arguments the arguments
	 * @return the command invocation string
	 */
	private String constructCommandInvocationString(GlobalOption[] globalOptions,
		LocalOption[] localOptions, String[] arguments) {
		StringBuffer commandLine = new StringBuffer("cvs"); //$NON-NLS-1$
		for (int i = 0; i < globalOptions.length; ++i) {
			String option = globalOptions[i].toString();
			if (option.length() == 0) continue;
			commandLine.append(' ');
			commandLine.append(option);
		}
		commandLine.append(' ');
		commandLine.append(getRequestId());
		for (int i = 0; i < localOptions.length; ++i) {
			String option = localOptions[i].toString();
			if (option.length() == 0) continue;
			commandLine.append(' ');
			commandLine.append(option);
		}
		for (int i = 0; i < arguments.length; ++i) {
			if (arguments[i].length() == 0) continue;
			commandLine.append(" \""); //$NON-NLS-1$
			commandLine.append(arguments[i]);
			commandLine.append('"'); //$NON-NLS-1$
		}
		return commandLine.toString();
	}	

	/**
	 * Superclass for all CVS command options
	 */
	protected static abstract class Option {
		protected String option, argument;
		protected Option(String option, String argument) {
			this.option = option;
			this.argument = argument;
		}
		/**
		 * Determines if this option is an element of an array of options
		 * @param array the array of options
		 * @return true iff the array contains this option
		 */
		public boolean isElementOf(Option[] array) {
			return findOption(array, option) != null;
		}
		/**
		 * Returns the option part of the option
		 */
		String getOption() {
			return option;
		}
		/**
		 * Compares two options for equality.
		 * @param other the other option
		 */
		public boolean equals(Object other) {
			if (this == other) return true;
			if (other instanceof Option) {
				Option otherOption = (Option) other;
				return option.equals(otherOption.option);
			}
			return false;
		}
		/**
		 * Sends the option to a CVS server
		 * @param session the CVS session
		 */
		public abstract void send(Session session) throws CVSException;		
		/*
		 * To make debugging a tad easier.
		 */
		public String toString() {
			if (argument != null && argument.length() != 0) {
				return option + " \"" + argument + '"'; //$NON-NLS-1$
			} else {
				return option;
			}
		}
	}	
	/**
	 * Option subtype for global options that are common to all commands.
	 */
	public static class GlobalOption extends Option {
		protected GlobalOption(String option) {
			super(option, null);
		}
		public void send(Session session) throws CVSException {
			session.sendGlobalOption(option);
		}
	}
	/**
	 * Option subtype for global quietness options.
	 */
	public static final class QuietOption extends GlobalOption {
		private QuietOption(String option) {
			super(option);
		}
		public void send(Session session) throws CVSException {
			if (option.length() != 0) super.send(session);
		}
	}
	/**
	 * Option subtype for local options that vary from command to command.
	 */
	public static class LocalOption extends Option {
		protected LocalOption(String option) {
			super(option, null);
		}
		protected LocalOption(String option, String argument) {
			super(option, argument);
		}
		public void send(Session session) throws CVSException {
			session.sendArgument(option);
			if (argument != null) session.sendArgument(argument);
		}
	}
	/**
	 * Options subtype for keyword substitution options.
	 */
	public static class KSubstOption extends LocalOption {
		private boolean isUnknownMode;
		private KSubstOption(String option) {
			this(option, false);
		}
		private KSubstOption(String option, boolean isUnknownMode) {
			super(option);
			this.isUnknownMode = isUnknownMode;
			ksubstOptionMap.put(option, this);
		}
		/**
		 * Gets the KSubstOption instance for the specified mode.
		 * 
		 * @param mode the mode, e.g. -kb
		 * @return an instance for that mode
		 */
		public static KSubstOption fromMode(String mode) {
			if (mode.length() == 0) mode = "-kkv"; // use default //$NON-NLS-1$
			KSubstOption option = (KSubstOption) ksubstOptionMap.get(mode);
			if (option == null) option = new KSubstOption(mode, true);
			return option;
		}
		/**
		 * Gets the KSubstOption instance for the specified file.
		 *
		 * @param file the file to get the option for
		 * @return an instance for that mode
		 */
		public static KSubstOption fromFile(IFile file) {
			if (CVSProvider.isText(file))
				return CVSProviderPlugin.getPlugin().getDefaultTextKSubstOption();
			return KSUBST_BINARY;
		}
		/**
		 * Returns an array of all valid modes.
		 */
		public static KSubstOption[] getAllKSubstOptions() {
			return (KSubstOption[]) ksubstOptionMap.values().toArray(new KSubstOption[ksubstOptionMap.size()]);
		}
		/**
		 * Returns the entry line mode string for this instance.
		 */
		public String toMode() {
			if (KSUBST_TEXT_EXPAND.equals(this)) return ""; //$NON-NLS-1$
			return getOption();
		}
		/**
		 * Returns true if the substitution mode requires no data translation
		 * during file transfer.
		 */
		public boolean isBinary() {
			return KSUBST_BINARY.equals(this);
		}
		/**
		 * Returns a short localized text string describing this mode.
		 */
		public String getShortDisplayText() {
			if (isUnknownMode) return Policy.bind("KSubstOption.unknown.short", option); //$NON-NLS-1$
			return Policy.bind("KSubstOption." + option + ".short"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		/**
		 * Returns a long localized text string describing this mode.
		 */
		public String getLongDisplayText() {
			if (isUnknownMode) return Policy.bind("KSubstOption.unknown.long", option); //$NON-NLS-1$
			return Policy.bind("KSubstOption." + option + ".long"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 * Makes a -m log message option.
	 * Valid for: add commit import
	 */
	public static LocalOption makeArgumentOption(LocalOption option, String argument) {
		return new LocalOption(option.getOption(), argument);  //$NON-NLS-1$
	}
	
	/**
	 * Makes a -r or -D option for a tag.
	 * Valid for: checkout export history rdiff update
	 */
	public static LocalOption makeTagOption(CVSTag tag) {
		int type = tag.getType();
		switch (type) {
			case CVSTag.BRANCH:
			case CVSTag.VERSION:
				return new LocalOption("-r", tag.getName()); //$NON-NLS-1$
			case CVSTag.DATE:
				return new LocalOption("-D", tag.getName()); //$NON-NLS-1$
			default:
				// tag must not be HEAD
				throw new IllegalArgumentException(Policy.bind("Command.invalidTag")); //$NON-NLS-1$
		}
	}

	/**
	 * Find a specific option in an array of options
	 * @param array the array of options
	 * @param option the option string to search for
	 * @return the first element matching the option string, or null if none
	 */
	public static Option findOption(Option[] array, String option) {
		for (int i = 0; i < array.length; ++i) {
			if (array[i].getOption().equals(option)) return array[i];
		}
		return null;
	}

	/**
	 * Collect all arguments of a specific option from an array of options
	 * @param array the array of options
	 * @param option the option string to search for
	 * @return an array of all arguments of belonging to matching options
	 */
	protected static String[] collectOptionArguments(Option[] array, String option) {
		List /* of String */ list = new ArrayList();
		for (int i = 0; i < array.length; ++i) {
			if (array[i].getOption().equals(option)) {
				list.add(array[i].argument);
			}
		}
		return (String[]) list.toArray(new String[list.size()]);
	}
	
	/**
	 * Allows commands to filter the set of global options to be sent.
	 * Subclasses that override this method should call the superclass.
	 * 
	 * @param session the session
	 * @param globalOptions the global options, read-only
	 * @return the filtered global options
	 */
	protected GlobalOption[] filterGlobalOptions(Session session, GlobalOption[] globalOptions) {
		if (! DO_NOT_CHANGE.isElementOf(globalOptions)) {
			QuietOption quietOption = CVSProviderPlugin.getPlugin().getQuietness();
			if (quietOption != null) {
				GlobalOption[] oldOptions = globalOptions;
				globalOptions = new GlobalOption[oldOptions.length + 1];
				System.arraycopy(oldOptions, 0, globalOptions, 1, oldOptions.length);
				globalOptions[0] = quietOption;
			}
		}
		return globalOptions;
	}
	
	/**
	 * Allows commands to filter the set of local options to be sent.
	 * Subclasses that override this method should call the superclass.
	 * 
	 * @param session the session
	 * @param globalOptions the global options, read-only
	 * @param localOptions the local options, read-only
	 * @return the filtered local options
	 */
	protected LocalOption[] filterLocalOptions(Session session, GlobalOption[] globalOptions, LocalOption[] localOptions) {
		return localOptions;
	}
	
	/**
	 * Execute a CVS command inside an ICVSRunnable passed to Session.run()
	 * <p>
	 * Dispatches the commands, retrieves the results, and determines whether or
	 * not an error occurred.  A listener may be supplied to capture message text
	 * that would normally be written to the standard error and standard output
	 * streams of a command line CVS client.
	 * </p>
	 * @param globalOptions the array of global options, or NO_GLOBAL_OPTIONS
	 * @param localOptions the array of local options, or NO_LOCAL_OPTIONS
	 * @param arguments the array of arguments (usually filenames relative to localRoot), or NO_ARGUMENTS
	 * @param listener the command output listener, or null to discard all messages
	 * @param monitor the progress monitor
	 * @return a status code indicating success or failure of the operation
	 * @throws CVSException if a fatal error occurs (e.g. connection timeout)
	 */
	public final IStatus execute(GlobalOption[] globalOptions, LocalOption[] localOptions, ICVSResource[] arguments, 
		ICommandOutputListener listener, IProgressMonitor pm) throws CVSException {
		
		// We assume that all the past resources have the same root
		Session openSession = Session.getOpenSession(arguments[0]);
		if (openSession == null) {
			throw new CVSException(Policy.bind("Command.noOpenSession")); //$NON-NLS-1$
		} else {
			// XXX Should check for compatibility between arguments and root
			// Convert arguments
			List stringArguments = new ArrayList(arguments.length);
			for (int i = 0; i < arguments.length; i++) {
				stringArguments.add(arguments[i].getRelativePath(openSession.getLocalRoot()));
			}
			return execute(openSession, globalOptions, localOptions, (String[]) stringArguments.toArray(new String[stringArguments.size()]), listener, pm);
		}
				
		
	}
}