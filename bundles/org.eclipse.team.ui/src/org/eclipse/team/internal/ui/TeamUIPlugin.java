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
package org.eclipse.team.internal.ui;


import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.internal.ui.synchronize.SynchronizeManager;
import org.eclipse.team.internal.ui.synchronize.TeamSynchronizingPerspective;
import org.eclipse.team.internal.ui.synchronize.actions.GlobalRefreshAction;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.ui.*;
import org.eclipse.ui.plugin.AbstractUIPlugin;

/**
 * TeamUIPlugin is the plugin for generic, non-provider specific,
 * team UI functionality in the workbench.
 */
public class TeamUIPlugin extends AbstractUIPlugin {

	private static TeamUIPlugin instance;
	
	// image paths
	public static final String ICON_PATH = "icons/full/"; //$NON-NLS-1$
	
	public static final String ID = "org.eclipse.team.ui"; //$NON-NLS-1$
	
	// plugin id
	public static final String PLUGIN_ID = "org.eclipse.team.ui"; //$NON-NLS-1$
	
	private static List propertyChangeListeners = new ArrayList(5);
	
	private Hashtable imageDescriptors = new Hashtable(20);
	
	/**
	 * Creates a new TeamUIPlugin.
	 * 
	 * @param descriptor  the plugin descriptor
	 */
	public TeamUIPlugin(IPluginDescriptor descriptor) {
		super(descriptor);
		initializeImages(this);
		initializePreferences();
		instance = this;
	}

	/**
	 * Creates an extension.  If the extension plugin has not
	 * been loaded a busy cursor will be activated during the duration of
	 * the load.
	 *
	 * @param element the config element defining the extension
	 * @param classAttribute the name of the attribute carrying the class
	 * @return the extension object
	 */
	public static Object createExtension(final IConfigurationElement element, final String classAttribute) throws CoreException {
		// If plugin has been loaded create extension.
		// Otherwise, show busy cursor then create extension.
		IPluginDescriptor plugin = element.getDeclaringExtension().getDeclaringPluginDescriptor();
		if (plugin.isPluginActivated()) {
			return element.createExecutableExtension(classAttribute);
		} else {
			final Object [] ret = new Object[1];
			final CoreException [] exc = new CoreException[1];
			BusyIndicator.showWhile(null, new Runnable() {
				public void run() {
					try {
						ret[0] = element.createExecutableExtension(classAttribute);
					} catch (CoreException e) {
						exc[0] = e;
					}
				}
			});
			if (exc[0] != null)
				throw exc[0];
			else
				return ret[0];
		}	
	}
	
	/**
	 * Convenience method to get the currently active workbench page. Note that
	 * the active page may not be the one that the usr perceives as active in
	 * some situations so this method of obtaining the activae page should only
	 * be used if no other method is available.
	 * 
	 * @return the active workbench page
	 */
	public static IWorkbenchPage getActivePage() {
		IWorkbenchWindow window = getPlugin().getWorkbench().getActiveWorkbenchWindow();
		if (window == null) return null;
		return window.getActivePage();
	}
	
