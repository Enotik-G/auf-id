import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { StatusBadge } from '@/components/StatusBadge'
import { CreateUserDialog } from '@/pages/admin/CreateUserDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { formatDateTime, roleLabel, statusLabel, USER_STATUSES } from '@/lib/format'
import type { UserPage } from '@/lib/types'

const PAGE_SIZE = 20

/**
 * Поле поиска. Набранное хранит у себя: искать будем по нажатию «Найти», а не на каждую букву.
 */
function SearchForm({ initialQuery, onSearch }: { initialQuery: string; onSearch: (query: string) => void }) {
  const [typed, setTyped] = useState(initialQuery)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSearch(typed)
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2" role="search">
      <Input
        type="search"
        placeholder="ФИО или почта"
        aria-label="Поиск по ФИО или почте"
        className="w-64"
        value={typed}
        onChange={(e) => setTyped(e.target.value)}
      />
      <Button type="submit" variant="outline">
        Найти
      </Button>
    </form>
  )
}

/**
 * Список учётных записей: поиск, фильтр по статусу, страницы.
 *
 * Условия поиска живут в адресе (/admin/users?q=иван&status=ACTIVE&page=2), а не только в памяти
 * страницы: так «Назад» в браузере возвращает к прошлому поиску, а ссылкой можно поделиться.
 * Номер страницы в адресе — с единицы, для человека; API считает с нуля.
 */
export function UsersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const status = searchParams.get('status') ?? ''
  const page = Math.max(1, Number(searchParams.get('page')) || 1)

  const [users, setUsers] = useState<UserPage | null>(null)
  // Увеличиваем, чтобы загрузить список заново при тех же условиях (например, после создания учётки).
  const [reloads, setReloads] = useState(0)
  // Ошибка помнит, к какому запросу относится: сменились условия — старая ошибка больше не показывается.
  const [error, setError] = useState<{ request: string; message: string } | null>(null)

  const apiParams = new URLSearchParams({ page: String(page - 1), size: String(PAGE_SIZE) })
  if (query) apiParams.set('query', query)
  if (status) apiParams.set('status', status)
  const request = `/api/v1/admin/users?${apiParams}`

  useEffect(() => {
    // Если условия сменятся раньше, чем придёт ответ, старый ответ уже не нужен: без этого флага
    // медленный ответ на прошлый поиск мог бы прийти последним и затереть свежий.
    let stale = false
    api.get<UserPage>(request).then(
      (result) => !stale && setUsers(result),
      (e: Error) => !stale && setError({ request, message: e.message }),
    )
    return () => {
      stale = true
    }
  }, [request, reloads])

  /** Поменять условия в адресе. Новые условия — снова с первой страницы. */
  function applyFilter(next: { q?: string; status?: string }) {
    const params = new URLSearchParams()
    const q = (next.q ?? query).trim()
    const s = next.status ?? status
    if (q) params.set('q', q)
    if (s) params.set('status', s)
    setSearchParams(params)
  }

  function goToPage(target: number) {
    const params = new URLSearchParams(searchParams)
    params.set('page', String(target))
    setSearchParams(params)
  }

  const pageCount = users === null ? 1 : Math.max(1, Math.ceil(users.total / PAGE_SIZE))

  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-xl font-semibold">Учётные записи</h1>
        <CreateUserDialog onCreated={() => setReloads((n) => n + 1)} />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {/* key: адрес сменился не из поля (кнопка «Назад» в браузере) — React создаёт поле заново,
            и в нём снова то, что ищем. */}
        <SearchForm key={query} initialQuery={query} onSearch={(q) => applyFilter({ q })} />
        <NativeSelect
          aria-label="Статус"
          value={status}
          // Список применяется сразу: выбор из списка — уже законченное действие, кнопка не нужна.
          onChange={(e) => applyFilter({ status: e.target.value })}
        >
          <NativeSelectOption value="">Все статусы</NativeSelectOption>
          {USER_STATUSES.map((s) => (
            <NativeSelectOption key={s} value={s}>
              {statusLabel(s)}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        {users !== null && <span className="ml-auto text-sm text-muted-foreground">Найдено: {users.total}</span>}
      </div>

      {error?.request === request && <p className="text-destructive">Не удалось загрузить список: {error.message}</p>}

      {users !== null && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ФИО</TableHead>
              <TableHead>Почта</TableHead>
              <TableHead>Статус</TableHead>
              <TableHead>Роли</TableHead>
              <TableHead>Последний вход</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.items.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  Никого не нашлось
                </TableCell>
              </TableRow>
            )}
            {users.items.map((user) => (
              <TableRow key={user.id}>
                <TableCell className="font-medium">
                  <Link to={`/admin/users/${user.id}`} className="hover:underline">
                    {user.fullName}
                  </Link>
                </TableCell>
                <TableCell>{user.email}</TableCell>
                <TableCell>
                  <StatusBadge status={user.status} />
                </TableCell>
                <TableCell>
                  <span className="flex flex-wrap gap-1">
                    {user.roles.map((role) => (
                      <Badge key={role} variant="secondary">
                        {roleLabel(role)}
                      </Badge>
                    ))}
                  </span>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {user.lastLoginAt === null ? '—' : formatDateTime(user.lastLoginAt)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {pageCount > 1 && (
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => goToPage(page - 1)}>
            Назад
          </Button>
          <span className="text-sm">
            Страница {page} из {pageCount}
          </span>
          <Button variant="outline" size="sm" disabled={page >= pageCount} onClick={() => goToPage(page + 1)}>
            Вперёд
          </Button>
        </div>
      )}
    </div>
  )
}
