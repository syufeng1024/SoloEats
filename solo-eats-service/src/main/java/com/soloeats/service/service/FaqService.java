package com.soloeats.service.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.service.service.command.FaqCommand;
import com.soloeats.service.service.view.FaqView;

import java.util.List;

public interface FaqService {

    PageResult<FaqView> pageAdmin(String category, String keyword, Integer status,
                                  long pageNum, long pageSize);

    FaqView detail(Long id);

    Long create(FaqCommand command);

    void update(Long id, FaqCommand command);

    /** 逻辑删除（deleted = 1）。 */
    void delete(Long id);

    /**
     * 用户端帮助中心：只返回 status = 0；命中条目 view_count 原子 +1。
     */
    List<FaqView> listPublic(String category, String keyword);
}
