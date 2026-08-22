import { Routes, Route } from "react-router-dom";
import { DevStatusPage } from "../pages/DevStatusPage";
import { HomePage } from "../pages/HomePage";
import { LoginPage } from "../features/auth/LoginPage";
import { ProtectedRoute } from "../features/auth/ProtectedRoute";

/**
 * Foundation + auth routing. Role-specific dashboard routes (Lab Assistant,
 * CR, Student) are added in Phases 20-22, once the academic domain exists -
 * `/` is a shared authenticated placeholder for all three roles until then.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <HomePage />
          </ProtectedRoute>
        }
      />
      <Route path="/dev-status" element={<DevStatusPage />} />
    </Routes>
  );
}
