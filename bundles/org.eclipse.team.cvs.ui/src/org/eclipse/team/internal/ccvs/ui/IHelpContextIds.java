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
package org.eclipse.team.internal.ccvs.ui;

/**
 * Here's how to reference the help context in code:
 * 
 * WorkbenchHelp.setHelp(actionOrControl, IHelpContextIds.NAME_DEFIED_BELOW);
 */
public interface IHelpContextIds {
	public static final String PREFIX = CVSUIPlugin.ID + "."; //$NON-NLS-1$

	// Dialogs
	public static final String TAG_CONFIGURATION_OVERVIEW = PREFIX + "tag_configuration_overview"; //$NON-NLS-1$
	public static final String TAG_CONFIGURATION_REFRESHLIST = PREFIX + "tag_configuration_refreshlist"; //$NON-NLS-1$
	public static final String TAG_CONFIGURATION_REFRESHACTION = PREFIX + "tag_configuration_refreshaction"; //$NON-NLS-1$
	public static final String USER_VALIDATION_DIALOG = PREFIX + "user_validation_dialog_context"; //$NON-NLS-1$
	public static final String RELEASE_COMMENT_DIALOG = PREFIX + "release_comment_dialog_context"; //$NON-NLS-1$
	public static final String BRANCH_DIALOG = PREFIX + "branch_dialog_context"; //$NON-NLS-1$
	public static final String PROJECT_SELECTION_DIALOG = PREFIX + "project_selection_dialog_context"; //$NON-NLS-1$
	
	// Different uses of the TagSelectionDialog
	public static final String REPLACE_TAG_SELECTION_DIALOG = PREFIX + "replace_tag_selection_dialog_context"; //$NON-NLS-1$
	public static final String COMPARE_TAG_SELECTION_DIALOG = PREFIX + "compare_tag_selection_dialog_context"; //$NON-NLS-1$
	public static final String TAG_REMOTE_WITH_EXISTING_DIALOG = PREFIX + "tag_remote_with_existing_dialog_context"; //$NON-NLS-1$
	public static final String SHARE_WITH_EXISTING_TAG_SELETION_DIALOG = PREFIX + "share_with_existing_tag_selection_dialog_context"; //$NON-NLS-1$

	// Different uses of the TagAsVersionDialog
	public static final String TAG_AS_VERSION_DIALOG = PREFIX + "tag_as_version_dialog_context"; //$NON-NLS-1$

	// Different uses of InputDialog from actions (not done)
	public static final String DEFINE_BRANCH_DIALOG = PREFIX + "define_branch_dialog_context"; //$NON-NLS-1$
	public static final String DEFINE_VERSION_DIALOG = PREFIX + "define_version_dialog_context"; //$NON-NLS-1$

	// Wizards (no one seems to add wizard help, only wizard page help)
	// (not done)
	public static final String SHARING_WIZARD = PREFIX + "sharing_wizard_context"; //$NON-NLS-1$
	public static final String KEYWORD_SUBSTITUTION_WIZARD = PREFIX + "keyword_substituton_wizard_context"; //$NON-NLS-1$
	public static final String NEW_LOCATION_WIZARD = PREFIX + "new_location_wizard_context"; //$NON-NLS-1$
	public static final String PATCH_WIZARD = PREFIX + "patch_wizard_context"; //$NON-NLS-1$

	// Wizard Pages
	public static final String SHARING_AUTOCONNECT_PAGE = PREFIX + "sharing_autoconnect_page_context"; //$NON-NLS-1$
	public static final String SHARING_SELECT_REPOSITORY_PAGE = PREFIX + "sharing_select_repository_page_context"; //$NON-NLS-1$
	public static final String SHARING_NEW_REPOSITORY_PAGE = PREFIX + "sharing_new_repository_page_context"; //$NON-NLS-1$
	public static final String SHARING_MODULE_PAGE = PREFIX + "sharing_module_page_context"; //$NON-NLS-1$
	public static final String SHARING_FINISH_PAGE = PREFIX + "sharing_finish_page_context"; //$NON-NLS-1$
	public static final String PATCH_SELECTION_PAGE = PREFIX + "patch_selection_page_context"; //$NON-NLS-1$
	public static final String PATCH_OPTIONS_PAGE = PREFIX + "patch_options_page_context"; //$NON-NLS-1$
	public static final String KEYWORD_SUBSTITUTION_SELECTION_PAGE = PREFIX + "keyword_substituton_selection_page_context"; //$NON-NLS-1$
	public static final String KEYWORD_SUBSTITUTION_SUMMARY_PAGE = PREFIX + "keyword_substituton_summary_page_context"; //$NON-NLS-1$
	public static final String KEYWORD_SUBSTITUTION_SHARED_PAGE = PREFIX + "keyword_substituton_shared_page_context"; //$NON-NLS-1$
	public static final String KEYWORD_SUBSTITUTION_CHANGED_PAGE = PREFIX + "keyword_substituton_changed_page_context"; //$NON-NLS-1$
	public static final String MERGE_START_PAGE = PREFIX + "merge_start_page_context"; //$NON-NLS-1$
	public static final String MERGE_END_PAGE = PREFIX + "merge_end_page_context"; //$NON-NLS-1$
	public static final String CHECKOUT_INTO_RESOURCE_SELECTION_PAGE = PREFIX + "checkout_into_resource_selection_page_context"; //$NON-NLS-1$
	public static final String CHECKOUT_INTO_CONFIGURATION_PAGE = PREFIX + "checkout_into_configuration_page_context"; //$NON-NLS-1$
	public static final String RESTORE_FROM_REPOSITORY_FILE_SELECTION_PAGE = PREFIX + "restore_from_repository_file_selection_page_context"; //$NON-NLS-1$
	public static final String WORKING_SET_FOLDER_SELECTION_PAGE = PREFIX + "working_set_folder_selection_page_context"; //$NON-NLS-1$

