$(function () {
	console.log('++ cali/reportPrint.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	$modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	// 중/소분류 코드 세트
	let smallItemCodeSet = {};
	let middleItemCodeSet = [];

	// 현재 페이지 추적 (모달 닫힌 후 재조회 시 유지)
	let currentPage = 1;

	// =====================================================================
	// 초기 기간 계산: 오늘 기준 직전 1개월 ~ 오늘
	// =====================================================================
	function initDateRange() {
		const now = new Date();
		const start = new Date(now.getFullYear(), now.getMonth() - 1, now.getDate());

		const fmt = (d) =>
			`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

		$('[name=startDate]', $modal).val(fmt(start));
		$('[name=endDate]', $modal).val(fmt(now));
	}

	// =====================================================================
	// 그리드 파일 다운로드 (개별 셀 — EXCEL / PDF)
	// ReportPrintFileRenderer 에서 window.downloadPrintFile 로 참조
	// =====================================================================
	async function downloadPrintFile(reportId, fileType, reportNum) {
		gLoadingMessage('다운로드 중...');
		try {
			const fetchUrl = `/api/file/report/${reportId}/${fileType}`
				+ (reportNum ? `?reportNum=${encodeURIComponent(reportNum)}` : '');
			const res = await fetch(fetchUrl);
			if (!res.ok) throw new Error(`HTTP ${res.status}`);

			const blob = await res.blob();
			const cd = res.headers.get('Content-Disposition') || '';
			let filename = fileType === 'signed_pdf' ? 'signed.pdf' : 'signed.xlsx';
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
			console.error('[reportPrint] 파일 다운로드 오류:', e);
			gToast('파일 다운로드 중 오류가 발생했습니다.', 'error');
		}
	}
	window.downloadPrintFile = downloadPrintFile;

	// =====================================================================
	// 성적서출력 실행 (단건 / 일괄 공통)
	// rows: 그리드 행 객체 배열 (id, reportNum 필드 포함)
	// =====================================================================
	async function doPrint(rows) {
		if (!rows || !rows.length) {
			gToast('출력할 성적서를 선택하세요.', 'warning');
			return;
		}

		// 출력 확인 다이얼로그
		const result = await Swal.fire({
			title: `선택한 ${rows.length}건을 출력하시겠습니까?`,
			html: 'ExcelWorkApp이 실행되며 지정된 프린터로 출력됩니다.',
			icon: 'question',
			showCancelButton: true,
			confirmButtonText: '출력',
			cancelButtonText: '취소',
		});
		if (!result.isConfirmed) return;

		gLoadingMessage('출력 처리 중...');
		try {
			const res = await fetch('/api/excelwork/batches/print', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					reportIds: rows.map((r) => r.id),
					serverUrl: '',
				}),
			});
			if (!res.ok) throw res;

			const data = await res.json();
			if (!data || data.code <= 0) throw new Error(data?.msg ?? '출력 작업 생성 실패');

			swal.close();
			// URI 스킴으로 ExcelWorkApp 실행 (브라우저는 이 탭에 머무름)
			window.location.href = data.data.excelworkUri;

			setTimeout(() => {
				gToast('출력 작업이 시작되었습니다. ExcelWorkApp에서 진행 상황을 확인하세요.', 'success');
			}, 500);
		} catch (e) {
			swal.close();
			console.error('[reportPrint] doPrint 오류:', e);
			await gApiErrorHandler(e);
		}
	}

	// =====================================================================
	// EXCEL 셀 렌더러 — signedXlsxFileId 기반 다운로드 버튼
	// =====================================================================
	class PrintExcelRenderer {
		constructor(props) {
			const { grid, rowKey, columnInfo } = props;
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
			btn.className = 'btn btn-outline-success btn-xs';
			btn.textContent = 'EXCEL';
			btn.onclick = (e) => {
				e.stopPropagation();
				downloadPrintFile(row.id, 'signed_xlsx', row.reportNum);
			};
			this.el.appendChild(btn);
		}
	}

	// =====================================================================
	// PDF 셀 렌더러 — signedPdfFileId 기반 다운로드 버튼
	// =====================================================================
	class PrintPdfRenderer {
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
			btn.className = 'btn btn-outline-danger btn-xs';
			btn.textContent = 'PDF';
			btn.onclick = (e) => {
				e.stopPropagation();
				downloadPrintFile(row.id, 'signed_pdf', row.reportNum);
			};
			this.el.appendChild(btn);
		}
	}

	// =====================================================================
	// 출력 셀 렌더러
	// =====================================================================
	class PrintActionRenderer {
		constructor(props) {
			this.el = document.createElement('div');
			this.el.style.textAlign = 'center';
			this.render(props);
		}
		getElement() { return this.el; }
		render(props) {
			this.el.innerHTML = '';
			const row = props.grid.getRow(props.rowKey);
			const btn = document.createElement('button');
			btn.className = 'btn btn-outline-primary btn-xs';
			btn.textContent = '출력';
			btn.onclick = (e) => {
				e.stopPropagation();
				doPrint([row]);
			};
			this.el.appendChild(btn);
		}
	}

	// =====================================================================
	// init_modal: 중/소분류 코드 비동기 초기화
	// =====================================================================
	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);

		initDateRange();

		try {
			const res = await gAjax('/api/basic/getItemCodeInfos', {}, { type: 'GET' });
			if (res?.code > 0) {
				const itemCodeSet = res.data;
				if (itemCodeSet.middleCodeInfos) {
					middleItemCodeSet = itemCodeSet.middleCodeInfos;
					const $middleCodeSelect = $('.middleCodeSelect', $modal);
					$.each(itemCodeSet.middleCodeInfos, function (index, row) {
						$middleCodeSelect.append(new Option(row.codeNum, row.id));
					});
				}
				if (itemCodeSet.smallCodeInfos) {
					smallItemCodeSet = itemCodeSet.smallCodeInfos;
				}
			} else {
				throw new Error('/api/basic/getItemCodeInfos 호출 실패');
			}
		} catch (xhr) {
			console.error('코드 조회 에러');
			await gApiErrorHandler(xhr);
		}
	};

	// =====================================================================
	// 그리드 데이터 소스
	// =====================================================================
	$modal.dataSource = {
		api: {
			readData: {
				url: '/api/report/reportPrintList',
				method: 'GET',
				serializer: (grid_param) => {
					grid_param.dateType         = $('[name=dateType]', $modal).val() ?? 'approval';
					grid_param.startDate        = $('[name=startDate]', $modal).val() ?? '';
					grid_param.endDate          = $('[name=endDate]', $modal).val() ?? '';
					grid_param.searchType       = $('[name=searchType]', $modal).val() ?? '';
					grid_param.keyword          = $('[name=keyword]', $modal).val() ?? '';
					grid_param.middleItemCodeId = Number($('.middleCodeSelect', $modal).val() ?? 0);
					grid_param.smallItemCodeId  = Number($('.smallCodeSelect', $modal).val() ?? 0);
					return $.param(grid_param);
				},
			},
		},
	};

	// =====================================================================
	// 그리드 정의
	// =====================================================================
	$modal.grid = gGrid('.reportPrintList', {
		scrollX: true,
		frozenCount: 3,   // 소분류/접수일/성적서번호 3열 frozen
		columns: [
			{
				header: '소분류',
				name: 'codeSmallName',
				width: 70,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '접수일',
				name: 'receiptDate',
				width: 85,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '성적서번호',
				name: 'reportNum',
				width: 100,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '신청업체',
				name: 'agentName',
				width: 100,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '성적서발행처',
				name: 'publishName',
				width: 100,
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
				header: '기기번호',
				name: 'itemNum',
				width: 90,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '제작회사',
				name: 'manufacturer',
				width: 90,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '형식',
				name: 'modelType',
				width: 80,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '교정일자',
				name: 'caliDate',
				width: 85,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: 'EXCEL',
				name: 'signedXlsxFileId',
				width: 65,
				align: 'center',
				sortable: false,
				renderer: { type: PrintExcelRenderer },
			},
			{
				header: 'PDF',
				name: 'signedPdfFileId',
				width: 60,
				align: 'center',
				sortable: false,
				renderer: { type: PrintPdfRenderer },
			},
			{
				header: '출력',
				name: 'printAction',
				width: 60,
				align: 'center',
				sortable: false,
				renderer: { type: PrintActionRenderer },
			},
		],
		pageOptions: {
			useClient: false,
			perPage: 50,
		},
		rowHeaders: ['checkbox'],
		minBodyHeight: 600,
		bodyHeight: 600,
		rowHeight: 'auto',
		data: $modal.dataSource,
	});

	$modal.grid.on('response', function () {
		requestAnimationFrame(() => {
			currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
		});
	});

	// =====================================================================
	// 행 클릭 → 성적서수정 모달 (읽기전용 — 저장 버튼 없음)
	// 체크박스·파일·출력 컬럼 클릭은 제외
	// =====================================================================
	$modal.grid.on('click', async function (ev) {
		const { columnName, rowKey } = ev;
		if (columnName === '_checked'
			|| columnName === 'signedXlsxFileId'
			|| columnName === 'signedPdfFileId'
			|| columnName === 'printAction') return;

		const row = $modal.grid.getRow(rowKey);
		if (!row || !row.id) return;

		const reportNum = row.reportNum ?? '';
		// 기술책임자 결재 완료된 성적서 — 항상 읽기전용(저장 버튼 숨김)
		await gModal(
			'/cali/reportModify',
			{ id: row.id },
			{
				title: `성적서 수정 [성적서번호 - ${reportNum}]`,
				size: 'xxxl',
				show_close_button: true,
				show_confirm_button: false,
			}
		);

		$modal.grid.getPagination().movePageTo(currentPage);
	});

	// =====================================================================
	// 검색 폼 이벤트
	// =====================================================================
	$modal
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();
			$modal.grid.getPagination().movePageTo(1);
		})
		.on('change', '.rowLeng', function () {
			const rowLeng = $(this).val();
			if (rowLeng > 0) $modal.grid.setPerPage(rowLeng);
		})
		.on('change', '.middleCodeSelect', function () {
			const middleCodeId = $(this).val();
			const $smallCodeSelect = $('.smallCodeSelect', $modal);
			$smallCodeSelect.find('option').remove();
			$smallCodeSelect.append(new Option('소분류전체', ''));
			if (middleCodeId && smallItemCodeSet[middleCodeId]?.length > 0) {
				smallItemCodeSet[middleCodeId].forEach((row) => {
					$smallCodeSelect.append(new Option(row.codeNum, row.id));
				});
			}
		});

	// =====================================================================
	// 성적서출력 버튼 (체크박스 선택 건 일괄 출력)
	// =====================================================================
	$modal.on('click', '.btnPrint', function () {
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows.length) {
			gToast('출력할 성적서를 선택하세요.', 'warning');
			return;
		}
		doPrint(checkedRows);
	});

	// =====================================================================
	// 페이지 마운트 처리 (common.js 규약)
	// =====================================================================
	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		$modal_root.on('modal_ready', function (e, p) {
			$modal.init_modal(p);
			if (typeof $modal.grid == 'object') {
				$modal.grid.refreshLayout();
			}
		});
	}

	if (typeof window.modal_deferred == 'object') {
		window.modal_deferred.resolve('script end');
	} else {
		if (!$modal_root.length) {
			initPage($modal);
		}
	}
});
