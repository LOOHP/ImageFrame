/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2025. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2025. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.imageframe.language;

import com.loohp.imageframe.objectholders.IFPlayerPreference;
import com.loohp.imageframe.objectholders.ImageMapAccessPermissionType;
import com.loohp.imageframe.objectholders.PreferenceState;

public class TranslationKey {

    public static final String RELOADED = "imageframe.messages.reloaded";
    public static final String RESYNC = "imageframe.messages.resync";
    public static final String STORAGE_MIGRATION = "imageframe.messages.storage_migration";
    public static final String IMAGE_MAP_PROCESSING = "imageframe.messages.image_map_processing";
    public static final String IMAGE_MAP_PROCESSING_ACTION_BAR = "imageframe.messages.image_map_processing_action_bar";
    public static final String IMAGE_MAP_QUEUED_ACTION_BAR = "imageframe.messages.image_map_queued_action_bar";
    public static final String IMAGE_MAP_CREATED = "imageframe.messages.image_map_created";
    public static final String IMAGE_MAP_REFRESHED = "imageframe.messages.image_map_refreshed";
    public static final String IMAGE_MAP_DELETED = "imageframe.messages.image_map_deleted";
    public static final String IMAGE_MAP_RENAMED = "imageframe.messages.image_map_renamed";
    public static final String IMAGE_MAP_TOGGLE_PAUSED = "imageframe.messages.image_map_toggle_paused";
    public static final String IMAGE_MAP_PLAYBACK_JUMP_TO = "imageframe.messages.image_map_playback_jump_to";
    public static final String IMAGE_MAP_PLAYER_PURGE = "imageframe.messages.image_map_player_purge";
    public static final String SET_CREATOR = "imageframe.messages.set_creator";
    public static final String INVALID_OVERLAY_MAP = "imageframe.messages.invalid_overlay_map";
    public static final String IMAGE_MAP_ALREADY_QUEUED = "imageframe.messages.image_map_already_queued";
    public static final String UNABLE_TO_LOAD_MAP = "imageframe.messages.unable_to_load_map";
    public static final String UNABLE_TO_CHANGE_IMAGE_TYPE = "imageframe.messages.unable_to_change_image_type";
    public static final String UNKNOWN_ERROR = "imageframe.messages.unknown_error";
    public static final String IMAGE_OVER_MAX_FILE_SIZE = "imageframe.messages.image_over_max_file_size";
    public static final String NOT_AN_IMAGE_MAP = "imageframe.messages.not_an_image_map";
    public static final String UPLOAD_LINK = "imageframe.messages.upload_link";
    public static final String UPLOAD_EXPIRED = "imageframe.messages.upload_expired";

    public static final String URL_IMAGE_MAP_INFO_1 = "imageframe.messages.url_image_map_info.1";
    public static final String URL_IMAGE_MAP_INFO_2 = "imageframe.messages.url_image_map_info.2";
    public static final String URL_IMAGE_MAP_INFO_3 = "imageframe.messages.url_image_map_info.3";
    public static final String URL_IMAGE_MAP_INFO_4 = "imageframe.messages.url_image_map_info.4";
    public static final String URL_IMAGE_MAP_INFO_5 = "imageframe.messages.url_image_map_info.5";
    public static final String URL_IMAGE_MAP_INFO_6 = "imageframe.messages.url_image_map_info.6";
    public static final String URL_IMAGE_MAP_INFO_7 = "imageframe.messages.url_image_map_info.7";
    public static final String URL_IMAGE_MAP_INFO_8 = "imageframe.messages.url_image_map_info.8";
    public static final String URL_IMAGE_MAP_INFO_9 = "imageframe.messages.url_image_map_info.9";

