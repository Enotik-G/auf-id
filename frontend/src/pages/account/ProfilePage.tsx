import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDateTime, roleLabel } from '@/lib/format'
import { useMe } from '@/lib/me'

/** Строка «подпись — значение». dl/dt/dd — разметка HTML как раз для таких пар. */
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 sm:grid-cols-[12rem_1fr]">
      <dt className="text-muted-foreground">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

/**
 * Профиль: только просмотр. ФИО и почту меняет администратор — сам человек их не правит
 * (почта здесь — логин, выданный колледжем).
 */
export function ProfilePage() {
  const me = useMe()
  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>{me.fullName}</CardTitle>
        <CardDescription>Ошибка в ФИО или почте — обратитесь к администратору.</CardDescription>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-3">
          <Field label="Почта">{me.email}</Field>
          <Field label="Роли">
            {me.roles.length === 0 ? (
              <span className="text-muted-foreground">нет</span>
            ) : (
              <span className="flex flex-wrap gap-1">
                {me.roles.map((role) => (
                  // key — чтобы React отличал элементы списка друг от друга при перерисовке.
                  <Badge key={role} variant="secondary">
                    {roleLabel(role)}
                  </Badge>
                ))}
              </span>
            )}
          </Field>
          <Field label="Учётная запись создана">{formatDateTime(me.createdAt)}</Field>
          <Field label="Последний вход">
            {me.lastLoginAt === null ? 'ещё не было' : formatDateTime(me.lastLoginAt)}
          </Field>
        </dl>
      </CardContent>
    </Card>
  )
}
