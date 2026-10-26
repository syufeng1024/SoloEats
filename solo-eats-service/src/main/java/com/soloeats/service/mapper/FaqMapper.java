package com.soloeats.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.service.domain.entity.Faq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FaqMapper extends BaseMapper<Faq> {

    /** 原子自增，不做「先读后写」。 */
    @Update("UPDATE t_faq SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int increaseViewCount(@Param("id") Long id);
}
