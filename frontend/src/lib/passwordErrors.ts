import { ApiError } from './api'

/** Куда показать сообщение: под конкретным полем формы или над всей формой. */
export type PasswordErrorPlace = 'currentPassword' | 'newPassword' | 'form'

export interface PasswordErrorMessage {
  place: PasswordErrorPlace
  text: string
}

/**
 * Ошибка сервера при смене или задании пароля → понятная человеку фраза и место для неё.
 *
 * Причины — поле reason в ответе (см. MeController): по нему, а не по тексту detail, потому что
 * текст сервер вправе поменять, а reason — часть контракта.
 */
export function describePasswordError(error: unknown): PasswordErrorMessage {
  if (!(error instanceof ApiError)) {
    // fetch падает так, когда сервер недоступен или пропала сеть.
    return { place: 'form', text: 'Не удалось связаться с сервером. Проверьте подключение и попробуйте ещё раз.' }
  }
  switch (error.reason) {
    case 'WRONG_CURRENT_PASSWORD':
      return { place: 'currentPassword', text: 'Текущий пароль введён неверно.' }
    case 'WRONG_LENGTH':
      return { place: 'newPassword', text: 'Пароль должен быть длиной от 12 до 128 символов.' }
    case 'COMMON':
      return {
        place: 'newPassword',
        text: 'Этот пароль слишком распространён — он есть в списках утёкших паролей. Придумайте другой.',
      }
    case 'TOO_MANY_ATTEMPTS':
      return { place: 'form', text: `Слишком много неверных попыток. ${waitText(error.retryAfterSeconds)}` }
    default:
      return { place: 'form', text: `Не удалось сменить пароль: ${error.message}` }
  }
}

function waitText(seconds: number | undefined): string {
  if (seconds === undefined || Number.isNaN(seconds)) {
    return 'Попробуйте позже.'
  }
  // Округляем вверх: «подождите 0 минут» при 30 секундах сбило бы с толку.
  const minutes = Math.max(1, Math.ceil(seconds / 60))
  return `Попробуйте снова через ${minutes} мин.`
}
