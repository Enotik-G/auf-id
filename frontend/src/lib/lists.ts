/**
 * Список из текста, который человек ввёл в поле: по одному значению на строку или через пробел.
 * Пустые строки и пробелы по краям отбрасываются, повторы — тоже.
 *
 * «https://a/cb\n\n  https://b/cb  » → ['https://a/cb', 'https://b/cb']
 */
export function parseList(text: string): string[] {
  const items = text.split(/\s+/).filter((item) => item !== '')
  return [...new Set(items)]
}
