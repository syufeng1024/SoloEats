package com.soloeats.service.service;

import com.soloeats.service.service.command.CreateComplaintCommand;
import com.soloeats.service.service.view.ComplaintCreatedView;
import com.soloeats.service.service.view.ComplaintView;

/**
 * 投诉单。提交时同步创建一张 ticket_type = 4 的工单，二者通过 t_complaint.ticket_id 关联。
 */
public interface ComplaintService {

    /**
     * 提交投诉并创建关联工单。责任判定之前 liability/result 为空。
     */
    ComplaintCreatedView create(CreateComplaintCommand command);

    /**
     * 责任判定，只允许一次。
     *
     * <p>punish_action = 3 封禁 / 2 扣款只登记到 t_complaint；实际执行需要
     * 商家（T03/T04）或骑手（T07）模块的端口接口，本阶段留 TODO，禁止跨模块直接调用。
     */
    void judge(Long complaintId, Integer liability, String result, Integer punishAction);

    ComplaintView findByTicketId(Long ticketId);
}
