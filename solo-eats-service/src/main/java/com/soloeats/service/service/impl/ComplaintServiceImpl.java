package com.soloeats.service.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.service.domain.entity.Complaint;
import com.soloeats.service.domain.enums.TicketType;
import com.soloeats.service.mapper.ComplaintMapper;
import com.soloeats.service.service.ComplaintService;
import com.soloeats.service.service.TicketService;
import com.soloeats.service.service.command.CreateComplaintCommand;
import com.soloeats.service.service.command.CreateTicketCommand;
import com.soloeats.service.service.view.ComplaintCreatedView;
import com.soloeats.service.service.view.ComplaintView;
import com.soloeats.service.service.view.TicketCreatedView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 投诉单。提交时自动建一张 ticket_type = 4 的工单，后续受理/回访/办结走工单状态机。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private static final int COMPLAINANT_USER = 1;
    private static final int SOURCE_USER = 1;
    /** 投诉默认按紧急处理，时限 4 小时。 */
    private static final int COMPLAINT_PRIORITY = 1;
    private static final int LIABILITY_MIN = 1;
    private static final int LIABILITY_MAX = 4;
    private static final int PUNISH_NONE = 0;
    private static final int PUNISH_BAN = 3;
    private static final int SUBJECT_MAX = 200;
    private static final String SUBJECT_PREFIX = "投诉：";

    private static final Set<Integer> TARGET_TYPES = Set.of(2, 3, 4);

    private final ComplaintMapper complaintMapper;
    private final TicketService ticketService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ComplaintCreatedView create(CreateComplaintCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        Long complainantId = requirePositive(command.complainantId(), "complainantId");
        Long orderId = requirePositive(command.orderId(), "orderId");
        if (command.targetType() == null || !TARGET_TYPES.contains(command.targetType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "targetType 只能是 2商家 3骑手 4平台");
        }
        Long targetId = requirePositive(command.targetId(), "targetId");
        String reason = normalizeReason(command.reason());

        TicketCreatedView ticket = ticketService.createTicket(new CreateTicketCommand(
                TicketType.COMPLAINT.getCode(),
                orderId,
                complaintSubject(reason),
                reason,
                command.images(),
                COMPLAINT_PRIORITY,
                SOURCE_USER,
                COMPLAINANT_USER,
                complainantId));

        Complaint complaint = new Complaint();
        complaint.setTicketId(ticket.ticketId());
        complaint.setOrderId(orderId);
        complaint.setComplainantType(COMPLAINANT_USER);
        complaint.setComplainantId(complainantId);
        complaint.setTargetType(command.targetType());
        complaint.setTargetId(targetId);
        complaint.setReason(reason);
        complaint.setPunishAction(PUNISH_NONE);
        complaintMapper.insert(complaint);

        log.info("complaint created id={} ticketId={} target={}:{}",
                complaint.getId(), ticket.ticketId(), command.targetType(), targetId);
        return new ComplaintCreatedView(complaint.getId(), ticket.ticketId(), ticket.ticketNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void judge(Long complaintId, Integer liability, String result, Integer punishAction) {
        if (complaintId == null || complaintId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "投诉ID不能为空");
        }
        if (liability == null || liability < LIABILITY_MIN || liability > LIABILITY_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "liability 只能是 1商家 2骑手 3平台 4用户");
        }
        int punish = punishAction == null ? PUNISH_NONE : punishAction;
        if (punish < PUNISH_NONE || punish > PUNISH_BAN) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "punishAction 只能是 0无 1警告 2扣款 3封禁");
        }
        String text = normalizeResult(result);

        int rows = complaintMapper.judge(complaintId, liability, text, punish);
        if (rows == 0) {
            if (complaintMapper.selectById(complaintId) == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "投诉单不存在");
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "该投诉已完成责任判定");
        }

        // TODO(T09 阶段B 之后)：punish=2 扣款、punish=3 封禁只登记在 t_complaint，
        // 实际执行需通过 T03/T04（商家）或 T07（骑手）的端口接口，禁止本模块跨域直接调用。
        log.info("complaint judged id={} liability={} punish={}", complaintId, liability, punish);
    }

    @Override
    public ComplaintView findByTicketId(Long ticketId) {
        if (ticketId == null || ticketId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工单ID不能为空");
        }
        return ComplaintView.from(complaintMapper.selectByTicketId(ticketId));
    }

    private Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须为正整数");
        }
        return value;
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reason 不能为空");
        }
        String text = reason.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reason 不能为空");
        }
        if (text.length() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reason 最长 500 个字符");
        }
        return text;
    }

    private String normalizeResult(String result) {
        if (!StringUtils.hasText(result)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "result 不能为空");
        }
        String text = result.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.length() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "result 最长 500 个字符");
        }
        return text;
    }

    /** 工单标题列宽 200，超长截断，完整原因保留在工单 content 中。 */
    private String complaintSubject(String reason) {
        int limit = SUBJECT_MAX - SUBJECT_PREFIX.length();
        String body = reason.length() <= limit ? reason : reason.substring(0, limit);
        return SUBJECT_PREFIX + body;
    }
}
