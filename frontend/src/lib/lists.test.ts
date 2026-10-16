import { describe, expect, it } from 'vitest'
import { parseList } from './lists'

describe('parseList', () => {
  it('делит по строкам и пробелам, отбрасывает пустое', () => {
    expect(parseList('https://a/cb\n\n  https://b/cb  ')).toEqual(['https://a/cb', 'https://b/cb'])
    expect(parseList('openid profile')).toEqual(['openid', 'profile'])
  })

  it('пустое поле — пустой список', () => {
    expect(parseList('   \n ')).toEqual([])
  })

  it('убирает повторы', () => {
    expect(parseList('openid openid')).toEqual(['openid'])
  })
})
