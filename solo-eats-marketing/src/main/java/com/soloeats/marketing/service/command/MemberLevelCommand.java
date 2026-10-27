package com.soloeats.marketing.service.command;

/**
 * 会员等级新建/编辑入参。
 *
 * @param levelCode       等级编码，租户内唯一，最长 16
 * @param levelName       等级名称，最长 32
 * @param levelNo         等级序号 1~6
 * @param growthThreshold 进入门槛，近 365 天成长值，不能为负
 * @param star            展示星级 1~3
 * @param benefitsJson    权益 JSON，可空；非空时必须是以 { 开头的对象串
 * @param iconUrl         图标地址，可空
 * @param sort            排序，越小越靠前
 * @param status          0 启用 / 1 停用，为空时取 0
 */
public record MemberLevelCommand(
        String levelCode,
        String levelName,
        Integer levelNo,
        Integer growthThreshold,
        Integer star,
        String benefitsJson,
        String iconUrl,
        Integer sort,
        Integer status
) {
}
