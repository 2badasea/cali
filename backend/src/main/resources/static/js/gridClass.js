// 그리드 셀에 연락처 입력 시, 하이픈 형태가 생기도록
console.log('++ js/gridClass.js');
class contact_num_editor {
	constructor(props) {
		const el = document.createElement('input');
		el.type = 'text';
		el.value = String(props.value ?? '');
		el.style.width = '100%';
		el.style.height = '100%';

		// keyup 시 전화번호 포맷 적용
		el.addEventListener('keyup', function () {
			let input = el.value;

			const tel = input.replace(/[^0-9]/g, '');

			if (/^02\d{7,8}$/.test(tel)) {
				input = tel.replace(/^(02)(\d{3,4})(\d{4})$/, '$1-$2-$3');
			} else {
				input = tel.replace(/^(\d{2,3})(\d{3,4})(\d{4})$/, `$1-$2-$3`);
			}

			el.value = input;
		});

		this.el = el;
	}

	getElement() {
		return this.el;
	}

	getValue() {
		return this.el.value;
	}

	mounted() {
		this.el.focus();
		this.el.select();
	}

	beforeDestroy() {}
}

// 품목을 조회할 수 있도록 한다.
class itemSearchEditor {
	constructor(props) {
		const { grid, rowKey, value } = props;

		const el = document.createElement('input');
		el.type = 'text';
		el.value = String(props.value ?? '');
		el.style.width = '100%';
		el.style.height = '100%';
		this.grid = grid;
		let $modal = $(grid.el).closest('.modal');

		// input 요소에 키 이벤트 바인딩
		$(el).on('keydown', async function (e) {
			if (e.keyCode === 13) {
				grid.blur();
				const rowData = grid.getRow(rowKey);
				const resModal = await gModal('/basic/searchItemList', rowData, {
					size: 'xxxl',
					title: '교정 품목 리스트',
					show_close_button: true,
				});
				if (resModal) {
					if (resModal.jsonData != undefined) {
						const d = resModal.jsonData;
						// (중요) 타입 일치: listItems.value가 보통 문자열이므로 String으로 맞추는 게 안전
						grid.setValue(rowKey, 'middleItemCodeId', String(d.middleItemCodeId ?? ''));
						grid.setValue(rowKey, 'smallItemCodeId', String(d.smallItemCodeId ?? ''));
						grid.setValue(rowKey, 'itemCaliCycle', String(d.caliCycle ?? ''));
						grid.setValue(rowKey, 'itemId', d.id);
						grid.setValue(rowKey, 'itemName', d.name);
						grid.setValue(rowKey, 'itemNameEn', d.nameEn);
						grid.setValue(rowKey, 'itemMakeAgent', d.makeAgent);
						grid.setValue(rowKey, 'itemMakeAgentEn', d.makeAgentEn);
						grid.setValue(rowKey, 'itemFormat', d.format);
						grid.setValue(rowKey, 'itemNum', d.num);
						grid.setValue(rowKey, 'caliFee', d.fee);
					}
				}
			}
		});
		this.el = el;
	}

	getElement() {
		return this.el;
	}

	getValue() {
		return this.el.value;
	}

	mounted() {
		this.el.focus();
		this.el.select();
	}

	beforeDestroy() {}
}

// editor에서 특정조건이 있는 경우엔 편집이 안 되도록 설정한다.
class readOnlyEditorByCondition {
	constructor(props) {
		const { grid, rowKey, value } = props;
		const rowData = grid.getRow(rowKey);

		const condition = props.columnInfo.editor.conditions;

		const el = document.createElement('input');
		el.type = 'text';
		el.value = String(props.value ?? '');
		el.style.width = '100%';
		el.style.height = '100%';
		el.style.textAlign = 'center';

		Object.entries(condition).forEach(([key, value]) => {
			if (rowData[key] == value) {
				el.readOnly = true;
				el.disabled = true;
			}
		});

		this.el = el;
	}

	getElement() {
		return this.el;
	}

	getValue() {
		return this.el.value.trim();
	}

	mounted() {
		this.el.focus();
		this.el.select();
	}

	beforeDestroy() {}
}

