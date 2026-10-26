package com.soloeats.service.service.view;

import com.soloeats.service.domain.entity.Complaint;

import java.time.LocalDateTime;

public record ComplaintView(
        Long id,
        Long ticketId,
        Long orderId,
        Integer complainantType,
        Long complainantId,
        Integer targetType,
        Long targetId,
        String reason,
        Integer liability,
        String result,
        Integer punishAction,
        LocalDateTime callbackAt,
        String callbackResult,
        LocalDateTime createdAt
) {

    public static ComplaintView from(Complaint complaint) {
        if (complaint == null) {
            return null;
        }
        return new ComplaintView(
                complaint.getId(),
                complaint.getTicketId(),
                complaint.getOrderId(),
                complaint.getComplainantType(),
                complaint.getComplainantId(),
                complaint.getTargetType(),
                complaint.getTargetId(),
                complaint.getReason(),
                complaint.getLiability(),
                complaint.getResult(),
                complaint.getPunishAction(),
                complaint.getCallbackAt(),
                complaint.getCallbackResult(),
                complaint.getCreatedAt()
        );
    }
}
