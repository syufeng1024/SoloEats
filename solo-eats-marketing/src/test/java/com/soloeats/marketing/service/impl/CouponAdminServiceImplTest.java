package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.CouponTemplate;
import com.soloeats.marketing.domain.entity.UserCoupon;
import com.soloeats.marketing.mapper.CouponTemplateMapper;
import com.soloeats.marketing.mapper.UserCouponMapper;
import com.soloeats.marketing.service.command.CouponTemplateSaveCommand;
import com.soloeats.marketing.service.view.CouponGrantRecordView;
import com.soloeats.marketing.service.view.CouponTemplateView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponAdminServiceImplTest {

    private static final long TEMPLATE_ID = 2001L;
    private static final long USER_A = 1001L;
    private static final long USER_B = 1002L;

    @Mock
    private CouponTemplateMapper couponTemplateMapper;

    @Mock
    private UserCouponMapper userCouponMapper;

    @InjectMocks
    private CouponAdminServiceImpl couponAdminService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不启动 Spring，Lambda 列名解析需要显式注册实体列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CouponTemplate.class);
        TableInfoHelper.initTableInfo(assistant, UserCoupon.class);
    }

    // ---------------------------------------------------------------
    // 新建
    // ---------------------------------------------------------------

    @Test
    @DisplayName("新建满减券：issuedQty 固定 0、status 固定 0，不接受外部传入")
    void createInitializesIssuedQtyAndStatus() {
        when(couponTemplateMapper.insert(any(CouponTemplate.class))).thenAnswer(invocation -> {
            ((CouponTemplate) invocation.getArgument(0)).setId(TEMPLATE_ID);
            return 1;
        });

        CouponTemplateView view = couponAdminService.create(new CouponTemplateSaveCommand(
                "  新人5元  ", 3, 0L, 500L, null, 1, 100, null, null, 7));

        assertThat(view.id()).isEqualTo(TEMPLATE_ID);
        assertThat(view.name()).isEqualTo("新人5元");
        assertThat(view.issuedQty()).isZero();
        assertThat(view.totalQty()).isEqualTo(100);
        assertThat(view.status()).isZero();
        assertThat(view.discountAmount()).isEqualTo(500L);
        assertThat(view.validDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("新建折扣券：discountRate 按 4 位小数 HALF_UP 落库")
    void createRoundsRateToFourScale() {
        when(couponTemplateMapper.insert(any(CouponTemplate.class)))
                .thenAnswer(invocation -> 1);

        CouponTemplateView view = couponAdminService.create(new CouponTemplateSaveCommand(
                "8.5折", 2, 3000L, null, new BigDecimal("0.85555"), 1, 100, null, null, null));

        assertThat(view.discountRate()).isEqualTo(new BigDecimal("0.8556"));
    }

    @Test
    @DisplayName("折扣券未配折扣率时拒绝新建")
    void createRateCouponWithoutRateRejected() {
        assertBizCode(() -> couponAdminService.create(new CouponTemplateSaveCommand(
                        "8.5折", 2, 3000L, null, null, 1, 100, null, null, null)),
                ErrorCode.PARAM_INVALID);
        verify(couponTemplateMapper, never()).insert(any(CouponTemplate.class));
    }

    @Test
    @DisplayName("折扣率超出 (0,1] 时拒绝新建")
    void createRateAboveOneRejected() {
        assertBizCode(() -> couponAdminService.create(new CouponTemplateSaveCommand(
                        "1.5折", 2, 3000L, null, new BigDecimal("1.5000"), 1, 100, null, null, null)),
                ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("模板名为空白时拒绝新建")
    void createBlankNameRejected() {
        assertBizCode(() -> couponAdminService.create(new CouponTemplateSaveCommand(
                        "   ", 1, 3000L, 500L, null, 1, 100, null, null, null)),
                ErrorCode.PARAM_INVALID);
        verify(couponTemplateMapper, never()).insert(any(CouponTemplate.class));
    }

    @Test
    @DisplayName("新建时不传 couponType 拒绝")
    void createWithoutCouponTypeRejected() {
        assertBizCode(() -> couponAdminService.create(new CouponTemplateSaveCommand(
                        "新人5元", null, 0L, 500L, null, 1, 100, null, null, null)),
                ErrorCode.PARAM_INVALID);
    }

    // ---------------------------------------------------------------
    // 查询与编辑
    // ---------------------------------------------------------------

    @Test
    @DisplayName("模板不存在时详情返回 40210")
    void detailMissingTemplate() {
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(null);

        assertBizCode(() -> couponAdminService.detail(TEMPLATE_ID),
                ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
    }

    @Test
    @DisplayName("编辑 totalQty 小于已发放数量时拒绝，不发起更新")
    void updateRejectsTotalQtyBelowIssued() {
        CouponTemplate existing = template();
        existing.setIssuedQty(10);
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(existing);

        assertBizCode(() -> couponAdminService.update(TEMPLATE_ID, command(5)),
                ErrorCode.PARAM_INVALID);
        verify(couponTemplateMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("编辑条件更新未命中且模板已消失时返回 40210")
    void updateConditionalMissOnMissingRow() {
        when(couponTemplateMapper.selectById(TEMPLATE_ID))
                .thenReturn(template(), (CouponTemplate) null);
        when(couponTemplateMapper.update(isNull(), any())).thenReturn(0);

        assertBizCode(() -> couponAdminService.update(TEMPLATE_ID, command(100)),
                ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
    }

    @Test
    @DisplayName("编辑只写可编辑字段：SET 不含 coupon_type/bearer/status/issued_qty")
    void updateWritesOnlyEditableColumns() {
        CouponTemplate existing = template();
        existing.setIssuedQty(10);
        CouponTemplate updated = template();
        updated.setName("新人6元");
        updated.setDiscountAmount(600L);
        updated.setTotalQty(200);
        updated.setIssuedQty(10);
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(existing, updated);
        when(couponTemplateMapper.update(isNull(), any())).thenReturn(1);

        CouponTemplateView view = couponAdminService.update(TEMPLATE_ID, new CouponTemplateSaveCommand(
                "新人6元", 9, 3000L, 600L, new BigDecimal("0.90000"), 2, 200,
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0), null));

        assertThat(view.name()).isEqualTo("新人6元");
        assertThat(view.totalQty()).isEqualTo(200);
        assertThat(view.issuedQty()).isEqualTo(10);

        ArgumentCaptor<LambdaUpdateWrapper<CouponTemplate>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(couponTemplateMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet())
                .contains("name")
                .contains("threshold_amount")
                .contains("discount_amount")
                .contains("discount_rate")
                .contains("total_qty")
                .contains("valid_start")
                .contains("valid_end")
                .contains("valid_days")
                .doesNotContain("coupon_type")
                .doesNotContain("bearer")
                .doesNotContain("status")
                .doesNotContain("issued_qty");
    }

    @Test
    @DisplayName("启停只接受 0/1，其他值拒绝")
    void updateStatusRejectsUnknownValue() {
        assertBizCode(() -> couponAdminService.updateStatus(TEMPLATE_ID, 2),
                ErrorCode.PARAM_INVALID);
        verify(couponTemplateMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("启停更新未命中时返回 40210")
    void updateStatusMissingTemplate() {
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template());
        when(couponTemplateMapper.update(isNull(), any())).thenReturn(0);

        assertBizCode(() -> couponAdminService.updateStatus(TEMPLATE_ID, 1),
                ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
    }

    // ---------------------------------------------------------------
    // 定向发券
    // ---------------------------------------------------------------

    @Test
    @DisplayName("发券成功：valid_days 计算过期时间，逐人插 0 状态用户券")
    void grantIssuesCouponsWithValidDaysExpiry() {
        CouponTemplate template = template();
        template.setTotalQty(100);
        template.setValidDays(7);
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template);
        when(couponTemplateMapper.deductIssueQty(TEMPLATE_ID, 2)).thenReturn(1);

        LocalDateTime before = LocalDateTime.now();
        int issued = couponAdminService.grant(TEMPLATE_ID, List.of(USER_A, USER_B));

        assertThat(issued).isEqualTo(2);
        ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserCoupon::getUserId)
                .containsExactly(USER_A, USER_B);
        assertThat(captor.getAllValues()).allSatisfy(coupon -> {
            assertThat(coupon.getTemplateId()).isEqualTo(TEMPLATE_ID);
            assertThat(coupon.getStatus()).isZero();
            assertThat(coupon.getExpireAt())
                    .isAfter(before.plusDays(6))
                    .isBefore(before.plusDays(8));
        });
    }

    @Test
    @DisplayName("重复 userId 去重后按人数占用额度")
    void grantDeduplicatesUserIds() {
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template());
        when(couponTemplateMapper.deductIssueQty(TEMPLATE_ID, 2)).thenReturn(1);

        int issued = couponAdminService.grant(TEMPLATE_ID, List.of(USER_A, USER_A, USER_B));

        assertThat(issued).isEqualTo(2);
        verify(couponTemplateMapper).deductIssueQty(TEMPLATE_ID, 2);
        verify(userCouponMapper, times(2)).insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("userIds 为空时拒绝发券")
    void grantRejectsEmptyUserIds() {
        assertBizCode(() -> couponAdminService.grant(TEMPLATE_ID, List.of()),
                ErrorCode.PARAM_INVALID);
        verify(couponTemplateMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("去重后超过 500 人拒绝发券")
    void grantRejectsMoreThan500Users() {
        List<Long> userIds = new ArrayList<>();
        for (long i = 1L; i <= 501L; i++) {
            userIds.add(i);
        }

        assertBizCode(() -> couponAdminService.grant(TEMPLATE_ID, userIds),
                ErrorCode.PARAM_INVALID);
        verify(couponTemplateMapper, never()).deductIssueQty(anyLong(), anyInt());
    }

    @Test
    @DisplayName("停用模板禁止发券：40212，且不占用额度")
    void grantRejectsDisabledTemplate() {
        CouponTemplate template = template();
        template.setStatus(1);
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template);

        assertBizCode(() -> couponAdminService.grant(TEMPLATE_ID, List.of(USER_A)),
                ErrorCode.COUPON_TEMPLATE_STATUS_INVALID);
        verify(couponTemplateMapper, never()).deductIssueQty(anyLong(), anyInt());
        verify(userCouponMapper, never()).insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("额度不足：条件更新影响 0 行抛 40211，整批不落库")
    void grantRejectsWhenQuotaNotEnough() {
        CouponTemplate template = template();
        template.setTotalQty(1);
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template);
        when(couponTemplateMapper.deductIssueQty(TEMPLATE_ID, 2)).thenReturn(0);

        assertBizCode(() -> couponAdminService.grant(TEMPLATE_ID, List.of(USER_A, USER_B)),
                ErrorCode.COUPON_STOCK_NOT_ENOUGH);
        verify(userCouponMapper, never()).insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("无 valid_days 时过期时间回落模板 valid_end")
    void grantFallsBackToValidEnd() {
        LocalDateTime validEnd = LocalDateTime.now().plusDays(3);
        CouponTemplate template = template();
        template.setValidDays(null);
        template.setValidEnd(validEnd);
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template);
        when(couponTemplateMapper.deductIssueQty(TEMPLATE_ID, 1)).thenReturn(1);

        couponAdminService.grant(TEMPLATE_ID, List.of(USER_A));

        ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponMapper).insert(captor.capture());
        assertThat(captor.getValue().getExpireAt()).isEqualTo(validEnd);
    }

    @Test
    @DisplayName("valid_days 与 valid_end 都为空时拒绝发券")
    void grantRejectsWithoutValidity() {
        CouponTemplate template = template();
        template.setValidDays(null);
        template.setValidEnd(null);
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template);

        assertBizCode(() -> couponAdminService.grant(TEMPLATE_ID, List.of(USER_A)),
                ErrorCode.COUPON_TEMPLATE_STATUS_INVALID);
        verify(couponTemplateMapper, never()).deductIssueQty(anyLong(), anyInt());
    }

    @Test
    @DisplayName("valid_end 已过期时拒绝发券，避免发出即失效的券")
    void grantRejectsExpiredValidEnd() {
        CouponTemplate template = template();
        template.setValidDays(null);
        template.setValidEnd(LocalDateTime.now().minusDays(1));
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template);

        assertBizCode(() -> couponAdminService.grant(TEMPLATE_ID, List.of(USER_A)),
                ErrorCode.COUPON_TEMPLATE_STATUS_INVALID);
    }

    // ---------------------------------------------------------------
    // 分页
    // ---------------------------------------------------------------

    @Test
    @DisplayName("模板分页：pageNum 0 归一为 1，pageSize 999 截断为 100，筛选条件落到 WHERE")
    void pageNormalizesPagingAndFilters() {
        stubTemplatePage(List.of(template()));

        PageResult<CouponTemplateView> result =
                couponAdminService.page("新人", 0, 1, 1, 0, 999);

        assertThat(result.pageNum()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(100);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list()).singleElement()
                .satisfies(view -> assertThat(view.id()).isEqualTo(TEMPLATE_ID));

        LambdaQueryWrapper<CouponTemplate> wrapper = captureTemplateWrapper();
        assertThat(wrapper.getSqlSegment())
                .contains("status")
                .contains("coupon_type")
                .contains("bearer")
                .contains("name");
    }

    @Test
    @DisplayName("模板分页：非法 status 筛选值拒绝")
    void pageRejectsUnknownStatusFilter() {
        assertBizCode(() -> couponAdminService.page(null, 9, null, null, 1, 20),
                ErrorCode.PARAM_INVALID);
        verify(couponTemplateMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("领取记录分页：按模板与状态筛选，模板不存在时 40210")
    void recordsFiltersByStatus() {
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(template());
        UserCoupon coupon = new UserCoupon();
        coupon.setId(3001L);
        coupon.setUserId(USER_A);
        coupon.setTemplateId(TEMPLATE_ID);
        coupon.setStatus(2);
        coupon.setExpireAt(LocalDateTime.now().plusDays(1));
        stubRecordPage(List.of(coupon));

        PageResult<CouponGrantRecordView> result =
                couponAdminService.records(TEMPLATE_ID, 2, 1, 20);

        assertThat(result.list()).singleElement().satisfies(view -> {
            assertThat(view.userId()).isEqualTo(USER_A);
            assertThat(view.status()).isEqualTo(2);
        });
        assertThat(captureRecordWrapper().getSqlSegment())
                .contains("template_id")
                .contains("status");
    }

    @Test
    @DisplayName("领取记录分页：模板不存在返回 40210，不查用户券")
    void recordsRejectsMissingTemplate() {
        when(couponTemplateMapper.selectById(TEMPLATE_ID)).thenReturn(null);

        assertBizCode(() -> couponAdminService.records(TEMPLATE_ID, null, 1, 20),
                ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        verify(userCouponMapper, never()).selectPage(any(), any());
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }

    private CouponTemplateSaveCommand command(int totalQty) {
        return new CouponTemplateSaveCommand(
                "新人5元", 1, 0L, 500L, null, 1, totalQty, null, null, 7);
    }

    private CouponTemplate template() {
        CouponTemplate template = new CouponTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("新人5元");
        template.setCouponType(1);
        template.setThresholdAmount(0L);
        template.setDiscountAmount(500L);
        template.setBearer(1);
        template.setTotalQty(100);
        template.setIssuedQty(0);
        template.setValidDays(7);
        template.setStatus(0);
        return template;
    }

    @SuppressWarnings("unchecked")
    private void stubTemplatePage(List<CouponTemplate> templates) {
        when(couponTemplateMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<CouponTemplate> page = invocation.getArgument(0);
            page.setRecords(templates);
            page.setTotal(templates.size());
            return page;
        });
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<CouponTemplate> captureTemplateWrapper() {
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<CouponTemplate>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(couponTemplateMapper).selectPage(any(Page.class), captor.capture());
        return (LambdaQueryWrapper<CouponTemplate>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private void stubRecordPage(List<UserCoupon> coupons) {
        when(userCouponMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<UserCoupon> page = invocation.getArgument(0);
            page.setRecords(coupons);
            page.setTotal(coupons.size());
            return page;
        });
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<UserCoupon> captureRecordWrapper() {
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<UserCoupon>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(userCouponMapper).selectPage(any(Page.class), captor.capture());
        return (LambdaQueryWrapper<UserCoupon>) captor.getValue();
    }
}
