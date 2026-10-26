package com.soloeats.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.service.PushService;
import com.soloeats.message.service.command.PushPayload;
import com.soloeats.service.domain.entity.Complaint;
import com.soloeats.service.domain.entity.ServiceTicket;
import com.soloeats.service.domain.entity.TicketRecord;
import com.soloeats.service.domain.enums.TicketStatus;
import com.soloeats.service.domain.enums.TicketType;
import com.soloeats.service.mapper.ComplaintMapper;
import com.soloeats.service.mapper.ServiceTicketMapper;
import com.soloeats.service.mapper.TicketRecordMapper;
import com.soloeats.service.service.TicketService;
import com.soloeats.service.service.command.CreateTicketCommand;
import com.soloeats.service.service.query.TicketQuery;
import com.soloeats.service.service.view.ComplaintView;
import com.soloeats.service.service.view.TicketCreatedView;
import com.soloeats.service.service.view.TicketDetailView;
import com.soloeats.service.service.view.TicketRecordView;
import com.soloeats.service.service.view.TicketStatsView;
import com.soloeats.service.service.view.TicketView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工单主干。
 *
 * <p>状态迁移不使用「先 SELECT 再 UPDATE」：读取只用于取得 priority / 审计记录的前置状态，
 * 真正生效的是带 {@code AND status = ?} 的条件更新，影响行数为 0 即说明并发下状态已变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final int SUBMITTER_USER = 1;

    private static final int PRIORITY_NORMAL = 0;
    private static final int PRIORITY_URGENT = 1;
    private static final int PRIORITY_EXTREME = 2;

    private static final int STATUS_PENDING = TicketStatus.PENDING.getCode();
    private static final int STATUS_PROCESSING = TicketStatus.PROCESSING.getCode();
    private static final int STATUS_WAIT_CALLBACK = TicketStatus.WAIT_CALLBACK.getCode();
    private static final int STATUS_FINISHED = TicketStatus.FINISHED.getCode();
    private static final int STATUS_CLOSED = TicketStatus.CLOSED.getCode();

    private static final int NOTIFY_TYPE_SYSTEM = 3;
    private static final String PUSH_TYPE = "SERVICE_TICKET";

    private static final int MAX_IMAGES = 9;
    private static final int MAX_IMAGE_LENGTH = 512;
    private static final int MAX_OPERATOR_NAME = 64;

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final DateTimeFormatter TICKET_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ServiceTicketMapper ticketMapper;
    private final TicketRecordMapper recordMapper;
    private final ComplaintMapper complaintMapper;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketCreatedView createTicket(CreateTicketCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        if (!TicketType.isValid(command.ticketType())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "ticketType 只能是 1订单问题 2退款 3配送 4投诉 5建议");
        }
        int priority = requirePriority(command.priority());
        int source = requireSubjectType(command.source(), "source");
        int submitterType = requireSubjectType(command.submitterType(), "submitterType");
        Long submitterId = requirePositive(command.submitterId(), "submitterId");
        if (command.orderId() != null && command.orderId() <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "orderId 必须为正整数");
        }
        String subject = normalizeText(command.subject(), "subject", 200);
        String content = normalizeText(command.content(), "content", 2000);
        String imagesJson = toImagesJson(command.images());

        ServiceTicket ticket = new ServiceTicket();
        ticket.setTicketNo(nextTicketNo());
        ticket.setTicketType(command.ticketType());
        ticket.setSource(source);
        ticket.setSubmitterType(submitterType);
        ticket.setSubmitterId(submitterId);
        ticket.setOrderId(command.orderId());
        ticket.setSubject(subject);
        ticket.setContent(content);
        ticket.setImagesJson(imagesJson);
        ticket.setPriority(priority);
        ticket.setStatus(STATUS_PENDING);
        ticket.setVersion(0);
        ticket.setCreatedAt(LocalDateTime.now());
        ticketMapper.insert(ticket);

        insertRecord(ticket.getId(), null, STATUS_PENDING, "CREATED",
                submitterId, "提交人", "提交工单");
        log.info("service ticket created id={} no={} type={} submitter={}:{}",
                ticket.getId(), ticket.getTicketNo(), ticket.getTicketType(),
                submitterType, submitterId);
        return new TicketCreatedView(ticket.getId(), ticket.getTicketNo());
    }

    @Override
    public PageResult<TicketView> pageMine(Long userId, Integer status, long pageNum, long pageSize) {
        Long submitterId = requirePositive(userId, "userId");
        if (status != null && !TicketStatus.isValid(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0~4");
        }
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<ServiceTicket> wrapper = new LambdaQueryWrapper<ServiceTicket>()
                .eq(ServiceTicket::getSubmitterType, SUBMITTER_USER)
                .eq(ServiceTicket::getSubmitterId, submitterId)
                .eq(status != null, ServiceTicket::getStatus, status)
                .orderByDesc(ServiceTicket::getCreatedAt)
                .orderByDesc(ServiceTicket::getId);

        Page<ServiceTicket> page = ticketMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        LocalDateTime now = LocalDateTime.now();
        List<TicketView> views = page.getRecords().stream()
                .map(ticket -> toView(ticket, now))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public TicketDetailView detailForConsumer(Long ticketId, Long userId) {
        return buildDetail(requireOwnedTicket(ticketId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeByConsumer(Long ticketId, Long userId) {
        ServiceTicket ticket = requireOwnedTicket(ticketId, userId);
        int rows = ticketMapper.closeBySubmitter(ticket.getId(), userId);
        if (rows == 0) {
            // 归属已校验，0 行只可能是状态不再是待受理
            throw new BizException(ErrorCode.TICKET_STATUS_INVALID, "仅待受理工单可以撤单");
        }
        insertRecord(ticket.getId(), STATUS_PENDING, STATUS_CLOSED, "USER_CLOSE",
                userId, "提交人", "用户撤单");
        log.info("service ticket closed by consumer id={} userId={}", ticketId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rateByConsumer(Long ticketId, Long userId, Integer satisfaction, String remark) {
        if (satisfaction == null || satisfaction < 1 || satisfaction > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, "satisfaction 只能是 1~5");
        }
        String normalizedRemark = normalizeOptionalText(remark, "satisfactionRemark", 500);
        ServiceTicket ticket = requireOwnedTicket(ticketId, userId);
        int rows = ticketMapper.rate(ticket.getId(), userId, satisfaction, normalizedRemark);
        if (rows == 0) {
            throw new BizException(ErrorCode.TICKET_STATUS_INVALID,
                    "仅已办结且未评价的工单可以评价");
        }
        insertRecord(ticket.getId(), STATUS_FINISHED, STATUS_FINISHED, "SATISFACTION",
                userId, "提交人", "满意度 " + satisfaction);
        log.info("service ticket rated id={} userId={} satisfaction={}",
                ticketId, userId, satisfaction);
    }

    @Override
    public PageResult<TicketView> pageAdmin(TicketQuery query, long pageNum, long pageSize) {
        TicketQuery condition = query == null ? new TicketQuery(null, null, null, null, null, null) : query;
        if (condition.status() != null && !TicketStatus.isValid(condition.status())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0~4");
        }
        if (condition.ticketType() != null && !TicketType.isValid(condition.ticketType())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "ticketType 只能是 1订单问题 2退款 3配送 4投诉 5建议");
        }
        if (condition.priority() != null) {
            requirePriority(condition.priority());
        }
        if (condition.assigneeId() != null && condition.assigneeId() <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "assigneeId 必须为正整数");
        }
        if (condition.dateFrom() != null && condition.dateTo() != null
                && condition.dateFrom().isAfter(condition.dateTo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }

        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);
        LocalDateTime start = condition.dateFrom() == null
                ? null : condition.dateFrom().atStartOfDay();
        LocalDateTime end = condition.dateTo() == null
                ? null : condition.dateTo().plusDays(1).atStartOfDay();

        LambdaQueryWrapper<ServiceTicket> wrapper = new LambdaQueryWrapper<ServiceTicket>()
                .eq(condition.status() != null, ServiceTicket::getStatus, condition.status())
                .eq(condition.ticketType() != null, ServiceTicket::getTicketType, condition.ticketType())
                .eq(condition.assigneeId() != null, ServiceTicket::getAssigneeId, condition.assigneeId())
                .eq(condition.priority() != null, ServiceTicket::getPriority, condition.priority())
                .ge(start != null, ServiceTicket::getCreatedAt, start)
                .lt(end != null, ServiceTicket::getCreatedAt, end)
                .orderByDesc(ServiceTicket::getCreatedAt)
                .orderByDesc(ServiceTicket::getId);

        Page<ServiceTicket> page = ticketMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        LocalDateTime now = LocalDateTime.now();
        List<TicketView> views = page.getRecords().stream()
                .map(ticket -> toView(ticket, now))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public TicketDetailView detailForAdmin(Long ticketId) {
        return buildDetail(requireTicket(ticketId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long ticketId, Long operatorId, String operatorName) {
        Long assigneeId = requirePositive(operatorId, "operatorId");
        ServiceTicket ticket = requireTicket(ticketId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(deadlineHours(ticket.getPriority()));

        int rows = ticketMapper.accept(ticket.getId(), assigneeId, now, deadline);
        if (rows == 0) {
            ServiceTicket latest = ticketMapper.selectById(ticketId);
            if (latest == null) {
                throw new BizException(ErrorCode.TICKET_NOT_FOUND);
            }
            if (Objects.equals(latest.getStatus(), STATUS_PROCESSING)) {
                throw new BizException(ErrorCode.TICKET_ALREADY_ASSIGNED);
            }
            throw new BizException(ErrorCode.TICKET_STATUS_INVALID, "仅待受理工单可以受理");
        }

        insertRecord(ticket.getId(), STATUS_PENDING, STATUS_PROCESSING, "ACCEPT",
                assigneeId, operatorName(operatorName), "客服已受理");
        pushAfterCommit(ticket, STATUS_PROCESSING, "工单已受理",
                "您的工单 " + ticket.getTicketNo() + " 已由客服受理");
        log.info("service ticket accepted id={} assignee={}", ticketId, assigneeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long ticketId, Long assigneeId, Long operatorId, String operatorName) {
        Long target = requirePositive(assigneeId, "assigneeId");
        Long operator = requirePositive(operatorId, "operatorId");
        ServiceTicket ticket = requireTicket(ticketId);

        int rows = ticketMapper.assign(ticket.getId(), target);
        if (rows == 0) {
            throw new BizException(ErrorCode.TICKET_STATUS_INVALID, "已办结或已关闭的工单不能转派");
        }
        insertRecord(ticket.getId(), ticket.getStatus(), ticket.getStatus(), "ASSIGN",
                operator, operatorName(operatorName), "指派给客服 " + target);
        log.info("service ticket assigned id={} from={} to={}", ticketId, operator, target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long ticketId, String content, Long operatorId, String operatorName) {
        Long operator = requirePositive(operatorId, "operatorId");
        String text = normalizeText(content, "content", 1000);
        ServiceTicket ticket = requireTicket(ticketId);

        if (ticketMapper.transit(ticket.getId(), STATUS_PROCESSING, STATUS_PROCESSING) == 0) {
            throwStatusInvalid(ticketId);
        }
        insertRecord(ticket.getId(), STATUS_PROCESSING, STATUS_PROCESSING, "HANDLE",
                operator, operatorName(operatorName), text);
        log.info("service ticket handled id={} operator={}", ticketId, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void callback(Long ticketId, String content, String result,
                         Long operatorId, String operatorName) {
        Long operator = requirePositive(operatorId, "operatorId");
        String text = normalizeText(content, "content", 1000);
        String callbackResult = normalizeOptionalText(result, "result", 500);
        ServiceTicket ticket = requireTicket(ticketId);

        if (ticketMapper.transit(ticket.getId(), STATUS_PROCESSING, STATUS_WAIT_CALLBACK) == 0) {
            throwStatusInvalid(ticketId);
        }
        insertRecord(ticket.getId(), STATUS_PROCESSING, STATUS_WAIT_CALLBACK, "CALLBACK",
                operator, operatorName(operatorName), text);
        complaintMapper.updateCallbackByTicket(ticket.getId(), LocalDateTime.now(), callbackResult);
        pushAfterCommit(ticket, STATUS_WAIT_CALLBACK, "工单回访",
                "工单 " + ticket.getTicketNo() + " 已进入回访环节");
        log.info("service ticket callback id={} operator={}", ticketId, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long ticketId, Long operatorId, String operatorName) {
        Long operator = requirePositive(operatorId, "operatorId");
        ServiceTicket ticket = requireTicket(ticketId);
        LocalDateTime now = LocalDateTime.now();

        if (ticketMapper.finish(ticket.getId(), now) == 0) {
            throwStatusInvalid(ticketId);
        }
        insertRecord(ticket.getId(), STATUS_WAIT_CALLBACK, STATUS_FINISHED, "FINISH",
                operator, operatorName(operatorName), "工单已办结");
        pushAfterCommit(ticket, STATUS_FINISHED, "工单已办结",
                "您的工单 " + ticket.getTicketNo() + " 已办结，可进行满意度评价");
        log.info("service ticket finished id={} operator={}", ticketId, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long ticketId, String content, Long operatorId, String operatorName) {
        Long operator = requirePositive(operatorId, "operatorId");
        String text = normalizeOptionalText(content, "content", 1000);
        ServiceTicket ticket = requireTicket(ticketId);
        Integer fromStatus = ticket.getStatus();

        if (!TicketStatus.canTransit(fromStatus, STATUS_CLOSED)) {
            throw new BizException(ErrorCode.TICKET_STATUS_INVALID,
                    "已办结或已关闭的工单不能再关闭");
        }
        if (ticketMapper.close(ticket.getId(), fromStatus) == 0) {
            throwStatusInvalid(ticketId);
        }
        String recordContent = StringUtils.hasText(text) ? text : "客服关闭工单";
        insertRecord(ticket.getId(), fromStatus, STATUS_CLOSED, "CLOSE",
                operator, operatorName(operatorName), recordContent);
        pushAfterCommit(ticket, STATUS_CLOSED, "工单已关闭",
                "您的工单 " + ticket.getTicketNo() + " 已关闭");
        log.info("service ticket closed id={} from={} operator={}", ticketId, fromStatus, operator);
    }

    @Override
    public TicketStatsView stats(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 与 dateTo 不能为空");
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.plusDays(1).atStartOfDay();

        // TODO(T09 阶段B)：接待量需叠加 peer_type=4 的会话接待数，本阶段只统计工单量。
        ServiceTicketMapper.TicketStatsRow row = ticketMapper.stats(start, end);
        long ticketCount = row == null || row.ticketCount() == null ? 0L : row.ticketCount();
        long solvedCount = row == null || row.solvedCount() == null ? 0L : row.solvedCount();
        BigDecimal solvedRate = ticketCount == 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(solvedCount)
                        .divide(BigDecimal.valueOf(ticketCount), 4, RoundingMode.HALF_UP);
        BigDecimal avgResponse = scale(row == null ? null : row.avgResponseMinutes());
        BigDecimal satisfaction = scale(row == null ? null : row.satisfactionAvg());
        return new TicketStatsView(ticketCount, solvedRate, avgResponse, satisfaction);
    }

    private TicketDetailView buildDetail(ServiceTicket ticket) {
        List<TicketRecordView> records = recordMapper.selectByTicketId(ticket.getId()).stream()
                .map(TicketRecordView::from)
                .toList();
        Complaint complaint = complaintMapper.selectByTicketId(ticket.getId());
        return new TicketDetailView(
                toView(ticket, LocalDateTime.now()),
                records,
                ComplaintView.from(complaint));
    }

    private TicketView toView(ServiceTicket ticket, LocalDateTime now) {
        return TicketView.from(ticket, parseImages(ticket.getImagesJson()), now);
    }

    /**
     * 归属校验先于任何数据返回与写操作：不存在抛 90010，非本人抛 90012。
     */
    private ServiceTicket requireOwnedTicket(Long ticketId, Long userId) {
        Long submitterId = requirePositive(userId, "userId");
        ServiceTicket ticket = requireTicket(ticketId);
        if (!Objects.equals(ticket.getSubmitterType(), SUBMITTER_USER)
                || !Objects.equals(ticket.getSubmitterId(), submitterId)) {
            throw new BizException(ErrorCode.TICKET_NOT_OWNER);
        }
        return ticket;
    }

    private ServiceTicket requireTicket(Long ticketId) {
        if (ticketId == null || ticketId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工单ID不能为空");
        }
        ServiceTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(ErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    /** 条件更新影响 0 行后重新读取，用于区分「不存在」与「状态不允许」。 */
    private void throwStatusInvalid(Long ticketId) {
        if (ticketMapper.selectById(ticketId) == null) {
            throw new BizException(ErrorCode.TICKET_NOT_FOUND);
        }
        throw new BizException(ErrorCode.TICKET_STATUS_INVALID);
    }

    private void insertRecord(Long ticketId, Integer fromStatus, Integer toStatus, String action,
                              Long operatorId, String operatorName, String content) {
        TicketRecord record = new TicketRecord();
        record.setTicketId(ticketId);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setAction(action);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setContent(content);
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private String operatorName(String name) {
        if (!StringUtils.hasText(name)) {
            return "客服";
        }
        String trimmed = name.strip();
        return trimmed.length() <= MAX_OPERATOR_NAME
                ? trimmed
                : trimmed.substring(0, MAX_OPERATOR_NAME);
    }

    private int requirePriority(Integer priority) {
        int value = priority == null ? PRIORITY_NORMAL : priority;
        if (value < PRIORITY_NORMAL || value > PRIORITY_EXTREME) {
            throw new BizException(ErrorCode.PARAM_INVALID, "priority 只能是 0普通 1紧急 2特急");
        }
        return value;
    }

    private int requireSubjectType(Integer type, String field) {
        int value = type == null ? SUBMITTER_USER : type;
        if (value < 1 || value > 4) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 只能是 1用户 2商家 3骑手 4平台");
        }
        return value;
    }

    private Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须为正整数");
        }
        return value;
    }

    private int deadlineHours(Integer priority) {
        if (Objects.equals(priority, PRIORITY_EXTREME)) {
            return 1;
        }
        if (Objects.equals(priority, PRIORITY_URGENT)) {
            return 4;
        }
        return 24;
    }

    /** 原样存纯文本，只统一换行符与去首尾空白，不做任何 HTML 渲染。 */
    private String normalizeText(String value, String field, int maxLength) {
        if (value == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        if (text.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 最长 " + maxLength + " 个字符");
        }
        return text;
    }

    private String normalizeOptionalText(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 最长 " + maxLength + " 个字符");
        }
        return text;
    }

    private String toImagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String image : images) {
            if (!StringUtils.hasText(image)) {
                continue;
            }
            String url = image.strip();
            if (url.length() > MAX_IMAGE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "图片地址最长 " + MAX_IMAGE_LENGTH + " 个字符");
            }
            normalized.add(url);
        }
        if (normalized.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片最多 " + MAX_IMAGES + " 张");
        }
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片列表格式不合法");
        }
    }

    private List<String> parseImages(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.warn("service ticket images_json parse failed, images dropped");
            return List.of();
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static long normalizePageNum(long pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private static long normalizePageSize(long pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String nextTicketNo() {
        return "TK" + TICKET_NO_TIME.format(LocalDateTime.now())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
    }

    /**
     * 事务提交后再推送；推送失败只记日志，不影响已落库的工单状态。
     */
    private void pushAfterCommit(ServiceTicket ticket, Integer status, String title, String content) {
        Runnable task = () -> {
            try {
                pushService.push(ticket.getSubmitterType(), ticket.getSubmitterId(),
                        new PushPayload(NOTIFY_TYPE_SYSTEM, PUSH_TYPE, null,
                                ticket.getOrderId(), null, status, title, content));
            } catch (Exception ex) {
                log.error("service ticket push failed id={} receiver={}:{}",
                        ticket.getId(), ticket.getSubmitterType(), ticket.getSubmitterId(), ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
