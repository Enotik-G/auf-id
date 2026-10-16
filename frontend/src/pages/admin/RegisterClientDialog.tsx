import { useState, type FormEvent } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { api } from '@/lib/api'
import { CLIENT_KINDS, clientKindLabel } from '@/lib/format'
import { parseList } from '@/lib/lists'
import type { ClientCredentials, ClientKind } from '@/lib/types'

/** Что значит каждый вид — короче, чем в ClientKind.java, но о том же. */
const KIND_HINTS: Record<ClientKind, string> = {
  BROWSER: 'Сайт, который работает в браузере (планировщик). Без секрета, вход защищает PKCE.',
  NATIVE: 'Программа на компьютере или телефоне (лаунчер). Единственный вид с refresh-токеном на 30 дней.',
  CONFIDENTIAL: 'Сайт с собственным сервером, где можно хранить секрет.',
  SERVICE: 'Программа, которая ходит к другим сервисам от своего имени, без человека.',
}

const REDIRECT_EXAMPLES: Record<ClientKind, string> = {
  BROWSER: 'https://planner.sinhub.ru/callback',
  NATIVE: 'http://127.0.0.1/callback',
  CONFIDENTIAL: 'https://grades.sinhub.ru/login/callback',
  SERVICE: '',
}

/**
 * Кнопка «Зарегистрировать сервис» и окно с формой.
 *
 * onRegistered получает секрет: показывает его страница списка, в том же окне, что и после ротации.
 */
export function RegisterClientDialog({ onRegistered }: { onRegistered: (credentials: ClientCredentials) => void }) {
  const [open, setOpen] = useState(false)
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>Зарегистрировать сервис</Button>
      </DialogTrigger>
      {/* Форма длинная: не выше экрана, остальное — прокруткой внутри окна. Иначе на невысоком экране
          верх окна уезжал за край, а до кнопки «Зарегистрировать» было не добраться. */}
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-lg">
        <RegisterClientForm
          onRegistered={(credentials) => {
            setOpen(false)
            onRegistered(credentials)
          }}
        />
      </DialogContent>
    </Dialog>
  )
}

function RegisterClientForm({ onRegistered }: { onRegistered: (credentials: ClientCredentials) => void }) {
  const [clientId, setClientId] = useState('')
  const [name, setName] = useState('')
  const [kind, setKind] = useState<ClientKind>('BROWSER')
  const [redirectUris, setRedirectUris] = useState('')
  const [postLogoutRedirectUris, setPostLogoutRedirectUris] = useState('')
  const [scopes, setScopes] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // У сервиса без пользователя нет входа человека — возвращать после него некуда.
  const withUser = kind !== 'SERVICE'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSending(true)
    setError(null)
    try {
      const credentials = await api.post<ClientCredentials>('/api/v1/admin/clients', {
        clientId: clientId.trim(),
        name: name.trim(),
        kind,
        // Поля, скрытые для SERVICE, не отправляем, даже если в них что-то успели ввести до смены вида.
        redirectUris: withUser ? parseList(redirectUris) : [],
        postLogoutRedirectUris: withUser ? parseList(postLogoutRedirectUris) : [],
        scopes: parseList(scopes),
      })
      onRegistered(credentials)
    } catch (e) {
      // Правила проверяет сервер, и текст отказа у него точный: «Нужен хотя бы один адрес возврата» и т.п.
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSending(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4">
      <DialogHeader>
        <DialogTitle>Новый сервис</DialogTitle>
        <DialogDescription>Настройки OAuth сервер выведет из вида сервиса — вручную их задавать не нужно.</DialogDescription>
      </DialogHeader>

      {error !== null && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-2 sm:grid-cols-2">
        <div className="grid gap-2">
          <Label htmlFor="client-name">Название</Label>
          <Input id="client-name" required value={name} onChange={(e) => setName(e.target.value)} placeholder="Планировщик" />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="client-id">Идентификатор (client_id)</Label>
          <Input
            id="client-id"
            required
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="planner"
            className="font-mono"
            aria-describedby="client-id-hint"
          />
        </div>
        <p id="client-id-hint" className="text-sm text-muted-foreground sm:col-span-2">
          Строчные латинские буквы, цифры и дефисы. Поменять потом нельзя: по нему сервис представляется.
        </p>
      </div>

      {/* Обычные переключатели браузера: выбор одного из четырёх с пояснением к каждому. */}
      <fieldset className="grid gap-2">
        <legend className="mb-2 text-sm font-medium">Вид</legend>
        {CLIENT_KINDS.map((option) => (
          <label
            key={option}
            className="flex cursor-pointer gap-3 rounded-lg border p-3 has-checked:border-foreground has-checked:bg-muted/50"
          >
            <input
              type="radio"
              name="client-kind"
              value={option}
              checked={kind === option}
              onChange={() => setKind(option)}
              className="mt-1 accent-foreground"
            />
            <span className="grid gap-0.5">
              <span className="text-sm font-medium">{clientKindLabel(option)}</span>
              <span className="text-sm text-muted-foreground">{KIND_HINTS[option]}</span>
            </span>
          </label>
        ))}
      </fieldset>

      {withUser && (
        <>
          <div className="grid gap-2">
            <Label htmlFor="client-redirect-uris">Адреса возврата после входа</Label>
            <Textarea
              id="client-redirect-uris"
              required
              rows={2}
              value={redirectUris}
              onChange={(e) => setRedirectUris(e.target.value)}
              placeholder={REDIRECT_EXAMPLES[kind]}
              className="font-mono text-xs"
              aria-describedby="client-redirect-uris-hint"
            />
            <p id="client-redirect-uris-hint" className="text-sm text-muted-foreground">
              По одному на строку. Только сюда AUF ID вернёт человека с кодом входа.
              {kind === 'NATIVE' && ' Для 127.0.0.1 порт можно не угадывать: подойдёт любой.'}
            </p>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="client-logout-uris">Адреса после выхода (необязательно)</Label>
            <Textarea
              id="client-logout-uris"
              rows={1}
              value={postLogoutRedirectUris}
              onChange={(e) => setPostLogoutRedirectUris(e.target.value)}
              className="font-mono text-xs"
            />
          </div>
        </>
      )}

      <div className="grid gap-2">
        <Label htmlFor="client-scopes">Права (scope){withUser && ' — необязательно'}</Label>
        <Input
          id="client-scopes"
          required={!withUser}
          value={scopes}
          onChange={(e) => setScopes(e.target.value)}
          placeholder={withUser ? 'openid profile email' : 'grades.read'}
          className="font-mono"
          aria-describedby="client-scopes-hint"
        />
        <p id="client-scopes-hint" className="text-sm text-muted-foreground">
          Через пробел.{' '}
          {withUser
            ? 'Пусто — openid, profile, email: кто вошёл, ФИО и почта.'
            : 'Обязательно: человека нет, и права по умолчанию (профиль, почта) ему не подходят.'}
        </p>
      </div>

      <DialogFooter>
        <Button type="submit" disabled={sending}>
          {sending ? 'Регистрируем…' : 'Зарегистрировать'}
        </Button>
      </DialogFooter>
    </form>
  )
}
