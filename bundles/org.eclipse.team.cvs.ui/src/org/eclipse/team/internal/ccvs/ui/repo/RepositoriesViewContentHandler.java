/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.repo;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class RepositoriesViewContentHandler extends DefaultHandler {

	public static final String REPOSITORIES_VIEW_TAG = "repositories-view"; //$NON-NLS-1$

	public static final String REPOSITORY_TAG = "repository"; //$NON-NLS-1$
	public static final String MODULE_TAG = "module"; //$NON-NLS-1$
	public static final String TAG_TAG = "tag"; //$NON-NLS-1$
	public static final String AUTO_REFRESH_FILE_TAG = "auto-refresh-file"; //$NON-NLS-1$
	
	public static final String ID_ATTRIBUTE = "id"; //$NON-NLS-1$
	public static final String NAME_ATTRIBUTE = "name"; //$NON-NLS-1$
	public static final String PATH_ATTRIBUTE = "path"; //$NON-NLS-1$
	public static final String TYPE_ATTRIBUTE = "type"; //$NON-NLS-1$
	public static final String REPOSITORY_PROGRAM_NAME_ATTRIBUTE = "program-name"; //$NON-NLS-1$
	
	public static final String[] TAG_TYPES = {"head", "branch", "version", "date"};
	public static final String DEFAULT_TAG_TYPE = "version";
	
	private RepositoryManager manager;
	private StringBuffer buffer = new StringBuffer();
	private Stack tagStack = new Stack();
	private RepositoryRoot currentRepositoryRoot;
	private String currentRemotePath;
	private List tags;
	private List autoRefreshFiles;

	public RepositoriesViewContentHandler(RepositoryManager manager) {
		this.manager = manager;
	}
	
	/**
	 * @see ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] chars, int startIndex, int length) throws SAXException {
		buffer.append(chars, startIndex, length);
	}

	/**
	 * @see ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
			
		if (!localName.equals(tagStack.peek())) {
			throw new SAXException(Policy.bind("RepositoriesViewContentHandler.unmatchedTag", localName)); //$NON-NLS-1$
		}
		
		if (localName.equals(REPOSITORIES_VIEW_TAG)) {
			// all done
		} else if (localName.equals(REPOSITORY_TAG)) {
			manager.add(currentRepositoryRoot);
			currentRepositoryRoot = null;
		} else if (localName.equals(MODULE_TAG)) {
			currentRepositoryRoot.addTags(currentRemotePath, 
				(CVSTag[]) tags.toArray(new CVSTag[tags.size()]));
			currentRepositoryRoot.setAutoRefreshFiles(currentRemotePath,
				(String[]) autoRefreshFiles.toArray(new String[autoRefreshFiles.size()]));
		}
		tagStack.pop();
	}
		
	/**
	 * @see ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(
			String namespaceURI,
			String localName,
			String qName,
			Attributes atts)
			throws SAXException {
			
		if (localName.equals(REPOSITORIES_VIEW_TAG)) {
			// just started
		} else if (localName.equals(REPOSITORY_TAG)) {
			String id = atts.getValue(ID_ATTRIBUTE);
			if (id == null) {
				throw new SAXException(Policy.bind("RepositoriesViewContentHandler.missingAttribute", REPOSITORY_TAG, ID_ATTRIBUTE));
			}
			ICVSRepositoryLocation root;
			try {
				root = CVSProviderPlugin.getPlugin().getRepository(id);
			} catch (CVSException e) {
				throw new SAXException(Policy.bind("RepositoriesViewContentHandler.errorCreatingRoot", id), e);
			}
			currentRepositoryRoot = new RepositoryRoot(root);
			String name = atts.getValue(NAME_ATTRIBUTE);
			if (name != null) {
				currentRepositoryRoot.setName(name);
			}
		} else if (localName.equals(MODULE_TAG)) {
			String path = atts.getValue(PATH_ATTRIBUTE);
			if (path == null) {
				throw new SAXException(Policy.bind("RepositoriesViewContentHandler.missingAttribute", MODULE_TAG, PATH_ATTRIBUTE));
			}
			startModule(path);
		} else if (localName.equals(TAG_TAG)) {
			String type = atts.getValue(TYPE_ATTRIBUTE);
			if (type != null) {
				type = DEFAULT_TAG_TYPE;
			}
			String name = atts.getValue(NAME_ATTRIBUTE);
			if (name == null) {
				throw new SAXException(Policy.bind("RepositoriesViewContentHandler.missingAttribute", TAG_TAG, NAME_ATTRIBUTE));
			}
			tags.add(new CVSTag(name, getCVSTagType(type)));
		} else if (localName.equals(AUTO_REFRESH_FILE_TAG)) {
			String path = atts.getValue(PATH_ATTRIBUTE);
			if (path == null) {
				throw new SAXException(Policy.bind("RepositoriesViewContentHandler.missingAttribute", AUTO_REFRESH_FILE_TAG, PATH_ATTRIBUTE));
			}
			autoRefreshFiles.add(path);
		}
		// empty buffer
		buffer = new StringBuffer();
		tagStack.push(localName);
	}

	private void startModule(String path) {
		currentRemotePath = path;
		tags = new ArrayList();
		autoRefreshFiles = new ArrayList();
	}
	
	/**
	 * Method getCVSTagType.
	 * @param type
	 */
	public int getCVSTagType(String type) {
		for (int i = 0; i < TAG_TYPES.length; i++) {
			if (TAG_TYPES[i].equals(type))
				return i;
		}
		return CVSTag.VERSION;
	}
	
}
