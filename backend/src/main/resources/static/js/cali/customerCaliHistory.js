$(function () {
	console.log('++ cali/customerCaliHistory.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	$modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	// 현재 페이지 추적 (모달 닫힌 후 재조회 시 유지)
	let currentPage = 1;

	// =====================================================================
	// 초기 기간 계산: 오늘 기준 최근 2주
	// =====================================================================
	function initDateRange() {
		const now = new Date();
		const twoWeeksAgo = new Date(now);
		twoWeeksAgo.setDate(now.getDate() - 14);

		const fmt = (d) =>
			`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

		$('[name=startDate]', $modal).val(fmt(twoWeeksAgo));
		$('[name=endDate]', $modal).val(fmt(now));
	}

	// =====================================================================
	// 날짜 범위 유효성 검증: 최대 3개월
	// =====================================================================
	function validateDateRange() {
		const start = $('[name=startDate]', $modal).val();
		const end   = $('[name=endDate]', $modal).val();
		if (!start || !end) return true;

		const diffMs = new Date(end) - new Date(start);
		const diffDays = diffMs / (1000 * 60 * 60 * 24);
		if (diffDays < 0) {
			gToast('시작일이 종료일보다 클 수 없습니다.', 'warning');
			return false;
		}
		if (diffDays > 93) {
			// 3개월 ≈ 93일 (최대 허용)
			gToast('조회 기간은 최대 3개월까지만 가능합니다.', 'warning');
			return false;
		}
		return true;
	}

	// =====================================================================
	// 개별 파일 다운로드 (SELF / AGCY 구분)
	//
	// SELF: /api/file/report/{reportId}/signed_xlsx | signed_pdf
	// AGCY: /api/file/fileDownload/{fileId}
	// =====================================================================
	async function downloadHistoryFile(reportId, reportType, fileId, fileType, reportNum) {
		gLoadingMessage('다운로드 중...');
		try {
			let fetchUrl;
			if (reportType === 'SELF') {
				// SELF: signed_xlsx / signed_pdf 고정 경로
				const fileTypeName = fileType === 'PDF' ? 'signed_pdf' : 'signed_xlsx';
				fetchUrl = `/api/file/report/${reportId}/${fileTypeName}`
					+ (reportNum ? `?reportNum=${encodeURIComponent(reportNum)}` : '');
			} else {
				// AGCY: file_info id 기반 다운로드
				fetchUrl = `/api/file/fileDownload/${fileId}`;
			}

			const res = await fetch(fetchUrl);
			if (!res.ok) throw new Error(`HTTP ${res.status}`);

			const blob = await res.blob();
			const cd   = res.headers.get('Content-Disposition') || '';
			const ext  = fileType === 'PDF' ? 'pdf' : 'xlsx';
			let filename = `${reportNum ?? 'file'}.${ext}`;
			const mStar  = cd.match(/filename\*=UTF-8''([^;\n]+)/i);
			const mPlain = cd.match(/filename="?([^";\n]+)"?/i);
			if (mStar)       filename = decodeURIComponent(mStar[1].trim());
			else if (mPlain) filename = mPlain[1].trim();

			const url = URL.createObjectURL(blob);
			const a   = document.createElement('a');
			a.href = url; a.download = filename;
			document.body.appendChild(a); a.click();
			document.body.removeChild(a);
			URL.revokeObjectURL(url);
			swal.close();
		} catch (e) {
			swal.close();
			console.error('[customerCaliHistory] 파일 다운로드 오류:', e);
			gToast('파일 다운로드 중 오류가 발생했습니다.', 'error');
		}
	}

	// =====================================================================
	// EXCEL 셀 렌더러 — excelFileId 기반 다운로드 버튼
	// SELF: signed_xlsx / AGCY: agcy_excel
	// =====================================================================
	class ExcelCellRenderer {
		constructor(props) {
			this.el = document.createElement('div');
			this.el.style.textAlign = 'center';
			this.render(props);
		}
		getElement() { return this.el; }
		render(props) {
			this.el.innerHTML = '';
			const fileId = props.value;
			const row    = props.grid.getRow(props.rowKey);
			if (!fileId) {
				this.el.innerHTML = '<span class="text-muted">-</span>';
				return;
			}
			const btn = document.createElement('button');
			btn.className = 'btn btn-outline-success btn-xs btn-file-download';
			btn.textContent = 'EXCEL';
			btn.onclick = (e) => {
				e.stopPropagation();
				downloadHistoryFile(row.id, row.reportType, fileId, 'EXCEL', row.reportNum);
			};
			this.el.appendChild(btn);
		}
	}

	// =====================================================================
	// PDF 셀 렌더러 — pdfFileId 기반 다운로드 버튼
	// =====================================================================
	class PdfCellRenderer {
		constructor(props) {
			this.el = document.createElement('div');
			this.el.style.textAlign = 'center';
			this.render(props);
		}
		getElement() { return this.el; }
		render(props) {
			this.el.innerHTML = '';
			const fileId = props.value;
			const row    = props.grid.getRow(props.rowKey);
			if (!fileId) {
				this.el.innerHTML = '<span class="text-muted">-</span>';
				return;
			}
			const btn = document.createElement('button');
			btn.className = 'btn btn-outline-danger btn-xs btn-file-download';
			btn.textContent = 'PDF';
			btn.onclick = (e) => {
				e.stopPropagation();
				downloadHistoryFile(row.id, row.reportType, fileId, 'PDF', row.reportNum);
			};
			this.el.appendChild(btn);
		}
	}

	// =====================================================================
	// init_modal: 페이지 초기화 (날짜 범위 설정)
	// =====================================================================
	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);
		initDateRange();
	};

	// =====================================================================
	// 그리드 데이터 소스
	// =====================================================================
	$modal.dataSource = {
		api: {
			readData: {
				url: '/api/report/agentCaliHistoryList',
				method: 'GET',
				serializer: (grid_param) => {
					grid_param.dateType   = $('[name=dateType]', $modal).val()   ?? 'approval';
					grid_param.startDate  = $('[name=startDate]', $modal).val()  ?? '';
					grid_param.endDate    = $('[name=endDate]', $modal).val()    ?? '';
					grid_param.reportType = $('[name=reportType]', $modal).val() ?? '';
					grid_param.searchType = $('[name=searchType]', $modal).val() ?? '';
					grid_param.keyword    = $('[name=keyword]', $modal).val()    ?? '';
					return $.param(grid_param);
				},
			},
		},
	};

	// =====================================================================
	// 그리드 정의
	// =====================================================================
	$modal.grid = gGrid('.customerCaliHistoryList', {
		scrollX: true,
		frozenCount: 3,   // 소분류번호 / 성적서번호 / 교정일자 3열 frozen
		columns: [
			{
				header: '소분류번호',
				name: 'smallCodeNum',
				width: 90,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '성적서번호',
				name: 'reportNum',
				width: 110,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '교정일자',
				name: 'caliDate',
				width: 90,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '신청업체',
				name: 'custAgent',
				width: 110,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '성적서발행처',
				name: 'reportAgent',
				width: 110,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '기기명',
				name: 'itemName',
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '제작회사',
				name: 'itemMakeAgent',
				width: 90,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '형식',
				name: 'itemFormat',
				width: 80,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '기기번호',
				name: 'itemNum',
				width: 90,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '실무자',
				name: 'workMemberName',
				width: 75,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '기술책임자',
				name: 'approvalMemberName',
				width: 85,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '발행일자',
				name: 'publishDate',
				width: 90,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
				formatter: function (data) {
					// AGCY는 publishDate=null → '-' 표시
					return data.value ?? '-';
				},
			},
			{
				header: 'EXCEL',
				name: 'excelFileId',
				width: 65,
				align: 'center',
				sortable: false,
				renderer: { type: ExcelCellRenderer },
			},
			{
				header: 'PDF',
				name: 'pdfFileId',
				width: 55,
				align: 'center',
				sortable: false,
				renderer: { type: PdfCellRenderer },
			},
		],
		pageOptions: {
			useClient: false,
			perPage: 100,
		},
		rowHeaders: ['checkbox'],
		minBodyHeight: 600,
		bodyHeight: 600,
		rowHeight: 'auto',
		data: $modal.dataSource,
	});

	// =====================================================================
	// 행 배경색: SELF=흰색(기본), AGCY=연한 파란색(row-agcy)
	// =====================================================================
	function applyRowClasses() {
		const allRows = $modal.grid.getData();
		allRows.forEach(function (row) {
			$modal.grid.removeRowClassName(row.rowKey, 'row-self');
			$modal.grid.removeRowClassName(row.rowKey, 'row-agcy');
			if (row.reportType === 'AGCY') {
				$modal.grid.addRowClassName(row.rowKey, 'row-agcy');
			}
		});
	}

	$modal.grid.on('response', function () {
		requestAnimationFrame(() => {
			applyRowClasses();
			currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
		});
	});

	// =====================================================================
	// 행 클릭 → 성적서 조회 모달 (읽기전용 — show_confirm_button: false)
	// 체크박스·EXCEL·PDF 컬럼 클릭 제외
	// SELF: reportModify / AGCY: agcyReportModify
	// =====================================================================
	$modal.grid.on('click', async function (ev) {
		const { columnName, rowKey } = ev;
		if (columnName === '_checked'
			|| columnName === 'excelFileId'
			|| columnName === 'pdfFileId') return;

		const row = $modal.grid.getRow(rowKey);
		if (!row || !row.id) return;

		const reportNum = row.reportNum ?? '';

		if (row.reportType === 'AGCY') {
			// 대행성적서: 읽기전용 조회 (업체계정 수정 불가)
			await gModal(
				'/cali/agcyReportModify',
				{ id: row.id },
				{
					title: `대행성적서 조회 [${reportNum || row.id}]`,
					size: 'xl',
					show_close_button: true,
					show_confirm_button: false,
				}
			);
		} else {
			// 자체성적서: 읽기전용 조회
			await gModal(
				'/cali/reportModify',
				{ id: row.id },
				{
					title: `성적서 조회 [${reportNum}]`,
					size: 'xxxl',
					show_close_button: true,
					show_confirm_button: false,
				}
			);
		}
		$modal.grid.getPagination().movePageTo(currentPage);
	});

	// =====================================================================
	// 검색 폼 이벤트
	// =====================================================================
	$modal
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();
			if (!validateDateRange()) return;
			$modal.grid.getPagination().movePageTo(1);
		})
		.on('change', '.rowLeng', function () {
			const rowLeng = $(this).val();
			if (rowLeng > 0) $modal.grid.setPerPage(rowLeng);
		});

	// =====================================================================
	// 일괄다운로드 버튼 (EXCEL / PDF)
	// SELF·AGCY 혼합 선택 시 에러 토스트 표시 (서버도 동일 검증)
	// =====================================================================
	async function bulkDownload(fileType) {
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows.length) {
			gToast('다운로드할 성적서를 선택하세요.', 'warning');
			return;
		}

		// SELF / AGCY 혼합 선택 검증
		const selfCount = checkedRows.filter(r => r.reportType === 'SELF').length;
		const agcyCount = checkedRows.filter(r => r.reportType === 'AGCY').length;
		if (selfCount > 0 && agcyCount > 0) {
			gToast('자체성적서(SELF)와 대행성적서(AGCY)는 함께 다운로드할 수 없습니다. 유형을 통일하여 선택하세요.', 'warning');
			return;
		}

		const ids = checkedRows.map(r => r.id).join(',');
		gLoadingMessage(`${checkedRows.length}개 파일 준비 중...`);
		window.location.href = `/api/report/agentCaliHistory/download?ids=${ids}&fileType=${fileType}`;
		setTimeout(() => swal.close(), 2000);
	}

	$modal.on('click', '.btnDownloadExcel', function () {
		bulkDownload('EXCEL');
	});

	$modal.on('click', '.btnDownloadPdf', function () {
		bulkDownload('PDF');
	});

	// =====================================================================
	// 페이지 마운트 처리 (common.js 규약)
	// =====================================================================
	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		setTimeout(() => {
			const p = $modal.data('param') || {};
			$modal.init_modal(p);
			if (typeof $modal.grid == 'object') {
				$modal.grid.refreshLayout();
			}
		}, 200);
	}

	if (typeof window.modal_deferred == 'object') {
		window.modal_deferred.resolve('script end');
	} else {
		if (!$modal_root.length) {
			initPage($modal);
		}
	}
});