	// Preference Pages
	public static final String PREF_REPOSITORIES_ARE_BINARY = PREFIX + "repositories_are_binary_pref"; //$NON-NLS-1$
	public static final String PREF_PRUNE = PREFIX + "prune_empty_directories_pref"; //$NON-NLS-1$
	public static final String PREF_QUIET = PREFIX + "quietness_level_pref"; //$NON-NLS-1$
	public static final String PREF_COMPRESSION = PREFIX + "compression_level_pref"; //$NON-NLS-1$
	public static final String PREF_KEYWORDMODE = PREFIX + "default_keywordmode_pref"; //$NON-NLS-1$
	public static final String PREF_COMMS_TIMEOUT = PREFIX + "comms_timeout_pref"; //$NON-NLS-1$
	public static final String PREF_CONSIDER_CONTENT = PREFIX + "consider_content_pref"; //$NON-NLS-1$
	public static final String PREF_MARKERS_ENABLED = PREFIX + "markers_enabled_pref"; //$NON-NLS-1$
	public static final String PREF_REPLACE_DELETE_UNMANAGED = PREFIX + "replace_deletion_of_unmanaged_pref"; //$NON-NLS-1$
	public static final String PREF_SAVE_DIRTY_EDITORS = PREFIX + "save_dirty_editors_pref"; //$NON-NLS-1$
	public static final String PREF_TREAT_NEW_FILE_AS_BINARY = PREFIX + "treat_new_files_as_binary_pref"; //$NON-NLS-1$
	public static final String PREF_DETERMINE_SERVER_VERSION = PREFIX + "determine_server_version"; //$NON-NLS-1$

	public static final String CONSOLE_PREFERENCE_PAGE = PREFIX + "console_preference_page_context"; //$NON-NLS-1$
	public static final String EXT_PREFERENCE_PAGE = PREFIX + "ext_preference_page_context"; //$NON-NLS-1$
	public static final String DECORATORS_PREFERENCE_PAGE = PREFIX + "decorators_preference_page_context"; //$NON-NLS-1$
	public static final String WATCH_EDIT_PREFERENCE_PAGE = PREFIX + "watch_edit_preference_page_context"; //$NON-NLS-1$
	public static final String REPOSITORIES_VIEW_PREFERENCE_PAGE = PREFIX + "repositories_view_preference_page_context"; //$NON-NLS-1$

	// Views
	public static final String CONSOLE_VIEW = PREFIX + "console_view_context"; //$NON-NLS-1$
	public static final String REPOSITORIES_VIEW = PREFIX + "repositories_view_context"; //$NON-NLS-1$
	public static final String RESOURCE_HISTORY_VIEW = PREFIX + "resource_history_view_context"; //$NON-NLS-1$
	public static final String COMPARE_REVISIONS_VIEW = PREFIX + "compare_revision_view_context"; //$NON-NLS-1$

	// Viewers
	public static final String CATCHUP_RELEASE_VIEWER = PREFIX + "catchup_release_viewer_context"; //$NON-NLS-1$

	// Add to .cvsignore dialog
	public static final String ADD_TO_CVSIGNORE = PREFIX + "add_to_cvsignore_context"; //$NON-NLS-1$

