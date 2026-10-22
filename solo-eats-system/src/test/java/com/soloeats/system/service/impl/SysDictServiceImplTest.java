package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysDictItem;
import com.soloeats.system.domain.entity.SysDictType;
import com.soloeats.system.mapper.SysDictItemMapper;
import com.soloeats.system.mapper.SysDictTypeMapper;
import com.soloeats.system.service.command.SaveDictItemCommand;
import com.soloeats.system.service.view.DictItemView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysDictServiceImplTest {

    private static final long TENANT_ID = 1L;
    private static final String DICT_TYPE = "order_status";
    private static final String CACHE_KEY = "dict:1:order_status";

    @Mock
    private SysDictTypeMapper sysDictTypeMapper;

    @Mock
    private SysDictItemMapper sysDictItemMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 与 Spring Boot 注入的 ObjectMapper 一致：注册 JavaTimeModule 才能反序列化 LocalDateTime。 */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private SysDictServiceImpl sysDictService;

    /**
     * 纯 Mockito 环境没有 MyBatis 启动流程，LambdaQueryWrapper 解析列名依赖 TableInfo 缓存。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysDictType.class);
        TableInfoHelper.initTableInfo(assistant, SysDictItem.class);
    }

    @BeforeEach
    void setUp() {
        sysDictService = new SysDictServiceImpl(
                sysDictTypeMapper, sysDictItemMapper, redisTemplate, objectMapper);
        // 不是每个用例都会读缓存，放宽该桩避免 UnnecessaryStubbingException
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("缓存未命中：查库后按 dict:{tenantId}:{dictType} 写入 1 小时缓存")
    void listItemsWritesCacheOnMiss() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(sysDictItemMapper.selectList(any())).thenReturn(List.of(
                item(1L, "待支付", "0", 0),
                item(2L, "待接单", "10", 1)));

        List<DictItemView> items = sysDictService.listItems(DICT_TYPE);

        assertThat(items).extracting(DictItemView::dictValue).containsExactly("0", "10");
        verify(valueOperations).set(eq(CACHE_KEY), anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("缓存命中：直接返回缓存，不再查库")
    void listItemsReturnsCacheOnHit() throws Exception {
        String json = objectMapper.writeValueAsString(
                List.of(new DictItemView(9L, DICT_TYPE, "待支付", "0", 1, null, 1, 0, null, null, null)));
        when(valueOperations.get(CACHE_KEY)).thenReturn(json);

        List<DictItemView> items = sysDictService.listItems(DICT_TYPE);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).dictValue()).isEqualTo("0");
        verifyNoInteractions(sysDictItemMapper);
    }

    @Test
    @DisplayName("新增默认字典项：清除同类型其他默认项并删除缓存")
    void createDefaultItemClearsOthersAndEvictsCache() {
        when(sysDictTypeMapper.selectCount(any())).thenReturn(1L);
        when(sysDictItemMapper.selectCount(any())).thenReturn(0L);
        when(sysDictItemMapper.insert(any(SysDictItem.class))).thenReturn(1);

        SaveDictItemCommand command = new SaveDictItemCommand(
                DICT_TYPE, "待支付", "0", 1, null, 1, 0, null);

        DictItemView view = sysDictService.createItem(command);

        assertThat(view.isDefault()).isEqualTo(1);
        assertThat(view.dictLabel()).isEqualTo("待支付");
        verify(sysDictItemMapper).update(isNull(), any());
        verify(redisTemplate).delete(CACHE_KEY);

        ArgumentCaptor<SysDictItem> captor = ArgumentCaptor.forClass(SysDictItem.class);
        verify(sysDictItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("dictValue 重复：抛 PARAM_INVALID，不写库也不动缓存")
    void createItemRejectsDuplicateValue() {
        when(sysDictTypeMapper.selectCount(any())).thenReturn(1L);
        when(sysDictItemMapper.selectCount(any())).thenReturn(1L);

        SaveDictItemCommand command = new SaveDictItemCommand(
                DICT_TYPE, "待支付", "0", 1, null, 0, 0, null);

        assertThatThrownBy(() -> sysDictService.createItem(command))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        verify(sysDictItemMapper, never()).insert(any(SysDictItem.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    private SysDictItem item(Long id, String label, String value, int sort) {
        SysDictItem item = new SysDictItem();
        item.setId(id);
        item.setTenantId(TENANT_ID);
        item.setDictType(DICT_TYPE);
        item.setDictLabel(label);
        item.setDictValue(value);
        item.setDictSort(sort);
        item.setIsDefault(0);
        item.setStatus(0);
        item.setDeleted(0);
        return item;
    }
}