    public static final String NO_PERMISSION = "imageframe.messages.no_permission";
    public static final String NO_CONSOLE = "imageframe.messages.no_console";
    public static final String PLAYER_NOT_FOUND = "imageframe.messages.player_not_found";
    public static final String INVALID_USAGE = "imageframe.messages.invalid_usage";
    public static final String NOT_ENOUGH_MAPS = "imageframe.messages.not_enough_maps";
    public static final String OVERSIZE = "imageframe.messages.oversize";
    public static final String URL_RESTRICTED = "imageframe.messages.url_restricted";
    public static final String PLAYER_CREATION_LIMIT_REACHED = "imageframe.messages.player_creation_limit_reached";
    public static final String DUPLICATE_MAP_NAME = "imageframe.messages.duplicate_map_name";
    public static final String MAP_LOOKUP = "imageframe.messages.map_lookup";
    public static final String ITEM_FRAME_OCCUPIED = "imageframe.messages.item_frame_occupied";
    public static final String NOT_ENOUGH_SPACE = "imageframe.messages.not_enough_space";
    public static final String INVALID_IMAGE_MAP = "imageframe.messages.invalid_image_map";
    public static final String GIVEN_INVISIBLE_FRAME = "imageframe.messages.given_invisible_frame";

    public static final String ACCESS_UPDATED = "imageframe.messages.access_permission.updated";
    public static String ACCESS_TYPE(ImageMapAccessPermissionType type) {
        String key = type == null ? "none" : type.name().toLowerCase();
        return "imageframe.messages.access_permission.types." + key;
    }

    public static final String SELECTION_BEGIN = "imageframe.messages.selection.begin";
    public static final String SELECTION_CLEAR = "imageframe.messages.selection.clear";
    public static final String SELECTION_CORNER1 = "imageframe.messages.selection.corner1";
    public static final String SELECTION_CORNER2 = "imageframe.messages.selection.corner2";
    public static final String SELECTION_INVALID = "imageframe.messages.selection.invalid";
    public static final String SELECTION_OVERSIZE = "imageframe.messages.selection.oversize";
    public static final String SELECTION_SUCCESS = "imageframe.messages.selection.success";
    public static final String SELECTION_NO_SELECTION = "imageframe.messages.selection.no_selection";
    public static final String SELECTION_INCORRECT_SIZE = "imageframe.messages.selection.incorrect_size";

    public static final String MARKERS_ADD_BEGIN = "imageframe.messages.markers.add_begin";
    public static final String MARKERS_ADD_CONFIRM = "imageframe.messages.markers.add_confirm";
    public static final String MARKERS_REMOVE = "imageframe.messages.markers.remove";
    public static final String MARKERS_CLEAR = "imageframe.messages.markers.clear";
    public static final String MARKERS_CANCEL = "imageframe.messages.markers.cancel";
    public static final String MARKERS_DUPLICATE_NAME = "imageframe.messages.markers.duplicate_name";
    public static final String MARKERS_NOT_A_MARKER = "imageframe.messages.markers.not_a_marker";
    public static final String MARKERS_NOT_RENDER_WARNING = "imageframe.messages.markers.not_render_on_frame_warning";
    public static final String MARKERS_LIMIT_REACHED = "imageframe.messages.markers.limit_reached";

    public static final String PREFERENCES_UPDATE = "imageframe.messages.preferences.update_message";
    public static String PREFERENCES_TYPE(IFPlayerPreference<?> type) {
        return "imageframe.messages.preferences.keys." + type.name().toLowerCase();
    }
    public static String PREFERENCES_VALUE(PreferenceState state) {
        return "imageframe.messages.preferences.values." + state.name().toLowerCase();
    }

    public static final String COMBINED_ITEM_NAME = "imageframe.settings.combined_map_item.name";
    public static final String COMBINED_ITEM_LORE_1 = "imageframe.settings.combined_map_item.lore.1";
    public static final String COMBINED_ITEM_LORE_2 = "imageframe.settings.combined_map_item.lore.2";
    public static final String COMBINED_ITEM_LORE_3 = "imageframe.settings.combined_map_item.lore.3";
    public static final String COMBINED_ITEM_LORE_4 = "imageframe.settings.combined_map_item.lore.4";
    public static final String COMBINED_ITEM_LORE_5 = "imageframe.settings.combined_map_item.lore.5";

}

