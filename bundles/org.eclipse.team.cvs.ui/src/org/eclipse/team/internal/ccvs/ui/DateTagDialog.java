/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ui.dialogs.DialogArea;

/**
 * Dialog for obtaining a date from the user
 */
public class DateTagDialog extends Dialog {

	DateArea dateArea;
	TimeArea timeArea;
	IDialogSettings settings;
	private Date dateEntered;

	public class DateArea extends DialogArea {

		private Combo fromDayCombo;
		private Combo fromMonthCombo;
		private Combo fromYearCombo;
		
		public DateArea(Dialog parentDialog, IDialogSettings settings) {
			super(parentDialog, settings);
		}

		public Control createArea(Composite parent) {
			Composite composite = createComposite(parent, 4);
			initializeDialogUnits(composite);
			createLabel(composite, "Date (M/D/Y):", 1);
			fromMonthCombo = new Combo(composite, SWT.READ_ONLY);
			fromDayCombo = new Combo(composite, SWT.READ_ONLY);
			fromYearCombo = new Combo(composite, SWT.NONE);
			fromYearCombo.setTextLimit(4);
			
			//set day, month and year combos with numbers
			//years allows a selection from the past 5 years
			//or any year written in
			String days[] = new String[31];
			for (int i = 0; i < 31; i++) {
				days[i] = String.valueOf(i);
			}

			String months[] = new String[12];
			SimpleDateFormat format = new SimpleDateFormat("MMMM"); //$NON-NLS-1$
			Calendar calendar = Calendar.getInstance();
			for (int i = 0; i < 12; i++) {
				calendar.set(Calendar.MONTH, i);
				months[i] = format.format(calendar.getTime());
			}

			String years[] = new String[5];
			Calendar calender = Calendar.getInstance();
			for (int i = 0; i < 5; i++) {
				years[i] = String.valueOf(calender.get(1) - i);
			}
			fromDayCombo.setItems(days);
			fromMonthCombo.setItems(months);
			fromYearCombo.setItems(years);
			return composite;
		}
		
		public void initializeValues(Calendar calendar ) {
			fromDayCombo.select(calendar.get(Calendar.DATE) - 1);
			fromMonthCombo.select(calendar.get(Calendar.MONTH));
			String yearValue = String.valueOf(calendar.get(Calendar.YEAR));
			int index = fromYearCombo.indexOf(yearValue);
			if (index == -1) {
				fromYearCombo.add(yearValue);
				index = fromYearCombo.indexOf(yearValue);
			}
			fromYearCombo.select(index);
			timeArea.initializeValues(calendar);
		}

		public void updateWidgetEnablements() {
			// Do nothing
		}
		
		public void adjustCalendar(Calendar calendar) {
			calendar.set(
					Integer.parseInt(String.valueOf(fromYearCombo.getText())),
					fromMonthCombo.getSelectionIndex(),
					Integer.parseInt(String.valueOf(fromDayCombo.getText())),
					00, 00, 00);
		}
	}
	public class TimeArea extends DialogArea {

		private Combo hourCombo;
		private Combo minuteCombo;
		private Combo secondCombo;
		private Button includeTime, localTime, utcTime;
		