	/**
	 * Return the default instance of the receiver. This represents the runtime plugin.
	 * 
	 * @return the singleton plugin instance
	 */
	public static TeamUIPlugin getPlugin() {
		// If the instance has not been initialized, we will wait.
		// This can occur if multiple threads try to load the plugin at the same
		// time (see bug 33825: http://bugs.eclipse.org/bugs/show_bug.cgi?id=33825)
		while (instance == null) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// ignore and keep trying
			}
		}
		return instance;
	}
	/**
	 * Initializes the preferences for this plugin if necessary.
	 */
	protected void initializePreferences() {
		IPreferenceStore store = getPreferenceStore();
		store.setDefault(IPreferenceIds.SYNCVIEW_VIEW_SYNCINFO_IN_LABEL, false);
		store.setDefault(IPreferenceIds.SYNCVIEW_COMPRESS_FOLDERS, true);
		store.setDefault(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE, TeamSynchronizingPerspective.ID);
		store.setDefault(IPreferenceIds.SYNCVIEW_VIEW_PROMPT_WHEN_NO_CHANGES, true);
		store.setDefault(IPreferenceIds.SYNCVIEW_VIEW_PROMPT_WITH_CHANGES, true);
		store.setDefault(IPreferenceIds.SYNCVIEW_VIEW_BKG_PROMPT_WHEN_NO_CHANGES, false);
		store.setDefault(IPreferenceIds.SYNCVIEW_VIEW_BKG_PROMPT_WITH_CHANGES, true);
		store.setDefault(IPreferenceIds.SYNCHRONIZING_DEFAULT_PARTICIPANT, GlobalRefreshAction.NO_DEFAULT_PARTICPANT);		
		store.setDefault(IPreferenceIds.SYNCHRONIZING_COMPLETE_SHOW_DIALOG, true);
		store.setDefault(IPreferenceIds.SYNCHRONIZING_SCHEDULED_COMPLETE_SHOW_DIALOG, true);
		store.setDefault(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE, IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE_PROMPT);		 //$NON-NLS-1$
	}
	
	/**
	 * Convenience method for logging statuses to the plugin log
	 * 
	 * @param status  the status to log
	 */
	public static void log(IStatus status) {
		getPlugin().getLog().log(status);
	}
	
	/**
	 * Convenience method for logging a TeamException in such a way that the
	 * stacktrace is logged as well.
	 * @param e
	 */
	public static void log(CoreException e) {
		IStatus status = e.getStatus();
		log (status.getSeverity(), status.getMessage(), e);
	}
	
	/**
	 * Log the given exception along with the provided message and severity indicator
	 */
	public static void log(int severity, String message, Throwable e) {
		log(new Status(severity, ID, 0, message, e));
	}
	
	/**
	 * @see Plugin#startup()
	 */
	public void startup() throws CoreException {
		Policy.localize("org.eclipse.team.internal.ui.messages"); //$NON-NLS-1$
		initializePreferences();		
		IAdapterFactory factory = new TeamAdapterFactory();
		Platform.getAdapterManager().registerAdapters(factory, DiffNode.class);
		((SynchronizeManager)TeamUI.getSynchronizeManager()).init();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugin#shutdown()
	 */
	public void shutdown() throws CoreException {
		super.shutdown();
		((SynchronizeManager)TeamUI.getSynchronizeManager()).dispose();
	}

	/**
	 * Register for changes made to Team properties.
	 */
	public static void addPropertyChangeListener(IPropertyChangeListener listener) {
		propertyChangeListeners.add(listener);
	}
	
	/**
	 * Deregister as a Team property changes.
	 */
	public static void removePropertyChangeListener(IPropertyChangeListener listener) {
		propertyChangeListeners.remove(listener);
	}
	
	/**
	 * Broadcast a Team property change.
	 */
	public static void broadcastPropertyChange(PropertyChangeEvent event) {
		for (Iterator it = propertyChangeListeners.iterator(); it.hasNext();) {
			IPropertyChangeListener listener = (IPropertyChangeListener)it.next();			
			listener.propertyChange(event);
		}
	}
	
	/**
	 * Creates an image and places it in the image registry.
	 * 
	 * @param id  the identifier for the image
	 * @param baseURL  the base URL for the image
	 */
	protected static void createImageDescriptor(TeamUIPlugin plugin, String id, URL baseURL) {
		// Delegate to the plugin instance to avoid concurrent class loading problems
		plugin.privateCreateImageDescriptor(id, baseURL);
	}
	private void privateCreateImageDescriptor(String id, URL baseURL) {
		URL url = null;
		try {
			url = new URL(baseURL, ICON_PATH + id);
		} catch (MalformedURLException e) {
		}
		ImageDescriptor desc = ImageDescriptor.createFromURL(url);
		imageDescriptors.put(id, desc);
	}
	
	/**
	 * Returns the image descriptor for the given image ID.
	 * Returns null if there is no such image.
	 * 
	 * @param id  the identifier for the image to retrieve
	 * @return the image associated with the given ID
	 */
	public static ImageDescriptor getImageDescriptor(String id) {
		// Delegate to the plugin instance to avoid concurrent class loading problems
		return getPlugin().privateGetImageDescriptor(id);
	}
	private ImageDescriptor privateGetImageDescriptor(String id) {
		if(! imageDescriptors.containsKey(id)) {
			URL baseURL = TeamUIPlugin.getPlugin().getDescriptor().getInstallURL();
			createImageDescriptor(getPlugin(), id, baseURL);
		}
		return (ImageDescriptor)imageDescriptors.get(id);
	}	

	/**
	 * Convenience method to get an image descriptor for an extension
	 * 
	 * @param extension  the extension declaring the image
	 * @param subdirectoryAndFilename  the path to the image
	 * @return the image
	 */
	public static ImageDescriptor getImageDescriptorFromExtension(IExtension extension, String subdirectoryAndFilename) {
		IPluginDescriptor pluginDescriptor = extension.getDeclaringPluginDescriptor();
		URL path = pluginDescriptor.getInstallURL();
		URL fullPathString = null;
		try {
			fullPathString = new URL(path,subdirectoryAndFilename);
			return ImageDescriptor.createFromURL(fullPathString);
		} catch (MalformedURLException e) {
		}
		return null;
	}
	/*
	 * Initializes the table of images used in this plugin. The plugin is
	 * provided because this method is called before the plugin staic
	 * variable has been set. See the comment on the getPlugin() method
	 * for a description of why this is required. 
	 */
	private void initializeImages(TeamUIPlugin plugin) {
		URL baseURL = plugin.getDescriptor().getInstallURL();

		// Overlays
		createImageDescriptor(plugin, ISharedImages.IMG_DIRTY_OVR, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_CONFLICT_OVR, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_CHECKEDIN_OVR, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_CHECKEDOUT_OVR, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_ERROR_OVR, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_WARNING_OVR, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_HOURGLASS_OVR, baseURL);
			
		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_VIEW, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_COMPARE_VIEW, baseURL);
		
		// Collapse all
		createImageDescriptor(plugin, ISharedImages.IMG_COLLAPSE_ALL, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_COLLAPSE_ALL_ENABLED, baseURL);
		
		// Target Management Icons
		createImageDescriptor(plugin, ISharedImages.IMG_SITE_ELEMENT, baseURL);
		
		// Sync View Icons
		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_INCOMING, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_OUTGOING, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_CONFLICTING, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_REFRESH, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_CHANGE_FILTER, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_IGNORE_WHITESPACE, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_CONTENTS, baseURL);

		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_INCOMING_DISABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_OUTGOING_DISABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_CONFLICTING_DISABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_REFRESH_DISABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_IGNORE_WHITESPACE_DISABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_CONTENTS_DISABLED, baseURL);

		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_INCOMING_ENABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_OUTGOING_ENABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_DLG_SYNC_CONFLICTING_ENABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_REFRESH_ENABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_IGNORE_WHITESPACE_ENABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_CONTENTS_ENABLED, baseURL);

		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_CATCHUP, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_RELEASE, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_FREE, baseURL);

		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_CATCHUP_DISABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_RELEASE_DISABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_FREE_DISABLED, baseURL);

		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_CATCHUP_ENABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_RELEASE_ENABLED, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_SYNC_MODE_FREE_ENABLED, baseURL);

		createImageDescriptor(plugin, ISharedImages.IMG_WIZBAN_SHARE, baseURL);
		
		// Wizard banners
		createImageDescriptor(plugin, ISharedImages.IMG_PROJECTSET_IMPORT_BANNER, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_PROJECTSET_EXPORT_BANNER, baseURL);	
		
		// Live Sync View icons
		createImageDescriptor(plugin, ISharedImages.IMG_COMPRESSED_FOLDER, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_WARNING, baseURL);
		createImageDescriptor(plugin, ISharedImages.IMG_HIERARCHICAL, baseURL);		
	}

	/**
	 * Returns the standard display to be used. The method first checks, if
	 * the thread calling this method has an associated display. If so, this
	 * display is returned. Otherwise the method returns the default display.
	 */
	public static Display getStandardDisplay() {
		Display display= Display.getCurrent();
		if (display == null) {
			display= Display.getDefault();
		}
		return display;		
	}
	
	public Image getImage(String key) {
		Image image = getImageRegistry().get(key);
		if(image == null) {
			ImageDescriptor d = getImageDescriptor(key);
			image = d.createImage();
			getImageRegistry().put(key, image);
		}
		return image;
	}
	
	public static void run(IRunnableWithProgress runnable) {
		try {
			PlatformUI.getWorkbench().getActiveWorkbenchWindow().run(true, true, runnable);
		} catch (InvocationTargetException e) {
			Utils.handleError(getStandardDisplay().getActiveShell(), e, null, null);
		} catch (InterruptedException e2) {
			// Nothing to be done
		}
	}	
}
