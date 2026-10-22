package com.soloeats.product.service;

import com.soloeats.product.service.command.SaveAddonTemplateCommand;
import com.soloeats.product.service.command.SaveSpecTemplateCommand;
import com.soloeats.product.service.view.AddonGroupView;
import com.soloeats.product.service.view.SpecGroupView;

import java.util.List;

public interface SpecTemplateService {

    List<SpecGroupView> listSpecTemplates();

    /**
     * id 为空新增，否则整体替换组信息与选项。
     */
    SpecGroupView saveSpecTemplate(Long id, SaveSpecTemplateCommand command);

    void deleteSpecTemplate(Long id);

    List<AddonGroupView> listAddonTemplates();

    AddonGroupView saveAddonTemplate(Long id, SaveAddonTemplateCommand command);

    void deleteAddonTemplate(Long id);

    /**
     * 指定 SPU 绑定的规格组（含选项），用于商品详情。
     */
    List<SpecGroupView> specGroupsOfSpu(Long spuId);

    /**
     * 指定 SPU 绑定的加料组（含加料项），用于商品详情。
     */
    List<AddonGroupView> addonGroupsOfSpu(Long spuId);
}
