package com.soloeats.service.service.view;

import com.soloeats.service.domain.entity.TicketRecord;

import java.time.LocalDateTime;

public record TicketRecordView(
        Long id,
        Integer fromStatus,
        Integer toStatus,
        String action,
        Long operatorId,
        String operatorName,
        String content,
        LocalDateTime createdAt
) {

    public static TicketRecordView from(TicketRecord record) {
        return new TicketRecordView(
                record.getId(),
                record.getFromStatus(),
                record.getToStatus(),
                record.getAction(),
                record.getOperatorId(),
                record.getOperatorName(),
                record.getContent(),
                record.getCreatedAt()
        );
    }
}
