package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.FreightRule;
import com.soloeats.delivery.domain.entity.FreightTemplate;
import com.soloeats.delivery.domain.entity.ShopFreightRel;
import com.soloeats.delivery.mapper.FreightRuleMapper;
import com.soloeats.delivery.mapper.FreightTemplateMapper;
import com.soloeats.delivery.mapper.ShopFreightRelMapper;
import com.soloeats.delivery.service.FreightService;
import com.soloeats.delivery.service.command.FreightEstimateCommand;
import com.soloeats.delivery.service.command.SaveFreightTemplateCommand;
import com.soloeats.delivery.service.view.FreightEstimateView;
import com.soloeats.delivery.service.view.FreightTemplateView;
import com.soloeats.delivery.support.FreightConditionEvaluator;
import com.soloeats.store.service.ShopService;
import com.soloeats.store.service.view.ShopDetailView;
import com.soloeats.store.support.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreightServiceImpl implements FreightService {

    static final long DEFAULT_TEMPLATE_ID = 1L;
    static final int RULE_DISTANCE = 1;
    static final int RULE_TIME = 2;
    static final int RULE_WEATHER = 3;
    static final int RULE_WEIGHT = 4;
    static final int RULE_FLOOR = 5;

    private static final int FULFILLMENT_DELIVERY = 1;
    private static final int FULFILLMENT_PICKUP = 2;
    private static final int STATUS_ENABLED = 0;
    private static final int DEFAULT_BASE_DISTANCE_M = 3000;
    private static final int DEFAULT_PER_STEP_DISTANCE_M = 500;
    private static final int DEFAULT_MAX_DISTANCE_M = 10000;
    private static final int NAME_MAX_LENGTH = 64;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final String ITEM_BASE = "起步价";
    private static final String ITEM_DISTANCE = "距离费";
    private static final String ITEM_TIME = "时段加价";
    private static final String ITEM_WEATHER = "恶劣天气加价";
    private static final String ITEM_WEIGHT = "重量附加";
    private static final String ITEM_FLOOR = "楼层附加";
    private static final String ITEM_FREE = "满额减免";

    private final FreightTemplateMapper freightTemplateMapper;
    private final FreightRuleMapper freightRuleMapper;
    private final ShopFreightRelMapper shopFreightRelMapper;
    private final ShopService shopService;
    private final ObjectMapper objectMapper;

    @Override
    public FreightEstimateView estimate(FreightEstimateCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "试算参数不能为空");
        }
        int fulfillmentType = command.fulfillmentType() == null
                ? FULFILLMENT_DELIVERY : command.fulfillmentType();
        if (fulfillmentType == FULFILLMENT_PICKUP) {
            return new FreightEstimateView(0L, 0,
                    List.of(new FreightEstimateView.Item("到店自取免配送费", 0L)));
        }
        if (fulfillmentType != FULFILLMENT_DELIVERY) {
            throw new BizException(ErrorCode.PARAM_INVALID, "fulfillmentType 只能是 1平台配送 或 2到店自取");
        }
        if (command.shopId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "shopId 不能为空");
        }
        long goodsAmount = requireAmount(command.goodsAmount(), "goodsAmount");
        validateCoordinate(command.lng(), command.lat());

        ShopDetailView shop = shopService.detail(command.shopId());
        FreightTemplate template = resolveTemplate(command.shopId());
        List<FreightRule> rules = loadRules(template.getId());

        int distanceM = (int) Math.round(GeoUtils.distanceMeters(
                command.lng(), command.lat(), shop.lng(), shop.lat()));
        int maxDistanceM = template.getMaxDistanceM() == null
                ? DEFAULT_MAX_DISTANCE_M : template.getMaxDistanceM();
        if (maxDistanceM > 0 && distanceM > maxDistanceM) {
            throw new BizException(ErrorCode.FREIGHT_OUT_OF_RANGE,
                    "超出配送范围，距门店 " + distanceM + " 米，上限 " + maxDistanceM + " 米");
        }

        long baseFee = nonNegative(template.getBaseFee());
        long distanceFee = distanceFee(distanceM, template);
        long timeFee = timeSurcharge(rules, command.deliverAt());
        long weatherFee = weatherSurcharge(rules);
        long weightFee = weightSurcharge(rules, command.weightKg());
        long floorFee = floorSurcharge(rules, command.floor());

        List<FreightEstimateView.Item> breakdown = new ArrayList<>(6);
        breakdown.add(new FreightEstimateView.Item(ITEM_BASE, baseFee));
        breakdown.add(new FreightEstimateView.Item(ITEM_DISTANCE, distanceFee));
        addIfPositive(breakdown, ITEM_TIME, timeFee);
        addIfPositive(breakdown, ITEM_WEATHER, weatherFee);
        addIfPositive(breakdown, ITEM_WEIGHT, weightFee);
        addIfPositive(breakdown, ITEM_FLOOR, floorFee);

        long subtotal = baseFee + distanceFee + timeFee + weatherFee + weightFee + floorFee;
        long deliveryFee = subtotal;
        Long freeOverAmount = template.getFreeOverAmount();
        if (freeOverAmount != null && subtotal > 0 && goodsAmount >= freeOverAmount) {
            deliveryFee = 0L;
            // 负数项让 deliveryFee = sum(breakdown) 始终成立
            breakdown.add(new FreightEstimateView.Item(ITEM_FREE, -subtotal));
        }
        return new FreightEstimateView(deliveryFee, distanceM, List.copyOf(breakdown));
    }

    @Override
    public List<FreightTemplateView> listTemplates() {
        List<FreightTemplate> templates = freightTemplateMapper.selectList(
                new LambdaQueryWrapper<FreightTemplate>().orderByAsc(FreightTemplate::getId));
        return assemble(templates);
    }

    @Override
    public PageResult<FreightTemplateView> page(long pageNum, long pageSize) {
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L
                ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<FreightTemplate> page = freightTemplateMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize),
                new LambdaQueryWrapper<FreightTemplate>().orderByAsc(FreightTemplate::getId));
        return PageResult.of(assemble(page.getRecords()), page.getTotal(),
                normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FreightTemplateView saveTemplate(SaveFreightTemplateCommand command) {
        FreightTemplate template = new FreightTemplate();
        apply(command, template);

        List<FreightRule> rules = toRules(command.rules());
        freightTemplateMapper.insert(template);
        insertRules(template.getId(), rules);
        log.info("freight template created templateId={} rules={}", template.getId(), rules.size());
        return toView(reload(template), rules);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FreightTemplateView updateTemplate(Long id, SaveFreightTemplateCommand command) {
        FreightTemplate existing = id == null ? null : freightTemplateMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "运费模板不存在");
        }
        apply(command, existing);
        List<FreightRule> rules = toRules(command.rules());

        if (freightTemplateMapper.updateById(existing) != 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "运费模板不存在或已删除");
        }
        // 规则全量覆盖：先删后插，避免残留已下线的加价项
        freightRuleMapper.delete(new LambdaQueryWrapper<FreightRule>()
                .eq(FreightRule::getTemplateId, id));
        insertRules(id, rules);
        log.info("freight template updated templateId={} rules={}", id, rules.size());
        return toView(reload(existing), rules);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindShop(Long shopId, Long templateId) {
        if (shopId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "shopId 不能为空");
        }
        if (templateId == null || freightTemplateMapper.selectById(templateId) == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "运费模板不存在");
        }
        ShopFreightRel existing = shopFreightRelMapper.findByShopId(shopId);
        if (existing == null) {
            ShopFreightRel rel = new ShopFreightRel();
            rel.setShopId(shopId);
            rel.setTemplateId(templateId);
            shopFreightRelMapper.insert(rel);
        } else if (!Objects.equals(existing.getTemplateId(), templateId)) {
            existing.setTemplateId(templateId);
            shopFreightRelMapper.updateById(existing);
        }
        log.info("shop freight template bound shopId={} templateId={}", shopId, templateId);
    }

    // ------------------------------------------------------------------
    // 试算内部计算
    // ------------------------------------------------------------------

    private FreightTemplate resolveTemplate(Long shopId) {
        ShopFreightRel rel = shopFreightRelMapper.findByShopId(shopId);
        Long templateId = rel == null || rel.getTemplateId() == null
                ? DEFAULT_TEMPLATE_ID : rel.getTemplateId();
        FreightTemplate template = freightTemplateMapper.selectById(templateId);
        if (template == null && !Objects.equals(templateId, DEFAULT_TEMPLATE_ID)) {
            // 绑定模板被删除时回落默认模板，避免门店整店无法下单
            template = freightTemplateMapper.selectById(DEFAULT_TEMPLATE_ID);
        }
        if (template == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "运费模板未配置");
        }
        return template;
    }

    private long distanceFee(int distanceM, FreightTemplate template) {
        int baseDistanceM = template.getBaseDistanceM() == null
                ? DEFAULT_BASE_DISTANCE_M : template.getBaseDistanceM();
        int perStepDistanceM = template.getPerStepDistanceM() == null
                ? DEFAULT_PER_STEP_DISTANCE_M : template.getPerStepDistanceM();
        long perStepFee = nonNegative(template.getPerStepFee());
        if (perStepDistanceM <= 0 || perStepFee == 0L) {
            return 0L;
        }
        int extra = Math.max(0, distanceM - baseDistanceM);
        int steps = (extra + perStepDistanceM - 1) / perStepDistanceM;
        return (long) steps * perStepFee;
    }

    private long timeSurcharge(List<FreightRule> rules, LocalDateTime deliverAt) {
        LocalTime time = (deliverAt == null ? LocalDateTime.now() : deliverAt).toLocalTime();
        long total = 0L;
        for (FreightRule rule : rules) {
            if (!Objects.equals(rule.getRuleType(), RULE_TIME)) {
                continue;
            }
            JsonNode condition = readCondition(rule);
            if (FreightConditionEvaluator.inWindows(condition, time)) {
                total += nonNegative(rule.getFeeDelta());
            }
        }
        return total;
    }

    private long weatherSurcharge(List<FreightRule> rules) {
        long total = 0L;
        for (FreightRule rule : rules) {
            if (!Objects.equals(rule.getRuleType(), RULE_WEATHER)) {
                continue;
            }
            if (FreightConditionEvaluator.weatherEnabled(readCondition(rule))) {
                total += nonNegative(rule.getFeeDelta());
            }
        }
        return total;
    }

    private long weightSurcharge(List<FreightRule> rules, BigDecimal weightKg) {
        if (weightKg == null || weightKg.signum() <= 0) {
            return 0L;
        }
        long total = 0L;
        for (FreightRule rule : rules) {
            if (!Objects.equals(rule.getRuleType(), RULE_WEIGHT)) {
                continue;
            }
            JsonNode condition = readCondition(rule);
            if (condition == null) {
                continue;
            }
            BigDecimal minWeight = BigDecimal.valueOf(
                    condition.path("minWeightKg").asDouble(0D));
            if (weightKg.compareTo(minWeight) <= 0) {
                continue;
            }
            BigDecimal stepKg = BigDecimal.valueOf(condition.path("stepKg").asDouble(1D));
            if (stepKg.signum() <= 0) {
                stepKg = BigDecimal.ONE;
            }
            long units = weightKg.subtract(minWeight)
                    .divide(stepKg, 0, RoundingMode.CEILING).longValue();
            total += units * nonNegative(rule.getFeeDelta());
        }
        return total;
    }

    private long floorSurcharge(List<FreightRule> rules, Integer floor) {
        if (floor == null || floor <= 0) {
            return 0L;
        }
        long total = 0L;
        for (FreightRule rule : rules) {
            if (!Objects.equals(rule.getRuleType(), RULE_FLOOR)) {
                continue;
            }
            JsonNode condition = readCondition(rule);
            int freeFloor = condition == null ? 0 : condition.path("freeFloor").asInt(0);
            if (floor > freeFloor) {
                total += (long) (floor - freeFloor) * nonNegative(rule.getFeeDelta());
            }
        }
        return total;
    }

    private JsonNode readCondition(FreightRule rule) {
        JsonNode condition = FreightConditionEvaluator.parse(rule.getConditionJson());
        if (condition == null && StringUtils.hasText(rule.getConditionJson())) {
            // 单条规则配置损坏不应阻塞整单试算，跳过该规则
            log.warn("freight rule condition_json invalid ruleId={}", rule.getId());
        }
        return condition;
    }

    private static void addIfPositive(List<FreightEstimateView.Item> breakdown,
                                      String name, long amount) {
        if (amount > 0L) {
            breakdown.add(new FreightEstimateView.Item(name, amount));
        }
    }

    // ------------------------------------------------------------------
    // 模板维护
    // ------------------------------------------------------------------

    private void apply(SaveFreightTemplateCommand command, FreightTemplate template) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板参数不能为空");
        }
        String name = command.templateName() == null ? null : command.templateName().strip();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "templateName 不能为空");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "templateName 不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }

        long baseFee = requireAmount(command.baseFee(), "baseFee");
        long perStepFee = requireAmount(command.perStepFee(), "perStepFee");
        Integer baseDistanceM = command.baseDistanceM() == null
                ? DEFAULT_BASE_DISTANCE_M : command.baseDistanceM();
        Integer perStepDistanceM = command.perStepDistanceM() == null
                ? DEFAULT_PER_STEP_DISTANCE_M : command.perStepDistanceM();
        Integer maxDistanceM = command.maxDistanceM() == null
                ? DEFAULT_MAX_DISTANCE_M : command.maxDistanceM();
        if (baseDistanceM <= 0 || perStepDistanceM <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "距离参数必须大于 0");
        }
        if (maxDistanceM < baseDistanceM) {
            throw new BizException(ErrorCode.PARAM_INVALID, "maxDistanceM 不能小于 baseDistanceM");
        }
        Long freeOverAmount = command.freeOverAmount() == null ? null
                : requireAmount(command.freeOverAmount(), "freeOverAmount");
        int status = command.status() == null ? STATUS_ENABLED : command.status();
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 或 1停用");
        }

        template.setTemplateName(name);
        template.setBaseFee(baseFee);
        template.setBaseDistanceM(baseDistanceM);
        template.setPerStepDistanceM(perStepDistanceM);
        template.setPerStepFee(perStepFee);
        template.setMaxDistanceM(maxDistanceM);
        template.setFreeOverAmount(freeOverAmount);
        template.setStatus(status);
    }

    private List<FreightRule> toRules(List<SaveFreightTemplateCommand.Rule> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<FreightRule> rules = new ArrayList<>(commands.size());
        for (SaveFreightTemplateCommand.Rule item : commands) {
            if (item == null || item.ruleType() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "ruleType 不能为空");
            }
            if (item.ruleType() < RULE_DISTANCE || item.ruleType() > RULE_FLOOR) {
                throw new BizException(ErrorCode.PARAM_INVALID, "ruleType 只能是 1~5");
            }
            String conditionJson = normalizeCondition(item.conditionJson());
            int status = item.status() == null ? STATUS_ENABLED : item.status();
            if (status != 0 && status != 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "规则 status 只能是 0 或 1");
            }
            FreightRule rule = new FreightRule();
            rule.setRuleType(item.ruleType());
            rule.setConditionJson(conditionJson);
            rule.setFeeDelta(requireAmount(item.feeDelta(), "feeDelta"));
            rule.setSort(item.sort() == null ? 0 : item.sort());
            rule.setStatus(status);
            rules.add(rule);
        }
        return rules;
    }

    private String normalizeCondition(String conditionJson) {
        if (!StringUtils.hasText(conditionJson)) {
            return null;
        }
        try {
            // 先解析再写库：非法 JSON 在保存期报错，避免试算期静默跳过
            objectMapper.readTree(conditionJson);
            return conditionJson;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "conditionJson 不是合法 JSON");
        }
    }

    private void insertRules(Long templateId, List<FreightRule> rules) {
        for (FreightRule rule : rules) {
            rule.setTemplateId(templateId);
            freightRuleMapper.insert(rule);
        }
    }

    private FreightTemplate reload(FreightTemplate template) {
        FreightTemplate saved = template.getId() == null
                ? null : freightTemplateMapper.selectById(template.getId());
        return saved == null ? template : saved;
    }

    private List<FreightRule> loadRules(Long templateId) {
        if (templateId == null) {
            return List.of();
        }
        return freightRuleMapper.selectList(new LambdaQueryWrapper<FreightRule>()
                .eq(FreightRule::getTemplateId, templateId)
                .eq(FreightRule::getStatus, STATUS_ENABLED)
                .orderByAsc(FreightRule::getSort)
                .orderByAsc(FreightRule::getId));
    }

    private List<FreightTemplateView> assemble(List<FreightTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        List<Long> templateIds = templates.stream()
                .map(FreightTemplate::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<FreightRule>> rulesByTemplate = new HashMap<>(templateIds.size());
        if (!templateIds.isEmpty()) {
            rulesByTemplate = freightRuleMapper.selectList(new LambdaQueryWrapper<FreightRule>()
                            .in(FreightRule::getTemplateId, templateIds)
                            .orderByAsc(FreightRule::getSort)
                            .orderByAsc(FreightRule::getId))
                    .stream()
                    .collect(Collectors.groupingBy(FreightRule::getTemplateId));
        }
        List<FreightTemplateView> views = new ArrayList<>(templates.size());
        for (FreightTemplate template : templates) {
            views.add(toView(template,
                    rulesByTemplate.getOrDefault(template.getId(), List.of())));
        }
        return views;
    }

    private static FreightTemplateView toView(FreightTemplate template, List<FreightRule> rules) {
        List<FreightTemplateView.Rule> ruleViews = rules.stream()
                .map(rule -> new FreightTemplateView.Rule(
                        rule.getId(),
                        rule.getRuleType(),
                        rule.getConditionJson(),
                        rule.getFeeDelta(),
                        rule.getSort(),
                        rule.getStatus()))
                .toList();
        return new FreightTemplateView(
                template.getId(),
                template.getTemplateName(),
                nonNegative(template.getBaseFee()),
                template.getBaseDistanceM() == null
                        ? DEFAULT_BASE_DISTANCE_M : template.getBaseDistanceM(),
                template.getPerStepDistanceM() == null
                        ? DEFAULT_PER_STEP_DISTANCE_M : template.getPerStepDistanceM(),
                nonNegative(template.getPerStepFee()),
                template.getMaxDistanceM() == null
                        ? DEFAULT_MAX_DISTANCE_M : template.getMaxDistanceM(),
                template.getFreeOverAmount(),
                template.getStatus(),
                ruleViews,
                template.getUpdatedAt());
    }

    private static long requireAmount(Long amount, String field) {
        if (amount == null || amount < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空且不能为负数");
        }
        return amount;
    }

    private static long nonNegative(Long value) {
        return value == null || value < 0L ? 0L : value;
    }

    private static void validateCoordinate(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "经纬度不能为空");
        }
        if (lng.compareTo(new BigDecimal("-180")) < 0
                || lng.compareTo(new BigDecimal("180")) > 0
                || lat.compareTo(new BigDecimal("-90")) < 0
                || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "经纬度超出合法范围");
        }
    }
}
