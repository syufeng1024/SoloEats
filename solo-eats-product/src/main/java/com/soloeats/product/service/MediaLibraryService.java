package com.soloeats.product.service;

import com.soloeats.product.service.command.SaveMediaGroupCommand;
import com.soloeats.product.service.command.SaveMediaItemCommand;
import com.soloeats.product.service.view.MediaGroupView;
import com.soloeats.product.service.view.MediaItemView;

import java.util.List;

public interface MediaLibraryService {

    List<MediaGroupView> listGroups();

    MediaGroupView saveGroup(Long id, SaveMediaGroupCommand command);

    void deleteGroup(Long id);

    List<MediaItemView> listItems(Long groupId);

    MediaItemView addItem(Long groupId, SaveMediaItemCommand command);

    void deleteItem(Long id);
}
