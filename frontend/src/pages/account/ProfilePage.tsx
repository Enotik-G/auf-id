import { useMe } from '@/lib/me'

// Заглушка: профиль целиком — следующим шагом.
export function ProfilePage() {
  const me = useMe()
  return <h1 className="text-xl font-semibold">Профиль: {me.email}</h1>
}