	// Actions
	public static final String GET_FILE_REVISION_ACTION = PREFIX + "get_file_revision_action_context"; //$NON-NLS-1$
	public static final String GET_FILE_CONTENTS_ACTION = PREFIX + "get_file_contents_action_context"; //$NON-NLS-1$
	public static final String TAG_WITH_EXISTING_ACTION = PREFIX + "tag_with_existing_action_context"; //$NON-NLS-1$
	public static final String NEW_REPOSITORY_LOCATION_ACTION = PREFIX + "new_repository_location_action_context"; //$NON-NLS-1$
	public static final String NEW_DEV_ECLIPSE_REPOSITORY_LOCATION_ACTION = PREFIX + "new_dev_eclipse repository_location_action_context"; //$NON-NLS-1$
	public static final String SHOW_REMOTE_FOLDERS_ACTION = PREFIX + "show_remote_folders_action_context"; //$NON-NLS-1$
	public static final String SHOW_REMOTE_MODULES_ACTION = PREFIX + "show_remote_modules_action_context"; //$NON-NLS-1$
	public static final String SHOW_COMMENT_IN_HISTORY_ACTION = PREFIX + "show_comment_in_history_action_context"; //$NON-NLS-1$
	public static final String SHOW_TAGS_IN_HISTORY_ACTION = PREFIX + "show_tag_in_history_action_context"; //$NON-NLS-1$

	// Team menu actions
	public static final String TEAM_SYNCHRONIZE_ACTION = PREFIX + "team_synchronize_action_context"; //$NON-NLS-1$
	public static final String TEAM_SYNCHRONIZE_OUTGOING_ACTION = PREFIX + "team_synchronize_outgoing_action_context"; //$NON-NLS-1$
	public static final String TEAM_COMMIT_ACTION = PREFIX + "team_commit_action_context"; //$NON-NLS-1$
	public static final String TEAM_UPDATE_ACTION = PREFIX + "team_update_action_context"; //$NON-NLS-1$
	public static final String TEAM_CREATE_PATCH_ACTION = PREFIX + "team_create_patch_action_context"; //$NON-NLS-1$
	public static final String TEAM_TAG_AS_VERSION_ACTION = PREFIX + "team_tag_as_version_action_context"; //$NON-NLS-1$
	public static final String TEAM_BRANCH_ACTION = PREFIX + "team_branch_action_context"; //$NON-NLS-1$
	public static final String TEAM_MERGE_ACTION = PREFIX + "team_merge_action_context"; //$NON-NLS-1$
	public static final String TEAM_SET_KEYWORD_MODE_ACTION = PREFIX + "team_set_keyword_mode_action_context"; //$NON-NLS-1$
	public static final String TEAM_DISCONNECT_ACTION = PREFIX + "team_disconnect_action_context"; //$NON-NLS-1$
	public static final String TEAM_ADD_ACTION = PREFIX + "team_add_action_context"; //$NON-NLS-1$
	public static final String TEAM_IGNORE_ACTION = PREFIX + "team_ignore_action_context"; //$NON-NLS-1$

	// Sync view menu actions
	public static final String SYNC_COMMIT_ACTION = PREFIX + "sync_commit_action_context"; //$NON-NLS-1$
	public static final String SYNC_FORCED_COMMIT_ACTION = PREFIX + "sync_forced_commit_action_context"; //$NON-NLS-1$
	public static final String SYNC_UPDATE_ACTION = PREFIX + "sync_update_action_context"; //$NON-NLS-1$
	public static final String SYNC_FORCED_UPDATE_ACTION = PREFIX + "sync_forced_update_action_context"; //$NON-NLS-1$
	public static final String SYNC_ADD_ACTION = PREFIX + "sync_add_action_context"; //$NON-NLS-1$
	public static final String SYNC_IGNORE_ACTION = PREFIX + "sync_ignore_action_context"; //$NON-NLS-1$
	public static final String MERGE_UPDATE_ACTION = PREFIX + "merge_update_action_context"; //$NON-NLS-1$
	public static final String MERGE_FORCED_UPDATE_ACTION = PREFIX + "merge_forced_update_action_context"; //$NON-NLS-1$
	public static final String MERGE_UPDATE_WITH_JOIN_ACTION = PREFIX + "merge_update_with_joinaction_context"; //$NON-NLS-1$
	
	// properties pages
	public static final String REPOSITORY_LOCATION_PROPERTY_PAGE = PREFIX + "repository_location_property_page_context"; //$NON-NLS-1$
	public static final String PROJECT_PROPERTY_PAGE = PREFIX + "project_property_page_context"; //$NON-NLS-1$
	public static final String FOLDER_PROPERTY_PAGE = PREFIX + "folder_property_page_context"; //$NON-NLS-1$
	public static final String FILE_PROPERTY_PAGE = PREFIX + "file_property_page_context"; //$NON-NLS-1$
}
