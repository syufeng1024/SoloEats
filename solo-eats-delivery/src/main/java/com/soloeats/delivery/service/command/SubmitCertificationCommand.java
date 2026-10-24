package com.soloeats.delivery.service.command;

import java.time.LocalDate;

public record SubmitCertificationCommand(
        String realName,
        String idCard,
        Long idCardFrontFileId,
        Long idCardBackFileId,
        Long healthCertFileId,
        LocalDate healthCertExpireAt,
        Integer vehicleType
) {
}
