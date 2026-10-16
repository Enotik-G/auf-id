import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

/**
 * Значение, которое админ должен передать дальше (ссылку человеку, секрет — разработчику сервиса):
 * поле только для чтения и кнопка «Копировать».
 *
 * Буфер обмена (navigator.clipboard) браузер даёт только на https и localhost. Не вышло — текст в
 * поле выделяется целиком, чтобы скопировать его вручную.
 */
export function CopyableValue({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false)
  // ref — прямой доступ к самому элементу <input>, чтобы выделить в нём текст.
  const field = useRef<HTMLInputElement>(null)

  async function copy() {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
    } catch {
      field.current?.select()
    }
  }

  return (
    <div className="flex gap-2">
      <Input
        ref={field}
        readOnly
        value={value}
        aria-label={label}
        onFocus={(e) => e.target.select()}
        className="font-mono text-xs"
      />
      <Button type="button" variant="outline" onClick={copy}>
        {copied ? 'Скопировано' : 'Копировать'}
      </Button>
    </div>
  )
}
