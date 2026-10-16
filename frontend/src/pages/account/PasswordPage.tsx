import { useState, type FormEvent } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import { describePasswordError, type PasswordErrorMessage } from '@/lib/passwordErrors'

/** Сообщение об ошибке под полем. id связывает его с полем через aria-describedby — для экранных чтецов. */
function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) {
    return null
  }
  return (
    <p id={id} className="text-sm text-destructive">
      {message}
    </p>
  )
}

export function PasswordPage() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [repeatPassword, setRepeatPassword] = useState('')
  const [error, setError] = useState<PasswordErrorMessage | null>(null)
  const [sending, setSending] = useState(false)
  const [done, setDone] = useState(false)

  // Повтор сверяет только браузер: он ловит опечатку, серверу его не отправляем (см. ChangePasswordRequest).
  const repeatMismatch = repeatPassword !== '' && repeatPassword !== newPassword

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    // Без этого браузер отправил бы форму сам и перезагрузил страницу.
    event.preventDefault()
    if (repeatMismatch) {
      return
    }
    setSending(true)
    setError(null)
    setDone(false)
    try {
      await api.post('/api/v1/me/password', { currentPassword, newPassword })
      // Пароли не держим в памяти страницы дольше нужного.
      setCurrentPassword('')
      setNewPassword('')
      setRepeatPassword('')
      setDone(true)
    } catch (e) {
      setError(describePasswordError(e))
    } finally {
      setSending(false)
    }
  }

  const errorFor = (place: PasswordErrorMessage['place']) => (error?.place === place ? error.text : undefined)

  return (
    <Card className="max-w-md">
      <CardHeader>
        <CardTitle>Смена пароля</CardTitle>
        <CardDescription>
          После смены лаунчер и другие сервисы попросят войти заново, а вход на других устройствах закроется.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="grid gap-4">
          {done && (
            <Alert>
              <AlertTitle>Пароль изменён</AlertTitle>
              <AlertDescription>На этом устройстве вы остаётесь в системе.</AlertDescription>
            </Alert>
          )}
          {error?.place === 'form' && (
            <Alert variant="destructive">
              <AlertDescription>{error.text}</AlertDescription>
            </Alert>
          )}

          <div className="grid gap-2">
            <Label htmlFor="current-password">Текущий пароль</Label>
            <Input
              id="current-password"
              type="password"
              // autoComplete подсказывает менеджеру паролей, какое поле какое: здесь он подставит
              // сохранённый пароль, а в поле нового — предложит сгенерировать.
              autoComplete="current-password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              aria-invalid={errorFor('currentPassword') !== undefined}
              aria-describedby="current-password-error"
            />
            <FieldError id="current-password-error" message={errorFor('currentPassword')} />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="new-password">Новый пароль</Label>
            <Input
              id="new-password"
              type="password"
              autoComplete="new-password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              aria-invalid={errorFor('newPassword') !== undefined}
              aria-describedby="new-password-hint new-password-error"
            />
            <p id="new-password-hint" className="text-sm text-muted-foreground">
              От 12 символов. Удобно взять фразу из нескольких слов.
            </p>
            <FieldError id="new-password-error" message={errorFor('newPassword')} />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="repeat-password">Новый пароль ещё раз</Label>
            <Input
              id="repeat-password"
              type="password"
              autoComplete="new-password"
              required
              value={repeatPassword}
              onChange={(e) => setRepeatPassword(e.target.value)}
              aria-invalid={repeatMismatch}
              aria-describedby="repeat-password-error"
            />
            <FieldError id="repeat-password-error" message={repeatMismatch ? 'Пароли не совпадают.' : undefined} />
          </div>

          <Button type="submit" disabled={sending || repeatMismatch} className="justify-self-start">
            {sending ? 'Сохраняем…' : 'Сменить пароль'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
