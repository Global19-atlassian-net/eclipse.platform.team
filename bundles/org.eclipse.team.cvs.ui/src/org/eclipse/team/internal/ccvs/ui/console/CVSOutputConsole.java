/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.console;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.listeners.IConsoleListener;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.*;

/**
 * Console that shows the output of CVS commands. It is shown as a page in the generic 
 * console view. It supports coloring for message, command, and error lines in addition
 * the font can be configured.
 * 
 * @since 3.0 
 */
public class CVSOutputConsole extends MessageConsole implements IConsoleListener, IPropertyChangeListener {
	
	// created colors for each line type - must be disposed at shutdown
	private Color commandColor;
	private Color messageColor;
	private Color errorColor;
	
	// used to time the commands
	private long commandStarted = 0;
	
	// streams for each command type - each stream has its own color
	private MessageConsoleStream commandStream;
	private MessageConsoleStream messageStream;
	private MessageConsoleStream errorStream;
	
	// preferences for showing the cvs console when cvs output is provided 
	private boolean showOnMessage;
	
	private ConsoleDocument document;
	private IConsoleManager consoleManager;
	
	// format for timings printed to console
	private static final DateFormat TIME_FORMAT = new SimpleDateFormat(Policy.bind("Console.resultTimeFormat")); //$NON-NLS-1$

	// Indicates whether the console is visible in the Console view
	private boolean visible = false;
	// Indicates whether the console's streams have been initialized
	private boolean initialized = false;
	
	/**
	 * Used to notify this console of lifecycle methods <code>init()</code>
	 * and <code>dispose()</code>.
	 */
	public class MyLifecycle implements org.eclipse.ui.console.IConsoleListener {
		public void consolesAdded(IConsole[] consoles) {
			for (int i = 0; i < consoles.length; i++) {
				IConsole console = consoles[i];
				if (console == CVSOutputConsole.this) {
					init();
				}
			}

		}
		public void consolesRemoved(IConsole[] consoles) {
			for (int i = 0; i < consoles.length; i++) {
				IConsole console = consoles[i];
				if (console == CVSOutputConsole.this) {
					ConsolePlugin.getDefault().getConsoleManager().removeConsoleListener(this);
					dispose();
				}
			}
		}
	}
	
