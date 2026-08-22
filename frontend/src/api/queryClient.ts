import { QueryClient } from "@tanstack/react-query";

/**
 * Single shared QueryClient instance for the app. Domain-specific query hooks
 * (labs, allocations, etc.) are added once their backend endpoints exist -
 * see docs/10-API-DOCUMENTATION.md - not created speculatively here.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