// 소분류 select box 커스텀
class small_code_selectbox_renderer {
	constructor(props) {
		const { grid, rowKey, value } = props;

		// 내부 store 접근 대신 공식 API 권장
		const rowData = grid.getRow(rowKey) || {};

		const allItems = props.columnInfo.editor.options.listItems; // tmpSmallCode (flat array)
		const middleId = rowData.middleItemCodeId; // ✅ 컬럼명과 동일하게

		// ✅ middleId에 맞게 필터링
		const filtered = allItems.filter((it) => String(it.middleCodeId) === String(middleId));

		const el = document.createElement('select');
		el.style.width = '100%';
		el.style.height = '80%';
		el.style.border = '1px solid #ddd';

		// ✅ 그리드가 mousedown/click 받으면 편집 종료해버리므로 차단
		this._stop = (e) => e.stopPropagation();
		el.addEventListener('mousedown', this._stop);
		el.addEventListener('click', this._stop);

		// 기본 옵션
		el.append(new Option('선택', ''));

		for (const item of filtered) {
			const opt = new Option(item.text, item.value);
			if (String(value) === String(item.value)) opt.selected = true; // ✅ value 기준
			el.append(opt);
		}

		this.el = el;
	}

	getElement() {
		return this.el;
	}
	getValue() {
		return this.el.value;
	}

	mounted() {
		this.el.focus(); // ✅ 포커스 주기
	}

	beforeDestroy() {
		// ✅ 리스너 정리
		this.el.removeEventListener('mousedown', this._stop);
		this.el.removeEventListener('click', this._stop);
	}
}

// 중분류 select box 커스텀
class middle_code_selectbox_renderer {
	constructor(props) {
		const { value } = props;
		const items = props.columnInfo.editor.options.listItems; // tmpMiddleCode (array)

		const el = document.createElement('select');
		el.style.width = '100%';
		el.style.height = '80%';
		el.style.border = '1px solid #ddd';

		this._stop = (e) => e.stopPropagation();
		el.addEventListener('mousedown', this._stop);
		el.addEventListener('click', this._stop);

		el.append(new Option('선택', ''));

		for (const item of items) {
			const opt = new Option(item.codeName, item.id);
			if (String(value) === String(item.id)) opt.selected = true; // ✅ value 기준
			el.append(opt);
		}

		this.el = el;
	}

	getElement() {
		return this.el;
	}
	getValue() {
		return this.el.value;
	}

	mounted() {
		this.el.focus();
	}

	beforeDestroy() {
		this.el.removeEventListener('mousedown', this._stop);
		this.el.removeEventListener('click', this._stop);
	}
}

// 금액만 입력가능
class number_format_editor {
	constructor(props) {
		const el = document.createElement('input');
		el.type = 'text';
		el.inputMode = 'numeric'; // 모바일 키패드 숫자 유도
		el.autocomplete = 'off';
		el.spellcheck = false;

		el.style.width = '100%';
		el.style.height = '100%';
		el.style.boxSizing = 'border-box';

		// 그리드 편집 종료(blur) 트리거 방지용(필요 시)
		const stop = (e) => e.stopPropagation();
		el.addEventListener('mousedown', stop);
		el.addEventListener('click', stop);
		this._stop = stop;

		// 초기값 세팅
		el.value = this._format(props.value);

		// - / + / e / . 등 차단 (type="number"에서 흔히 허용되는 것들)
		el.addEventListener('keydown', (e) => {
			const blocked = ['-', '+', 'e', 'E', '.', ',']; // 콤마는 우리가 자동 삽입하므로 직접 입력은 차단
			if (blocked.includes(e.key)) e.preventDefault();
		});

		// 붙여넣기 방어: 숫자 외 제거 후 포맷 적용
		el.addEventListener('paste', (e) => {
			e.preventDefault();
			const text = (e.clipboardData || window.clipboardData).getData('text') || '';
			const digits = text.replace(/[^\d]/g, '');
			el.value = this._format(digits);
		});

		// 입력될 때마다 숫자만 남기고 콤마 포맷
		el.addEventListener('input', () => {
			const digits = this._digits(el.value);
			el.value = this._format(digits);
			// 커서 위치를 정교하게 유지하려면 추가 로직이 필요하지만,
			// 대부분 “끝으로 이동”도 UX에 무리가 없습니다.
		});

		this.el = el;
	}

	_digits(v) {
		return String(v ?? '').replace(/[^\d]/g, ''); // 숫자만
	}

