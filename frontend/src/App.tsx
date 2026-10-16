import { useEffect, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { Layout } from '@/components/Layout'
import { api } from '@/lib/api'
import { isAdmin, MeContext } from '@/lib/me'
import type { Me } from '@/lib/types'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { PasswordPage } from '@/pages/account/PasswordPage'
import { ProfilePage } from '@/pages/account/ProfilePage'
import { AdminHomePage } from '@/pages/admin/AdminHomePage'

/**
 * Корень интерфейса: узнаёт, кто вошёл, и выбирает страницу по адресу.
 *
 * Права проверяет сервер: /admin он не отдаст не-админу, а API админки ответит 403. Проверка
 * isAdmin здесь — только чтобы не показывать пустую админку тому, кто перешёл на неё ссылкой
 * внутри интерфейса (такой переход идёт без запроса страницы к серверу).
 */
export default function App() {
  const [me, setMe] = useState<Me | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Один раз при открытии страницы. На 401 клиент API сам уведёт на вход.
  useEffect(() => {
    api.get<Me>('/api/v1/me').then(setMe, (e: Error) => setError(e.message))
  }, [])

  if (error !== null) {
    return <p className="p-8 text-destructive">Не удалось загрузить данные: {error}</p>
  }
  if (me === null) {
    return <p className="p-8 text-muted-foreground">Загрузка…</p>
  }

  return (
    <MeContext.Provider value={me}>
      <Routes>
        <Route element={<Layout />}>
          {/* На сервере / — своя страница AUF ID; сюда попадают только при разработке (Vite). */}
          <Route path="/" element={<Navigate to="/account" replace />} />
          <Route path="/account" element={<ProfilePage />} />
          <Route path="/account/password" element={<PasswordPage />} />
          <Route path="/admin" element={isAdmin(me) ? <AdminHomePage /> : <NotFoundPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </MeContext.Provider>
  )
}
