import type { PageDomain, BaseEntity } from '../common'

export interface NewsTypeConfigQueryParams extends PageDomain {
  typeName?: string
  typeCode?: string
  isActive?: number
}

export interface NewsTypeConfig extends BaseEntity {
  id?: number
  typeName?: string
  typeCode?: string
  description?: string
  sortOrder?: number
  isActive?: number
}