	_format(v) {
		const digits = this._digits(v);

		// 빈 값 허용 여부: "0 이상"이지만 사용자가 지우는 과정이 있으니 입력 중엔 빈값 허용
		if (digits === '') return '';

		// 선행 0 정리(예: 00012 -> 12), 단 "0" 자체는 유지
		const normalized = digits.replace(/^0+(?=\d)/, '');

		// 천 단위 콤마
		return normalized.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
	}

	getElement() {
		return this.el;
	}

	getValue() {
		const digits = this._digits(this.el.value);

		// 빈 값 처리 정책:
		// - 금액 필드가 NOT NULL이면 0으로 저장하는 게 보통 더 안전
		if (digits === '') return 0;

		const n = Number(digits);
		return Number.isFinite(n) ? n : 0;
	}

	mounted() {
		this.el.focus();
		this.el.select();
	}

	beforeDestroy() {
		// (선택) 이벤트 정리
		this.el.removeEventListener('mousedown', this._stop);
		this.el.removeEventListener('click', this._stop);
	}
}

// 날짜 선택
class DateEditor {
	constructor(props) {
		const el = document.createElement('input');
		el.type = 'date';

		// 셀 꽉 채우기
		el.style.width = '100%';
		el.style.height = '100%';
		el.style.boxSizing = 'border-box';
		el.style.border = '0';
		el.style.outline = '0';
		el.style.background = 'transparent';
		el.style.textAlign = 'center';

		// 값 세팅: DB가 YYYY-MM-DD면 그대로, 그 외면 빈 값
		const v = props.value;
		el.value = typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : '';

		// required만 옵션으로 지원(원하면)
		const opt = props.columnInfo?.editor?.options ?? {};
		if (opt.required) el.required = true;

		this.el = el;
	}

	getElement() {
		return this.el;
	}

	getValue() {
		// type="date"는 "YYYY-MM-DD" 또는 "" 로만 나온다
		return this.el.value;
	}

	mounted() {
		this.el.focus();
	}

	beforeDestroy() {}
}

// 실무자결재 페이지: 업로드 버튼 + 드래그앤드롭 커스텀 렌더러
// - 버튼 클릭: 파일 선택 창 열기 (triggerWorkApprovalFileSelect 호출)
// - 드래그앤드롭: 셀 위에 엑셀 파일 드롭
class UploadCellRenderer {
	constructor(props) {
		const el = document.createElement('div');
		el.style.cssText = 'display:flex; align-items:center; justify-content:center; width:100%; height:100%; min-height:28px;';

		const btn = document.createElement('button');
		btn.type = 'button';
		btn.className = 'btn btn-sm btn-success';
		// 엑셀 파일 아이콘(Bootstrap Icons) + EXCEL 텍스트
		const excelIconHtml = '<i class="bi bi-file-earmark-excel" style="font-size:13px; margin-right:3px; vertical-align:-1px;"></i>EXCEL';
		btn.innerHTML = excelIconHtml;

		// 버튼 클릭 → 파일 선택 창 열기 (workApproval.js에 정의된 함수 호출)
		btn.addEventListener('click', (e) => {
			e.stopPropagation();
			if (typeof triggerWorkApprovalFileSelect === 'function') {
				triggerWorkApprovalFileSelect(props.rowKey);
			}
		});

		el.appendChild(btn);

		// 드래그 진입: 시각 피드백
		el.addEventListener('dragover', (e) => {
			e.preventDefault();
			e.stopPropagation();
			el.style.background = '#e8f0fe';
			btn.innerHTML = '<i class="bi bi-box-arrow-in-down" style="font-size:13px;"></i>';
		});

		// 드래그 이탈: 초기화
		el.addEventListener('dragleave', () => {
			el.style.background = '';
			btn.innerHTML = excelIconHtml;
		});

		// 드롭: 파일 처리 (workApproval.js에 정의된 함수 호출)
		el.addEventListener('drop', (e) => {
			e.preventDefault();
			e.stopPropagation();
			el.style.background = '';
			btn.innerHTML = excelIconHtml;
			if (typeof handleWorkApprovalUploadFile === 'function') {
				handleWorkApprovalUploadFile(e.dataTransfer.files[0], props.rowKey);
			}
		});

		this.el = el;
	}

	getElement() {
		return this.el;
	}

