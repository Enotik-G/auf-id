import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { StatusBadge } from '@/components/StatusBadge'
import { LinksCard } from '@/pages/admin/LinksCard'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api, ApiError } from '@/lib/api'
import { formatDateTime, roleLabel, ROLES } from '@/lib/format'
import { useMe } from '@/lib/me'
import type { AdminUser, Role } from '@/lib/types'

function errorText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

/**
 * Карточка учётной записи: данные, доступ, роли.
 *
 * Каждое действие отправляет запрос и затем показывает учётку такой, какой её вернул или отдаёт
 * сервер, а не такой, какой интерфейс её «ожидает»: так на экране всегда правда.
 */
export function UserDetailsPage() {
  // :userId из маршрута /admin/users/:userId.
  const { userId } = useParams()
  const [user, setUser] = useState<AdminUser | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let stale = false
    api.get<AdminUser>(`/api/v1/admin/users/${userId}`).then(
      (result) => !stale && setUser(result),
      (e: Error) => !stale && setLoadError(e.message),
    )
    return () => {
      stale = true
    }
  }, [userId])

  /** Перечитать учётку после действия, у которого сервер ничего не возвращает (204). */
  async function reload() {
    setUser(await api.get<AdminUser>(`/api/v1/admin/users/${userId}`))
  }

  return (
    <div className="grid max-w-2xl gap-4">
      <Link to="/admin/users" className="text-sm text-muted-foreground hover:underline">
        ← К списку
      </Link>
      {loadError !== null && <p className="text-destructive">Не удалось загрузить учётную запись: {loadError}</p>}
      {user !== null && (
        <>
          <h1 className="text-xl font-semibold">{user.fullName}</h1>
          <DetailsCard user={user} onSaved={setUser} />
          <AccessCard user={user} onChanged={reload} />
          {/* key по статусу: учётку заблокировали или активировали — показанная ссылка уже не годится,
              и React создаёт карточку заново, без неё. */}
          <LinksCard key={user.status} user={user} />
          <RolesCard user={user} onChanged={reload} />
        </>
      )}
    </div>
  )
}

/** ФИО и почта. Отправляем только изменённые поля: PATCH не трогает то, чего нет в запросе. */
function DetailsCard({ user, onSaved }: { user: AdminUser; onSaved: (user: AdminUser) => void }) {
  const [fullName, setFullName] = useState(user.fullName)
  const [email, setEmail] = useState(user.email)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const changes: { fullName?: string; email?: string } = {}
  if (fullName.trim() !== user.fullName) changes.fullName = fullName.trim()
  if (email.trim().toLowerCase() !== user.email) changes.email = email.trim()
  const changed = Object.keys(changes).length > 0

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSending(true)
    setError(null)
    setSaved(false)
    try {
      const updated = await api.patch<AdminUser>(`/api/v1/admin/users/${user.id}`, changes)
      onSaved(updated)
      // Поля — как сохранил сервер (почта, например, уже в нижнем регистре).
      setFullName(updated.fullName)
      setEmail(updated.email)
      setSaved(true)
    } catch (e) {
      setError(errorText(e))
    } finally {
      setSending(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Данные</CardTitle>
        <CardDescription>
          Создана {formatDateTime(user.createdAt)}. Последний вход:{' '}
          {user.lastLoginAt === null ? 'ещё не было' : formatDateTime(user.lastLoginAt)}.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="grid gap-4">
          {error !== null && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="grid gap-2">
            <Label htmlFor="user-full-name">ФИО</Label>
            <Input
              id="user-full-name"
              required
              maxLength={255}
              value={fullName}
              onChange={(e) => {
                setFullName(e.target.value)
                setSaved(false)
              }}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="user-email">Почта</Label>
            <Input
              id="user-email"
              type="email"
              required
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
                setSaved(false)
              }}
              aria-describedby="user-email-hint"
            />
            <p id="user-email-hint" className="text-sm text-muted-foreground">
              Почта — это логин: входить человек будет с новой.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <Button type="submit" disabled={!changed || sending}>
              {sending ? 'Сохраняем…' : 'Сохранить'}
            </Button>
            {saved && <span className="text-sm text-muted-foreground">Сохранено</span>}
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

/** Статус и блокировка. */
function AccessCard({ user, onChanged }: { user: AdminUser; onChanged: () => Promise<void> }) {
  const me = useMe()
  const [error, setError] = useState<string | null>(null)
  const isMe = user.id === me.id

  async function run(action: 'block' | 'unblock') {
    setError(null)
    try {
      await api.post(`/api/v1/admin/users/${user.id}/${action}`)
      await onChanged()
    } catch (e) {
      setError(errorText(e))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Доступ</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4">
        {error !== null && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <div className="flex items-center gap-3">
          <StatusBadge status={user.status} />
          {user.status === 'BLOCKED' ? (
            <Button variant="outline" onClick={() => run('unblock')}>
              Разблокировать
            </Button>
          ) : (
            user.status !== 'DELETED' && (
              // Блокировка выкидывает человека из всех сервисов сразу — переспрашиваем.
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="destructive">Заблокировать</Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Заблокировать {user.email}?</AlertDialogTitle>
                    <AlertDialogDescription>
                      Вход закроется сразу, выданные токены будут отозваны, неиспользованные ссылки активации
                      погаснут.
                      {isMe && ' Это ваша учётная запись: вы сразу выйдете из системы.'}
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Отмена</AlertDialogCancel>
                    <AlertDialogAction variant="destructive" onClick={() => run('block')}>
                      Заблокировать
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )
          )}
        </div>
      </CardContent>
    </Card>
  )
}

/** Роли: флажок выдаёт или снимает роль сразу, отдельной кнопки «Сохранить» нет. */
function RolesCard({ user, onChanged }: { user: AdminUser; onChanged: () => Promise<void> }) {
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function toggle(role: Role, grant: boolean) {
    setError(null)
    setBusy(true)
    try {
      // PUT — выдать (повтор ничего не меняет), DELETE — снять.
      const path = `/api/v1/admin/users/${user.id}/roles/${role}`
      await (grant ? api.put(path) : api.delete(path))
      await onChanged()
    } catch (e) {
      // 409 — снятие ADMIN у последнего администратора; текст сервера это и объясняет.
      setError(e instanceof ApiError && e.status === 409 ? e.message : errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Роли</CardTitle>
        <CardDescription>Меняются сразу. В сервисах — со следующим токеном, не позже чем через 10 минут.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {error !== null && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {ROLES.map((role) => (
          <div key={role} className="flex items-center gap-2">
            <Checkbox
              id={`user-role-${role}`}
              checked={user.roles.includes(role)}
              disabled={busy}
              onCheckedChange={(checked) => toggle(role, checked === true)}
            />
            <Label htmlFor={`user-role-${role}`} className="font-normal">
              {roleLabel(role)}
            </Label>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
