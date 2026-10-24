package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-标签关联。uk_user_tag(user_id, tag_id) 保证同一用户不会重复绑定同一标签；
 * 后台打标按「先删后插」全量覆盖。
 */
@Data
@TableName("t_member_tag_rel")
public class MemberTagRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private Long tagId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