	render(props) {}
}

// 셀에 체크박스를 "항상" 표시하는 커스텀 렌더러
class AuthCheckboxRenderer {
  constructor(props) {
    // 수직 중앙 정렬을 위한 flex wrapper
    this.wrapper = document.createElement('div');
    this.wrapper.style.cssText = 'display:flex;align-items:center;justify-content:center;height:100%;';

    this.el = document.createElement('input');
    this.el.type = 'checkbox';
    this.el.className = 'cell-auth-checkbox';

    // 셀 클릭 이벤트(행 선택 등)와 충돌 방지
    this.el.addEventListener('click', (e) => e.stopPropagation());

    // 체크 변경 시 그리드 데이터 갱신
    this.el.addEventListener('change', () => {
      const checked = this.el.checked === true;
      props.grid.setValue(props.rowKey, props.columnInfo.name, checked);
    });

    this.wrapper.appendChild(this.el);
    this.render(props);
  }

  getElement() {
    return this.wrapper;
  }

  render(props) {
    this.el.checked = props.value === true;
  }
}

// 알림 읽음 여부 렌더러 (alarm/alarmList 그리드 전용)
// isRead 값이 'n'이면 주황 점 + "미읽음", 'y'이면 회색 점 + "읽음" 표시
class IsReadRenderer {
	constructor(props) {
		this.el = document.createElement('span');
		this.render(props);
	}

	render(props) {
		const isUnread = props.value === 'n';
		this.el.innerHTML = isUnread
			? '<span style="color:#f0ad4e;font-size:.7rem;">●</span> 미읽음'
			: '<span style="color:#adb5bd;font-size:.7rem;">●</span> 읽음';
	}

	getElement() { return this.el; }
	mounted() {}
	beforeDestroy() {}
}

// 실무자결재: 성적서 파일 다운로드 렌더러 (원본/EXCEL/PDF)
// - originFileId → fileType 'origin', excelFileId → 'signed_xlsx', pdfFileId → 'signed_pdf'
// - value(fileId)가 있을 때만 아이콘 버튼 표시, 없으면 빈 셀
// - 클릭 시 window.downloadReportFile(reportId, fileType, reportNum) 호출 (workApproval.js에서 노출)
class ReportFileDownloadRenderer {
	constructor(props) {
		this.el = document.createElement('div');
		this.el.style.cssText = 'display:flex; align-items:center; justify-content:center; width:100%; height:100%; min-height:28px;';
		this.render(props);
	}

	render(props) {
		const hasFile = !!props.value;
		const colName = props.columnInfo.name; // 'originFileId' | 'excelFileId' | 'pdfFileId'

		if (!hasFile) {
			this.el.innerHTML = '';
			return;
		}

		const fileTypeMap = {
			originFileId:    'origin',
			excelFileId:     'signed_xlsx',
			pdfFileId:       'signed_pdf',
			signedXlsxFileId: 'signed_xlsx',  // managerApproval 그리드용
			signedPdfFileId:  'signed_pdf',   // managerApproval 그리드용
		};
		const fileType  = fileTypeMap[colName] ?? 'origin';
		const reportId  = props.grid.getRow(props.rowKey).id;
		const reportNum = props.grid.getRow(props.rowKey).reportNum ?? '';

		const isPdf  = colName === 'pdfFileId' || colName === 'signedPdfFileId';
		const btnCls = isPdf ? 'btn-danger' : 'btn-success';
		const icon   = isPdf ? 'bi-file-earmark-pdf-fill' : 'bi-file-earmark-excel-fill';

		this.el.innerHTML =
			`<button class="btn btn-sm ${btnCls} w-100" style="height:100%; min-height:28px;" title="다운로드">` +
			`<i class="bi ${icon}"></i></button>`;

		this.el.querySelector('button').addEventListener('click', (e) => {
			e.stopPropagation();
			if (typeof downloadReportFile === 'function') {
				downloadReportFile(reportId, fileType, reportNum);
			}
		});
	}

	getElement() { return this.el; }
}

// 실무자결재: 성적서작성(ExcelWork) 상태 배지 렌더러
// - IDLE: 빈 셀 / READY: 대기(노란) / PROGRESS: 진행중(파란+스피너) / SUCCESS: 완료(초록) / FAIL: 실패(빨간)
class WriteStatusCellRenderer {
	constructor(props) {
		this.el = document.createElement('div');
		this.el.style.cssText = 'display:flex; align-items:center; justify-content:center; width:100%; height:100%; min-height:28px;';
		this.render(props);
	}

