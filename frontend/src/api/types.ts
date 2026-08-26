/** Spring Data's standard `Page<T>` JSON shape - every paginated endpoint in this backend returns this. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
