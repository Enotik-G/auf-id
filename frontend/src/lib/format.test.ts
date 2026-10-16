import { describe, expect, it } from 'vitest'
import { formatDateTime, roleLabel } from './format'

describe('formatDateTime', () => {
  it('показывает время по-русски в заданном поясе', () => {
    expect(formatDateTime('2026-10-16T09:00:00Z', 'Europe/Moscow')).toBe('16 октября 2026 г. в 12:00')
  })
})

describe('roleLabel', () => {
  it('переводит роль на русский', () => {
    expect(roleLabel('ADMIN')).toBe('Администратор')
  })
})
