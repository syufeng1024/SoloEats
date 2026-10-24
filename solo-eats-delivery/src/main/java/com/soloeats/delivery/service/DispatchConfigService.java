package com.soloeats.delivery.service;

import com.soloeats.delivery.domain.entity.DispatchConfig;
import com.soloeats.delivery.service.command.SaveDispatchConfigCommand;
import com.soloeats.delivery.service.view.DispatchConfigView;

/**
 * 派单策略配置。读取顺序：门店（config_scope=3）覆盖全局（config_scope=1）。
 */
public interface DispatchConfigService {

    DispatchConfigView getConfig(Long shopId);

    DispatchConfigView save(SaveDispatchConfigCommand command);

    /** 策略计算与派单编排使用；全局配置缺失抛 10000。 */
    DispatchConfig effectiveConfig(Long shopId);
}
