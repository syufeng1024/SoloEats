import { describe, expect, it } from 'vitest'
import {
  DATA_SCOPE,
  DATA_SCOPE_OPTIONS,
  LOGIN_SUCCESS,
  MAIL_LOG_STATUS,
  MENU_MANAGE_TYPE_OPTIONS,
  MENU_TYPE_OPTIONS,
  MENU_VISIBLE,
  NOTICE_STATUS,
  PACKAGE_STATUS,
  ROLE_TYPE,
  SYSTEM_STATUS,
  SYSTEM_STATUS_OPTIONS,
  TENANT_MANUAL_STATUS_OPTIONS,
  TENANT_STATUS,
  USER_TYPE,
  labelOf,
  tagOf,
} from './systemStatus'

describe('system status dictionaries', () => {
  it('status 0 为启用、1 为停用', () => {
    expect(SYSTEM_STATUS[0]).toEqual({ label: '启用', type: 'success' })
    expect(SYSTEM_STATUS[1]).toEqual({ label: '停用', type: 'info' })
    expect(SYSTEM_STATUS_OPTIONS.map((item) => item.value)).toEqual([0, 1])
  })

  it('用户类型与角色类型同口径：1平台 2商家 3骑手', () => {
    expect(USER_TYPE[1].label).toBe('平台')
    expect(USER_TYPE[2].label).toBe('商家')
    expect(USER_TYPE[3].label).toBe('骑手')
    expect(ROLE_TYPE[1]).toEqual(USER_TYPE[1])
  })

  it('数据范围 1~5 全部有值', () => {
    expect(DATA_SCOPE_OPTIONS.map((item) => item.value)).toEqual([1, 2, 3, 4, 5])
    expect(DATA_SCOPE[5].label).toBe('仅本人')
  })

  it('菜单类型完整，菜单管理选项排除按钮', () => {
    expect(MENU_TYPE_OPTIONS.map((item) => item.value)).toEqual([1, 2, 3])
    expect(MENU_MANAGE_TYPE_OPTIONS.map((item) => item.value)).toEqual([1, 2])
  })

  it('菜单显隐：0显示 1隐藏', () => {
    expect(MENU_VISIBLE[0].label).toBe('显示')
    expect(MENU_VISIBLE[1].label).toBe('隐藏')
  })

  it('未知值返回占位符与默认 tag', () => {
    expect(labelOf(SYSTEM_STATUS, 99)).toBe('—')
    expect(tagOf(SYSTEM_STATUS, 99)).toBe('info')
    expect(labelOf(SYSTEM_STATUS, null)).toBe('—')
    expect(tagOf(SYSTEM_STATUS, undefined)).toBe('info')
  })

  it('租户状态含 0/1/2，人工启停不含过期态', () => {
    expect(TENANT_STATUS[0].label).toBe('启用')
    expect(TENANT_STATUS[1].label).toBe('停用')
    expect(TENANT_STATUS[2].label).toBe('已过期')
    expect(TENANT_MANUAL_STATUS_OPTIONS.map((item) => item.value)).toEqual([0, 1])
    expect(PACKAGE_STATUS[1].label).toBe('停用')
  })

  it('公告与邮件日志状态覆盖三态', () => {
    expect(NOTICE_STATUS[0].label).toBe('草稿')
    expect(NOTICE_STATUS[1].label).toBe('已发布')
    expect(NOTICE_STATUS[2].label).toBe('已撤回')
    expect(MAIL_LOG_STATUS[0].label).toBe('待发')
    expect(MAIL_LOG_STATUS[1].label).toBe('成功')
    expect(MAIL_LOG_STATUS[2].label).toBe('失败')
  })

  it('登录结果是 1成功 0失败', () => {
    expect(LOGIN_SUCCESS[1].label).toBe('成功')
    expect(LOGIN_SUCCESS[0].label).toBe('失败')
    expect(tagOf(LOGIN_SUCCESS, 0)).toBe('danger')
  })
})
