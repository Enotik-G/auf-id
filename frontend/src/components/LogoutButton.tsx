import { Button } from '@/components/ui/button'
import { readCsrfToken } from '@/lib/api'

/**
 * Выход: POST /logout — Spring закрывает сессию. Только POST: ссылку GET чужой сайт мог бы вставить
 * в картинку и выкинуть человека из системы.
 *
 * Не HTML-формой, а запросом с CSRF-токеном в заголовке, как весь API. В режиме spa() (см.
 * SecurityConfiguration) токен из cookie принимается только в заголовке X-XSRF-TOKEN; в поле
 * формы _csrf сервер ждёт «замаскированный» токен (защита от атаки BREACH), который умеет вставить
 * лишь сам — в свои страницы Thymeleaf. Из cookie в поле формы — получим отказ 403.
 */
async function logOut() {
  await fetch('/logout', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': readCsrfToken() ?? '' },
    credentials: 'same-origin',
  })
  window.location.assign('/login?logout')
}

export function LogoutButton() {
  return (
    <Button variant="ghost" size="sm" onClick={logOut}>
      Выйти
    </Button>
  )
}
