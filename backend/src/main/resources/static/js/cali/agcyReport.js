$(function () {
	console.log('++ cali/agcyReport.js');

	// $modal/$modal_root 선택 (F5 표준)
	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	const $bodyCandidate = $candidates.filter('.modal-body');
	if ($bodyCandidate.length) {
		$modal = $bodyCandidate.first();
	} else {
		$modal = $candidates.first();
	}
	let $modal_root = $modal.closest('.modal');

	// 현재 페이지 (모달 닫힌 후 재조회 시 유지)
	let currentPage = 1;

	// =====================================================================
	// EXCEL 셀 렌더러 — excelFileId 기반 다운로드 버튼
	// =====================================================================
	class AgcyExcelRenderer {
		constructor(props) {
			this.el = document.createElement('div');
			this.el.style.textAlign = 'center';
			this.render(props);
		}
		getElement() { return this.el; }
		render(props) {
			this.el.innerHTML = '';
			const fileId = props.value;
			if (!fileId) {
				this.el.innerHTML = '<span class="text-muted">-</span>';
				return;
			}
			const btn = document.createElement('button');
			btn.type = 'button';
			btn.className = 'btn btn-outline-success btn-xs';
			btn.textContent = 'EXCEL';
			btn.onclick = (e) => {
				e.stopPropagation();
				window.location.href = '/api/file/fileDownload/' + fileId;
			};
			this.el.appendChild(btn);
		}
	}

	// =====================================================================
	// PDF 셀 렌더러 — pdfFileId 기반 다운로드 버튼
	// =====================================================================
	class AgcyPdfRenderer {
		constructor(props) {
			this.el = document.createElement('div');
			this.el.style.textAlign = 'center';
			this.render(props);
		}
		getElement() { return this.el; }
		render(props) {
			this.el.innerHTML = '';
			const fileId = props.value;
			if (!fileId) {
				this.el.innerHTML = '<span class="text-muted">-</span>';
				return;
			}
			const btn = document.createElement('button');
			btn.type = 'button';
			btn.className = 'btn btn-outline-danger btn-xs';
			btn.textContent = 'PDF';
			btn.onclick = (e) => {
				e.stopPropagation();
				window.location.href = '/api/file/fileDownload/' + fileId;
			};
			this.el.appendChild(btn);
		}
	}

	// =====================================================================
	// 그리드 데이터 소스 (TUI Grid dataSource — 서버 페이징)
	// =====================================================================
	$modal.dataSource = {
		api: {
			readData: {
				url: '/api/report/agcyReportList',
				method: 'GET',
				serializer: (grid_param) => {
					grid_param.status     = $('[name=status]', $modal).val()     || '';
					grid_param.startDate  = $('[name=startDate]', $modal).val()  || '';
					grid_param.endDate    = $('[name=endDate]', $modal).val()    || '';
					grid_param.searchType = $('[name=searchType]', $modal).val() || 'all';
					grid_param.keyword    = $('[name=keyword]', $modal).val()    || '';
					return $.param(grid_param);
				},
			},
		},
	};

	// =====================================================================
	// 그리드 정의 (gGrid 래퍼)
	// =====================================================================
	$modal.grid = gGrid('.agcyReportList', {
		scrollX: true,
		frozenCount: 2, // 접수구분, 접수번호 freeze
		bodyHeight: 663,
		minBodyHeight: 663,
		rowHeaders: ['checkbox'],
		pageOptions: {
			useClient: false,
			perPage: 25,
		},
		rowHeight: 'auto',
		data: $modal.dataSource,
		columns: [
			{
				header: '접수구분',
				name: 'orderType',
				width: 80,
				align: 'center',
				className: 'cursor_pointer',
				formatter: ({ value }) => {
					if (value === 'ACCREDDIT')   return '공인';
					if (value === 'UNACCREDDIT') return '비공인';
					if (value === 'TESTING')     return '시험';
					return value ?? '';
				},
			},
			{
				header: '접수번호',
				name: 'orderNum',
				width: 130,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '자체성적서번호',
				name: 'agcySelfReportNum',
				width: 150,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '외부성적서번호',
				name: 'reportNum',
				width: 150,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '신청업체',
				name: 'custAgent',
				minWidth: 150,
				align: 'left',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '성적서발행처',
				name: 'reportAgent',
				minWidth: 130,
				align: 'left',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '대행의뢰처',
				name: 'agcyAgent',
				minWidth: 130,
				align: 'left',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '기기명',
				name: 'itemName',
				minWidth: 150,
				align: 'left',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '제작회사',
				name: 'itemMakeAgent',
				minWidth: 120,
				align: 'left',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '형식',
				name: 'itemFormat',
				width: 100,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '기기번호',
				name: 'itemNum',
				width: 110,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '교정수수료',
				name: 'caliFee',
				width: 100,
				align: 'right',
				className: 'cursor_pointer',
				formatter: ({ value }) => (value != null ? Number(value).toLocaleString() : ''),
			},
			{
				header: '교정일자',
				name: 'caliDate',
				width: 100,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '비고',
				name: 'remark',
				minWidth: 120,
				align: 'left',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '진행상태',
				name: 'reportStatus',
				width: 80,
				align: 'center',
				className: 'cursor_pointer',
				formatter: ({ value }) => {
					if (value === 'NORMAL')  return '대기';
					if (value === 'SUCCESS') return '완료';
					if (value === 'CANCEL')  return '취소';
					return value ?? '';
				},
			},
			{
				header: 'EXCEL',
				name: 'excelFileId',
				width: 70,
				align: 'center',
				sortable: false,
				renderer: { type: AgcyExcelRenderer },
			},
			{
				header: 'PDF',
				name: 'pdfFileId',
				width: 60,
				align: 'center',
				sortable: false,
				renderer: { type: AgcyPdfRenderer },
			},
		],
	});

	// =====================================================================
	// 행 색상 적용 (SUCCESS → 초록, CANCEL → 붉은색)
	// response 이벤트 후 호출
	// =====================================================================
	function applyRowColors() {
		$modal.grid.getData().forEach((row) => {
			if (row.reportStatus === 'CANCEL') {
				$modal.grid.addRowClassName(row.rowKey, 'row-cancel');
			} else if (row.reportStatus === 'SUCCESS') {
				$modal.grid.addRowClassName(row.rowKey, 'row-complete');
			}
		});
	}

	// 데이터 로드 완료 시 행 색상 및 현재 페이지 갱신
	$modal.grid.on('response', function () {
		requestAnimationFrame(() => {
			applyRowColors();
			currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
		});
	});

	// =====================================================================
	// 일괄 다운로드 (EXCEL 또는 PDF)
	// =====================================================================
	async function bulkDownload(fileType) {
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows.length) {
			gToast('다운로드할 성적서를 선택하세요.', 'warning');
			return;
		}

		// 선택된 파일 유형이 없는 행이 하나라도 있으면 차단
		const fileIdKey = fileType === 'EXCEL' ? 'excelFileId' : 'pdfFileId';
		const hasNoFile = checkedRows.some((row) => !row[fileIdKey]);
		if (hasNoFile) {
			gToast(`${fileType} 파일이 없는 성적서가 포함되어 있습니다.`, 'warning');
			return;
		}

		const ids = checkedRows.map((row) => row.id).join(',');
		gLoadingMessage('다운로드 준비 중...');
		window.location.href = `/api/report/agcyReport/download?ids=${ids}&fileType=${fileType}`;
		setTimeout(() => Swal.close(), 2000);
	}

	// =====================================================================
	// 이벤트 바인딩
	// =====================================================================

	// 행 클릭 → 수정 모달 (체크박스·EXCEL·PDF 컬럼 제외)
	$modal.grid.on('click', async (ev) => {
		if (ev.targetType !== 'cell') return;
		const { columnName, rowKey } = ev;
		if (columnName === '_checked' || columnName === 'excelFileId' || columnName === 'pdfFileId') return;

		const row = $modal.grid.getRow(rowKey);
		if (!row || !row.id) return;

		await gModal(
			'/cali/agcyReportModify',
			{ id: row.id },
			{
				title: '대행성적서 수정',
				size: 'xxl',
				show_close_button: true,
				show_confirm_button: true,
				confirm_btn_label: '저장',
			}
		);
		$modal.grid.getPagination().movePageTo(currentPage);
	});

	$modal
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();
			$modal.grid.getPagination().movePageTo(1);
		})
		.on('change', '.rowLeng', function () {
			const perPage = parseInt($(this).val());
			if (perPage > 0) $modal.grid.setPerPage(perPage);
		})
		.on('click', '.btnMultiUpdate', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (!checkedRows.length) {
				gToast('수정할 성적서를 선택하세요.', 'warning');
				return;
			}
			const reportIds = checkedRows.map((row) => row.id);
			await gModal(
				'/cali/agcyReportMultiUpdate',
				{ reportIds },
				{
					title: '대행성적서 통합수정',
					size: 'lg',
					show_close_button: true,
					show_confirm_button: true,
					confirm_btn_label: '저장',
				}
			);
			$modal.grid.getPagination().movePageTo(currentPage);
		})
		.on('click', '.btnDownloadExcel', function (e) {
			e.preventDefault();
			bulkDownload('EXCEL');
		})
		.on('click', '.btnDownloadPdf', function (e) {
			e.preventDefault();
			bulkDownload('PDF');
		});

	// =====================================================================
	// init_modal: 초기화 (페이지 진입 시 initPage 에서 호출)
	// dataSource가 설정되어 있으므로 그리드 자동 로드됨
	// =====================================================================
	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);
	};

	// =====================================================================
	// 모달 마운트 처리 (F5 표준 블록)
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