	render(props) {
		const v = props.value;
		if (!v || v === 'IDLE') {
			this.el.innerHTML = '';
			return;
		}
		const map = {
			READY:    '<span class="badge bg-warning text-dark">대기</span>',
			PROGRESS: '<span class="badge bg-primary"><span class="spinner-border spinner-border-sm me-1" role="status" style="width:.6rem;height:.6rem;"></span>진행중</span>',
			SUCCESS:  '<span class="badge bg-success">완료</span>',
			FAIL:     '<span class="badge bg-danger">실패</span>',
		};
		this.el.innerHTML = map[v] ?? `<span class="badge bg-secondary">${v}</span>`;
	}

	getElement() { return this.el; }
}

// 실무자결재·기술책임자결재: 수리/불가/초기화 상태변경 버튼 렌더러
// - workStatus=SUCCESS(실무자결재 완료)이면 수리·불가 버튼 disabled
// - reportStatus=REPAIR이면 수리 버튼 btn-info(하늘색), IMPOSSIBLE이면 불가 버튼 btn-danger(빨간색)
// - 클릭 이벤트는 부모 페이지에서 위임(e.stopPropagation으로 그리드 행 클릭 차단)
class StatusChangeCellRenderer {
	constructor(props) {
		this.el = document.createElement('div');
		this.el.style.cssText = 'display:flex; flex-direction:column; align-items:center; gap:2px; padding:2px 0; width:100%;';
		this.render(props);
	}

	render(props) {
		const row = props.grid.getRow(props.rowKey);
		if (!row) { this.el.innerHTML = ''; return; }

		const { reportStatus, workStatus, id: reportId } = row;
		const isApproved    = workStatus === 'SUCCESS';
		const repairCls     = reportStatus === 'REPAIR'     ? 'btn-info'    : 'btn-secondary';
		const impossibleCls = reportStatus === 'IMPOSSIBLE' ? 'btn-danger'  : 'btn-secondary';
		const disabledAttr  = isApproved ? ' disabled' : '';

		this.el.innerHTML =
			`<button type="button" class="btn btn-sm ${repairCls} btn-status-repair" ` +
				`data-id="${reportId}"${disabledAttr} style="font-size:11px; padding:1px 8px; line-height:1.4; width:52px;">수리</button>` +
			`<button type="button" class="btn btn-sm ${impossibleCls} btn-status-impossible" ` +
				`data-id="${reportId}"${disabledAttr} style="font-size:11px; padding:1px 8px; line-height:1.4; width:52px;">불가</button>` +
			`<button type="button" class="btn btn-sm btn-warning btn-status-reset" ` +
				`data-id="${reportId}" style="font-size:11px; padding:1px 8px; line-height:1.4; width:52px;">초기화</button>`;

		// 그리드 행 클릭 이벤트 전파 차단 (행 선택·모달 오픈 방지)
		this.el.querySelectorAll('button').forEach(btn =>
			btn.addEventListener('click', e => e.stopPropagation())
		);
	}

	getElement() { return this.el; }
}

// 기술책임자결재: '결재자' 열 렌더러
// - approval_status !== 'SUCCESS': '반려' 버튼(btn-danger) + '확인' 버튼(btn-primary) 표시
// - approval_status === 'SUCCESS': 기술책임자 이름 + (개행) 결재일시 텍스트 표시
class ManagerApprovalCellRenderer {
	constructor(props) {
		this.el = document.createElement('div');
		this.el.style.cssText = 'display:flex; flex-direction:column; align-items:center; gap:2px; padding:2px 0; width:100%;';
		this.render(props);
	}

