package com.soloeats.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.store.domain.entity.Shop;
import com.soloeats.store.domain.entity.ShopDecoration;
import com.soloeats.store.mapper.ShopDecorationMapper;
import com.soloeats.store.mapper.ShopMapper;
import com.soloeats.store.service.ShopDecorationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopDecorationServiceImpl implements ShopDecorationService {

    private static final int MAX_CONFIG_LENGTH = 20_000;
    private static final String EMPTY_OBJECT = "{}";

    private final ShopMapper shopMapper;
    private final ShopDecorationMapper decorationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String get(Long shopId, Long merchantId) {
        requireOwnedShop(shopId, merchantId);
        ShopDecoration decoration = findDecoration(shopId);
        return decoration == null || !StringUtils.hasText(decoration.getConfigJson())
                ? EMPTY_OBJECT
                : decoration.getConfigJson();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(Long shopId, Long merchantId, String configJson) {
        Shop shop = requireOwnedShop(shopId, merchantId);
        String normalized = validateConfig(configJson);

        ShopDecoration decoration = findDecoration(shopId);
        if (decoration == null) {
            decoration = new ShopDecoration();
            decoration.setTenantId(shop.getTenantId());
            decoration.setShopId(shopId);
            decoration.setConfigJson(normalized);
            decoration.setStatus(0);
            decoration.setDeleted(0);
            decorationMapper.insert(decoration);
        } else {
            decoration.setConfigJson(normalized);
            if (decorationMapper.updateById(decoration) == 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "装修配置已被修改，请刷新后重试");
            }
        }
        log.info("shop decoration saved shopId={}", shopId);
        return normalized;
    }

    private String validateConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "configJson 不能为空");
        }
        if (configJson.length() > MAX_CONFIG_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "configJson 长度不能超过 " + MAX_CONFIG_LENGTH + " 字符");
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (node == null || !node.isObject()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "configJson 必须是 JSON 对象");
            }
            return objectMapper.writeValueAsString(node);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "configJson 不是合法 JSON");
        }
    }

    private ShopDecoration findDecoration(Long shopId) {
        return decorationMapper.selectOne(new LambdaQueryWrapper<ShopDecoration>()
                .eq(ShopDecoration::getShopId, shopId)
                .eq(ShopDecoration::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private Shop requireOwnedShop(Long shopId, Long merchantId) {
        if (merchantId == null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "令牌缺少 merchantId");
        }
        Shop shop = shopId == null ? null : shopMapper.selectById(shopId);
        if (shop == null || !Objects.equals(shop.getDeleted(), 0)) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND);
        }
        if (!Objects.equals(shop.getMerchantId(), merchantId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权操作其他商家的门店");
        }
        return shop;
    }
}
