package com.soloeats.message.service.view;

/** 未读数：total 为三类之和，byType 供前端做独立角标。 */
public record UnreadCountView(long total, ByType byType) {

    public record ByType(long order, long marketing, long system) {
    }
}
