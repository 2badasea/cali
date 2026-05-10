// src/pages/MenuManagement.jsx
import { useState, useEffect, useCallback, useRef } from "react";
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
  arrayMove,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { adminFetch, adminAlert, adminConfirm, adminLoading, adminCloseLoading, adminToast } from "../utils/adminCommon";

// ── 상수 ──────────────────────────────────────────────────────────────────────

const TARGET_OPTIONS = [
  { value: "_self", label: "현재 탭 (_self)" },
  { value: "_blank", label: "새 탭 (_blank)" },
];

const EMPTY_FORM = {
  menuAlias: "",
  menuCode: "",
  url: "",
  target: "_self",
  isVisible: "y",
};

// ── 헬퍼: 부모 노드의 현재 자식 목록 탐색 (드래그 원본 순서 추출용) ───────────

function findChildrenByParentId(nodes, parentId) {
  for (const n of nodes) {
    if (n.id === parentId) return n.children ?? [];
    const found = findChildrenByParentId(n.children ?? [], parentId);
    if (found !== null) return found;
  }
  return null;
}

// ── 헬퍼: 드래그 순서 변경 confirm 다이얼로그 HTML 생성 ───────────────────────

function buildReorderHtml(original, reordered) {
  const toRows = (list) =>
    list.map((n, i) => `<div class="reorder-row">${i + 1}. ${n.menuAlias}</div>`).join("");
  return `
    <div class="reorder-confirm-grid">
      <div class="reorder-col">
        <div class="reorder-col-title">현재 순서</div>
        ${toRows(original)}
      </div>
      <div class="reorder-arrow">→</div>
      <div class="reorder-col">
        <div class="reorder-col-title">변경 후 순서</div>
        ${toRows(reordered)}
      </div>
    </div>`;
}

// ── 드래그 가능한 메뉴 노드 ────────────────────────────────────────────────────

function SortableMenuItem({ node, selected, onSelect, onContextMenu, isCreatingChild }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: node.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : 1,
  };

  const classNames = [
    "menu-tree-item",
    selected?.id === node.id ? "selected" : "",
    isCreatingChild ? "creating-child" : "",
    node.isVisible === "n" ? "is-hidden" : "",
  ].filter(Boolean).join(" ");

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={classNames}
      onClick={() => onSelect(node)}
      onContextMenu={(e) => onContextMenu(e, node)}
      {...attributes}
      {...listeners}
    >
      <span className="menu-tree-bullet">☰</span>
      <span className="menu-tree-label">{node.menuAlias}</span>
      {node.isVisible === "n" && <span className="menu-tree-hidden-badge">숨김</span>}
    </div>
  );
}

// ── 재귀 트리 렌더러 ──────────────────────────────────────────────────────────

function MenuTreeLevel({ nodes, parentNode, selected, onSelect, onContextMenu, onDragEnd, createParent }) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));
  const [expanded, setExpanded] = useState({});

  const toggleExpand = (e, id) => {
    e.stopPropagation();
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const handleDragEnd = (event) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const oldIndex = nodes.findIndex((n) => n.id === active.id);
    const newIndex = nodes.findIndex((n) => n.id === over.id);
    const reordered = arrayMove(nodes, oldIndex, newIndex);

    // 부모 컴포넌트로 위임 (confirm + API 호출은 상위에서 처리)
    onDragEnd(parentNode, reordered);
  };

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={nodes.map((n) => n.id)} strategy={verticalListSortingStrategy}>
        {nodes.map((node) => (
          <div key={node.id} className="menu-tree-node-wrap">
            <div className="menu-tree-row">
              {/* 펼치기/접기 버튼 */}
              {node.children?.length > 0 ? (
                <button
                  className="menu-tree-expand-btn"
                  onClick={(e) => toggleExpand(e, node.id)}
                >
                  {expanded[node.id] ? "▾" : "▸"}
                </button>
              ) : (
                <span className="menu-tree-expand-placeholder" />
              )}

              <SortableMenuItem
                node={node}
                selected={selected}
                onSelect={onSelect}
                onContextMenu={onContextMenu}
                isCreatingChild={createParent?.id === node.id}
              />
            </div>

            {/* 하위 메뉴 재귀 렌더링 */}
            {node.children?.length > 0 && expanded[node.id] && (
              <div className="menu-tree-children">
                <MenuTreeLevel
                  nodes={node.children}
                  parentNode={node}
                  selected={selected}
                  onSelect={onSelect}
                  onContextMenu={onContextMenu}
                  onDragEnd={onDragEnd}
                  createParent={createParent}
                />
              </div>
            )}
          </div>
        ))}
      </SortableContext>
    </DndContext>
  );
}

