package com.soloeats.app.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/** t_rider_ping_anomaly 的骑手端只读投影。 */
@Data
public class RiderAnomalyRow {

    private Long id;

    private Long deliveryId;

    /** 1超时 2联系不上用户 3地址错误 4餐品破损 5商家出餐慢 6用户拒收。 */
    private Integer type;

    private String remark;

    private String imagesJson;

    private LocalDateTime createdAt;
}
