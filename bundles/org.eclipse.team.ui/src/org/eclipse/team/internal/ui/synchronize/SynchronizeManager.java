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
package org.eclipse.team.internal.ui.synchronize;

import java.io.*;
import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.ListenerList;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.internal.ui.registry.SynchronizeParticipantDescriptor;
import org.eclipse.team.internal.ui.registry.SynchronizeParticipantRegistry;
import org.eclipse.team.ui.ITeamUIConstants;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.ui.*;

/**
 * Manages the registered synchronize participants. It handles notification of
 * participant lifecycles, creation of <code>static</code> participants, management
 * of dynamic participants, and the re-creation of persisted participants.
 * <p>
 * A participant is defined in a plugin manifest and can have several properties:
 * - static: means that they always exist and don't have to be added to the manager
 * - dynamic: will be added to the manager at some later time
 * 
 * Part (title, id, icon, composite) - described in plugin.xml (IPartInstance)
 * Can have multiple parts of the same type at runtime -> (IPart)
 *   - must acquire a part (IPartInstance.createPart())
 *   - must released to part when done (IPartInstance.releasePart())
 * Some parts can added dynamically to the registry and events are fired to listeners. Listeners can create the newly added part via
 * the #createPart() method.
 * Parts can be persisted/restored with some state
 *  
 * 
 * 
 * Lifecycle:
 * 	startup -> registry read and stored in a participant instance
 *     createParticipant(id) -> 
 * 	releaseParticipant(IParticipantDescriptor) -> 
 *     getParticipantRegistry -> return IParticipantDescriptors that describe the participants
 * 	shutdown -> persist all settings
 * 
 * @see ISynchronizeView
 * @see ISynchronizeParticipant
 * @since 3.0
 */
public class SynchronizeManager implements ISynchronizeManager {
	/**
	 * Synchronize participants listeners
	 */
	private ListenerList fListeners = null;
	
	/**
	 * Contains the participant descriptions
	 */
	private SynchronizeParticipantRegistry participantRegistry = new SynchronizeParticipantRegistry();
	
	/**
	 * Contains a table of the state saved between sessions for a participant. The set is keyed
	 * as such {String key -> ISynchronizeParticipantReference}.
	 */
	private Map participantReferences = Collections.synchronizedMap(new HashMap(10));

	// change notification constants
	private final static int ADDED = 1;
	private final static int REMOVED = 2;

	// save context constants
	private final static String CTX_PARTICIPANTS = "syncparticipants"; //$NON-NLS-1$
	private final static String CTX_PARTICIPANT = "participant"; //$NON-NLS-1$
	private final static String CTX_ID = "id"; //$NON-NLS-1$
	private final static String CTX_SECONDARY_ID = "secondary_id"; //$NON-NLS-1$
	private final static String CTX_PARTICIPANT_DATA = "data"; //$NON-NLS-1$
	private final static String FILENAME = "syncParticipants.xml"; //$NON-NLS-1$

	/**
	 * Notifies a participant listeners of additions or removals of participant references.
	 */
	class SynchronizeViewPageNotifier implements ISafeRunnable {

		private ISynchronizeParticipantListener fListener;
		private int fType;
		private ISynchronizeParticipantReference[] fChanged;

		public void handleException(Throwable exception) {
			TeamUIPlugin.log(IStatus.ERROR, Policy.bind("SynchronizeManager.7"), exception); //$NON-NLS-1$
		}

		public void run() throws Exception {
			switch (fType) {
				case ADDED :
					fListener.participantsAdded(fChanged);
					break;
				case REMOVED :
					fListener.participantsRemoved(fChanged);
					break;
			}
		}

		/**
		 * Notifies the given listener of the adds/removes
		 * @param participants the participants that changed
		 * @param update the type of change
		 */
		public void notify(ISynchronizeParticipantReference[] participants, int update) {
			if (fListeners == null) {
				return;
			}
			fChanged = participants;
			fType = update;
			Object[] copiedListeners = fListeners.getListeners();
			for (int i = 0; i < copiedListeners.length; i++) {
				fListener = (ISynchronizeParticipantListener) copiedListeners[i];
				Platform.run(this);
			}
			fChanged = null;
			fListener = null;
		}
	}

