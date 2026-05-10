// src/components/Sidebar.jsx
import { NavLink } from "react-router-dom";

const menus = [
  { to: "/dashboard", label: "대시보드" },
  // { to: "/monitoring-calibration", label: "모니터링(교정)" },  // TODO: 추후 구현 예정
  // { to: "/monitoring-estimate", label: "모니터링(견적)" },     // TODO: 추후 구현 예정
  { to: "/notices", label: "업데이트공지" },
  { to: "/company-info", label: "회사정보" },
  { to: "/menu-management", label: "메뉴관리" },
];

const backendOrigin = import.meta.env.VITE_BACKEND_ORIGIN;

export default function Sidebar() {
  return (
    <div className="sidebar">
      <div className="sidebar-brand">
        <div className="brand-mark" />
        <div className="brand-title">CALI ADMIN</div>
      </div>

      <nav className="sidebar-nav">
        {menus.map((m) => (
          <NavLink
            key={m.to}
            to={m.to}
            className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
          >
            <span className="sidebar-bullet" />
            <span className="sidebar-text">{m.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-footer">
        <button className="sidebar-cta" type="button" onClick={() => {
          location.href = `${backendOrigin}/cali/caliOrder`;
        }}>
          교정관리
        </button>
      </div>
    </div>
  );
}
