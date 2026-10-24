package com.solocrew.crewcast.desktop.share;

import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.facade.ShareVideoCompatRow;
import com.solocrew.crewcast.settings.ShareEntry;

import java.awt.Component;
import java.nio.file.Path;

/** Host callbacks for share-row controls (convert, cancel, remove, TV menu). */
public interface ShareRowActions {

    void removeShare(int index);

    void reloadShareList(boolean showCheckingPlaceholder);

    void showCompatPopup(Component invoker, ShareListItem shareItem, ShareVideoCompatRow videoRow);

    void prepareCompatForShareEntry(ShareEntry entry, ShareListItem shareItem, Path folderVideoPath);

    void cancelCompatForPath(Path path, ShareListItem shareItem);

    void prepareAllNeedingCompat();

    void prepareAllCompatForFolder(ShareEntry folderEntry);
}
