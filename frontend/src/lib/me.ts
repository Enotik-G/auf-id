import { createContext, useContext } from 'react'
import type { Me } from './types'

// «Кто вошёл» — общее для всех страниц. App загружает его один раз и кладёт в контекст, а любой
// компонент достаёт через useMe(), не передавая через все уровни разметки.
export const MeContext = createContext<Me | null>(null)

export function useMe(): Me {
  const me = useContext(MeContext)
  if (me === null) {
    // Ошибка программиста, а не пользователя: компонент нарисован вне <MeContext.Provider>.
    throw new Error('useMe() вызван вне MeContext')
  }
  return me
}

export function isAdmin(me: Me): boolean {
  return me.roles.includes('ADMIN')
}
