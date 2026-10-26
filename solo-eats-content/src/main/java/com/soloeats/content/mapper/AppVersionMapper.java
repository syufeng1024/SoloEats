package com.soloeats.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.content.domain.entity.AppVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface AppVersionMapper extends BaseMapper<AppVersion> {

    /**
     * 库内最新可用版本。status / publish_at / deleted 全部在 SQL 中过滤，
     * 服务层只拿库内 version_code 与客户端 code 比较，不信任前端结果。
     */
    @Select("""
            SELECT *
              FROM t_app_version
             WHERE app_type = #{appType}
               AND status = 1
               AND deleted = 0
               AND (publish_at IS NULL OR publish_at <= #{now})
             ORDER BY version_code DESC, id DESC
             LIMIT 1
            """)
    AppVersion selectLatest(@Param("appType") Integer appType,
                            @Param("now") LocalDateTime now);
}
