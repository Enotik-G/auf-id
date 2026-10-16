import { describe, expect, it } from 'vitest'
import { ApiError } from './api'
import { describePasswordError } from './passwordErrors'

describe('describePasswordError', () => {
  it('неверный текущий пароль — под полем текущего пароля', () => {
    const message = describePasswordError(new ApiError(400, 'detail', 'WRONG_CURRENT_PASSWORD'))
    expect(message.place).toBe('currentPassword')
  })

  it('слабый новый пароль — под полем нового пароля', () => {
    expect(describePasswordError(new ApiError(400, 'detail', 'WRONG_LENGTH')).place).toBe('newPassword')
    expect(describePasswordError(new ApiError(400, 'detail', 'COMMON')).place).toBe('newPassword')
  })

  it('429 — сколько минут ждать, с округлением вверх', () => {
    const message = describePasswordError(new ApiError(429, 'detail', 'TOO_MANY_ATTEMPTS', 61))
    expect(message).toEqual({ place: 'form', text: 'Слишком много неверных попыток. Попробуйте снова через 2 мин.' })
  })

  it('сеть недоступна — общее сообщение над формой', () => {
    expect(describePasswordError(new TypeError('Failed to fetch')).place).toBe('form')
  })
})