		public TimeArea(Dialog parentDialog, IDialogSettings settings) {
			super(parentDialog, settings);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.team.internal.ui.dialogs.DialogArea#createArea(org.eclipse.swt.widgets.Composite)
		 */
		public Control createArea(Composite parent) {
			Composite composite = createComposite(parent, 2);
			initializeDialogUnits(composite);
			includeTime = createCheckbox(composite, "Include time component in tag", 2);
			createLabel(composite, "Time (HH:MM:SS):", 1);
			Composite dateComposite = new Composite(composite, SWT.NONE);
			GridLayout dateLayout = new GridLayout();
			dateLayout.numColumns = 3;
			dateComposite.setLayout(dateLayout);
			hourCombo = new Combo(dateComposite, SWT.READ_ONLY);
			minuteCombo = new Combo(dateComposite, SWT.READ_ONLY);
			secondCombo = new Combo(dateComposite, SWT.READ_ONLY);
			localTime = createRadioButton(composite, "Time is local", 2);
			utcTime = createRadioButton(composite, "Time is in universal time coordinates (UTC)", 2);
			
			String sixty[] = new String[60];
			for (int i = 0; i < 60; i++) {
				sixty[i] = String.valueOf(i);
			}
			String hours[] = new String[24];
			for (int i = 0; i < 24; i++) {
				hours[i] = String.valueOf(i);
			}
			hourCombo.setItems(hours);
			minuteCombo.setItems(sixty);
			secondCombo.setItems(sixty);
			
			includeTime.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					updateWidgetEnablements();
				}
			});
			return composite;
		}
		
		public void initializeValues(Calendar calendar) {
			hourCombo.select(calendar.get(Calendar.HOUR));
			minuteCombo.select(calendar.get(Calendar.MINUTE));
			secondCombo.select(calendar.get(Calendar.SECOND));
			
			includeTime.setSelection(settings.getBoolean("includeTime")); // TODO: persist settings
			localTime.setSelection(!settings.getBoolean("utcTime"));
			utcTime.setSelection(settings.getBoolean("utcTime"));
		}
		public void updateWidgetEnablements() {
			hourCombo.setEnabled(includeTime.getSelection());
			minuteCombo.setEnabled(includeTime.getSelection());
			secondCombo.setEnabled(includeTime.getSelection());
			localTime.setEnabled(includeTime.getSelection());
			utcTime.setEnabled(includeTime.getSelection());
		}
		public void adjustCalendar(Calendar calendar) {
			if (includeTime.getSelection()) {
				calendar.set(Calendar.HOUR, hourCombo.getSelectionIndex());
				calendar.set(Calendar.MINUTE, minuteCombo.getSelectionIndex());
				calendar.set(Calendar.SECOND, secondCombo.getSelectionIndex());
				if (utcTime.getSelection()) {
					calendar.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
				}
			}
		}
	}
	
	public DateTagDialog(Shell parentShell) {
		super(parentShell);
		IDialogSettings workbenchSettings = CVSUIPlugin.getPlugin().getDialogSettings();
		this.settings = workbenchSettings.getSection("DateTagDialog");//$NON-NLS-1$
		if (this.settings == null) {
			this.settings = workbenchSettings.addNewSection("DateTagDialog");//$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	protected Control createDialogArea(Composite parent) {
		Composite topLevel = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		initializeDialogUnits(topLevel);
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		topLevel.setLayout(layout);
		
		createDateArea(topLevel);
		createTimeArea(topLevel);
		initializeValues();
		updateWidgetEnablements();
		
		// set F1 help
		//WorkbenchHelp.setHelp(topLevel, IHelpContextIds.DATE_TAG_DIALOG);
		Dialog.applyDialogFont(parent);
		return topLevel;
	}

	private void createDateArea(Composite topLevel) {
		dateArea = new DateArea(this, settings);
		dateArea.createArea(topLevel);
	}

	private void createTimeArea(Composite topLevel) {
		timeArea = new TimeArea(this, settings);
		timeArea.createArea(topLevel);
	}

	private void initializeValues() {
		Calendar calendar = Calendar.getInstance();
		dateArea.initializeValues(calendar);
		timeArea.initializeValues(calendar);
	}
	
	private void updateWidgetEnablements() {
		timeArea.updateWidgetEnablements();
		dateArea.updateWidgetEnablements();
	}

	/**
	 * Return the date specified by the user in UTC.
	 * @return the date specified by the user
	 */
	public Date getDate() {
		return dateEntered;
	}
	
	private Date privateGetDate() {
		Calendar calendar = Calendar.getInstance();
		dateArea.adjustCalendar(calendar);
		timeArea.adjustCalendar(calendar);
		return calendar.getTime();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#buttonPressed(int)
	 */
	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.OK_ID) {
			dateEntered = privateGetDate();
		}
		super.buttonPressed(buttonId);
	}

}
