package com.soloeats.member.service.query;

/**
 * 后台会员列表查询条件。keyword 只接受手机号后四位（可传完整号码，服务层截取后四位），
 * 不接受按明文手机号检索。
 */
public record MemberQuery(Integer status, String keyword, Long levelId) {

    public static MemberQuery empty() {
        return new MemberQuery(null, null, null);
    }
}
