import { Badge } from '@/components/ui/badge'
import { statusLabel } from '@/lib/format'
import type { UserStatus } from '@/lib/types'

// Цвет плашки по смыслу: красная — доступа нет, обведённая — ещё не начал пользоваться.
const VARIANTS: Record<UserStatus, 'secondary' | 'outline' | 'destructive'> = {
  ACTIVE: 'secondary',
  INVITED: 'outline',
  BLOCKED: 'destructive',
  LOCKED: 'destructive',
  DELETED: 'outline',
}

export function StatusBadge({ status }: { status: UserStatus }) {
  return <Badge variant={VARIANTS[status]}>{statusLabel(status)}</Badge>
}