	/**
	 * Represents a paticipant instance and allows lazy initialization of the instance
	 * only when the participant is required.
	 */
	private class ParticipantInstance implements ISynchronizeParticipantReference {
		private ReferenceCounter counter;
		private IMemento savedState;
		private SynchronizeParticipantDescriptor descriptor;
		private String secondaryId;
		
		public ParticipantInstance(SynchronizeParticipantDescriptor descriptor, String secondaryId, IMemento savedState) {
			this.counter = new ReferenceCounter();
			this.secondaryId = secondaryId;
			this.savedState = savedState;
			this.descriptor = descriptor;
		}
		
		public void save(IMemento memento) {
			String key = getKey(descriptor.getId(), getSecondaryId());
			ISynchronizeParticipant ref = (ISynchronizeParticipant) counter.get(key);
			if(ref != null) {
				ref.saveState(memento);
			} else if(savedState != null) {
				memento.putMemento(savedState);
			}
		}
		
		public boolean equals(Object other) {
			if(other == this) return true;
			if (! (other instanceof ISynchronizeParticipantReference)) return false;
			ISynchronizeParticipantReference otherRef = (ISynchronizeParticipantReference) other;
			String otherSecondaryId = otherRef.getSecondaryId();
			return otherRef.getId().equals(getId()) && Utils.equalObject(getSecondaryId(), otherSecondaryId);
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipantReference#getId()
		 */
		public String getId() {
			return descriptor.getId();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipantReference#getSecondaryId()
		 */
		public String getSecondaryId() {
			return secondaryId;
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipantReference#releasedParticipant()
		 */
		public void releaseParticipant() {
			String key = getKey(descriptor.getId(), getSecondaryId());
			ISynchronizeParticipant ref = (ISynchronizeParticipant) counter.get(key);
			if (ref == null)
				return;
			int count = counter.removeRef(key);
			if (count <= 0) {
				saveState();
				ref.dispose();
			}	
			System.out.println("** release called " + getId() + ":" + count);
		}		

		/* (non-Javadoc)
		 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipantReference#createParticipant()
		 */
		public ISynchronizeParticipant createParticipant() throws TeamException {
			String key = getKey(descriptor.getId(), getSecondaryId());
			ISynchronizeParticipant participant = (ISynchronizeParticipant) counter.get(key);
			int refCount = 1;
			if (participant == null) {
				participant = instantiate();
				if(participant != null)
					counter.put(key, participant);
			} else {
				refCount = counter.addRef(key);
			}
			System.out.println("** create called " + getId() + ":" + refCount);
			return participant;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.team.ui.synchronize.ISynchronizeParticipantReference#getDescriptor()
		 */
		public ISynchronizeParticipantDescriptor getDescriptor() {
			return descriptor;
		}
		
		private ISynchronizeParticipant instantiate() throws TeamException {
			try {
					ISynchronizeParticipant participant = (ISynchronizeParticipant) TeamUIPlugin.createExtension(descriptor.getConfigurationElement(), SynchronizeParticipantDescriptor.ATT_CLASS);
					participant.setInitializationData(descriptor.getConfigurationElement(), null, null);
					participant.init(savedState);
					savedState = null;
					return participant;
				} catch (PartInitException e) {				
					throw new TeamException(Policy.bind("SynchronizeManager.11", descriptor.getName()), e);  //$NON-NLS-1$
				} catch (CoreException e) {
					throw TeamException.asTeamException(e);
				} catch(Exception e) {
					throw new TeamException(Policy.bind("SynchronizeManager.11", descriptor.getName()), e);  //$NON-NLS-1$
				}
			}
	}

	public SynchronizeManager() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.team.ui.sync.ISynchronizeManager#addSynchronizeParticipantListener(org.eclipse.team.ui.sync.ISynchronizeParticipantListener)
	 */
	public void addSynchronizeParticipantListener(ISynchronizeParticipantListener listener) {
		if (fListeners == null) {
			fListeners = new ListenerList(5);
		}
		fListeners.add(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.team.ui.sync.ISynchronizeManager#removeSynchronizeParticipantListener(org.eclipse.team.ui.sync.ISynchronizeParticipantListener)
	 */
	public void removeSynchronizeParticipantListener(ISynchronizeParticipantListener listener) {
		if (fListeners != null) {
			fListeners.remove(listener);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeManager#getParticipantDescriptor(java.lang.String)
	 */
	public ISynchronizeParticipantDescriptor getParticipantDescriptor(String id) {
		return participantRegistry.find(id);
	}
	
	/**
	 * Creates a new participant reference with of the provided type. If the secondayId is specified it
	 * is used as the qualifier for multiple instances of the same type.
	 * <p>
	 * The returned participant reference is a light weight handle describing the participant. The plug-in
	 * defining the participant is not loaded. To instantiate a participant a client must call 
	 * {@link ISynchronizeParticipantReference#createParticipant()} and must call 
	 * {@link ISynchronizeParticipantReference#releaseParticipant()} when finished with the participant.
	 * </p>
	 * @param type the type of the participant
	 * @param secondaryId a unique id for multiple instance support
	 * @return a reference to a participant
	 */
	public ISynchronizeParticipantReference createParticipant(String type, String secondaryId) throws PartInitException {
		SynchronizeParticipantDescriptor desc = participantRegistry.find(type);
		// ensure that the view id is valid
		if (desc == null)
			throw new PartInitException("Could not create participant" + type);
		// ensure that multiple instances are allowed if a secondary id is given
		if (secondaryId != null) {
		    if (!desc.doesAllowMultiple()) {
				throw new PartInitException("Not allowed to create multiple participant instances of this type" + type);
		    }
		}
		String key = getKey(type, secondaryId);
		ISynchronizeParticipantReference ref = (ISynchronizeParticipantReference) participantReferences.get(key);
		if (ref == null) {
			ref = new ParticipantInstance(desc, secondaryId, null);
			participantReferences.put(key, ref);
		}
		return ref;
	}
	
	/**
     * Returns the key to use in the ReferenceCounter.
     * 
     * @param id the primary view id
     * @param secondaryId the secondary view id or <code>null</code>
     * @return the key to use in the ReferenceCounter
     */
    private String getKey(String id, String secondaryId) {
        return secondaryId == null ? id : id + '/' + secondaryId;
    }
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.team.ui.sync.ISynchronizeManager#addSynchronizeParticipants(org.eclipse.team.ui.sync.ISynchronizeParticipant[])
	 */
	public synchronized void addSynchronizeParticipants(ISynchronizeParticipantReference[] participantRefs) {
		// renamed to createSynchronizeParticipant(id)
		List added = new ArrayList(participantRefs.length);
		for (int i = 0; i < participantRefs.length; i++) {
			ISynchronizeParticipantReference participantRef = participantRefs[i];
			String key = getKey(participantRef.getId(), participantRef.getSecondaryId());
			if(! participantReferences.containsKey(key)) {
				participantReferences.put(key, participantRef);
				added.add(participantRef);
			}
		}
		if (!added.isEmpty()) {
			saveState();
			fireUpdate((ISynchronizeParticipantReference[]) added.toArray(new ISynchronizeParticipantReference[added.size()]), ADDED);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.team.ui.sync.ISynchronizeManager#removeSynchronizeParticipants(org.eclipse.team.ui.sync.ISynchronizeParticipant[])
	 */
	public synchronized void removeSynchronizeParticipants(ISynchronizeParticipantReference[] participants) {
		List removed = new ArrayList(participants.length);
		for (int i = 0; i < participants.length; i++) {
			ISynchronizeParticipantReference participant = participants[i];
			String key = getKey(participant.getId(), participant.getSecondaryId());
			if(participantReferences.containsKey(key)) {
				participantReferences.remove(key);
				removed.add(participant);
			}
		}
		if (!removed.isEmpty()) {
			saveState();
			fireUpdate((ISynchronizeParticipantReference[]) removed.toArray(new ISynchronizeParticipantReference[removed.size()]), REMOVED);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeManager#get(java.lang.String)
	 */
	public ISynchronizeParticipantReference get(String id, String secondaryId) {
		String key = getKey(id, secondaryId);
		return (ISynchronizeParticipantReference) participantReferences.get(key);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.team.ui.sync.ISynchronizeManager#getSynchronizeParticipants()
	 */
	public synchronized ISynchronizeParticipantReference[] getSynchronizeParticipants() {
		return (ISynchronizeParticipantReference[]) participantReferences.values().toArray(new ISynchronizeParticipantReference[participantReferences.values().size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeManager#showSynchronizeViewInActivePage()
	 */
	public ISynchronizeView showSynchronizeViewInActivePage() {
		IWorkbench workbench = TeamUIPlugin.getPlugin().getWorkbench();
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();

		boolean switchPerspectives = promptForPerspectiveSwitch();
		IWorkbenchPage activePage = null;
		if(switchPerspectives) {
			try {
				String pId = TeamUIPlugin.getPlugin().getPreferenceStore().getString(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE);
				activePage = workbench.showPerspective(pId, window);
			} catch (WorkbenchException e) {
				Utils.handleError(window.getShell(), e, Policy.bind("SynchronizeView.14"), e.getMessage()); //$NON-NLS-1$
			}
		}
		try {
			if (activePage == null) {
				activePage = TeamUIPlugin.getActivePage();
				if (activePage == null)
					return null;
			}
			IViewPart part = activePage.showView(ISynchronizeView.VIEW_ID);
			try {
				return (ISynchronizeView) part;
			} catch (ClassCastException e) {
				// Strange that we cannot cast the part (see bug 53671)
				TeamUIPlugin.log(IStatus.ERROR, Policy.bind("SynchronizeManager.18", part.getClass().getName()), e); //$NON-NLS-1$
				return null;
			}
		} catch (PartInitException pe) {
			Utils.handleError(window.getShell(), pe, Policy.bind("SynchronizeView.16"), pe.getMessage()); //$NON-NLS-1$
			return null;
		}
	}
	
	/**
	 * Decides what action to take when switching perspectives and showing the synchronize view. Basically there are a
	 * set of user preferences that control how perspective switching.
	 */
	private boolean promptForPerspectiveSwitch() {
		// Decide if a prompt is even required
		String option = TeamUIPlugin.getPlugin().getPreferenceStore().getString(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE);	
		if(option.equals(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE_ALWAYS)) {
			return true;
		} else if(option.equals(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE_NEVER)) {
			return false;
		}
		
		// Otherwise determine if a prompt is required
		IPerspectiveRegistry registry= PlatformUI.getWorkbench().getPerspectiveRegistry();
		String defaultSyncPerspectiveId = TeamUIPlugin.getPlugin().getPreferenceStore().getString(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE);
		IPerspectiveDescriptor perspectiveDescriptor = registry.findPerspectiveWithId(defaultSyncPerspectiveId);
		IWorkbenchPage page = TeamUIPlugin.getActivePage();
		if(page != null) {
			IPerspectiveDescriptor p = page.getPerspective();
			if(p != null && p.getId().equals(defaultSyncPerspectiveId)) {
				// currently in default perspective
				return false;
			}
		}
		
		if(perspectiveDescriptor != null) {
			String perspectiveName = perspectiveDescriptor.getLabel();
			
			MessageDialog m = new MessageDialog(Display.getDefault().getActiveShell(),
						Policy.bind("SynchronizeManager.27"),  //$NON-NLS-1$
						null,	// accept the default window icon
						Policy.bind("SynchronizeManager.30", perspectiveDescriptor.getLabel()), //$NON-NLS-1$
						MessageDialog.QUESTION, 
						new String[] {IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, Policy.bind("SynchronizeManager.29"), Policy.bind("SynchronizeManager.28")}, //$NON-NLS-1$ //$NON-NLS-2$
						0); 	// yes is the default
		
			int result = m.open();
			switch (result) {
				// yes
				case 0 :
					return true;
				// no
				case 1 :
					return false;
				// always
				case 2 :
					TeamUIPlugin.getPlugin().getPreferenceStore().setValue(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE, IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE_ALWAYS);
					return true;
				// never
				case 3 :
					TeamUIPlugin.getPlugin().getPreferenceStore().setValue(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE, IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE_NEVER);
					return false;
			}
		}
		return false;
	}

	/**
	 * Creates the participant registry and restore any saved participants.
	 * Will also instantiate any static participants.
	 */
	public void init() {
		try {
			// Initialize the participant registry - reads all participant
			// extension descriptions.
			participantRegistry.readRegistry(Platform.getPluginRegistry(), TeamUIPlugin.ID, ITeamUIConstants.PT_SYNCPARTICIPANTS);

			// Instantiate and register any dynamic participants saved from a
			// previous session.
			restoreSavedParticipants();

			// Instantiate and register any static participant that has not
			// already been created.
			initializeStaticParticipants();
		} catch (CoreException e) {
			TeamUIPlugin.log(new Status(IStatus.ERROR, TeamUIPlugin.ID, 1, Policy.bind("SynchronizeManager.8"), e)); //$NON-NLS-1$
		}
	}

	/**
	 * Allow participant instances to clean-up.
	 */
	public void dispose() {
		// save state and settings for existing participants.
		saveState();
	}
	
	private void initializeStaticParticipants() throws CoreException {
		SynchronizeParticipantDescriptor[] desc = participantRegistry.getSynchronizeParticipants();
		List participants = new ArrayList();
		for (int i = 0; i < desc.length; i++) {
			SynchronizeParticipantDescriptor descriptor = desc[i];
			String key = getKey(descriptor.getId(), null);
			if (descriptor.isStatic() && !participantReferences.containsKey(key)) {
				participantReferences.put(key, new ParticipantInstance(descriptor, null /* no secondary id */, null /* no saved state */));
			}
		}
	}

	/**
	 * Restores participants that have been saved between sessions.
	 */
	private void restoreSavedParticipants() throws TeamException, CoreException {
		File file = getStateFile();
		Reader reader;
		try {
			reader = new BufferedReader(new FileReader(file));
		} catch (FileNotFoundException e) {
			return;
		}
		List participants = new ArrayList();
		IMemento memento = XMLMemento.createReadRoot(reader);
		IMemento[] participantNodes = memento.getChildren(CTX_PARTICIPANT);
		for (int i = 0; i < participantNodes.length; i++) {
			IMemento memento2 = participantNodes[i];
			String id = memento2.getString(CTX_ID);
			String secondayId = memento2.getString(CTX_SECONDARY_ID);
			SynchronizeParticipantDescriptor desc = participantRegistry.find(id);
			if (desc != null) {
				IConfigurationElement cfgElement = desc.getConfigurationElement();
				String key = getKey(id, secondayId);
				participantReferences.put(key, new ParticipantInstance(desc, secondayId, memento2.getChild(CTX_PARTICIPANT_DATA)));
			} else {
				TeamUIPlugin.log(new Status(IStatus.ERROR, TeamUIPlugin.ID, 1, Policy.bind("SynchronizeManager.9", id), null)); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Saves a file containing the list of participant ids that are registered
	 * with this manager. Each initialized participant is also given the chance to save
	 * it's state.
	 */
	private void saveState() {
		XMLMemento xmlMemento = XMLMemento.createWriteRoot(CTX_PARTICIPANTS);
		List children = new ArrayList();
		for (Iterator it = participantReferences.values().iterator(); it.hasNext(); ) {
			ParticipantInstance ref = (ParticipantInstance) it.next();
			// Participants can opt out of being saved between sessions
			if(! ref.getDescriptor().isPersistent()) continue;					
			// Create the state placeholder for a participant 
			IMemento participantNode = xmlMemento.createChild(CTX_PARTICIPANT);
			participantNode.putString(CTX_ID, ref.getId());	
			String secondaryId = ref.getSecondaryId();
			if(secondaryId != null) {
				participantNode.putString(CTX_SECONDARY_ID,secondaryId);
			}
			IMemento participantData = participantNode.createChild(CTX_PARTICIPANT_DATA);
			ref.save(participantData);
		}
		try {
			Writer writer = new BufferedWriter(new FileWriter(getStateFile()));
			try {
				xmlMemento.save(writer);
			} finally {
				writer.close();
			}
		} catch (IOException e) {
			TeamUIPlugin.log(new Status(IStatus.ERROR, TeamUIPlugin.ID, 1, Policy.bind("SynchronizeManager.10"), e)); //$NON-NLS-1$
		}
	}

	private File getStateFile() {
		IPath pluginStateLocation = TeamUIPlugin.getPlugin().getStateLocation();
		return pluginStateLocation.append(FILENAME).toFile(); //$NON-NLS-1$	
	}
	
	/**
	 * Fires notification.
	 * @param participants participants added/removed
	 * @param type ADD or REMOVE
	 */
	private void fireUpdate(ISynchronizeParticipantReference[] participants, int type) {
		new SynchronizeViewPageNotifier().notify(participants, type);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeManager#getDescriptor()
	 */
	public ISynchronizeParticipantDescriptor getDescriptor(String id) {
		return participantRegistry.find(id);
	}
}