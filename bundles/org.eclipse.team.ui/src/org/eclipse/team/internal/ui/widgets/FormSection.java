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
package org.eclipse.team.internal.ui.widgets;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

public abstract class FormSection implements IPropertyChangeListener {
	public static final int SELECTION = 1;
	private String headerColorKey = FormWidgetFactory.DEFAULT_HEADER_COLOR;
	private String headerText;
	private Control client;
	protected Label header;
	protected Control separator;
	private SectionChangeManager sectionManager;
	private String description;
	private boolean dirty;
	protected Label descriptionLabel;
	private ToggleControl toggle;
	private boolean readOnly;
	private boolean addSeparator = true;
	private boolean descriptionPainted = true;
	private boolean headerPainted = true;
	private boolean collapsable = false;
	private boolean collapsed = false;
	private int widthHint = SWT.DEFAULT;
	private int heightHint = SWT.DEFAULT;
	private Composite control;
	public boolean compactMode=false;

	/*
	 * This is a special layout for the section. Both the
	 * header and the description labels will wrap and
	 * they will use client's size to calculate needed
	 * height. This kind of behaviour is not possible
	 * with stock grid layout.
	 */
	class SectionLayout extends Layout implements ILayoutExtension {
		int vspacing = 3;
		int sepHeight = 2;

		public int getMinimumWidth(Composite parent, boolean flush) {
			return 30;
		}

		public int getMaximumWidth(Composite parent, boolean flush) {
			int maxWidth = 0;
			if (client != null) {
				if (client instanceof Composite) {
					Layout cl = ((Composite) client).getLayout();
					if (cl instanceof ILayoutExtension)
						maxWidth =
							((ILayoutExtension) cl).getMaximumWidth(
								(Composite) client,
								flush);
				}
				if (maxWidth == 0) {
					Point csize =
						client.computeSize(SWT.DEFAULT, SWT.DEFAULT, flush);
					maxWidth = csize.x;
				}
			}
			if (headerPainted && header != null) {
				Point hsize =
					header.computeSize(SWT.DEFAULT, SWT.DEFAULT, flush);
				maxWidth = Math.max(maxWidth, hsize.x);
			}
			if (descriptionPainted && descriptionLabel != null) {
				Point dsize =
					descriptionLabel.computeSize(
						SWT.DEFAULT,
						SWT.DEFAULT,
						flush);
				maxWidth = Math.max(maxWidth, dsize.x);
			}
			return maxWidth;
		}

		protected Point computeSize(
			Composite parent,
			int wHint,
			int hHint,
			boolean flush) {
			int width = 0;
			int height = 0;
			int cwidth = 0;
			int collapsedHeight = 0;

			if (wHint != SWT.DEFAULT)
				width = wHint;
			if (hHint != SWT.DEFAULT)
				height = hHint;

			cwidth = width;

			if (client != null && !client.isDisposed()) {
				if (toggle != null && toggle.getSelection() && compactMode) {
				}
				else {
				//Point csize = client.computeSize(SWT.DEFAULT, SWT.DEFAULT, flush);
				Point csize = client.computeSize(wHint, SWT.DEFAULT);
				if (width == 0) {
					width = csize.x;
					cwidth = width;
				}
				if (height == 0)
					height = csize.y;
				}
			}

			Point toggleSize = null;

			if (collapsable && toggle != null)
				toggleSize =
					toggle.computeSize(SWT.DEFAULT, SWT.DEFAULT, flush);

			if (hHint == SWT.DEFAULT && headerPainted && header != null) {
				int hwidth = cwidth;
				if (toggleSize != null)
					hwidth = cwidth - toggleSize.x - 5;
				Point hsize = header.computeSize(hwidth, SWT.DEFAULT, flush);
				height += hsize.y;
				collapsedHeight = hsize.y;
				height += vspacing;
			}

			if (hHint == SWT.DEFAULT && addSeparator) {
				height += sepHeight;
				height += vspacing;
				collapsedHeight += vspacing + sepHeight;
			}
			if (hHint == SWT.DEFAULT
				&& descriptionPainted
				&& descriptionLabel != null) {
				Point dsize =
					descriptionLabel.computeSize(cwidth, SWT.DEFAULT, flush);
				height += dsize.y;
				height += vspacing;
			}
			if (toggle != null && toggle.getSelection()) {
				// collapsed state
				height = collapsedHeight;
			}
			return new Point(width, height);
		}
		protected void layout(Composite parent, boolean flush) {
			int width = parent.getClientArea().width;
			int height = parent.getClientArea().height;
			int y = 0;
			Point toggleSize = null;

			if (collapsable) {
				toggleSize =
					toggle.computeSize(SWT.DEFAULT, SWT.DEFAULT, flush);
			}
			if (headerPainted && header != null) {
				Point hsize;

				int availableWidth = width;
				if (toggleSize != null)
					availableWidth = width - toggleSize.x - 5;
				hsize = header.computeSize(availableWidth, SWT.DEFAULT, flush);
				int hx = 0;
				if (toggle != null) {
					int ty = y + hsize.y - toggleSize.y;
					toggle.setBounds(0, ty, toggleSize.x, toggleSize.y);
					hx = toggleSize.x; // + 5;
				}
				header.setBounds(hx, y, availableWidth, hsize.y);

				y += hsize.y + vspacing;
			}
			if (addSeparator && separator != null) {
				separator.setBounds(0, y, width, 2);
				y += sepHeight + vspacing;
			}
			if (toggle != null && toggle.getSelection()) {
				return;
			}
			if (descriptionPainted && descriptionLabel != null) {
				Point dsize =
					descriptionLabel.computeSize(width, SWT.DEFAULT, flush);
				descriptionLabel.setBounds(0, y, width, dsize.y);
				y += dsize.y + vspacing;
			}
			if (client != null) {
				client.setBounds(0, y, width, height - y);
			}
		}
	}

