import { Button } from '@/components/ui/button'

// Корневой компонент. Пока — заглушка: дальше здесь появятся страницы /account и /admin.
export default function App() {
  return (
    <main className="mx-auto max-w-xl p-8">
      <h1 className="text-2xl font-bold">AUF ID</h1>
      <p className="mt-2 text-muted-foreground">Веб-интерфейс: личный кабинет и панель администратора.</p>
      <div className="mt-6 flex gap-2">
        <Button>Основная</Button>
        <Button variant="outline">Вторичная</Button>
        <Button variant="destructive">Опасная</Button>
      </div>
    </main>
  )
}
