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
	//   2. SweetAlert2 + jQuery UI datepicker 로 결재일자 선택
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

		// 결재일자 선택 SweetAlert2 (기존 의존성 jQuery UI datepicker 사용)
		const { isConfirmed, value: approvalDate } = await Swal.fire({
			title: '결재일자 선택',
			html: '<input type="text" id="swalApprovalDate" class="swal2-input"'
				+ ' placeholder="날짜를 선택하세요" readonly style="cursor:pointer;">',
			confirmButtonText: '결재',
			cancelButtonText: '취소',
			showCancelButton: true,
			focusConfirm: false,
			didOpen: () => {
				// z-index 조정: jQuery UI datepicker 달력이 SweetAlert2 위에 표시되도록
				$('#swalApprovalDate').datepicker({
					dateFormat: 'yy-mm-dd',
					beforeShow: (input, inst) => {
						setTimeout(() => { inst.dpDiv.css('z-index', 99999); }, 0);
					},
				});
			},
			preConfirm: () => {
				const date = $('#swalApprovalDate').val().trim();
				if (!date) {
					Swal.showValidationMessage('결재일자를 선택해주세요.');
					return false;
				}
				return date;
			},
			willClose: () => {
				try { $('#swalApprovalDate').datepicker('destroy'); } catch (e) { /* ignore */ }
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
				// 추후 기능 정의 예정, 현재 빈값 표시
				header: '진행',
				name: 'progress',
				width: 60,
				align: 'center',
				formatter: () => '',
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
	// =====================================================================
	$('.managerApprovalList').on('click', '.btn-manager-reject', async function (e) {
		e.stopPropagation();
		const reportId = $(this).data('id');
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
	});

	// =====================================================================
	// '결재자' 열 — 확인(결재) 버튼 (단건)
	// =====================================================================
	$('.managerApprovalList').on('click', '.btn-manager-approve', async function (e) {
		e.stopPropagation();
		const reportId = $(this).data('id');
		const row = $modal.grid.getData().find(r => r.id === reportId);
		if (!row) return;
		await doApprove([row]);
		$modal.grid.getPagination().movePageTo(currentPage);
	});

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