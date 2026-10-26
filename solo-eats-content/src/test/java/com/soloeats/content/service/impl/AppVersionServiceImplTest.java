package com.soloeats.content.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.content.domain.entity.AppVersion;
import com.soloeats.content.mapper.AppVersionMapper;
import com.soloeats.content.service.view.AppVersionCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceImplTest {

    @Mock
    private AppVersionMapper versionMapper;

    private AppVersionServiceImpl appVersionService;

    @BeforeEach
    void setUp() {
        appVersionService = new AppVersionServiceImpl(versionMapper);
    }

    @Test
    @DisplayName("灰度 0：100 个用户 + 100 个设备全部不投放")
    void grayZeroReleasesNobody() {
        for (long userId = 1L; userId <= 100L; userId++) {
            assertThat(AppVersionServiceImpl.grayHit(0, userId, null)).isFalse();
        }
        for (int index = 0; index < 100; index++) {
            assertThat(AppVersionServiceImpl.grayHit(0, null, "device-" + index)).isFalse();
        }
        assertThat(AppVersionServiceImpl.grayHit(null, 1L, null)).isFalse();
    }

    @Test
    @DisplayName("灰度 100：100 个用户全部投放，匿名设备也全量")
    void grayHundredReleasesEverybody() {
        for (long userId = 1L; userId <= 100L; userId++) {
            assertThat(AppVersionServiceImpl.grayHit(100, userId, null)).isTrue();
        }
        for (int index = 0; index < 100; index++) {
            assertThat(AppVersionServiceImpl.grayHit(100, null, "device-" + index)).isTrue();
        }
    }

    @Test
    @DisplayName("中间值：userId % 100 < grayPercent，100 个用户命中数恰好等于百分比")
    void middleGrayUsesUserIdModulo() {
        int grayPercent = 37;
        int hits = 0;
        for (long userId = 1L; userId <= 100L; userId++) {
            boolean expected = Math.floorMod(userId, 100L) < grayPercent;
            boolean actual = AppVersionServiceImpl.grayHit(grayPercent, userId, "ignored");
            assertThat(actual).as("userId=%s", userId).isEqualTo(expected);
            if (actual) {
                hits++;
            }
        }
        assertThat(hits).isEqualTo(grayPercent);
    }

    @Test
    @DisplayName("匿名设备：FNV hash 稳定，取模结果可复现且不随调用变化")
    void anonymousDeviceBucketIsStable() {
        for (int index = 0; index < 100; index++) {
            String deviceId = "device-" + index;
            int expectedBucket = Math.floorMod(
                    AppVersionServiceImpl.stableHash(deviceId), 100);
            for (int percent : new int[]{1, 30, 50, 99}) {
                assertThat(AppVersionServiceImpl.grayHit(percent, null, deviceId))
                        .as("device=%s percent=%s", deviceId, percent)
                        .isEqualTo(expectedBucket < percent);
            }
        }
    }

    @Test
    @DisplayName("同一设备重复请求结果一致；登录用户优先用 userId，不受 deviceId 影响")
    void identityPrecedenceIsDeterministic() {
        assertThat(AppVersionServiceImpl.grayHit(50, null, "stable-device"))
                .isEqualTo(AppVersionServiceImpl.grayHit(50, null, "stable-device"));
        // userId=200 -> bucket 0，任何 percent>=1 都命中，deviceId 不参与
        assertThat(AppVersionServiceImpl.grayHit(1, 200L, "not-used")).isTrue();
        // userId=201 -> bucket 1，percent=1 不命中
        assertThat(AppVersionServiceImpl.grayHit(1, 201L, "not-used")).isFalse();
    }

    @Test
    @DisplayName("匿名且无 deviceId：保守不投放")
    void anonymousWithoutDeviceIsNotReleased() {
        assertThat(AppVersionServiceImpl.grayHit(99, null, null)).isFalse();
        assertThat(AppVersionServiceImpl.grayHit(99, null, "   ")).isFalse();
    }

    @Test
    @DisplayName("库内最新版本更高且灰度命中：hasUpdate=true，forceUpdate 跟随库内标记")
    void checkUsesLatestDbVersion() {
        when(versionMapper.selectLatest(eq(1), any(LocalDateTime.class)))
                .thenReturn(version(120, 1, 100));

        AppVersionCheckResult result = appVersionService.check(1, 100, 9L, null);

        assertThat(result.hasUpdate()).isTrue();
        assertThat(result.forceUpdate()).isTrue();
        assertThat(result.versionCode()).isEqualTo(120);
        assertThat(result.versionName()).isEqualTo("1.2.0");
    }

    @Test
    @DisplayName("currentCode 高于库内最新版本：不因前端传值而误判更新")
    void currentCodeAboveLatestMeansNoUpdate() {
        when(versionMapper.selectLatest(eq(1), any(LocalDateTime.class)))
                .thenReturn(version(120, 1, 100));

        AppVersionCheckResult result = appVersionService.check(1, 999, 9L, null);

        assertThat(result.hasUpdate()).isFalse();
        assertThat(result.forceUpdate()).isFalse();
    }

    @Test
    @DisplayName("版本号相等：无更新")
    void equalVersionMeansNoUpdate() {
        when(versionMapper.selectLatest(eq(2), any(LocalDateTime.class)))
                .thenReturn(version(100, 1, 100));

        AppVersionCheckResult result = appVersionService.check(2, 100, 9L, null);

        assertThat(result.hasUpdate()).isFalse();
        assertThat(result.forceUpdate()).isFalse();
    }

    @Test
    @DisplayName("灰度 0：即使版本更高也不下发")
    void grayZeroSuppressesHigherVersion() {
        when(versionMapper.selectLatest(eq(1), any(LocalDateTime.class)))
                .thenReturn(version(120, 1, 0));

        AppVersionCheckResult result = appVersionService.check(1, 100, 9L, null);

        assertThat(result.hasUpdate()).isFalse();
        assertThat(result.forceUpdate()).isFalse();
    }

    @Test
    @DisplayName("库内无可用版本：返回 upToDate")
    void noLatestVersionReturnsUpToDate() {
        when(versionMapper.selectLatest(eq(3), any(LocalDateTime.class)))
                .thenReturn(null);

        AppVersionCheckResult result = appVersionService.check(3, 1, null, "device");

        assertThat(result.hasUpdate()).isFalse();
        assertThat(result.forceUpdate()).isFalse();
        assertThat(result.appType()).isEqualTo(3);
    }

    @Test
    @DisplayName("appType 非法：拒绝请求")
    void invalidAppTypeRejected() {
        assertThatThrownBy(() -> appVersionService.check(9, 1, null, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    private AppVersion version(int versionCode, int forceUpdate, int grayPercent) {
        AppVersion version = new AppVersion();
        version.setId(1L);
        version.setAppType(1);
        version.setVersionName("1.2.0");
        version.setVersionCode(versionCode);
        version.setUpdateDesc("修复已知问题");
        version.setDownloadUrl("/api/v1/files/app/user-1.2.0.apk");
        version.setForceUpdate(forceUpdate);
        version.setGrayPercent(grayPercent);
        version.setStatus(1);
        version.setPublishAt(LocalDateTime.now().minusDays(1));
        return version;
    }
}
