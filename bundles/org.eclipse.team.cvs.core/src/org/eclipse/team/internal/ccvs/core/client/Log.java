package org.eclipse.team.internal.ccvs.core.client;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

public class Log extends AbstractMessageCommand {
	/*** Local options: specific to log ***/

	protected Log() { }
	protected String getCommandId() {
		return "log";
	}
}

