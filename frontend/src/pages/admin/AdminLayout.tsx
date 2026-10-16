import { NavLink, Outlet } from 'react-router'
import { cn } from '@/lib/utils'

function Tab({ to, children }: { to: string; children: string }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          '-mb-px border-b-2 px-1 pb-2 text-sm transition-colors',
          isActive ? 'border-foreground font-medium' : 'border-transparent text-muted-foreground hover:text-foreground',
        )
      }
    >
      {children}
    </NavLink>
  )
}

/** Каркас раздела «Администрирование»: вкладки и под ними текущая страница. */
export function AdminLayout() {
  return (
    <div className="grid gap-6">
      <nav className="flex gap-4 border-b">
        <Tab to="/admin/users">Учётные записи</Tab>
        <Tab to="/admin/clients">Сервисы</Tab>
      </nav>
      <Outlet />
    </div>
  )
}
