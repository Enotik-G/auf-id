import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

/**
 * Ссылка, которую админ должен передать человеку: поле только для чтения и кнопка «Копировать».
 *
 * Буфер обмена (navigator.clipboard) браузер даёт только на https и localhost. Не вышло — текст в
 * поле выделяется целиком, чтобы скопировать его вручную.
 */
export function CopyableLink({ link }: { link: string }) {
  const [copied, setCopied] = useState(false)
  // ref — прямой доступ к самому элементу <input>, чтобы выделить в нём текст.
  const field = useRef<HTMLInputElement>(null)

  async function copy() {
    try {
      await navigator.clipboard.writeText(link)
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
        value={link}
        aria-label="Ссылка"
        onFocus={(e) => e.target.select()}
        className="font-mono text-xs"
      />
      <Button type="button" variant="outline" onClick={copy}>
        {copied ? 'Скопировано' : 'Копировать'}
      </Button>
    </div>
  )
}
