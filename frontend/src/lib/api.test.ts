import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, readCsrfToken } from './api'

// Настоящего сервера нет: fetch, cookie и адрес страницы подменены заглушками. Проверяется, какой
// запрос клиент отправил и как разобрал ответ.

function respondWith(status: number, body?: unknown, headers: Record<string, string> = {}) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status, headers }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function stubBrowser(cookies = '') {
  const assign = vi.fn()
  vi.stubGlobal('document', { cookie: cookies })
  vi.stubGlobal('window', { location: { assign, href: 'http://localhost/account' } })
  return assign
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('readCsrfToken', () => {
  it('находит токен под локальным именем', () => {
    expect(readCsrfToken('JSESSIONID=abc; XSRF-TOKEN=token-1')).toBe('token-1')
  })

  it('находит токен под серверным именем __Host-', () => {
    expect(readCsrfToken('__Host-XSRF-TOKEN=token-2; other=x')).toBe('token-2')
  })

  it('раскодирует значение', () => {
    expect(readCsrfToken('XSRF-TOKEN=a%2Fb')).toBe('a/b')
  })

  it('нет cookie — нет токена', () => {
    expect(readCsrfToken('JSESSIONID=abc')).toBeUndefined()
  })
})

describe('api', () => {
  it('GET просит JSON и не шлёт CSRF-токен', async () => {
    stubBrowser('XSRF-TOKEN=secret')
    const fetchMock = respondWith(200, { email: 'ivan@sinhub.ru' })

    const me = await api.get<{ email: string }>('/api/v1/me')

    expect(me.email).toBe('ivan@sinhub.ru')
    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/v1/me')
    expect(init.headers.Accept).toBe('application/json')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('POST шлёт тело JSON и CSRF-токен из cookie', async () => {
    stubBrowser('XSRF-TOKEN=secret')
    const fetchMock = respondWith(204)

    const result = await api.post('/api/v1/me/password', { currentPassword: 'a', newPassword: 'b' })

    expect(result).toBeUndefined()
    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('secret')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body)).toEqual({ currentPassword: 'a', newPassword: 'b' })
  })

  it('ошибку разбирает в ApiError с текстом и reason', async () => {
    stubBrowser()
    respondWith(400, { detail: 'Текущий пароль введён неверно', reason: 'WRONG_CURRENT_PASSWORD' })

    const error = await api.post('/api/v1/me/password', {}).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, message: 'Текущий пароль введён неверно', reason: 'WRONG_CURRENT_PASSWORD' })
  })

  it('у 429 читает Retry-After', async () => {
    stubBrowser()
    respondWith(429, { detail: 'Слишком много попыток', reason: 'TOO_MANY_ATTEMPTS' }, { 'Retry-After': '540' })

    const error = await api.post('/api/v1/me/password', {}).catch((e: unknown) => e)

    expect(error).toMatchObject({ status: 429, retryAfterSeconds: 540 })
  })

  it('на 401 отправляет на вход', async () => {
    const assign = stubBrowser()
    respondWith(401)

    const error = await api.get('/api/v1/me').catch((e: unknown) => e)

    expect(error).toMatchObject({ status: 401 })
    expect(assign).toHaveBeenCalledOnce()
  })
})
