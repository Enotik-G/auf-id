import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div>
      <h1 className="text-xl font-semibold">Страница не найдена</h1>
      <p className="mt-2 text-muted-foreground">
        <Link to="/account" className="underline underline-offset-4">
          В профиль
        </Link>
      </p>
    </div>
  )
}
