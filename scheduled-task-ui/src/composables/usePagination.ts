import { ref } from 'vue'
import type { PageQuery, PageResult } from '@/types'

export function usePagination(defaultSize = 20) {
  const current = ref(1)
  const size = ref(defaultSize)
  const total = ref(0)
  const pages = ref(0)
  const records = ref<any[]>([])

  const buildQuery = (extra: Record<string, any> = {}): PageQuery => ({
    current: current.value,
    size: size.value,
    ...extra,
  })

  const setPageResult = (res: PageResult<any>) => {
    total.value = res.total
    pages.value = res.pages
    current.value = res.current
    size.value = res.size
    records.value = res.records
  }

  const reset = () => {
    current.value = 1
    size.value = defaultSize
    total.value = 0
    pages.value = 0
    records.value = []
  }

  return {
    current,
    size,
    total,
    pages,
    records,
    buildQuery,
    setPageResult,
    reset,
  }
}
