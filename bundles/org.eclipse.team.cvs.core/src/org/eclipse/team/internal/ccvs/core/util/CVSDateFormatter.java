package org.eclipse.team.internal.ccvs.core.util;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility class for converting timestamps used in Entry file lines. The format
 * required in the Entry file is ISO C asctime() function (Sun Apr 7 01:29:26 1996).
 */
public class CVSDateFormatter {
	
	// entry file date is actually a zero padded number:
	// Mon Jan  9 12:33:44 2002
	// Mon Jan 12 23:34:22 2002
	public static final String ENTRYLINE_FORMAT = "E MMM d HH:mm:ss yyyy"; //$NON-NLS-1$
	public static final String SERVER_FORMAT = "dd MMM yyyy HH:mm:ss";//$NON-NLS-1$
	
	private static SimpleDateFormat serverFormat = new SimpleDateFormat(SERVER_FORMAT, Locale.US);
	private static SimpleDateFormat entryLineFormat = new SimpleDateFormat(ENTRYLINE_FORMAT, Locale.US);
	
	static {
		serverFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		entryLineFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
	
	static public Date serverStampToDate(String text) throws ParseException {
		// FIXME this cuts the timezone which we do not want
		if (text.indexOf("-") != -1) {//$NON-NLS-1$
			text = text.substring(0,text.indexOf("-"));//$NON-NLS-1$
		}
		return serverFormat.parse(text);
	}

	static public String dateToServerStamp(Date date) {
		return serverFormat.format(date);
	}	
	
	static public Date entryLineToDate(String text) throws ParseException {
		return entryLineFormat.parse(text);
	}

	static public String dateToEntryLine(Date date) {
		return entryLineFormat.format(date);
	}
}