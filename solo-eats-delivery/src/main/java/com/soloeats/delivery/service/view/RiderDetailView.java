package com.soloeats.delivery.service.view;

import java.util.List;

public record RiderDetailView(
        RiderView rider,
        List<RiderCertificationView> certifications,
        long todayOnlineSeconds
) {
}
