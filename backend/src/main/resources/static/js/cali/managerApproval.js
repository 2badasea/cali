$(function () {
	console.log('++ cali/managerApproval.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	$modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	// 중/소분류 코드 세트
	let smallItemCodeSet = {};
	let middleItemCodeSet = [];

	// 현재 페이지 추적 (버튼 클릭 후 재조회 시 유지)
	let currentPage = 1;

	// =====================================================================
	// 초기 기간 계산: 2개월 전 1일 ~ 현재 월 말일
	// =====================================================================
	function initDateRange() {
		const now = new Date();
		const start = new Date(now.getFullYear(), now.getMonth() - 2, 1);
		const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);

		const fmt = (d) =>
			`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

		$('[name=startDate]', $modal).val(fmt(start));
		$('[name=endDate]', $modal).val(fmt(end));
	}

	// =====================================================================
	// 그리드 파일 다운로드 (개별 셀)
	// ReportFileDownloadRenderer 에서 window.downloadReportFile 로 참조
	// =====================================================================
	async function downloadReportFile(reportId, fileType, reportNum) {
		gLoadingMessage('다운로드 중...');
		try {
			const fetchUrl = `/api/file/report/${reportId}/${fileType}`
				+ (reportNum ? `?reportNum=${encodeURIComponent(reportNum)}` : '');
			const res = await fetch(fetchUrl);
			if (!res.ok) throw new Error(`HTTP ${res.status}`);

			const blob = await res.blob();
			const cd = res.headers.get('Content-Disposition') || '';
			let filename = fileType === 'signed_pdf' ? 'signed.pdf' : (fileType === 'signed_xlsx' ? 'signed.xlsx' : 'origin.xlsx');
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
			console.error('[managerApproval] 파일 다운로드 오류:', e);
			gToast('파일 다운로드 중 오류가 발생했습니다.', 'error');
		}
	}
	window.downloadReportFile = downloadReportFile;

	// =====================================================================
	// 결재 공통 처리 함수 — 단건(확인 버튼) / 다건(다중결재 버튼) 공유
	//
	// 처리 흐름:
	//   1. 결재 불가 케이스 사전 검증 (approval_status 기준)
	//   2. SweetAlert2 + 네이티브 input[type="date"] 로 결재일자 선택
	//   3. POST /api/excelwork/batches/manager-approval 호출
	//   4. excelworkUri 로 ExcelWorkApp 기동, 그리드 갱신
	// =====================================================================
	async function doApprove(rows) {
		if (!rows.length) {
			gToast('결재할 성적서를 선택하세요.', 'warning');
			return;
		}

		// approval_status 검증: READY/PROGRESS/SUCCESS → 결재 불가
		const blocked = rows.filter(r => ['READY', 'PROGRESS', 'SUCCESS'].includes(r.approvalStatus));
		if (blocked.length > 0) {
			const nums = blocked.map(r => r.reportNum).join(', ');
			await gMessage('결재 불가',
				`아래 성적서는 결재가 진행 중이거나 완료되었습니다:<br>${nums}`,
				'warning', 'alert');
			return;
		}

		// 결재일자 선택 SweetAlert2
		// jQuery UI datepicker는 SweetAlert2 backdrop이 클릭을 차단하여 달력이 보이지 않는 버그가 있음.
		// 네이티브 input[type="date"]는 브라우저 자체 UI를 사용하므로 SweetAlert2 DOM에 무관하게 정상 동작.
		// 반환 값 형식이 'yyyy-MM-dd' 로 백엔드 파싱 포맷(LocalDate.parse)과 바로 호환됨.
		const { isConfirmed, value: approvalDate } = await Swal.fire({
			title: '결재일자 선택',
			html: '<input type="date" id="swalApprovalDate" class="swal2-input" style="width: auto;">',
			confirmButtonText: '결재',
			cancelButtonText: '취소',
			showCancelButton: true,
			focusConfirm: false,
			preConfirm: () => {
				const date = document.getElementById('swalApprovalDate').value;
				if (!date) {
					Swal.showValidationMessage('결재일자를 선택해주세요.');
					return false;
				}
				return date;
			},
		});

		if (!isConfirmed || !approvalDate) return;

		const reportIds = rows.map(r => r.id);

		try {
			gLoadingMessage('결재처리중입니다...');
			const res = await fetch('/api/excelwork/batches/manager-approval', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds, approvalDate }),
			});
			swal.close();
			if (!res.ok) throw res;
			const data = await res.json();
			if (data?.code > 0) {
				// ExcelWorkApp 기동 (URI 스킴 실행)
				const uri = data.data?.excelworkUri;
				if (uri) window.location.href = uri;
				$modal.grid.getPagination().movePageTo(currentPage);
			} else {
				await gMessage('오류', data.msg ?? '처리 중 오류가 발생했습니다.', 'error', 'alert');
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
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
				url: '/api/admin/managerApproval/list',
				method: 'GET',
				serializer: (grid_param) => {
					grid_param.dateType      = $('[name=dateType]', $modal).val() ?? 'cali';
					grid_param.startDate     = $('[name=startDate]', $modal).val() ?? '';
					grid_param.endDate       = $('[name=endDate]', $modal).val() ?? '';
					grid_param.searchType    = $('[name=searchType]', $modal).val() ?? '';
					grid_param.keyword       = $('[name=keyword]', $modal).val() ?? '';
					grid_param.middleItemCodeId = Number($('.middleCodeSelect', $modal).val() ?? 0);
					grid_param.smallItemCodeId  = Number($('.smallCodeSelect', $modal).val() ?? 0);
					grid_param.approvalStatus = $('[name=approvalStatus]', $modal).val() ?? '';
					return $.param(grid_param);
				},
			},
		},
	};

	// =====================================================================
	// 그리드 정의
	// =====================================================================
	$modal.grid = gGrid('.managerApprovalList', {
		scrollX: true,
		frozenCount: 4,   // 소분류/접수일/완료예정일/성적서번호 4열 frozen (rowHeader checkbox는 frozenCount 미포함)
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
				header: '완료예정일',
				name: 'expectCompleteDate',
				width: 90,
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
				// approvalStatus 기반 진행 상태 텍스트 표시
				// '결재자' 컬럼 렌더러는 IDLE/READY/PROGRESS를 모두 동일하게 버튼으로 표시하므로
				// 이 컬럼에서 텍스트+색상으로 세분화하여 ExcelWork 처리 중 여부를 구분
				header: '진행',
				name: 'progress',
				width: 65,
				align: 'center',
				formatter: function (data) {
					const configs = {
						IDLE:     { label: '대기',   color: '#6c757d' },
						READY:    { label: '준비중', color: '#0d6efd' },
						PROGRESS: { label: '처리중', color: '#fd7e14' },
						SUCCESS:  { label: '완료',   color: '#198754' },
					};
					const cfg = configs[data.row.approvalStatus];
					if (!cfg) return '';
					return `<span style="color:${cfg.color}; font-weight:600;">${cfg.label}</span>`;
				},
			},
			{
				header: '원본',
				name: 'originFileId',
				width: 60,
				align: 'center',
				sortable: false,
				renderer: { type: ReportFileDownloadRenderer },
			},
			{
				header: 'EXCEL',
				name: 'signedXlsxFileId',
				width: 60,
				align: 'center',
				sortable: false,
				renderer: { type: ReportFileDownloadRenderer },
			},
			{
				header: 'PDF',
				name: 'signedPdfFileId',
				width: 60,
				align: 'center',
				sortable: false,
				renderer: { type: ReportFileDownloadRenderer },
			},
			{
				header: '실무자',
				name: 'workMemberName',
				width: 70,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '기술책임자',
				name: 'approvalMemberName',
				width: 80,
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '발행타입',
				name: 'reportLang',
				width: 65,
				align: 'center',
				formatter: function (data) {
					const map = { KR: '국문', EN: '영문', BOTH: '국영문' };
					return map[data.value] ?? data.value ?? '';
				},
			},
			{
				// ManagerApprovalCellRenderer: 미결재=반려+확인 버튼, 결재완료=이름+일시
				header: '결재자',
				name: 'approvalStatus',
				width: 130,
				align: 'center',
				sortable: false,
				renderer: { type: ManagerApprovalCellRenderer },
			},
			{
				// ManagerCancelCellRenderer: approval_status=SUCCESS인 행에만 취소 버튼
				header: '취소',
				name: 'cancel',
				width: 60,
				align: 'center',
				sortable: false,
				renderer: { type: ManagerCancelCellRenderer },
			},
		],
		pageOptions: {
			useClient: false,
			perPage: 25,
		},
		rowHeaders: ['checkbox'],
		minBodyHeight: 600,
		bodyHeight: 600,
		rowHeight: 'auto',
		data: $modal.dataSource,
	});

	// =====================================================================
	// 행 배경색: approvalStatus=SUCCESS → row-ma-approved
	// =====================================================================
	function applyRowClasses() {
		const allRows = $modal.grid.getData();
		allRows.forEach(function (row) {
			$modal.grid.removeRowClassName(row.rowKey, 'row-ma-approved');
			if (row.approvalStatus === 'SUCCESS') {
				$modal.grid.addRowClassName(row.rowKey, 'row-ma-approved');
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
	// 그리드 행 클릭 → 성적서수정(reportModify) 모달 호출
	// 체크박스·파일다운로드·결재자·취소 컬럼 클릭은 제외
	// 결재 완료(approvalStatus=SUCCESS) 행은 저장 버튼 비활성화 (읽기 전용 뷰)
	// =====================================================================
	$modal.grid.on('click', async function (ev) {
		const { columnName, rowKey } = ev;
		if (columnName === '_checked'
			|| columnName === 'originFileId'
			|| columnName === 'signedXlsxFileId'
			|| columnName === 'signedPdfFileId'
			|| columnName === 'approvalStatus'
			|| columnName === 'cancel'
			|| columnName === 'progress') return;

		const row = $modal.grid.getRow(rowKey);
		if (!row || !row.id) return;

		const reportNum = row.reportNum ?? '';
		// 결재 완료 상태이면 저장 버튼 비활성화 (읽기 전용 뷰)
		const isModifiable = row.approvalStatus !== 'SUCCESS';
		await gModal(
			'/cali/reportModify',
			{ id: row.id },
			{
				title: `성적서 수정 [성적서번호 - ${reportNum}]`,
				size: 'xxxl',
				show_close_button: true,
				show_confirm_button: isModifiable,
				confirm_button_text: '저장',
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
	// '결재자' 열 — 반려 버튼 (단건)
	// gridClass.js ManagerApprovalCellRenderer에서 직접 호출
	// (TUI Grid 셀 내부 클릭은 jQuery 위임이 신뢰할 수 없으므로 window 함수로 노출)
	// =====================================================================
	window.onManagerReject = async function (reportId) {
		const row = $modal.grid.getData().find(r => r.id === reportId);
		const reportNum = row?.reportNum ?? '';

		await gModal(
			'/cali/reportReject',
			{ reportIds: [reportId], title: `${reportNum} 반려` },
			{
				title: `${reportNum} 반려`,
				size: 'sm',
				show_close_button: true,
				show_confirm_button: true,
				confirm_button_text: '반려',
			}
		);
		$modal.grid.getPagination().movePageTo(currentPage);
	};

	// =====================================================================
	// '결재자' 열 — 확인(결재) 버튼 (단건)
	// gridClass.js ManagerApprovalCellRenderer에서 직접 호출
	// =====================================================================
	window.onManagerApprove = async function (reportId) {
		const row = $modal.grid.getData().find(r => r.id === reportId);
		if (!row) return;
		await doApprove([row]);
		$modal.grid.getPagination().movePageTo(currentPage);
	};

	// =====================================================================
	// '취소' 열 — 취소 버튼 (단건)
	// =====================================================================
	$('.managerApprovalList').on('click', '.btn-manager-cancel', async function (e) {
		e.stopPropagation();
		const reportId = $(this).data('id');
		const confirmResult = await gMessage('결재 취소', '결재를 취소하시겠습니까?', 'question', 'confirm');
		if (!confirmResult.isConfirmed) return;

		try {
			gLoadingMessage('처리 중...');
			const res = await fetch('/api/admin/managerApproval/cancel', {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds: [reportId] }),
			});
			swal.close();
			if (!res.ok) throw res;
			const data = await res.json();
			if (data?.code > 0) {
				gToast(data.msg ?? '취소 처리가 완료되었습니다.', 'success');
				$modal.grid.getPagination().movePageTo(currentPage);
			} else {
				await gMessage('오류', data.msg ?? '처리 중 오류가 발생했습니다.', 'error', 'alert');
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
		}
	});

	// =====================================================================
	// 반려 버튼 (체크박스 다건)
	// =====================================================================
	$modal.on('click', '.btnReject', async function () {
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows.length) {
			gToast('반려할 성적서를 선택하세요.', 'warning');
			return;
		}

		// 반려 불가 케이스 사전 안내 (approval_status IN READY/PROGRESS/SUCCESS)
		const blocked = checkedRows.filter(r =>
			['READY', 'PROGRESS', 'SUCCESS'].includes(r.approvalStatus)
		);
		if (blocked.length > 0) {
			const blockedNums = blocked.map(r => r.reportNum).join(', ');
			await gMessage('반려 불가',
				`아래 성적서는 결재상태(READY/PROGRESS/SUCCESS)로 인해 반려할 수 없습니다:<br>${blockedNums}`,
				'warning', 'alert');
			return;
		}

		const reportIds = checkedRows.map(r => r.id);
		const firstNum  = checkedRows[0].reportNum ?? '';
		const titleText = checkedRows.length === 1
			? `${firstNum} 반려`
			: `${firstNum} 외 ${checkedRows.length - 1}건 반려`;

		await gModal(
			'/cali/reportReject',
			{ reportIds, title: titleText },
			{
				title: titleText,
				size: 'sm',
				show_close_button: true,
				show_confirm_button: true,
				confirm_button_text: '반려',
			}
		);
		$modal.grid.getPagination().movePageTo(currentPage);
	});

	// =====================================================================
	// 다중결재 버튼 (체크박스 다건)
	// =====================================================================
	$modal.on('click', '.btnMultiApprove', async function () {
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows.length) {
			gToast('결재할 성적서를 선택하세요.', 'warning');
			return;
		}
		await doApprove(checkedRows);
		$modal.grid.getPagination().movePageTo(currentPage);
	});

	// =====================================================================
	// 일괄취소 버튼 (체크박스 다건, approval_status=SUCCESS 필터)
	// =====================================================================
	$modal.on('click', '.btnBulkCancel', async function () {
		const checkedRows = $modal.grid.getCheckedRows();
		const targets = checkedRows.filter(r => r.approvalStatus === 'SUCCESS');
		if (!targets.length) {
			gToast('취소 가능한 성적서(결재완료 상태)를 선택하세요.', 'warning');
			return;
		}

		const confirmResult = await gMessage(
			'일괄취소',
			`${targets.length}건의 결재를 취소하시겠습니까?`,
			'question', 'confirm'
		);
		if (!confirmResult.isConfirmed) return;

		try {
			gLoadingMessage('처리 중...');
			const reportIds = targets.map(r => r.id);
			const res = await fetch('/api/admin/managerApproval/cancel', {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds }),
			});
			swal.close();
			if (!res.ok) throw res;
			const data = await res.json();
			if (data?.code > 0) {
				gToast(data.msg ?? '취소 처리가 완료되었습니다.', 'success');
				$modal.grid.getPagination().movePageTo(currentPage);
			} else {
				await gMessage('오류', data.msg ?? '처리 중 오류가 발생했습니다.', 'error', 'alert');
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
		}
	});

	// =====================================================================
	// 다중업로드 버튼 — reportUpload 모달 (type='manager')
	// =====================================================================
	$modal.on('click', '.btnMultiUpload', async function () {
		await gModal(
			'/cali/reportUpload',
			{ type: 'manager' },
			{
				title: '기술책임자용 성적서 업로드',
				size: 'md',
				show_close_button: true,
				show_confirm_button: false,
			}
		);
		$modal.grid.getPagination().movePageTo(currentPage);
	});

	// =====================================================================
	// EXCEL / PDF 일괄 다운로드 버튼 (ZIP 스트리밍)
	// =====================================================================
	async function bulkDownload(fileType) {
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows.length) {
			gToast('다운로드할 성적서를 선택하세요.', 'warning');
			return;
		}
		const ids = checkedRows.map(r => r.id).join(',');
		gLoadingMessage(`${checkedRows.length}개 파일 ZIP 준비 중...`);
		window.location.href = `/api/admin/managerApproval/download?ids=${ids}&fileType=${fileType}`;
		setTimeout(() => swal.close(), 2000);
	}

	// =====================================================================
	// 비정상종료복구 버튼 — 기술책임자결재 비정상종료 스마트 복구
	// approvalStatus=READY/PROGRESS 인 항목 대상, signed 파일 존재 여부 기준 복구
	// =====================================================================
	$modal.on('click', '.btnRecoverApproval', async function () {
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows || checkedRows.length === 0) {
			gToast('리스트에서 항목을 선택해 주세요.', 'warning');
			return;
		}

		// 복구 대상: approvalStatus = READY 또는 PROGRESS (SUCCESS는 이미 완료)
		const targetRows = checkedRows.filter(r =>
			r.approvalStatus === 'READY' || r.approvalStatus === 'PROGRESS'
		);
		if (targetRows.length === 0) {
			gToast('복구 대상 항목이 없습니다. (결재준비중/결재진행중 상태만 복구 가능)', 'warning');
			return;
		}

		const reportIds = targetRows.map(r => r.id);

		try {
			// ── Step 1. 스토리지 파일 존재 여부 미리 확인 ─────────────────────
			gLoadingMessage('스토리지 파일 확인 중...');

			const params = new URLSearchParams();
			reportIds.forEach(id => params.append('reportIds', id));
			const previewRes = await fetch(`/api/excelwork/manager-recover-preview?${params.toString()}`);
			swal.close();
			if (!previewRes.ok) throw previewRes;
			const previewData = await previewRes.json();
			if (!previewData || previewData.code <= 0) {
				await gMessage('오류', previewData?.msg ?? '미리보기 조회 중 오류가 발생했습니다.', 'error', 'alert');
				return;
			}

			const { successItems, idleItems } = previewData.data;

			if (successItems.length === 0 && idleItems.length === 0) {
				await gMessage('알림', '복구할 항목이 없습니다.<br><small class="text-muted">선택된 성적서가 모두 이미 완료 상태입니다.</small>', 'info', 'alert');
				return;
			}

			// ── Step 2. 예상 결과 미리보기 확인 다이얼로그 ────────────────────
			let htmlContent = '<div class="text-start">';

			if (successItems.length > 0) {
				const nums = successItems.map(i => {
					const label = i.reportNum ?? `#${i.reportId}`;
					const elapsed = i.batchStartedMinutesAgo != null
						? ` <span class="text-muted small">(${i.batchStartedMinutesAgo}분 전 시작)</span>`
						: '';
					return `<li>${label}${elapsed}</li>`;
				}).join('');
				htmlContent += `
					<p class="mb-1 fw-bold text-success">✔ 완료 처리 (파일 확인됨) — ${successItems.length}건</p>
					<ul class="mb-3 small">${nums}</ul>`;
			}

			if (idleItems.length > 0) {
				const nums = idleItems.map(i => {
					const label = i.reportNum ?? `#${i.reportId}`;
					const elapsed = i.batchStartedMinutesAgo != null
						? ` <span class="text-muted small">(${i.batchStartedMinutesAgo}분 전 시작)</span>`
						: '';
					return `<li>${label}${elapsed}</li>`;
				}).join('');
				htmlContent += `
					<p class="mb-1 fw-bold text-warning">↩ 초기화 (파일 없음) — ${idleItems.length}건</p>
					<ul class="mb-3 small">${nums}</ul>`;
			}

			htmlContent += `
				<div class="alert alert-warning small mb-0 p-2">
					⚠ ExcelWork 앱이 현재 실행 중이라면 진행 중인 작업이 강제로 중단됩니다.
				</div>
			</div>`;

			const confirmResult = await gMessage(
				'비정상종료복구',
				htmlContent,
				'question', 'confirm',
				{ confirmButtonText: '복구 실행', cancelButtonText: '취소' }
			);
			if (!confirmResult.isConfirmed) return;

			// ── Step 3. 복구 실행 ──────────────────────────────────────────────
			gLoadingMessage('복구 처리 중...');
			const recoverRes = await fetch('/api/excelwork/manager-smart-recover', {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds }),
			});
			swal.close();
			if (!recoverRes.ok) throw recoverRes;
			const recoverData = await recoverRes.json();

			if (recoverData?.code > 0) {
				const { successCount, idleCount } = recoverData.data;
				let resultHtml = '';
				if (successCount > 0) resultHtml += `<p class="mb-1 text-success">✔ 완료 처리: ${successCount}건</p>`;
				if (idleCount > 0)    resultHtml += `<p class="mb-0 text-secondary">↩ 초기화: ${idleCount}건</p>`;
				await gMessage('복구 완료', resultHtml || '복구가 완료되었습니다.', 'success', 'alert');
				$modal.grid.getPagination().movePageTo(currentPage);
			} else {
				await gMessage('오류', recoverData?.msg ?? '복구 중 오류가 발생했습니다.', 'error', 'alert');
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
		}
	});

	$modal.on('click', '.btnDownloadExcel', () => bulkDownload('EXCEL'));
	$modal.on('click', '.btnDownloadPdf',   () => bulkDownload('PDF'));

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