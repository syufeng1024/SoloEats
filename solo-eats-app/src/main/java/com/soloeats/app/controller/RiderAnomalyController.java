package com.soloeats.app.controller;

import com.soloeats.app.mapper.RiderAnomalyRow;
import com.soloeats.app.mapper.RiderSelfQueryMapper;
import com.soloeats.app.vo.RiderAnomalyView;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.delivery.service.RiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 骑手本人异常上报记录。
 *
 * <p>数据来源：t_rider_ping_anomaly，不是定位异常表。该表有两类写入方：
 * 骑手主动上报（RiderFulfillmentServiceImpl#reportAnomaly）与系统超时扫描
 * （DeliveryTimeoutServiceImpl#markTimeouts，type=1，remark 为「承诺送达时间 ... 已超时」）。
 * 表中没有来源字段，两者无法可靠区分，因此本接口返回该骑手名下全部异常记录。
 * 同一动作写入的 t_order_exception 是给平台/用户侧处理的工单，不在骑手端展示。
 */
@RequireRole(Roles.RIDER)
@RestController
@RequestMapping("/api/v1/rider/anomalies")
@RequiredArgsConstructor
public class RiderAnomalyController {

    private static final long MAX_PAGE_SIZE = 100L;

    private final RiderService riderService;
    private final RiderSelfQueryMapper riderProfileQueryMapper;

    @GetMapping
    public ApiResponse<PageResult<RiderAnomalyView>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        long normalizedPageNum = Math.max(1L, pageNum);
        long normalizedPageSize = Math.min(Math.max(1L, pageSize), MAX_PAGE_SIZE);
        Long riderId = currentRiderId();

        long total = riderProfileQueryMapper.countAnomalies(riderId);
        if (total == 0L) {
            return ApiResponse.ok(PageResult.of(
                    List.of(), 0L, normalizedPageNum, normalizedPageSize));
        }
        long offset = (normalizedPageNum - 1L) * normalizedPageSize;
        List<RiderAnomalyView> list = riderProfileQueryMapper
                .listAnomalies(riderId, normalizedPageSize, offset)
                .stream()
                .map(RiderAnomalyController::toView)
                .toList();
        return ApiResponse.ok(PageResult.of(
                list, total, normalizedPageNum, normalizedPageSize));
    }

    private static RiderAnomalyView toView(RiderAnomalyRow row) {
        return new RiderAnomalyView(
                row.getId(),
                row.getDeliveryId(),
                row.getType(),
                typeName(row.getType()),
                row.getRemark(),
                row.getImagesJson(),
                row.getCreatedAt());
    }

    private static String typeName(Integer type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case 1 -> "超时";
            case 2 -> "联系不上用户";
            case 3 -> "地址错误";
            case 4 -> "餐品破损";
            case 5 -> "商家出餐慢";
            case 6 -> "用户拒收";
            default -> "其他";
        };
    }

    private Long currentRiderId() {
        UserPrincipal principal = AuthContext.require();
        return riderService.resolveRiderId(principal.userId(), principal.riderId());
    }
}
