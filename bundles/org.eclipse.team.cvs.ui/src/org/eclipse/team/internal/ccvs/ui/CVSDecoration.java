package org.eclipse.team.internal.ccvs.ui;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.util.List;
import java.util.Map;

public class CVSDecoration {
	
	private String format;
	private Map bindings;
	private List overlays;

	/* package */ CVSDecoration() {
		this(null, null, null);
	}

	/* package */ CVSDecoration(String format, Map bindings, List overlays) {
		setFormat(format);
		setBindings(bindings);
		setOverlays(overlays);
	}
	
	public int hashCode() {
		return overlays.hashCode();
	}
	public boolean equals(Object o) {
		if (!(o instanceof CVSDecoration)) return false;
		return overlays.equals(((CVSDecoration)o).overlays);
	}
	/**
	 * Gets the overlays.
	 * @return Returns a List
	 */
	public List getOverlays() {
		return overlays;
	}

	/**
	 * Sets the overlays.
	 * @param overlays The overlays to set
	 */
	public void setOverlays(List overlays) {
		this.overlays = overlays;
	}

	/**
	 * Gets the substitutions.
	 * @return Returns a String[]
	 */
	public Map getBindings() {
		return bindings;
	}

	/**
	 * Sets the substitutions.
	 * @param substitutions The substitutions to set
	 */
	public void setBindings(Map bindings) {
		this.bindings = bindings;
	}

	/**
	 * Gets the textBinding.
	 * @return Returns a String
	 */
	public String getFormat() {
		return format;
	}

	/**
	 * Sets the textBinding.
	 * @param textBinding The textBinding to set
	 */
	public void setFormat(String format) {
		this.format = format;
	}			
}
