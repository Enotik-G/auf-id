import { useState } from 'react'
import { CopyableLink } from '@/components/CopyableLink'
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
import { api } from '@/lib/api'
import type { AdminUser, InvitationResponse, PasswordResetLinkResponse } from '@/lib/types'

/**
 * Что можно выдать учётке в её состоянии. Ждёт активации — новую ссылку активации, работает —
 * ссылку сброса забытого пароля. Заблокированной не выдаётся ничего (сервер ответил бы 409).
 */
const LINK_KINDS = {
  INVITED: {
    title: 'Ссылка активации',
    description: 'По ней человек задаёт пароль и начинает пользоваться учётной записью. Действует 7 дней.',
    button: 'Выдать новую ссылку активации',
    warning: 'Прежняя ссылка активации перестанет работать — даже если её уже передали.',
    request: async (userId: string) =>
      (await api.post<InvitationResponse>(`/api/v1/admin/users/${userId}/activation-link`)).activationLink,
  },
  ACTIVE: {
    title: 'Сброс пароля',
    description: 'Если человек забыл пароль: по ссылке он задаст новый. Действует 24 часа.',
    button: 'Выдать ссылку для сброса пароля',
    warning: 'Прежние ссылки сброса этой учётки перестанут работать. Сам пароль не меняется, пока по ссылке не зайдут.',
    request: async (userId: string) =>
      (await api.post<PasswordResetLinkResponse>(`/api/v1/admin/users/${userId}/password-reset-link`))
        .passwordResetLink,
  },
}

export function LinksCard({ user }: { user: AdminUser }) {
  const [link, setLink] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (user.status !== 'INVITED' && user.status !== 'ACTIVE') {
    return null
  }
  const kind = LINK_KINDS[user.status]

  async function issue() {
    setError(null)
    try {
      setLink(await kind.request(user.id))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{kind.title}</CardTitle>
        <CardDescription>{kind.description}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {error !== null && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {link !== null ? (
          <>
            <CopyableLink link={link} />
            <p className="text-sm text-muted-foreground">
              Передайте ссылку лично. Показать её ещё раз нельзя: сервер хранит только отпечаток. Потеряется —
              выдайте новую.
            </p>
          </>
        ) : (
          // Новая ссылка гасит прежнюю — переспрашиваем, чтобы не погасить уже отданную случайно.
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="outline" className="justify-self-start">
                {kind.button}
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>{kind.button}?</AlertDialogTitle>
                <AlertDialogDescription>{kind.warning}</AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Отмена</AlertDialogCancel>
                <AlertDialogAction onClick={issue}>Выдать</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        )}
      </CardContent>
    </Card>
  )
}
