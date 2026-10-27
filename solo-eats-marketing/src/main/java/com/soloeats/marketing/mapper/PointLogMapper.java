package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.PointLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PointLogMapper extends BaseMapper<PointLog> {

    /**
     * 幂等预检查：同一 (source, biz_id) 是否已有流水。
     * BaseMapper#insert 仍可能撞 uk_biz，服务层需同时捕获 DuplicateKeyException。
     */
    @Select("""
            SELECT COUNT(*)
            FROM t_point_log
            WHERE source = #{source}
              AND biz_id = #{bizId}
            """)
    int countByBiz(@Param("source") Integer source, @Param("bizId") Long bizId);

    /**
     * 扫描已到期的正向积分流水（批次）。id 游标翻页，避免一次全表。
     * 是否已生成过期流水由服务层按 uk_biz(6, 原流水id) 判断。
     */
    @Select("""
            SELECT *
            FROM t_point_log
            WHERE point > 0
              AND expire_at IS NOT NULL
              AND expire_at < #{before}
              AND id > #{lastId}
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<PointLog> selectExpiredBatches(@Param("before") LocalDateTime before,
                                       @Param("lastId") long lastId,
                                       @Param("limit") int limit);
}
