package com.soloeats.marketing.service;

import com.soloeats.marketing.service.command.MemberLevelCommand;
import com.soloeats.marketing.service.view.GrowthLogView;
import com.soloeats.marketing.service.view.MemberLevelView;
import com.soloeats.marketing.service.view.MemberProfileView;

import java.util.List;

/**
 * 会员等级与成长值。成长值与积分是两套独立账户，本接口不读写积分。
 */
public interface MemberLevelService {

    /** 全部等级（含停用），按 level_no 升序，供后台列表使用。 */
    List<MemberLevelView> listLevels();

    /** 新建等级，返回主键。levelCode 租户内重复抛 PARAM_INVALID。 */
    Long createLevel(MemberLevelCommand command);

    /** 编辑等级。id 不存在抛 RESOURCE_NOT_FOUND。 */
    void updateLevel(Long id, MemberLevelCommand command);

    /**
     * C 端会员档案：当前等级、近 365 天成长值、距下一级差值、当前等级权益。
     * 已是最高级时 growthToNext = 0、maxLevel = true。
     */
    MemberProfileView profile(Long userId);

    /** 成长值明细，按 id 倒序分页。pageNum 从 1 起，pageSize 默认 20、上限 100。 */
    List<GrowthLogView> growthLogs(Long userId, long pageNum, long pageSize);

    /**
     * 全量季度重算：每批 500 个用户，按近 365 天成长值重新匹配等级。
     * 返回实际发生变更（成长值或等级 ID 变化）的人数。
     */
    int recalcAll();

    /**
     * 追加一笔成长值并回写 t_member_user 的 growth_value / member_level_id。
     *
     * @param source 1消费 2签到 3评价 4邀请 5手工调整
     * @param growth 本次变动值，可负
     */
    void addGrowth(Long userId, Integer source, Long bizId, int growth, String remark);
}
