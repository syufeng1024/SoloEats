package com.soloeats.service.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.service.service.view.FeedbackView;

import java.util.List;

public interface FeedbackService {

    /** 用户提交意见反馈，userId 取登录态。 */
    Long create(Long userId, String content, List<String> images, String contact);

    PageResult<FeedbackView> page(Integer status, long pageNum, long pageSize);

    /** 回复一次：0待处理 → 1已回复，重复回复抛参数错误。 */
    void reply(Long id, String reply);
}
