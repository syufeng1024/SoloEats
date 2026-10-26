package com.soloeats.content.service.view;

import java.time.LocalDateTime;

/** 首页公告条只读引用 t_sys_notice，不新建公告表。 */
public record NoticeBriefView(Long id, String title, LocalDateTime publishAt) {
}
