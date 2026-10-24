package com.soloeats.delivery.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RiderPositionView(
        BigDecimal lng,
        BigDecimal lat,
        LocalDateTime lastPing,
        Integer load
) {
}
