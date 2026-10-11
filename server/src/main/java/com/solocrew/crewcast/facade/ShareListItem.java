package com.solocrew.crewcast.facade;

import com.solocrew.crewcast.settings.ShareEntry;

public record ShareListItem(int index, ShareEntry entry, String meta, String audioMeta, boolean hevcWarning) {
}
