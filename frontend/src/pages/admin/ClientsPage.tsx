import { useEffect, useState, type ReactElement } from 'react'
import { CopyableValue } from '@/components/CopyableValue'
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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { clientKindLabel, formatDateTime } from '@/lib/format'
import type { ClientCredentials, ClientSummary } from '@/lib/types'

/**
 * Зарегистрированные сервисы (клиенты OAuth): список, новый секрет, снятие регистрации.
 *
 * Сервисов единицы, поэтому список целиком, без поиска и страниц — так его и отдаёт сервер.
 */
export function ClientsPage() {
  const [clients, setClients] = useState<ClientSummary[] | null>(null)
  const [reloads, setReloads] = useState(0)
  const [error, setError] = useState<string | null>(null)
  // Только что выданный секрет: показывается один раз, в отдельном окне.
  const [issued, setIssued] = useState<ClientCredentials | null>(null)

  useEffect(() => {
    let stale = false
    api.get<ClientSummary[]>('/api/v1/admin/clients').then(
      (result) => !stale && setClients(result),
      (e: Error) => !stale && setError(e.message),
    )
    return () => {
      stale = true
    }
  }, [reloads])

  async function rotateSecret(clientId: string) {
    setError(null)
    try {
      setIssued(await api.post<ClientCredentials>(`/api/v1/admin/clients/${clientId}/secret`))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  async function unregister(clientId: string) {
    setError(null)
    try {
      await api.delete(`/api/v1/admin/clients/${clientId}`)
      setReloads((n) => n + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  return (
    <div className="grid gap-4">
      <h1 className="text-xl font-semibold">Сервисы</h1>
      <p className="text-sm text-muted-foreground">
        Приложения, которые входят через AUF ID. Новый сервис подключается регистрацией здесь — правки в самом
        AUF ID ему не нужны.
      </p>

      {error !== null && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {clients !== null && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Сервис</TableHead>
              <TableHead>Вид</TableHead>
              <TableHead>Адреса возврата</TableHead>
              <TableHead>Зарегистрирован</TableHead>
              <TableHead>
                <span className="sr-only">Действия</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {clients.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  Ни одного сервиса пока не зарегистрировано
                </TableCell>
              </TableRow>
            )}
            {clients.map((client) => (
              <TableRow key={client.clientId}>
                <TableCell>
                  <div className="font-medium">{client.name}</div>
                  <div className="font-mono text-xs text-muted-foreground">{client.clientId}</div>
                </TableCell>
                <TableCell>
                  <Badge variant="secondary">{clientKindLabel(client.kind)}</Badge>
                </TableCell>
                {/* Адрес может быть длинным: ячейкам таблицы shadcn запрещает перенос, здесь разрешаем. */}
                <TableCell className="font-mono text-xs break-all whitespace-normal">
                  {client.redirectUris.length === 0 ? '—' : client.redirectUris.map((uri) => <div key={uri}>{uri}</div>)}
                </TableCell>
                <TableCell className="text-muted-foreground">{formatDateTime(client.registeredAt)}</TableCell>
                <TableCell>
                  {/* Кнопки столбиком: в строку с длинным адресом они не помещались. */}
                  <div className="flex flex-col items-end gap-1">
                    {/* У браузерного приложения секрета нет — вкладке его негде спрятать. */}
                    {client.kind !== 'BROWSER' && (
                      <Confirm
                        trigger={<Button variant="outline" size="sm">Новый секрет</Button>}
                        title={`Выдать новый секрет для «${client.name}»?`}
                        description="Прежний секрет перестанет работать сразу: сервис не сможет входить, пока в нём не заменят секрет на новый."
                        action="Выдать"
                        onConfirm={() => rotateSecret(client.clientId)}
                      />
                    )}
                    <Confirm
                      trigger={<Button variant="destructive" size="sm">Снять регистрацию</Button>}
                      title={`Снять регистрацию «${client.name}»?`}
                      description="Сервис больше не сможет входить через AUF ID. Его токены перестанут обновляться, а уже выданные доживут свои 10 минут."
                      action="Снять регистрацию"
                      destructive
                      onConfirm={() => unregister(client.clientId)}
                    />
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <SecretDialog credentials={issued} onClose={() => setIssued(null)} />
    </div>
  )
}

/** Кнопка с переспросом. Действия здесь ломают работающий сервис — без переспроса их не делаем. */
function Confirm(props: {
  trigger: ReactElement
  title: string
  description: string
  action: string
  destructive?: boolean
  onConfirm: () => void
}) {
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>{props.trigger}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{props.title}</AlertDialogTitle>
          <AlertDialogDescription>{props.description}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Отмена</AlertDialogCancel>
          <AlertDialogAction variant={props.destructive ? 'destructive' : 'default'} onClick={props.onConfirm}>
            {props.action}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

/**
 * Окно с только что выданным секретом. Открыто, пока credentials не null; закрыли — секрет забыт.
 * Его же покажет регистрация нового сервиса.
 */
export function SecretDialog({ credentials, onClose }: { credentials: ClientCredentials | null; onClose: () => void }) {
  return (
    <Dialog open={credentials !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {credentials !== null && (
          <>
            <DialogHeader>
              <DialogTitle>Секрет сервиса {credentials.clientId}</DialogTitle>
              <DialogDescription>
                Передайте его разработчикам сервиса защищённым способом — не в общий чат. Показать секрет ещё раз
                нельзя: сервер хранит только его хеш. Потеряется — выдайте новый.
              </DialogDescription>
            </DialogHeader>
            {credentials.secret !== null ? (
              <CopyableValue value={credentials.secret} label="Секрет" />
            ) : (
              <p className="text-sm">Браузерному приложению секрет не нужен — вход защищает PKCE.</p>
            )}
            <DialogFooter showCloseButton />
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
