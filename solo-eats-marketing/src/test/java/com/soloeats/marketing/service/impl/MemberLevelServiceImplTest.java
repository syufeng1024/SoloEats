package com.soloeats.marketing.service.impl;

import com.soloeats.marketing.domain.entity.MemberLevel;
import com.soloeats.marketing.domain.entity.UserGrowthSnapshot;
import com.soloeats.marketing.mapper.MemberGrowthLogMapper;
import com.soloeats.marketing.mapper.MemberLevelMapper;
import com.soloeats.marketing.mapper.MemberUserGrowthMapper;
import com.soloeats.marketing.service.view.MemberProfileView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员等级与成长值单测。等级门槛从高到低匹配，边界取「大于等于」。
 */
@ExtendWith(MockitoExtension.class)
class MemberLevelServiceImplTest {

    private static final long USER_ID = 2001L;
    private static final int RECALC_BATCH_SIZE = 500;

    @Mock
    private MemberLevelMapper memberLevelMapper;

    @Mock
    private MemberGrowthLogMapper memberGrowthLogMapper;

    @Mock
    private MemberUserGrowthMapper memberUserGrowthMapper;

    @InjectMocks
    private MemberLevelServiceImpl memberLevelService;

    // ---------------------------------------------------------------
    // 等级匹配
    // ---------------------------------------------------------------