	/**
	 * Constructor initializes preferences and colors but doesn't create the console
	 * page yet.
	 */
	public CVSOutputConsole() {
		super("CVS", CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_CVS_CONSOLE)); //$NON-NLS-1$
		showOnMessage = CVSUIPlugin.getPlugin().getPreferenceStore().getBoolean(ICVSUIConstants.PREF_CONSOLE_SHOW_ON_MESSAGE);
		document = new ConsoleDocument();
		consoleManager = ConsolePlugin.getDefault().getConsoleManager();
		CVSProviderPlugin.getPlugin().setConsoleListener(CVSOutputConsole.this);
		CVSUIPlugin.getPlugin().getPreferenceStore().addPropertyChangeListener(CVSOutputConsole.this);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.console.AbstractConsole#init()
	 */
	protected void init() {
		// Called when console is added to the console view
		super.init();	
		
		initLimitOutput();
		initWrapSetting();
		
		//	Ensure that initialization occurs in the ui thread
		CVSUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
			public void run() {
				JFaceResources.getFontRegistry().addListener(CVSOutputConsole.this);
				initializeStreams();
				dump();
			}
		});
	}
	
	private void initWrapSetting() {
		IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();
		if(store.getBoolean(ICVSUIConstants.PREF_CONSOLE_WRAP)) {
			setConsoleWidth(store.getInt(ICVSUIConstants.PREF_CONSOLE_WIDTH));
		} else {
			setConsoleWidth(-1);
		}
	}
	
	private void initLimitOutput() {
		IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();
		if(store.getBoolean(ICVSUIConstants.PREF_CONSOLE_LIMIT_OUTPUT)) {
			setWaterMarks(1000, store.getInt(ICVSUIConstants.PREF_CONSOLE_HIGH_WATER_MARK));
		} else {
			setWaterMarks(-1, -1);
		}
	}
	
	/*
	 * Initialize thre streams of the console. Must be 
	 * called from the UI thread.
	 */
	private void initializeStreams() {
		synchronized(document) {
			if (!initialized) {
				commandStream = newMessageStream();
				errorStream = newMessageStream();
				messageStream = newMessageStream();
				// install colors
				commandColor = createColor(CVSUIPlugin.getStandardDisplay(), ICVSUIConstants.PREF_CONSOLE_COMMAND_COLOR);
				commandStream.setColor(commandColor);
				messageColor = createColor(CVSUIPlugin.getStandardDisplay(), ICVSUIConstants.PREF_CONSOLE_MESSAGE_COLOR);
				messageStream.setColor(messageColor);
				errorColor = createColor(CVSUIPlugin.getStandardDisplay(), ICVSUIConstants.PREF_CONSOLE_ERROR_COLOR);
				errorStream.setColor(errorColor);
				// install font
				Font f = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getFontRegistry().get(ICVSUIConstants.PREF_CONSOLE_FONT);
				setFont(f);
				initialized = true;
			}
		}
	}

	private void dump() {
		synchronized(document) {
			visible = true;
			ConsoleDocument.ConsoleLine[] lines = document.getLines();
			for (int i = 0; i < lines.length; i++) {
				ConsoleDocument.ConsoleLine line = lines[i];
				appendLine(line.type, line.line);
			}
			document.clear();
		}
	}
	
	private void appendLine(int type, String line) {
		showConsole();
		synchronized(document) {
			if(visible) {
				switch(type) {
					case ConsoleDocument.COMMAND:
						commandStream.println(line);
						break;
					case ConsoleDocument.MESSAGE:
						messageStream.println("  " + line); //$NON-NLS-1$
						break;
					case ConsoleDocument.ERROR:
						errorStream.println("  " + line); //$NON-NLS-1$
						break;
				}
			} else {
				document.appendConsoleLine(type, line);
			}
		}
	}
	
	/*
     * Add hyperlinks for the following descriptors
     */
    private void addHyperlinks(String text, CVSHyperlinkDescriptor[] descriptors) {
        try {
            int offset = getOffsetFor(text);
            for (int i = 0; i < descriptors.length; i++) {
                CVSHyperlinkDescriptor descriptor = descriptors[i];
                addHyperlink(getHyperlink(descriptor), offset + descriptor.getOffset(), descriptor.getLength());
            }
        } catch (BadLocationException e) {
            CVSUIPlugin.log(CVSException.wrapException(e));
        }
    }


    /*
     * Return a hyperlink for the given descriptor
     */
    private IHyperlink getHyperlink(final CVSHyperlinkDescriptor descriptor) {
        // TODO Auto-generated method stub
        return new IHyperlink() {
            /* (non-Javadoc)
             * @see org.eclipse.ui.console.IHyperlink#linkEntered()
             */
            public void linkEntered() {
                // TODO Auto-generated method stub

            }

            /* (non-Javadoc)
             * @see org.eclipse.ui.console.IHyperlink#linkExited()
             */
            public void linkExited() {
                // TODO Auto-generated method stub

            }

            /* (non-Javadoc)
             * @see org.eclipse.ui.console.IHyperlink#linkActivated()
             */
            public void linkActivated() {
                System.out.println(descriptor.getLocalResource());
            }
        };
    }

    /*
     * Return the offset for the text that was just added to the console
     * @throws BadLocationException
     */
    private int getOffsetFor(String text) throws BadLocationException {
        IDocument doc = getDocument();
        int offset = doc.getLength() - 1;
        while (Character.isWhitespace(doc.getChar(offset))) {
            offset--;
        }
        text = text.trim();
        return offset - text.length() + 1;
    }

    private void showConsole() {
		if(showOnMessage) {
			if(!visible)
				CVSConsoleFactory.showConsole();
			else
				consoleManager.showConsoleView(this);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.console.MessageConsole#dispose()
	 */
	protected void dispose() {
		// Here we can't call super.dispose() because we actually want the partitioner to remain
		// connected, but we won't show lines until the console is added to the console manager
		// again.
		
		// Called when console is removed from the console view
		synchronized (document) {
			visible = false;
			JFaceResources.getFontRegistry().removeListener(this);
		}
	}
	
	/**
	 * Clean-up created fonts.
	 */
	public void shutdown() {
		// Call super dispose because we want the partitioner to be
		// disconnected.
		super.dispose();
		if (commandColor != null)
			commandColor.dispose();
		if (messageColor != null)
			messageColor.dispose();
		if (errorColor != null)
			errorColor.dispose();
		CVSUIPlugin.getPlugin().getPreferenceStore().removePropertyChangeListener(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IConsoleListener#commandInvoked(java.lang.String)
	 */
	public void commandInvoked(Session session, String line) {
	    if (!session.isOutputToConsole()) return;
		commandStarted = System.currentTimeMillis();
		appendLine(ConsoleDocument.COMMAND, Policy.bind("Console.preExecutionDelimiter")); //$NON-NLS-1$
		appendLine(ConsoleDocument.COMMAND, line);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IConsoleListener#messageLineReceived(java.lang.String)
	 */
	public void messageLineReceived(Session session, String line, IStatus status) {
	    if (session.isOutputToConsole()) {
	        appendLine(ConsoleDocument.MESSAGE, "  " + line); //$NON-NLS-1$
	        addHyperlinks(line, status);
	    }
	}
	
    private void addHyperlinks(String line, IStatus status) {
        if (visible) {
            CVSHyperlinkDescriptor[] descriptors;
            if (status instanceof CVSParseStatus) {
                descriptors = ((CVSParseStatus)status).getHyperlinkDescriptors();
            } else {
                descriptors = null;
            }
            if (descriptors != null && descriptors.length > 0)
                addHyperlinks(line, descriptors);
        }
    }

    /* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IConsoleListener#errorLineReceived(java.lang.String)
	 */
	public void errorLineReceived(Session session, String line, IStatus status) {
	    if (session.isOutputToConsole()) {
	        appendLine(ConsoleDocument.ERROR, "  " + line); //$NON-NLS-1$
	        addHyperlinks(line, status);
	    }
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.client.listeners.IConsoleListener#commandCompleted(org.eclipse.core.runtime.IStatus, java.lang.Exception)
	 */
	public void commandCompleted(Session session, IStatus status, Exception exception) {
	    if (!session.isOutputToConsole()) return;
		long commandRuntime = System.currentTimeMillis() - commandStarted;
		String time;
		try {
			time = TIME_FORMAT.format(new Date(commandRuntime));
		} catch (RuntimeException e) {
			CVSUIPlugin.log(IStatus.ERROR, Policy.bind("Console.couldNotFormatTime"), e); //$NON-NLS-1$
			time = ""; //$NON-NLS-1$
		}
		String statusText;
		if (status != null) {
			if (status.getCode() == CVSStatus.SERVER_ERROR) {
				statusText = Policy.bind("Console.resultServerError", status.getMessage(), time); //$NON-NLS-1$
			} else {
				statusText = Policy.bind("Console.resultOk", time); //$NON-NLS-1$
			}
			appendLine(ConsoleDocument.COMMAND, statusText);
			IStatus[] children = status.getChildren();
			if (children.length == 0) {
				if (!status.isOK())
					appendLine(ConsoleDocument.COMMAND, messageLineForStatus(status));
			} else {
				for (int i = 0; i < children.length; i++) {
					if (!children[i].isOK())
						appendLine(ConsoleDocument.COMMAND, messageLineForStatus(children[i]));
				}
			}
		} else if (exception != null) {
			if (exception instanceof OperationCanceledException) {
				statusText = Policy.bind("Console.resultAborted", time); //$NON-NLS-1$
			} else {
				statusText = Policy.bind("Console.resultException", time); //$NON-NLS-1$
			}
			appendLine(ConsoleDocument.COMMAND, statusText);
		} else {
			statusText = Policy.bind("Console.resultOk", time); //$NON-NLS-1$
		}
		appendLine(ConsoleDocument.COMMAND, Policy.bind("Console.postExecutionDelimiter")); //$NON-NLS-1$
		appendLine(ConsoleDocument.COMMAND, ""); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		String property = event.getProperty();
		// colors
		if (visible) {
			if (property.equals(ICVSUIConstants.PREF_CONSOLE_COMMAND_COLOR)) {
				Color newColor = createColor(CVSUIPlugin.getStandardDisplay(), ICVSUIConstants.PREF_CONSOLE_COMMAND_COLOR);
				commandStream.setColor(newColor);
				commandColor.dispose();
				commandColor = newColor;
			} else if (property.equals(ICVSUIConstants.PREF_CONSOLE_MESSAGE_COLOR)) {
				Color newColor = createColor(CVSUIPlugin.getStandardDisplay(), ICVSUIConstants.PREF_CONSOLE_MESSAGE_COLOR);
				messageStream.setColor(newColor);
				messageColor.dispose();
				messageColor = newColor;
			} else if (property.equals(ICVSUIConstants.PREF_CONSOLE_ERROR_COLOR)) {
				Color newColor = createColor(CVSUIPlugin.getStandardDisplay(), ICVSUIConstants.PREF_CONSOLE_ERROR_COLOR);
				errorStream.setColor(newColor);
				errorColor.dispose();
				errorColor = newColor;
				// font
			} else if (property.equals(ICVSUIConstants.PREF_CONSOLE_FONT)) {
				setFont(((FontRegistry) event.getSource()).get(ICVSUIConstants.PREF_CONSOLE_FONT));
			}
		}
		if (property.equals(ICVSUIConstants.PREF_CONSOLE_SHOW_ON_MESSAGE)) {
			Object value = event.getNewValue();
			if (value instanceof String) {
				showOnMessage = Boolean.valueOf((String) value).booleanValue();
			} else {
				showOnMessage = ((Boolean) value).booleanValue();
			}
		} else if(property.equals(ICVSUIConstants.PREF_CONSOLE_LIMIT_OUTPUT)) {
			initLimitOutput();
		} else if(property.equals(ICVSUIConstants.PREF_CONSOLE_WRAP)) {
			initWrapSetting();
		}
	}
	
	/**
	 * Returns the NLSd message based on the status returned from the CVS
	 * command.
	 * 
	 * @param status an NLSd message based on the status returned from the CVS
	 * command.
	 */
	private String messageLineForStatus(IStatus status) {
		if (status.getSeverity() == IStatus.ERROR) {
			return Policy.bind("Console.error", status.getMessage()); //$NON-NLS-1$
		} else if (status.getSeverity() == IStatus.WARNING) {
			return Policy.bind("Console.warning", status.getMessage()); //$NON-NLS-1$
		} else if (status.getSeverity() == IStatus.INFO) {
			return Policy.bind("Console.info", status.getMessage()); //$NON-NLS-1$
		}
		return status.getMessage();
	}
	
	/**
	 * Returns a color instance based on data from a preference field.
	 */
	private Color createColor(Display display, String preference) {
		RGB rgb = PreferenceConverter.getColor(CVSUIPlugin.getPlugin().getPreferenceStore(), preference);
		return new Color(display, rgb);
	}
}