// ── 우클릭 컨텍스트 메뉴 ──────────────────────────────────────────────────────

function ContextMenu({ x, y, targetNode, onCreateChild, onDelete, onClose }) {
  const ref = useRef(null);

  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose();
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [onClose]);

  return (
    <ul
      ref={ref}
      className="menu-context-menu"
      style={{ top: y, left: x }}
    >
      <li onClick={() => { onCreateChild(targetNode); onClose(); }}>자식 메뉴 생성</li>
      <li className="danger" onClick={() => { onDelete(targetNode); onClose(); }}>메뉴 삭제</li>
    </ul>
  );
}

// ── 우측 폼 ───────────────────────────────────────────────────────────────────

function MenuForm({ mode, form, onChange, onSave, onCheckCode, codeChecked }) {
  // mode: "idle" | "create" | "edit"
  if (mode === "idle") {
    return (
      <div className="menu-form-placeholder">
        <p>트리에서 메뉴를 선택하거나<br />상단의 <b>메뉴 추가</b> 버튼을 클릭하세요.</p>
      </div>
    );
  }

  const title = mode === "create" ? "메뉴 등록" : "메뉴 수정";

  return (
    <div className="menu-form">
      <h3 className="menu-form-title">{title}</h3>

      <div className="menu-form-grid">
        {/* 메뉴명 */}
        <label className="menu-form-label required">메뉴명</label>
        <input
          className="menu-form-input"
          value={form.menuAlias}
          onChange={(e) => onChange("menuAlias", e.target.value)}
          placeholder="화면에 표시될 메뉴명"
          maxLength={100}
        />

        {/* 메뉴 코드 */}
        <label className="menu-form-label required">메뉴 코드</label>
        <div className="menu-form-code-row">
          <input
            className="menu-form-input"
            value={form.menuCode}
            onChange={(e) => onChange("menuCode", e.target.value.toUpperCase())}
            placeholder="예: WORK_APPROVAL"
            maxLength={50}
          />
          <button className="menu-form-check-btn" onClick={onCheckCode}>
            중복확인
          </button>
          {codeChecked === true && <span className="code-ok">✓ 사용 가능</span>}
          {codeChecked === false && <span className="code-ng">✗ 중복</span>}
        </div>

        {/* URL */}
        <label className="menu-form-label">링크 URL</label>
        <input
          className="menu-form-input"
          value={form.url}
          onChange={(e) => onChange("url", e.target.value)}
          placeholder="예: /cali/workApproval"
          maxLength={255}
        />

        {/* 링크 방식 */}
        <label className="menu-form-label">링크 방식</label>
        <select
          className="menu-form-input"
          value={form.target}
          onChange={(e) => onChange("target", e.target.value)}
        >
          {TARGET_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>

        {/* 노출 여부 */}
        <label className="menu-form-label">노출 여부</label>
        <select
          className="menu-form-input"
          value={form.isVisible}
          onChange={(e) => onChange("isVisible", e.target.value)}
        >
          <option value="y">노출</option>
          <option value="n">숨김</option>
        </select>
      </div>

      <div className="menu-form-actions">
        <button className="menu-form-save-btn" onClick={onSave}>저장</button>
      </div>
    </div>
  );
}

// ── 메인 페이지 ───────────────────────────────────────────────────────────────

export default function MenuManagement() {
  const [tree, setTree] = useState([]);             // 전체 트리 데이터
  const [selected, setSelected] = useState(null);   // 현재 선택된 메뉴 노드
  const [mode, setMode] = useState("idle");         // "idle" | "create" | "edit"
  const [form, setForm] = useState(EMPTY_FORM);
  const [codeChecked, setCodeChecked] = useState(null); // null | true | false
  const [createParent, setCreateParent] = useState(null); // 자식 생성 시 부모 노드
  const [contextMenu, setContextMenu] = useState(null);  // { x, y, node }

  // 폼이 열릴 때의 원본값 — dirty 감지에 사용
  const originalFormRef = useRef({ ...EMPTY_FORM });

  // ── dirty 감지 ────────────────────────────────────────────────────────────

  const isDirty = () => {
    if (mode === "idle") return false;
    return Object.keys(EMPTY_FORM).some((k) => form[k] !== originalFormRef.current[k]);
  };

  // ── 폼 이탈 전 dirty 체크: dirty 상태면 confirm → 취소 시 false 반환 ────────

  const checkDirtyBeforeLeave = async () => {
    if (!isDirty()) return true;
    return adminConfirm(
      "편집 중인 내용이 있습니다",
      "저장하지 않고 이동하시겠습니까?<br/>입력한 내용은 사라집니다."
    );
  };

  // ── 트리 로드 ──────────────────────────────────────────────────────────────

  const loadTree = useCallback(async () => {
    try {
      const res = await adminFetch("/api/admin/menus/tree");
      setTree(res.data ?? []);
    } catch {
      adminAlert("메뉴 목록 조회 실패", "메뉴 트리를 불러오지 못했습니다.");
    }
  }, []);

  useEffect(() => { loadTree(); }, [loadTree]);

  // ── 폼 필드 변경 ──────────────────────────────────────────────────────────

  const handleFormChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (field === "menuCode") setCodeChecked(null); // 코드 변경 시 중복확인 초기화
  };

  // ── 대메뉴 추가 버튼 ───────────────────────────────────────────────────────

  const handleAddRootMenu = async () => {
    if (!(await checkDirtyBeforeLeave())) return;
    setSelected(null);
    setCreateParent(null);
    setForm(EMPTY_FORM);
    setCodeChecked(null);
    setMode("create");
    originalFormRef.current = { ...EMPTY_FORM };
  };

  // ── 트리 노드 클릭 → 수정 폼 로드 ─────────────────────────────────────────

  const handleSelect = async (node) => {
    // 이미 선택된 노드는 다시 클릭해도 dirty check 없이 무시
    if (selected?.id === node.id && mode === "edit") return;
    if (!(await checkDirtyBeforeLeave())) return;
    setSelected(node);
    setCreateParent(null);
    const f = {
      menuAlias: node.menuAlias ?? "",
      menuCode: node.menuCode ?? "",
      url: node.url ?? "",
      target: node.target ?? "_self",
      isVisible: node.isVisible ?? "y",
    };
    setForm(f);
    originalFormRef.current = { ...f };
    setCodeChecked(null);
    setMode("edit");
  };

  // ── 우클릭 컨텍스트 메뉴 ──────────────────────────────────────────────────

  const handleContextMenu = (e, node) => {
    e.preventDefault();
    setContextMenu({ x: e.clientX, y: e.clientY, node });
  };

  // ── 자식 메뉴 생성 (컨텍스트 메뉴에서 호출) ───────────────────────────────

  const handleCreateChild = async (parentNode) => {
    if (!(await checkDirtyBeforeLeave())) return;
    setSelected(null);
    setCreateParent(parentNode);
    setForm(EMPTY_FORM);
    setCodeChecked(null);
    setMode("create");
    originalFormRef.current = { ...EMPTY_FORM };
  };

  // ── 메뉴 코드 중복 확인 ────────────────────────────────────────────────────

  const handleCheckCode = async () => {
    if (!form.menuCode.trim()) {
      adminToast("메뉴 코드를 입력해주세요.", "warning");
      return;
    }
    try {
      const excludeId = mode === "edit" ? selected?.id : null;
      const params = new URLSearchParams({ menuCode: form.menuCode });
      if (excludeId) params.append("excludeId", excludeId);
      const res = await adminFetch(`/api/admin/menus/check-code?${params}`);
      setCodeChecked(!res.data); // data=true이면 중복 → codeChecked=false
    } catch {
      adminToast("중복 확인 중 오류가 발생했습니다.", "error");
    }
  };

  // ── 저장 (등록 / 수정) ─────────────────────────────────────────────────────

  const handleSave = async () => {
    if (!form.menuAlias.trim()) { adminToast("메뉴명을 입력해주세요.", "warning"); return; }
    if (!form.menuCode.trim())  { adminToast("메뉴 코드를 입력해주세요.", "warning"); return; }
    if (codeChecked === null)   { adminToast("메뉴 코드 중복 확인을 해주세요.", "warning"); return; }
    if (codeChecked === false)  { adminToast("중복된 메뉴 코드입니다.", "warning"); return; }

    const confirmed = await adminConfirm(
      mode === "create" ? "메뉴 등록" : "메뉴 수정",
      mode === "create" ? "메뉴를 등록하시겠습니까?" : "변경 내용을 저장하시겠습니까?"
    );
    if (!confirmed) return;

    adminLoading("저장 중...");
    try {
      if (mode === "create") {
        const body = { ...form, parentId: createParent?.id ?? null };
        await adminFetch("/api/admin/menus", { method: "POST", body: JSON.stringify(body) });
      } else {
        await adminFetch(`/api/admin/menus/${selected.id}`, { method: "PATCH", body: JSON.stringify(form) });
      }
      await loadTree();
      setMode("idle");
      setSelected(null);
      setForm(EMPTY_FORM);
      setCodeChecked(null);
      originalFormRef.current = { ...EMPTY_FORM };
      adminCloseLoading();
      adminToast("저장되었습니다.", "success");
    } catch (err) {
      adminCloseLoading();
      // adminFetch는 { data: { code, msg, data } } 형태의 Error를 throw함
      const msg = err.data?.msg ?? "저장 중 오류가 발생했습니다.";
      adminAlert("저장 실패", msg);
    }
  };

  // ── 메뉴 삭제 (컨텍스트 메뉴에서 호출) ───────────────────────────────────

  const handleDelete = async (node) => {
    const confirmed = await adminConfirm(
      "메뉴 삭제",
      `'${node.menuAlias}' 메뉴를 삭제하시겠습니까?<br/>연결된 읽기 권한도 함께 삭제됩니다.`
    );
    if (!confirmed) return;

    adminLoading("삭제 중...");
    try {
      await adminFetch(`/api/admin/menus/${node.id}`, { method: "DELETE" });
      await loadTree();
      if (selected?.id === node.id) {
        setMode("idle");
        setSelected(null);
        originalFormRef.current = { ...EMPTY_FORM };
      }
      adminCloseLoading();
      adminToast("삭제되었습니다.", "success");
    } catch (err) {
      adminCloseLoading();
      const msg = err.data?.msg ?? "삭제 중 오류가 발생했습니다.";
      adminAlert("삭제 실패", msg);
    }
  };

  // ── 드래그 앤 드롭 순서 변경 ──────────────────────────────────────────────

  const handleDragEnd = async (parentNode, reorderedSiblings) => {
    // 드롭 전 현재 순서 추출 (tree 상태 기준)
    const originalSiblings = parentNode === null
      ? tree
      : (findChildrenByParentId(tree, parentNode.id) ?? []);

    // 순서 변경 전/후 비교 confirm
    const html = buildReorderHtml(originalSiblings, reorderedSiblings);
    const confirmed = await adminConfirm("메뉴 순서 변경", html);
    if (!confirmed) return;

    // 낙관적 UI 업데이트
    const updateTreeNode = (nodes) =>
      nodes.map((n) => {
        if (parentNode === null && reorderedSiblings.some((s) => s.id === n.id)) {
          return reorderedSiblings.find((s) => s.id === n.id);
        }
        if (n.id === parentNode?.id) {
          return { ...n, children: reorderedSiblings };
        }
        return { ...n, children: updateTreeNode(n.children ?? []) };
      });

    const updated = parentNode === null ? reorderedSiblings : updateTreeNode(tree);
    setTree(updated);

    // 서버에 새 sort_order 전송
    const items = reorderedSiblings.map((n, idx) => ({ id: n.id, sortOrder: idx + 1 }));
    try {
      await adminFetch("/api/admin/menus/reorder", { method: "PATCH", body: JSON.stringify(items) });
      adminToast("순서가 변경되었습니다.", "success");
    } catch {
      adminToast("순서 변경 저장에 실패했습니다. 새로고침 후 다시 시도해주세요.", "error");
      loadTree(); // 실패 시 서버 상태로 롤백
    }
  };

  // ── 렌더링 ────────────────────────────────────────────────────────────────

  return (
    <div className="page">
      <h5 className="env-page-title">메뉴 관리</h5>

      <div className="menu-mgmt-layout">
        {/* ── 좌측: 트리 패널 ── */}
        <div className="menu-tree-panel">
          <div className="menu-tree-toolbar">
            <button className="menu-add-btn" onClick={handleAddRootMenu}>+ 메뉴 추가</button>
          </div>

          <div className="menu-tree-body">
            {tree.length === 0 ? (
              <p className="menu-tree-empty">등록된 메뉴가 없습니다.</p>
            ) : (
              <MenuTreeLevel
                nodes={tree}
                parentNode={null}
                selected={selected}
                onSelect={handleSelect}
                onContextMenu={handleContextMenu}
                onDragEnd={handleDragEnd}
                createParent={createParent}
              />
            )}
          </div>
        </div>

        {/* ── 우측: 상세 폼 ── */}
        <div className="menu-form-panel">
          {mode !== "idle" && createParent && (
            <p className="menu-form-parent-info">
              상위 메뉴: <strong>{createParent.menuAlias}</strong>
            </p>
          )}
          <MenuForm
            mode={mode}
            form={form}
            onChange={handleFormChange}
            onSave={handleSave}
            onCheckCode={handleCheckCode}
            codeChecked={codeChecked}
          />
        </div>
      </div>

      {/* ── 우클릭 컨텍스트 메뉴 ── */}
      {contextMenu && (
        <ContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          targetNode={contextMenu.node}
          onCreateChild={handleCreateChild}
          onDelete={handleDelete}
          onClose={() => setContextMenu(null)}
        />
      )}
    </div>
  );
}