	render(props) {
		const row = props.grid.getRow(props.rowKey);
		if (!row) { this.el.innerHTML = ''; return; }

		const { approvalStatus, approvalMemberName, approvalDatetime, id: reportId } = row;

		if (approvalStatus === 'SUCCESS') {
			// 결재 완료: 이름 + 결재일시 표시
			const dateStr = approvalDatetime
				? String(approvalDatetime).replace('T', ' ').substring(0, 16)
				: '';
			this.el.innerHTML =
				`<span style="font-size:12px; font-weight:500;">${approvalMemberName ?? ''}</span>` +
				`<span style="font-size:11px; color:#666;">${dateStr}</span>`;
		} else {
			// 미결재: 반려 + 확인 버튼
			this.el.innerHTML =
				`<button type="button" class="btn btn-sm btn-danger btn-manager-reject" ` +
					`data-id="${reportId}" style="font-size:11px; padding:1px 8px; line-height:1.4; width:52px;">반려</button>` +
				`<button type="button" class="btn btn-sm btn-primary btn-manager-approve" ` +
					`data-id="${reportId}" style="font-size:11px; padding:1px 8px; line-height:1.4; width:52px;">확인</button>`;

			this.el.querySelectorAll('button').forEach(btn =>
				btn.addEventListener('click', e => e.stopPropagation())
			);
		}
	}

	getElement() { return this.el; }
}

// 기술책임자결재: '취소' 열 렌더러
// - approval_status === 'SUCCESS'인 행만 '취소' 버튼(btn-secondary) 표시
// - 그 외: 빈 셀
class ManagerCancelCellRenderer {
	constructor(props) {
		this.el = document.createElement('div');
		this.el.style.cssText = 'display:flex; align-items:center; justify-content:center; width:100%; height:100%; min-height:28px;';
		this.render(props);
	}

	render(props) {
		const row = props.grid.getRow(props.rowKey);
		if (!row || row.approvalStatus !== 'SUCCESS') {
			this.el.innerHTML = '';
			return;
		}
		const btn = document.createElement('button');
		btn.type = 'button';
		btn.className = 'btn btn-sm btn-secondary btn-manager-cancel';
		btn.dataset.id = row.id;
		btn.style.cssText = 'font-size:11px; padding:1px 8px; line-height:1.4;';
		btn.textContent = '취소';
		btn.addEventListener('click', e => e.stopPropagation());
		this.el.innerHTML = '';
		this.el.appendChild(btn);
	}

	getElement() { return this.el; }
}

// 실무자결재: 업로드(input[type=file]) + 조건부 결재 버튼 렌더러
// - input[type=file] : 항상 표시 → 파일 선택 시 window.handleSingleFileUpload() 호출
// - 결재 버튼        : originFileId 있을 때만 표시 → window.doWorkApproval() 호출
// (참조 함수는 workApproval.js에서 window에 노출됨)
class WorkApprovalCellRenderer {
	constructor(props) {
		this.el = document.createElement('div');
		this.el.style.cssText = 'display:flex; align-items:center; justify-content:center; gap:2px; width:100%; height:100%; min-height:28px;';
		this.render(props);
	}

	render(props) {
		const row       = props.grid.getRow(props.rowKey);
		const hasOrigin = !!(row && row.originFileId);
		const reportId  = row?.id;
		const reportNum = row?.reportNum ?? '';

		const label = document.createElement('label');
		label.className = 'btn btn-sm btn-outline-secondary mb-0';
		label.style.cssText = 'cursor:pointer; padding:2px 5px; font-size:0.75rem; line-height:1.4;';
		label.title = '파일 업로드';
		label.innerHTML = '<i class="bi bi-upload"></i>';

		const input = document.createElement('input');
		input.type   = 'file';
		input.accept = '.xlsx,.xls';
		input.style.display = 'none';
		label.appendChild(input);

		input.addEventListener('change', (e) => {
			e.stopPropagation();
			const file = e.target.files[0];
			if (!file) return;
			if (typeof handleSingleFileUpload === 'function') {
				handleSingleFileUpload(reportId, reportNum, file, input);
			}
		});

		this.el.appendChild(label);

		if (hasOrigin) {
			const btn = document.createElement('button');
			btn.className = 'btn btn-sm btn-warning';
			btn.style.cssText = 'padding:2px 5px; font-size:0.75rem; line-height:1.4;';
			btn.title = '실무자결재';
			btn.innerHTML = '<i class="bi bi-pen-fill"></i>';
			btn.addEventListener('click', (e) => {
				e.stopPropagation();
				if (typeof doWorkApproval === 'function') {
					doWorkApproval([reportId], reportNum);
				}
			});
			this.el.appendChild(btn);
		}
	}

	getElement() { return this.el; }
}
