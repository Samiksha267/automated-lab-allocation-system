import { Routes, Route } from "react-router-dom";
import { DevStatusPage } from "../pages/DevStatusPage";
import { HomePage } from "../pages/HomePage";
import { LoginPage } from "../features/auth/LoginPage";
import { ProtectedRoute } from "../features/auth/ProtectedRoute";
import { RequireRouteRole } from "../features/auth/RequireRouteRole";
import { LabAssistantLayout } from "../features/lab-assistant/LabAssistantLayout";
import { DashboardPage } from "../features/lab-assistant/DashboardPage";
import { LabsPage } from "../features/lab-assistant/LabsPage";
import { LabDetailPage } from "../features/lab-assistant/LabDetailPage";
import { FacultyPage } from "../features/lab-assistant/FacultyPage";
import { FacultyDetailPage } from "../features/lab-assistant/FacultyDetailPage";
import { SubjectsPage } from "../features/lab-assistant/SubjectsPage";
import { SubjectDetailPage } from "../features/lab-assistant/SubjectDetailPage";
import { AcademicHierarchyPage } from "../features/lab-assistant/AcademicHierarchyPage";
import { CrManagementPage } from "../features/lab-assistant/CrManagementPage";
import { TimetablePage } from "../features/lab-assistant/TimetablePage";
import { TimetableVersionsPage } from "../features/lab-assistant/TimetableVersionsPage";
import { TimetableVersionDetailPage } from "../features/lab-assistant/TimetableVersionDetailPage";
import { ImportsPage } from "../features/lab-assistant/ImportsPage";
import { ImportReviewPage } from "../features/lab-assistant/ImportReviewPage";
import { ConflictsPage } from "../features/lab-assistant/ConflictsPage";
import { AuditLogsPage } from "../features/lab-assistant/AuditLogsPage";
import { AnalyticsPage } from "../features/lab-assistant/AnalyticsPage";
import { CrLayout } from "../features/cr/CrLayout";
import { MyClassPage } from "../features/cr/MyClassPage";
import { MyTimetablePage } from "../features/cr/MyTimetablePage";
import { AvailableLabsPage } from "../features/cr/AvailableLabsPage";
import { ScheduleExtraLabPage } from "../features/cr/ScheduleExtraLabPage";
import { MyExtraLabsPage } from "../features/cr/MyExtraLabsPage";
import { StudentLayout } from "../features/student/StudentLayout";
import { StudentTimetablePage } from "../features/student/StudentTimetablePage";

/**
 * `/lab-assistant/*` (Phase 20) is guarded twice: `ProtectedRoute`
 * (authenticated at all) then `RequireRouteRole` (must be LAB_ASSISTANT) -
 * a CR/Student who navigates here directly is redirected to `/`, never
 * shown management content (PART 3/55). Both are UX guards only; the
 * backend's `@PreAuthorize` remains the real boundary
 * (docs/09-AUTHORIZATION-RBAC.md).
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

      <Route
        path="/lab-assistant"
        element={
          <ProtectedRoute>
            <RequireRouteRole roles={["LAB_ASSISTANT"]}>
              <LabAssistantLayout />
            </RequireRouteRole>
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="labs" element={<LabsPage />} />
        <Route path="labs/:labId" element={<LabDetailPage />} />
        <Route path="faculty" element={<FacultyPage />} />
        <Route path="faculty/:facultyId" element={<FacultyDetailPage />} />
        <Route path="subjects" element={<SubjectsPage />} />
        <Route path="subjects/:subjectId" element={<SubjectDetailPage />} />
        <Route path="academic-hierarchy" element={<AcademicHierarchyPage />} />
        <Route path="cr-management" element={<CrManagementPage />} />
        <Route path="timetable" element={<TimetablePage />} />
        <Route path="timetable-versions" element={<TimetableVersionsPage />} />
        <Route path="timetable-versions/:versionId" element={<TimetableVersionDetailPage />} />
        <Route path="imports" element={<ImportsPage />} />
        <Route path="imports/:importId" element={<ImportReviewPage />} />
        <Route path="conflicts" element={<ConflictsPage />} />
        <Route path="audit-logs" element={<AuditLogsPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
      </Route>

      <Route
        path="/cr"
        element={
          <ProtectedRoute>
            <RequireRouteRole roles={["CR"]}>
              <CrLayout />
            </RequireRouteRole>
          </ProtectedRoute>
        }
      >
        <Route index element={<MyClassPage />} />
        <Route path="timetable" element={<MyTimetablePage />} />
        <Route path="available-labs" element={<AvailableLabsPage />} />
        <Route path="extra-labs/new" element={<ScheduleExtraLabPage />} />
        <Route path="extra-labs" element={<MyExtraLabsPage />} />
      </Route>

      <Route
        path="/student"
        element={
          <ProtectedRoute>
            <RequireRouteRole roles={["STUDENT"]}>
              <StudentLayout />
            </RequireRouteRole>
          </ProtectedRoute>
        }
      >
        <Route index element={<StudentTimetablePage />} />
        <Route path="timetable" element={<StudentTimetablePage />} />
      </Route>
    </Routes>
  );
}
