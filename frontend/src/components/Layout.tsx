import type { ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router'
import { LogoutButton } from '@/components/LogoutButton'
import { isAdmin, useMe } from '@/lib/me'
import { cn } from '@/lib/utils'

/** Пункт меню: NavLink сам знает, открыта ли его страница, — активный выделяем. */
function MenuLink({ to, end, children }: { to: string; end?: boolean; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn('rounded-md px-2.5 py-1.5 text-sm transition-colors hover:bg-muted', isActive && 'bg-muted font-medium')
      }
    >
      {children}
    </NavLink>
  )
}

/** Общий каркас всех страниц: шапка с меню, а ниже — текущая страница (<Outlet />). */
export function Layout() {
  const me = useMe()
  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex max-w-5xl items-center gap-4 px-4 py-2">
          <span className="font-heading font-semibold">AUF ID</span>
          <nav className="flex gap-1">
            {/* end — «активен только на самом /account», а не на всех /account/… */}
            <MenuLink to="/account" end>
              Профиль
            </MenuLink>
            <MenuLink to="/account/password">Пароль</MenuLink>
            {isAdmin(me) && <MenuLink to="/admin">Администрирование</MenuLink>}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <span className="text-sm text-muted-foreground">{me.fullName}</span>
            <LogoutButton />
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