    @Test
    @DisplayName("成长值恰等于门槛：命中该档（边界包含）")
    void growthEqualsThresholdHitsThatLevel() {
        when(memberGrowthLogMapper.sumGrowthSince(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(5000);
        when(memberLevelMapper.selectEnabledDesc()).thenReturn(levelsDesc());

        MemberProfileView profile = memberLevelService.profile(USER_ID);

        assertThat(profile.growth()).isEqualTo(5000);
        assertThat(profile.levelCode()).isEqualTo("V4");
        assertThat(profile.levelNo()).isEqualTo(4);
        assertThat(profile.nextThreshold()).isEqualTo(10000);
        assertThat(profile.growthToNext()).isEqualTo(5000);
        assertThat(profile.maxLevel()).isFalse();
    }

    @Test
    @DisplayName("成长值处于两档之间：命中较低那档")
    void growthBetweenThresholdsHitsLowerLevel() {
        when(memberGrowthLogMapper.sumGrowthSince(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(7000);
        when(memberLevelMapper.selectEnabledDesc()).thenReturn(levelsDesc());

        MemberProfileView profile = memberLevelService.profile(USER_ID);

        assertThat(profile.levelCode()).isEqualTo("V4");
        assertThat(profile.growth()).isEqualTo(7000);
        assertThat(profile.nextThreshold()).isEqualTo(10000);
        assertThat(profile.growthToNext()).isEqualTo(3000);
        assertThat(profile.maxLevel()).isFalse();
    }

    @Test
    @DisplayName("已是最高档：growthToNext=0 且 maxLevel=true")
    void highestLevelReturnsZeroGrowthToNext() {
        when(memberGrowthLogMapper.sumGrowthSince(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(25000);
        when(memberLevelMapper.selectEnabledDesc()).thenReturn(levelsDesc());

        MemberProfileView profile = memberLevelService.profile(USER_ID);

        assertThat(profile.levelCode()).isEqualTo("V6");
        assertThat(profile.growth()).isEqualTo(25000);
        assertThat(profile.nextThreshold()).isZero();
        assertThat(profile.growthToNext()).isZero();
        assertThat(profile.maxLevel()).isTrue();
    }

    @Test
    @DisplayName("负成长值：仍命中最低档 V1，距 V2 差 600")
    void negativeGrowthFallsBackToLowestLevel() {
        when(memberGrowthLogMapper.sumGrowthSince(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(-100);
        when(memberLevelMapper.selectEnabledDesc()).thenReturn(levelsDesc());

        MemberProfileView profile = memberLevelService.profile(USER_ID);

        assertThat(profile.growth()).isEqualTo(-100);
        assertThat(profile.levelId()).isEqualTo(1L);
        assertThat(profile.levelCode()).isEqualTo("V1");
        assertThat(profile.levelNo()).isEqualTo(1);
        assertThat(profile.nextThreshold()).isEqualTo(500);
        assertThat(profile.growthToNext()).isEqualTo(600);
        assertThat(profile.maxLevel()).isFalse();
    }

    @Test
    @DisplayName("成长值为 0：命中门槛为 0 的最低档")
    void zeroGrowthHitsLowestLevel() {
        when(memberGrowthLogMapper.sumGrowthSince(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        when(memberLevelMapper.selectEnabledDesc()).thenReturn(levelsDesc());

        MemberProfileView profile = memberLevelService.profile(USER_ID);

        assertThat(profile.levelCode()).isEqualTo("V1");
        assertThat(profile.levelNo()).isEqualTo(1);
        assertThat(profile.nextThreshold()).isEqualTo(500);
        assertThat(profile.growthToNext()).isEqualTo(500);
        assertThat(profile.maxLevel()).isFalse();
    }

    // ---------------------------------------------------------------
    // 全量重算分页
    // ---------------------------------------------------------------

    @Test
    @DisplayName("recalcAll：按 lastId 游标每批 500，不一次性 selectList 全量用户")
    void recalcAllScansByLastIdCursor() {
        List<UserGrowthSnapshot> firstBatch = new ArrayList<>(RECALC_BATCH_SIZE);
        for (long id = 1; id <= RECALC_BATCH_SIZE; id++) {
            firstBatch.add(snapshot(id));
        }
        List<UserGrowthSnapshot> secondBatch = List.of(snapshot(501L));

        when(memberLevelMapper.selectEnabledDesc()).thenReturn(levelsDesc());
        when(memberGrowthLogMapper.selectUserGrowthBatch(
                any(LocalDateTime.class), eq(0L), eq(RECALC_BATCH_SIZE)))
                .thenReturn(firstBatch);
        when(memberGrowthLogMapper.selectUserGrowthBatch(
                any(LocalDateTime.class), eq(500L), eq(RECALC_BATCH_SIZE)))
                .thenReturn(secondBatch);
        when(memberUserGrowthMapper.updateGrowthAndLevel(anyLong(), anyInt(), any()))
                .thenReturn(1);

        int changed = memberLevelService.recalcAll();

        assertThat(changed).isEqualTo(RECALC_BATCH_SIZE + 1);

        // 第一批 lastId=0；第二批 lastId=第一批最后一条的 500
        verify(memberGrowthLogMapper).selectUserGrowthBatch(
                any(LocalDateTime.class), eq(0L), eq(RECALC_BATCH_SIZE));
        verify(memberGrowthLogMapper).selectUserGrowthBatch(
                any(LocalDateTime.class), eq(500L), eq(RECALC_BATCH_SIZE));
        verify(memberUserGrowthMapper, times(RECALC_BATCH_SIZE + 1))
                .updateGrowthAndLevel(anyLong(), anyInt(), any());

        // 明确禁止全量拉取用户
        verify(memberGrowthLogMapper, never()).selectList(any());
        verify(memberLevelMapper, never()).selectList(any());
    }

    // ---------------------------------------------------------------
    // 辅助
    // ---------------------------------------------------------------

    private static List<MemberLevel> levelsDesc() {
        return List.of(
                level(6L, "V6", "钻石", 6, 20000),
                level(5L, "V5", "铂金", 5, 10000),
                level(4L, "V4", "黄金", 4, 5000),
                level(3L, "V3", "白银", 3, 2000),
                level(2L, "V2", "青铜", 2, 500),
                level(1L, "V1", "黑铁", 1, 0));
    }

    private static MemberLevel level(long id, String code, String name, int no, int threshold) {
        MemberLevel level = new MemberLevel();
        level.setId(id);
        level.setLevelCode(code);
        level.setLevelName(name);
        level.setLevelNo(no);
        level.setGrowthThreshold(threshold);
        level.setStar(1);
        level.setStatus(0);
        return level;
    }

    private static UserGrowthSnapshot snapshot(long userId) {
        UserGrowthSnapshot snapshot = new UserGrowthSnapshot();
        snapshot.setUserId(userId);
        snapshot.setOldGrowthValue(-1);
        snapshot.setOldMemberLevelId(null);
        snapshot.setGrowth(0);
        return snapshot;
    }
}
