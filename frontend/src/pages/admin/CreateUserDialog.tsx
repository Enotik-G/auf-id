import { useState, type FormEvent } from 'react'
import { CopyableLink } from '@/components/CopyableLink'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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
import { api, ApiError } from '@/lib/api'
import { roleLabel, ROLES } from '@/lib/format'
import type { InvitationResponse, Role } from '@/lib/types'

/**
 * Кнопка «Создать учётную запись» и окно с формой. После создания в том же окне — ссылка активации.
 *
 * onCreated — сообщить списку, что пора перезагрузиться.
 */
export function CreateUserDialog({ onCreated }: { onCreated: () => void }) {
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button>Создать учётную запись</Button>
      </DialogTrigger>
      <DialogContent>
        {/* Содержимое окна Radix убирает со страницы при закрытии, а с ним и состояние формы:
            следующее открытие начнётся с чистой формы, без ручного сброса полей. */}
        <CreateUserForm onCreated={onCreated} />
      </DialogContent>
    </Dialog>
  )
}

function CreateUserForm({ onCreated }: { onCreated: () => void }) {
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [roles, setRoles] = useState<Role[]>(['STUDENT'])
  const [sending, setSending] = useState(false)
  // Ошибка про почту — под полем почты, остальное — над формой.
  const [emailError, setEmailError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [created, setCreated] = useState<InvitationResponse | null>(null)

  function toggleRole(role: Role, checked: boolean) {
    setRoles((current) => (checked ? [...current, role] : current.filter((r) => r !== role)))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSending(true)
    setEmailError(null)
    setFormError(null)
    try {
      setCreated(await api.post<InvitationResponse>('/api/v1/admin/users', { email, fullName, roles }))
      onCreated()
    } catch (e) {
      // 409 — почта занята, 400 — почта не той формы или не того домена. Текст сервера уже по-русски.
      if (e instanceof ApiError && (e.status === 409 || e.status === 400)) {
        setEmailError(e.message)
      } else {
        setFormError(e instanceof Error ? e.message : String(e))
      }
    } finally {
      setSending(false)
    }
  }

  if (created !== null) {
    return (
      <>
        <DialogHeader>
          <DialogTitle>Учётная запись создана</DialogTitle>
          <DialogDescription>
            {/* ФИО не склоняем — поэтому в кавычках, а не «передайте Анне». */}
            Ссылку для «{fullName}» передайте лично: по ней задаётся пароль. Она одноразовая и действует 7 дней.
          </DialogDescription>
        </DialogHeader>
        <CopyableLink link={created.activationLink} />
        <Alert>
          <AlertDescription>
            Показать ссылку ещё раз нельзя: сервер хранит только её отпечаток. Потеряется — выдайте новую в
            карточке учётной записи.
          </AlertDescription>
        </Alert>
        <DialogFooter showCloseButton />
      </>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4">
      <DialogHeader>
        <DialogTitle>Новая учётная запись</DialogTitle>
        <DialogDescription>Человек получит ссылку, по которой сам задаст пароль.</DialogDescription>
      </DialogHeader>

      {formError !== null && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-2">
        <Label htmlFor="new-user-email">Почта</Label>
        <Input
          id="new-user-email"
          type="email"
          required
          placeholder="ivan.petrov@sinhub.ru"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-invalid={emailError !== null}
          aria-describedby="new-user-email-error"
        />
        {emailError !== null && (
          <p id="new-user-email-error" className="text-sm text-destructive">
            {emailError}
          </p>
        )}
      </div>

      <div className="grid gap-2">
        <Label htmlFor="new-user-name">ФИО</Label>
        <Input
          id="new-user-name"
          required
          maxLength={255}
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
        />
      </div>

      {/* fieldset + legend — так экранный чтец объявит флажки как одну группу «Роли». */}
      <fieldset className="grid gap-2">
        <legend className="mb-2 text-sm font-medium">Роли</legend>
        {ROLES.map((role) => (
          <div key={role} className="flex items-center gap-2">
            <Checkbox
              id={`new-user-role-${role}`}
              checked={roles.includes(role)}
              onCheckedChange={(checked) => toggleRole(role, checked === true)}
            />
            <Label htmlFor={`new-user-role-${role}`} className="font-normal">
              {roleLabel(role)}
            </Label>
          </div>
        ))}
      </fieldset>

      <DialogFooter>
        <Button type="submit" disabled={sending}>
          {sending ? 'Создаём…' : 'Создать'}
        </Button>
      </DialogFooter>
    </form>
  )
}
