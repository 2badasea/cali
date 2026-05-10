import { Routes, Route, Navigate } from "react-router-dom";
import AdminLayout from "./layouts/AdminLayout";

import Dashboard from "./pages/Dashboard";
// import MonitoringCalibration from "./pages/MonitoringCalibration";  // TODO: 추후 구현 예정
// import MonitoringEstimate from "./pages/MonitoringEstimate";        // TODO: 추후 구현 예정
import Notices from "./pages/Notices";
import CompanyInfo from "./pages/CompanyInfo";
import MenuManagement from "./pages/MenuManagement";

export default function App() {
  return (
    <Routes>
      {/* 어드민 레이아웃 아래로 페이지들이 들어감 */}
      <Route element={<AdminLayout />}>
        <Route path="/" element={<Navigate to="/notices" replace />} />
        <Route path="/dashboard" element={<Dashboard />} />
        {/* <Route path="/monitoring-calibration" element={<MonitoringCalibration />} /> */}
        {/* <Route path="/monitoring-estimate" element={<MonitoringEstimate />} /> */}
        <Route path="/notices" element={<Notices />} />
        <Route path="/company-info" element={<CompanyInfo />} />
        <Route path="/menu-management" element={<MenuManagement />} />
      </Route>

      <Route path="*" element={<div className="page">Not Found</div>} />
    </Routes>
  );
}
