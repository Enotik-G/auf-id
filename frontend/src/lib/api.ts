// Клиент API AUF ID: все запросы интерфейса к серверу идут через эти функции.
//
// Интерфейс и API живут на одном адресе, поэтому вход — обычной cookie сессии: браузер шлёт её
// сам. Отсюда две обязанности клиента:
//   1. CSRF: для всего, кроме GET, вернуть серверу токен из cookie в заголовке X-XSRF-TOKEN —
//      так сервер отличает запрос нашей страницы от формы, подсунутой чужим сайтом;
//   2. сессия кончилась (401) — отправить человека на вход.

/** Ошибка API в формате RFC 9457 (ProblemDetail), как её отдаёт AUF ID. */
export class ApiError extends Error {
  readonly status: number
  /** Машинная причина для интерфейса: WRONG_CURRENT_PASSWORD, WEAK… — есть не у всех ошибок. */
  readonly reason?: string
  /** Через сколько секунд можно повторить — у ответа 429. */
  readonly retryAfterSeconds?: number

  constructor(status: number, detail: string, reason?: string, retryAfterSeconds?: number) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.reason = reason
    this.retryAfterSeconds = retryAfterSeconds
  }
}

/**
 * Имя cookie с CSRF-токеном зависит от окружения: локально XSRF-TOKEN, на сервере
 * __Host-XSRF-TOKEN (такую нельзя подбросить с соседнего поддомена). Ищем обе.
 */
const CSRF_COOKIE_NAMES = ['__Host-XSRF-TOKEN', 'XSRF-TOKEN']

export function readCsrfToken(cookies: string = document.cookie): string | undefined {
  for (const pair of cookies.split('; ')) {
    const separator = pair.indexOf('=')
    const name = pair.slice(0, separator)
    if (CSRF_COOKIE_NAMES.includes(name)) {
      return decodeURIComponent(pair.slice(separator + 1))
    }
  }
  return undefined
}

/**
 * Сессия кончилась — на вход.
 *
 * На сервере проще всего перезагрузить текущую страницу: Spring увидит, что вход нужен, запомнит
 * адрес, покажет страницу входа и после неё вернёт сюда же. При разработке страницу отдаёт Vite,
 * а не Spring, — поэтому сразу на /login (Vite проксирует его в AUF ID).
 */
function sendToLogin() {
  window.location.assign(import.meta.env.DEV ? '/login' : window.location.href)
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {
    // Без этого заголовка Spring запомнил бы API-запрос как «куда вернуть после входа», и после
    // входа человек увидел бы голый JSON вместо страницы.
    Accept: 'application/json',
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const csrfToken = readCsrfToken()
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken
    }
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
  })

  if (response.status === 401) {
    sendToLogin()
    throw new ApiError(401, 'Нужно войти заново')
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = response.headers.get('Retry-After')
  const retryAfterSeconds = retryAfter === null ? undefined : Number(retryAfter)
  try {
    const problem = (await response.json()) as { detail?: string; reason?: string }
    return new ApiError(response.status, problem.detail ?? response.statusText, problem.reason, retryAfterSeconds)
  } catch {
    // Тело не JSON — например, ошибка прокси. Остаётся статус.
    return new ApiError(response.status, response.statusText || `Ошибка ${response.status}`, undefined, retryAfterSeconds)
  }
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
}
