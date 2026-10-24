package com.soloeats.message.service.query;

/**
 * 站内信分页条件。msgType 1订单 2营销 3系统；readFlag 0未读 1已读。
 */
public record SiteMessageQuery(Integer msgType, Integer readFlag) {

    public static SiteMessageQuery empty() {
        return new SiteMessageQuery(null, null);
    }
}
