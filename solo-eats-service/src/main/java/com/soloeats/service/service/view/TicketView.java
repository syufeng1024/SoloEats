package com.soloeats.service.service.view;

import com.soloeats.service.domain.entity.ServiceTicket;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单列表项。overdue 由 deadline_at 与当前时间比较得出，未受理工单没有时限，恒为 false。
 */
public record TicketView(
        Long id,
        String ticketNo,
        Integer ticketType,
        Integer source,
        Integer submitterType,
        Long submitterId,
        Long orderId,
        String subject,
        String content,
        List<String> images,
        Integer priority,
        Integer status,
        Long assigneeId,
        LocalDateTime acceptAt,
        LocalDateTime deadlineAt,
        LocalDateTime finishAt,
        Integer satisfaction,
        String satisfactionRemark,
        boolean overdue,
        LocalDateTime createdAt
) {

    public static TicketView from(ServiceTicket ticket, List<String> images, LocalDateTime now) {
        boolean active = ticket.getStatus() != null && ticket.getStatus() >= 0 && ticket.getStatus() <= 2;
        boolean overdue = active && ticket.getDeadlineAt() != null
                && now.isAfter(ticket.getDeadlineAt());
        return new TicketView(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getTicketType(),
                ticket.getSource(),
                ticket.getSubmitterType(),
                ticket.getSubmitterId(),
                ticket.getOrderId(),
                ticket.getSubject(),
                ticket.getContent(),
                images,
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getAssigneeId(),
                ticket.getAcceptAt(),
                ticket.getDeadlineAt(),
                ticket.getFinishAt(),
                ticket.getSatisfaction(),
                ticket.getSatisfactionRemark(),
                overdue,
                ticket.getCreatedAt()
        );
    }
}