	/**
	 * @return
	 */
	public boolean isCompactMode() {
		return compactMode;
	}

	/**
	 * @param compactMode
	 */
	public void setCompactMode(boolean compactMode) {
		this.compactMode = compactMode;
	}

	public FormSection() {
		/*
		// Description causes problems re word wrapping
		// and causes bad layout in schema and
		// feature editors when in Motif - turning off
		if (SWT.getPlatform().equals("motif")) {
			descriptionPainted = false;
		}
		*/
		JFaceResources.getFontRegistry().addListener(this);
	}
	public void commitChanges(boolean onSave) {
	}
	public abstract Composite createClient(
		Composite parent,
		FormWidgetFactory factory);
	public final Control createControl(
		Composite parent,
		final FormWidgetFactory factory) {
		Composite section = factory.createComposite(parent);
		SectionLayout slayout = new SectionLayout();
		section.setLayout(slayout);
		section.setData(this);

		if (headerPainted) {
			Color headerColor = factory.getColor(getHeaderColorKey());
			header =
				factory.createHeadingLabel(
					section,
					getHeaderText(),
					headerColor,
					SWT.WRAP);
			if (collapsable) {
				toggle = new ToggleControl(section, SWT.NULL);
				toggle.setSelection(collapsed);
				toggle.setBackground(factory.getBackgroundColor());
				toggle.setActiveDecorationColor(factory.getHyperlinkColor());
				toggle.setDecorationColor(
					factory.getColor(
						FormWidgetFactory.COLOR_COMPOSITE_SEPARATOR));
				toggle.setActiveCursor(factory.getHyperlinkCursor());
				toggle.addFocusListener(factory.visibilityHandler);
				toggle.addKeyListener(factory.keyboardHandler);
				toggle.addSelectionListener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						doToggle();
					}
				});
				header.addMouseListener(new MouseAdapter() {
					public void mouseDown(MouseEvent e) {
						toggle.setSelection(!toggle.getSelection());
						toggle.redraw();
						doToggle();
					}
				});
				header.addMouseTrackListener(new MouseTrackAdapter() {
					public void mouseEnter(MouseEvent e) {
						header.setCursor(factory.getHyperlinkCursor());
					}
					public void mouseExit(MouseEvent e) {
						header.setCursor(null);
					}
				});
			}
		}

		if (addSeparator) {
			//separator = factory.createSeparator(section, SWT.HORIZONTAL);
			separator = factory.createCompositeSeparator(section);
		}

		if (descriptionPainted && description != null) {
			descriptionLabel =
				factory.createLabel(section, description, SWT.WRAP);
		}
		client = createClient(section, factory);
		section.setData(this);
		control = section;
		return section;
	}

	private void doToggle() {
		collapsed = toggle.getSelection();
		reflow();
		if (descriptionLabel != null)
			descriptionLabel.setVisible(!collapsed);
		if (client != null)
			client.setVisible(!collapsed);
	}

	protected void reflow() {
		control.setRedraw(false);
		control.getParent().setRedraw(false);
		control.layout(true);
		control.getParent().layout(true);
		control.setRedraw(true);
		control.getParent().setRedraw(true);
	}

	protected Text createText(
		Composite parent,
		String label,
		FormWidgetFactory factory) {
		return createText(parent, label, factory, 1);
	}
	protected Text createText(
		Composite parent,
		String label,
		FormWidgetFactory factory,
		int span) {
		factory.createLabel(parent, label);
		Text text = factory.createText(parent, "");
		int hfill =
			span == 1
				? GridData.FILL_HORIZONTAL
				: GridData.HORIZONTAL_ALIGN_FILL;
		GridData gd = new GridData(hfill | GridData.VERTICAL_ALIGN_CENTER);
		gd.horizontalSpan = span;
		text.setLayoutData(gd);
		return text;
	}
	protected Text createText(
		Composite parent,
		String label,
		FormWidgetFactory factory,
		int span,
		int style) {
		Label l = factory.createLabel(parent, label);
		if ((style & SWT.MULTI) != 0) {
			GridData gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
			l.setLayoutData(gd);
		}
		Text text = factory.createText(parent, "", style);
		int hfill =
			span == 1
				? GridData.FILL_HORIZONTAL
				: GridData.HORIZONTAL_ALIGN_FILL;
		GridData gd = new GridData(hfill | GridData.VERTICAL_ALIGN_CENTER);
		gd.horizontalSpan = span;
		text.setLayoutData(gd);
		return text;
	}
	protected Text createText(
		Composite parent,
		FormWidgetFactory factory,
		int span) {
		Text text = factory.createText(parent, "");
		int hfill =
			span == 1
				? GridData.FILL_HORIZONTAL
				: GridData.HORIZONTAL_ALIGN_FILL;
		GridData gd = new GridData(hfill | GridData.VERTICAL_ALIGN_CENTER);
		//gd.grabExcessHorizontalSpace = true;
		gd.horizontalSpan = span;
		text.setLayoutData(gd);
		return text;
	}
	public void dispose() {
		JFaceResources.getFontRegistry().removeListener(this);
	}
	public boolean doGlobalAction(String actionId) {
		return false;
	}
	public void expandTo(Object object) {
	}
	public final void fireChangeNotification(
		int changeType,
		Object changeObject) {
		if (sectionManager == null)
			return;
		sectionManager.dispatchNotification(this, changeType, changeObject);
	}
	public final void fireSelectionNotification(Object changeObject) {
		fireChangeNotification(SELECTION, changeObject);
	}
	public java.lang.String getDescription() {
		return description;
	}
	public java.lang.String getHeaderColorKey() {
		return headerColorKey;
	}
	public java.lang.String getHeaderText() {
		return headerText;
	}
	public int getHeightHint() {
		return heightHint;
	}
	public int getWidthHint() {
		return widthHint;
	}
	public void initialize(Object input) {
	}
	public boolean isAddSeparator() {
		return addSeparator;
	}
	public boolean isDescriptionPainted() {
		return descriptionPainted;
	}
	public boolean isDirty() {
		return dirty;
	}
	public boolean isHeaderPainted() {
		return headerPainted;
	}
	public boolean isReadOnly() {
		return readOnly;
	}
	public void sectionChanged(
		FormSection source,
		int changeType,
		Object changeObject) {
	}
	public void setAddSeparator(boolean newAddSeparator) {
		addSeparator = newAddSeparator;
	}

	private String trimNewLines(String text) {
		StringBuffer buff = new StringBuffer();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n')
				buff.append(' ');
			else
				buff.append(c);
		}
		return buff.toString();
	}

	public void setDescription(java.lang.String newDescription) {
		// we will trim the new lines so that we can
		// use layout-based word wrapping instead
		// of hard-coded one
		description = trimNewLines(newDescription);
		//description = newDescription;
		if (descriptionLabel != null)
			descriptionLabel.setText(newDescription);
	}
	public void setDescriptionPainted(boolean newDescriptionPainted) {
		descriptionPainted = newDescriptionPainted;
	}
	public void setDirty(boolean newDirty) {
		dirty = newDirty;
	}
	public void setFocus() {
		if (toggle != null)
			toggle.setFocus();
	}
	public void setHeaderColorKey(java.lang.String newHeaderColorKey) {
		headerColorKey = newHeaderColorKey;
	}
	public void setHeaderPainted(boolean newHeaderPainted) {
		headerPainted = newHeaderPainted;
	}
	public void setHeaderText(java.lang.String newHeaderText) {
		headerText = newHeaderText;
		if (header != null)
			header.setText(headerText);
	}
	public void setHeightHint(int newHeightHint) {
		heightHint = newHeightHint;
	}
	void setManager(SectionChangeManager manager) {
		this.sectionManager = manager;
	}
	public void setReadOnly(boolean newReadOnly) {
		readOnly = newReadOnly;
	}

	public void setWidthHint(int newWidthHint) {
		widthHint = newWidthHint;
	}

	public void update() {
	}

	public void propertyChange(PropertyChangeEvent arg0) {
		if (control != null && header != null && !control.isDisposed() && !header.isDisposed()) {
			header.setFont(JFaceResources.getBannerFont());
			control.layout(true);
		}
	}

	/**
	 * Gets the collapsable.
	 * @return Returns a boolean
	 */
	public boolean getCollapsable() {
		return collapsable;
	}

	/**
	 * Sets the collapsable.
	 * @param collapsable The collapsable to set
	 */
	public void setCollapsable(boolean collapsable) {
		this.collapsable = collapsable;
	}

	public void setCollapsed(boolean collapsed) {
		this.collapsed = collapsed;
	}
	
	public boolean isCollapsed() {
		return collapsed;
	}

	public boolean canPaste(Clipboard clipboard) {
		return false;
	}
}
