$(function () {
	console.log('++ cali/workApproval.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	$modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	// 중/소분류 코드 세트 (init_modal에서 채워짐)
	let smallItemCodeSet = {};
	let middleItemCodeSet = [];

	// =====================================================================
	// 진행상태 label 변환
	// =====================================================================
	function reportStatusLabel(value) {
		const map = {
			NORMAL: '기본',
			REPAIR: '수리',
			IMPOSSIBLE: '불가',
			REJECTED: '반려',
			RESUBMITTED: '재업로드',
		};
		return map[value] ?? value ?? '';
	}

	// =====================================================================
	// 성적서 파일 다운로드 (원본/EXCEL/PDF)
	// fetch + blob 방식: 다운로드 응답을 받은 뒤 a 태그 클릭으로 저장 유도
	// gLoadingMessage 로 연속클릭 방지 → 다운로드 준비 완료 후 닫기
	// gridClass.js의 ReportFileDownloadRenderer에서 window.downloadReportFile 로 참조됨
	// =====================================================================
	async function downloadReportFile(reportId, fileType, reportNum) {
		gLoadingMessage('다운로드 중...');
		try {
			// reportNum 이 있으면 서버에서 "{reportNum}.xlsx/.pdf" 형태의 파일명으로 내려줌
			const fetchUrl = `/api/file/report/${reportId}/${fileType}`
				+ (reportNum ? `?reportNum=${encodeURIComponent(reportNum)}` : '');
			const res = await fetch(fetchUrl);
			if (!res.ok) throw new Error(`HTTP ${res.status}`);

			const blob = await res.blob();

			// Content-Disposition 에서 파일명 추출
			const cd = res.headers.get('Content-Disposition') || '';
			let filename = fileType === 'signed_pdf' ? 'signed.pdf' : (fileType === 'signed_xlsx' ? 'signed.xlsx' : 'origin.xlsx');
			// RFC 5987 (filename*=UTF-8'') 우선, 없으면 filename= 사용
			const mStar = cd.match(/filename\*=UTF-8''([^;\n]+)/i);
			const mPlain = cd.match(/filename="?([^";\n]+)"?/i);
			if (mStar)  filename = decodeURIComponent(mStar[1].trim());
			else if (mPlain) filename = mPlain[1].trim();

			const url = URL.createObjectURL(blob);
			const a   = document.createElement('a');
			a.href     = url;
			a.download = filename;
			document.body.appendChild(a);
			a.click();
			document.body.removeChild(a);
			URL.revokeObjectURL(url);

			swal.close();
		} catch (e) {
			swal.close();
			console.error('[workApproval] 파일 다운로드 오류:', e);
			gToast('파일 다운로드 중 오류가 발생했습니다.', 'error');
		}
	}

	// =====================================================================
	// handleSingleFileUpload: 그리드 단건 업로드/결재
	// 파일 선택 → 확장자·파일명 검증 → 결재/업로드/취소 선택 → 처리
	// =====================================================================
	async function handleSingleFileUpload(reportId, reportNum, file, inputEl) {
		// 확장자 검증
		const ext = file.name.split('.').pop().toLowerCase();
		if (!['xlsx', 'xls'].includes(ext)) {
			gToast('엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.', 'warning');
			inputEl.value = '';
			return;
		}

		// 파일명(확장자 제거) = 성적서번호 일치 검증
		const fileNameBase = file.name.replace(/\.[^/.]+$/, '');
		if (fileNameBase !== reportNum) {
			gToast(`파일명(${fileNameBase})이 성적서번호(${reportNum})와 일치하지 않습니다.`, 'warning');
			inputEl.value = '';
			return;
		}

		// 결재 / 업로드 / 취소 선택
		const result = await Swal.fire({
			title: '성적서 업로드',
			html: `<strong>${reportNum}</strong>에 대해 진행할 작업을 선택해주세요.`,
			icon: 'question',
			showDenyButton: true,
			showCancelButton: true,
			confirmButtonText: '결재',
			denyButtonText: '업로드',
			cancelButtonText: '취소',
		});

		if (result.isDismissed) {
			// 취소
			inputEl.value = '';
			return;
		}

		const isApproval = result.isConfirmed;

		// 원본 파일 업로드 (CALI 서버)
		try {
			gLoadingMessage(isApproval ? '파일 업로드 중...' : '원본 파일 교체 중...');
			const formData = new FormData();
			formData.append('files', file);
			formData.append('reportIds', reportId);

			const uploadRes = await fetch('/api/report/upload/origin', {
				method: 'POST',
				body: formData,
			});
			swal.close();
			if (!uploadRes.ok) throw uploadRes;
			const uploadData = await uploadRes.json();
			if (!uploadData || uploadData.code <= 0) {
				await gMessage('오류', uploadData.msg ?? '파일 업로드 중 오류가 발생했습니다.', 'error', 'alert');
				inputEl.value = '';
				return;
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			inputEl.value = '';
			return;
		}

		inputEl.value = '';

		if (!isApproval) {
			// 업로드만
			await gMessage('업로드 완료', `${reportNum} 원본 파일이 교체되었습니다.`, 'success', 'alert');
			const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
			$modal.grid.getPagination().movePageTo(currentPage);
			return;
		}

		// 결재: 배치 생성 + 폴링
		await doWorkApproval([reportId], reportNum, true);
	}

	// =====================================================================
	// doWorkApproval: WORK_APPROVAL 배치 생성 → ExcelWorkApp 실행 → 백그라운드 폴링
	// reportIds         : 결재 대상 성적서 id 배열 (단건 또는 다중)
	// representReportNum: 타이틀용 성적서번호 (다중 시 "외 N건" 형태로 표시)
	// skipConfirm       : true이면 결재 확인 다이얼로그 생략 (다중결재·단건 업로드 결재 시 사용)
	//
	// ExcelWork 방식: 서명 삽입 + PDF 변환은 ExcelWorkApp(로컬 앱)이 처리.
	//   - 배치 생성 후 excelwork:// URI를 실행 → ExcelWorkApp 기동
	//   - 진행상황은 ExcelWorkApp 작업 목록에서 확인
	//   - 브라우저는 백그라운드 폴링으로 완료를 감지하여 그리드만 갱신
	// =====================================================================
	async function doWorkApproval(reportIds, representReportNum, skipConfirm = false) {
		if (!reportIds || reportIds.length === 0) return;

		const titleSuffix = reportIds.length === 1
			? `[${representReportNum}]`
			: `[${representReportNum} 외 ${reportIds.length - 1}건]`;

		// ── Step 1. 결재 확인 (skipConfirm 시 생략) ──────────────────────
		if (!skipConfirm) {
			const confirmResult = await gMessage(
				'실무자결재',
				`${titleSuffix}<br>선택한 성적서를 실무자결재 처리하시겠습니까?`,
				'question',
				'confirm',
				{ confirmButtonText: '결재' }
			);
			if (!confirmResult.isConfirmed) return;
		}

		// ── Step 2. 배치 생성 API 호출 (ExcelWork 방식) ───────────────────
		let batchId, excelworkUri;
		try {
			gLoadingMessage('실무자결재 작업을 준비합니다.');
			const res = await fetch('/api/excelwork/batches/work-approval', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds }),
			});
			swal.close();
			if (!res.ok) throw res;
			const resData = await res.json();
			if (resData?.code > 0) {
				batchId      = resData.data.batchId;
				excelworkUri = resData.data.excelworkUri;
			} else {
				await gMessage('오류', resData.msg ?? '배치 생성 중 오류가 발생했습니다.', 'error', 'alert');
				return;
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			return;
		}

		// ── Step 3. ExcelWorkApp 실행 ─────────────────────────────────────
		// excelwork:// URI로 로컬 ExcelWorkApp을 기동.
		// ExcelWorkApp이 서명 삽입 + PDF 변환 + 업로드를 처리하며 앱 내 작업 목록에 진행상황 표시.
		window.location.href = excelworkUri;

		// ── Step 4. 백그라운드 폴링 — 완료 시 그리드 갱신 ─────────────────
		// ExcelWorkApp이 처리하는 동안 브라우저에서 조용히 폴링하여 완료를 감지한다.
		// 진행상황 표시는 ExcelWorkApp 작업 목록에서 확인.
		const MAX_POLL_COUNT  = 120; // 최대 10분 (5초 × 120회)
		const POLL_INTERVAL   = 5000;

		for (let pollCount = 0; pollCount < MAX_POLL_COUNT; pollCount++) {
			await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL));

			try {
				const pollRes = await fetch(`/api/report/jobs/batches/${batchId}`);
				if (!pollRes.ok) continue;
				const pollData = await pollRes.json();
				if (!pollData || pollData.code <= 0) continue;

				const batch = pollData.data;

				if (['SUCCESS', 'FAIL', 'CANCELED'].includes(batch.status)) {
					if (batch.status === 'SUCCESS') {
						const icon = batch.failCount > 0 ? 'warning' : 'success';
						await gMessage(
							'실무자결재 완료',
							`성공 ${batch.successCount}건 / 실패 ${batch.failCount}건`,
							icon, 'alert'
						);
					} else if (batch.status === 'FAIL') {
						await gMessage('실무자결재 실패', `${batch.failCount}건 처리 실패`, 'error', 'alert');
					} else {
						await gMessage('작업 취소', '작업이 취소되었습니다.', 'info', 'alert');
					}

					// 그리드 재조회
					const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
					$modal.grid.getPagination().movePageTo(currentPage);
					break;
				}

			} catch (pollErr) {
				console.warn('[workApproval] 폴링 오류:', pollErr);
			}
		}
	}

	// =====================================================================
	// gridClass.js의 renderer class(ReportFileDownloadRenderer, WorkApprovalCellRenderer)에서
	// 참조할 수 있도록 window에 노출 — 클로저 내부 함수이므로 명시적 노출 필요
	// =====================================================================
	window.downloadReportFile     = downloadReportFile;
	window.handleSingleFileUpload = handleSingleFileUpload;
	window.doWorkApproval         = doWorkApproval;

	// =====================================================================
	// init_modal: 중/소분류 코드 비동기 초기화 (initPage 또는 modal_ready에서 호출됨)
	// =====================================================================
	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);

		try {
			const resGetItemCodeSet = await gAjax(
				'/api/basic/getItemCodeInfos',
				{},
				{
					type: 'GET',
				}
			);

			if (resGetItemCodeSet?.code > 0) {
				const itemCodeSet = resGetItemCodeSet.data;
				if (itemCodeSet.middleCodeInfos) {
					middleItemCodeSet = itemCodeSet.middleCodeInfos;
					const $middleCodeSelect = $('.middleCodeSelect', $modal);
					$.each(itemCodeSet.middleCodeInfos, function (index, row) {
						const option = new Option(row.codeNum, row.id);
						$middleCodeSelect.append(option);
					});
				}
				if (itemCodeSet.smallCodeInfos) {
					smallItemCodeSet = itemCodeSet.smallCodeInfos;
				}
			} else {
				console.log('호출실패');
				throw new Error('/api/basic/getItemCodeInfos 호출 실패');
			}
		} catch (xhr) {
			console.error('통신에러');
			await gApiErrorHandler(xhr);
		}
	};

	// =====================================================================
	// 그리드 데이터 소스 정의
	// =====================================================================
	$modal.dataSource = {
		api: {
			readData: {
				url: '/api/report/workApprovalList',
				method: 'GET',
				serializer: (grid_param) => {
					grid_param.reportStatus    = $('form.searchForm .reportStatusFilter', $modal).val() ?? '';
					grid_param.workStatus      = $('form.searchForm .workStatusFilter', $modal).val() ?? '';
					grid_param.writeStatus     = $('form.searchForm .writeStatusFilter', $modal).val() ?? '';
					grid_param.orderType       = $('form.searchForm .orderTypeFilter', $modal).val() ?? '';
					grid_param.middleItemCodeId = Number($('form.searchForm .middleCodeSelect', $modal).val() ?? 0);
					grid_param.smallItemCodeId  = Number($('form.searchForm .smallCodeSelect', $modal).val() ?? 0);
					grid_param.searchType      = $('form.searchForm .searchType', $modal).val() ?? '';
					grid_param.keyword         = $('form.searchForm #keyword', $modal).val() ?? '';
					return $.param(grid_param);
				},
			},
		},
	};

	// =====================================================================
	// 그리드 정의
	// =====================================================================
	$modal.grid = gGrid('.workApprovalList', {
		scrollX: true,
		// 구분/관리번호/소분류/접수일/성적서번호 5개 컬럼 고정 (rowHeader checkbox는 frozenCount 미포함)
		frozenCount: 5,
		columns: [
			{
				// 접수구분: ACCREDDIT=공인, UNACCREDDIT=비공인, TESTING=시험
				header: '구분',
				name: 'orderType',
				width: 60,
				align: 'center',
				className: 'cursor_pointer',
				formatter: function (data) {
					const map = { ACCREDDIT: '공인', UNACCREDDIT: '비공인', TESTING: '시험' };
					return map[data.value] ?? data.value ?? '';
				},
			},
			{
				header: '관리번호',
				name: 'manageNo',
				width: 100,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '소분류',
				name: 'smallCodeNum',
				width: 65,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '접수일',
				name: 'orderDate',
				width: 85,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '성적서번호',
				name: 'reportNum',
				width: 120,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '신청업체',
				name: 'custAgent',
				width: 130,
				align: 'center',
				className: 'cursor_pointer',
				whiteSpace: 'pre-line',
			},
			{
				header: '성적서발행처',
				name: 'reportAgent',
				width: 130,
				align: 'center',
				className: 'cursor_pointer',
				whiteSpace: 'pre-line',
			},
			{
				header: '기기명',
				name: 'itemName',
				align: 'center',
				className: 'cursor_pointer',
				whiteSpace: 'pre-line',
			},
			{
				header: '기기번호',
				name: 'itemNum',
				width: 120,
				align: 'center',
				className: 'cursor_pointer',
				whiteSpace: 'pre-line',
			},
			{
				header: '제작회사',
				name: 'itemMakeAgent',
				width: 120,
				align: 'center',
				className: 'cursor_pointer',
				whiteSpace: 'pre-line',
			},
			{
				header: '형식',
				name: 'itemFormat',
				width: 120,
				align: 'center',
				className: 'cursor_pointer',
				whiteSpace: 'pre-line',
			},
			{
				header: '진행상태',
				name: 'reportStatus',
				width: 90,
				align: 'center',
				className: 'cursor_pointer',
				formatter: function (data) {
					return reportStatusLabel(data.value);
				},
			},
			{
				// 성적서작성(ExcelWork) 상태 — WriteStatusCellRenderer 로 배지 표시
				header: '작성',
				name: 'writeStatus',
				width: 65,
				align: 'center',
				sortable: false,
				renderer: { type: WriteStatusCellRenderer },
			},
			{
				header: '작성자',
				name: 'writeMemberName',
				width: 80,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '실무자',
				name: 'workMemberName',
				width: 80,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '기술책임자',
				name: 'approvalMemberName',
				width: 90,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				// 원본 성적서 엑셀 다운로드 (file_info name='report_origin')
				// 업로드 컬럼 바로 앞 — 성적서작성 완료 시 표시
				header: '원본',
				name: 'originFileId',
				width: 80,
				align: 'center',
				sortable: false,
				renderer: { type: ReportFileDownloadRenderer },
			},
			{
				// 업로드/결재 컬럼
				// - input[type=file] : 항상 표시 (원본 파일 업로드 또는 교체)
				// - 결재 버튼        : originFileId 있을 때만 표시 (실무자결재 트리거)
				header: '업로드/결재',
				name: 'uploadBtn',
				width: 95,
				align: 'center',
				sortable: false,
				renderer: {
					type: WorkApprovalCellRenderer,
				},
			},
			{
				// 결재 완료 후 생성되는 EXCEL 출력 다운로드 (file_info name='report_excel')
				// 업로드 컬럼 오른쪽
				header: 'EXCEL',
				name: 'excelFileId',
				width: 60,
				align: 'center',
				sortable: false,
				renderer: { type: ReportFileDownloadRenderer },
			},
			{
				// 결재 완료 후 생성되는 PDF 출력 다운로드 (file_info name='report_pdf')
				// 업로드 컬럼 오른쪽
				header: 'PDF',
				name: 'pdfFileId',
				width: 55,
				align: 'center',
				sortable: false,
				renderer: { type: ReportFileDownloadRenderer },
			},
			{
				// 수리/불가/초기화 상태변경 버튼 열 (StatusChangeCellRenderer — gridClass.js)
				header: '상태변경',
				name: 'statusChange',
				width: 68,
				align: 'center',
				sortable: false,
				renderer: { type: StatusChangeCellRenderer },
			},
		],
		pageOptions: {
			useClient: false, // 서버 페이징
			perPage: 25,      // 기본 25건
		},
		rowHeaders: ['checkbox'],
		minBodyHeight: 600,
		bodyHeight: 600,
		rowHeight: 'auto',
		data: $modal.dataSource,
	});

	// =====================================================================
	// 그리드 행 클릭 → 성적서수정(reportModify) 모달 호출
	// 업로드 컬럼(uploadBtn)과 체크박스 rowHeader(_checked) 클릭은 제외
	// 모달 닫힘 후 현재 페이지 재조회
	// =====================================================================
	$modal.grid.on('click', async function (ev) {
		const { columnName, rowKey } = ev;
		// 체크박스 rowHeader, 업로드 컬럼, 파일 다운로드 컬럼, 상태변경 컬럼 클릭 무시
		if (columnName === '_checked' || columnName === 'uploadBtn'
			|| columnName === 'originFileId' || columnName === 'excelFileId' || columnName === 'pdfFileId'
			|| columnName === 'statusChange') return;

		const row = $modal.grid.getRow(rowKey);
		if (!row || !row.id) return;

		const reportNum = row.reportNum ?? '';
		// 기술책임자 결재 완료(approvalStatus=SUCCESS)이면 저장 버튼 비활성화
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
				// 성적서작성·수리/불가/초기화 버튼은 reportModify.js init_modal에서 동적 삽입됨
			},
		);

		// 모달 닫힘 후 현재 페이지 유지하며 그리드 재조회
		const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
		$modal.grid.getPagination().movePageTo(currentPage);
	});

	// =====================================================================
	// 행 배경색 적용: reportStatus·workStatus 기준으로 TUI Grid 행 CSS 클래스 설정
	// response 이벤트 후 호출하여 서버 데이터 로드 시마다 갱신
	// =====================================================================
	const ROW_CLASS_NAMES = ['row-repair', 'row-impossible', 'row-rejected', 'row-resubmitted', 'row-approved'];

	function applyRowClasses() {
		const allRows = $modal.grid.getData();
		allRows.forEach(function (row) {
			const rowKey = row.rowKey;
			ROW_CLASS_NAMES.forEach(cls => $modal.grid.removeRowClassName(rowKey, cls));
			if (row.workStatus === 'SUCCESS') {
				$modal.grid.addRowClassName(rowKey, 'row-approved');
			} else if (row.reportStatus === 'REPAIR') {
				$modal.grid.addRowClassName(rowKey, 'row-repair');
			} else if (row.reportStatus === 'IMPOSSIBLE') {
				$modal.grid.addRowClassName(rowKey, 'row-impossible');
			} else if (row.reportStatus === 'REJECTED') {
				$modal.grid.addRowClassName(rowKey, 'row-rejected');
			} else if (row.reportStatus === 'RESUBMITTED') {
				$modal.grid.addRowClassName(rowKey, 'row-resubmitted');
			}
		});
	}

	// 그리드가 렌더링되면 색상표시 활성화
	$modal.grid.on('response', function () {
		requestAnimationFrame(() => applyRowClasses());
	});

	// =====================================================================
	// 상태변경 버튼 클릭 핸들러 (StatusChangeCellRenderer 버튼 위임)
	// PATCH /api/report/updateStatus/{id} 호출 후 현재 페이지 재조회
	// =====================================================================
	$('.workApprovalList').on('click', '.btn-status-repair, .btn-status-impossible, .btn-status-reset', async function (e) {
		e.stopPropagation();
		const $btn     = $(this);
		const reportId = $btn.data('id');

		let newStatus, confirmMsg;
		if ($btn.hasClass('btn-status-repair')) {
			newStatus  = 'REPAIR';
			confirmMsg = '수리 처리하시겠습니까?<br><small class="text-muted">성적서작성·결재 데이터 및 파일이 초기화됩니다.</small>';
		} else if ($btn.hasClass('btn-status-impossible')) {
			newStatus  = 'IMPOSSIBLE';
			confirmMsg = '불가 처리하시겠습니까?<br><small class="text-muted">성적서작성·결재 데이터 및 파일이 초기화됩니다.</small>';
		} else {
			newStatus  = 'NORMAL';
			confirmMsg = '초기화(NORMAL) 처리하시겠습니까?';
		}

		const confirmResult = await gMessage('상태변경', confirmMsg, 'question', 'confirm');
		if (!confirmResult.isConfirmed) return;

		try {
			gLoadingMessage('처리 중...');
			const res = await fetch(`/api/report/updateStatus/${reportId}`, {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ newStatus }),
			});
			swal.close();
			if (!res.ok) throw res;
			const data = await res.json();
			if (data?.code > 0) {
				gToast(data.msg ?? '상태가 변경되었습니다.', 'success');
				const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
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
	// 페이지 이벤트 바인딩
	// =====================================================================
	$modal
		// 검색 폼 submit
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();
			$modal.grid.getPagination().movePageTo(1);
		})
		// 행 수 변경
		.on('change', '.rowLeng', function () {
			const rowLeng = $(this).val();
			if (rowLeng > 0) {
				$modal.grid.setPerPage(rowLeng);
			}
		})
		// 중분류 변경 → 소분류 옵션 갱신
		.on('change', '.middleCodeSelect', function () {
			const middleCodeId = $(this).val();
			const $smallCodeSelect = $('.smallCodeSelect', $modal);
			$($smallCodeSelect).find('option').remove();
			$smallCodeSelect.append(new Option('소분류전체', ''));
			if (!middleCodeId) {
				$smallCodeSelect.val('');
			} else {
				if (smallItemCodeSet[middleCodeId] != undefined && smallItemCodeSet[middleCodeId].length > 0) {
					smallItemCodeSet[middleCodeId].forEach((row) => {
						$smallCodeSelect.append(new Option(row.codeNum, row.id));
					});
				}
			}
		})
		// 버튼: 성적서작성
		// 1) 체크된 항목 없으면 warning
		// 2) 소분류가 모두 동일해야 함
		// 3) 검증 통과 시 성적서작성 모달(reportWrite) 호출
		.on('click', '.btnWriteReport', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (!checkedRows || checkedRows.length === 0) {
				gToast('리스트에서 항목을 선택해 주세요.', 'warning');
				return;
			}
			// 소분류 동일성 체크 (smallCodeNum 기준)
			const smallCodes = [...new Set(checkedRows.map((row) => row.smallCodeNum))];
			if (smallCodes.length > 1) {
				gToast('동일한 소분류 항목만 선택해 주세요.', 'warning');
				return;
			}

			// 첫 번째 체크 행에서 소분류 정보 추출
			const firstRow        = checkedRows[0];
			const smallCodeNum    = firstRow.smallCodeNum;
			const smallItemCodeId = firstRow.smallItemCodeId;
			// 체크된 모든 행의 성적서 id 수집 → 배치 생성 시 전달
			const reportIds       = checkedRows.map((row) => row.id);

			// 성적서작성 모달 호출
			await gModal(
				'/cali/reportWrite',
				{ smallItemCodeId, smallCodeNum, reportIds },
				{
					title: `성적서 작성 [소분류코드 - ${smallCodeNum}]`,
					size: 'xl',
					show_close_button: true,
				},
			);

			// 모달 닫힘 후 그리드 재조회 — write_status 변경 반영
			const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
			$modal.grid.getPagination().movePageTo(currentPage);
		})
		// 버튼: 비정상 종료 복구 (스마트 복구)
		// 1) 체크된 항목 없으면 warning
		// 2) writeStatus 가 READY 또는 PROGRESS 인 항목만 대상 (SUCCESS 는 이미 완료이므로 제외)
		// 3) GET /api/excelwork/recover-preview → 스토리지 파일 존재 여부 확인 후 예상 결과 미리보기
		// 4) 확인 후 PATCH /api/excelwork/smart-recover → 파일 있음: SUCCESS 완료처리 / 파일 없음: IDLE 초기화
		.on('click', '.btnWriteReset', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (!checkedRows || checkedRows.length === 0) {
				gToast('리스트에서 항목을 선택해 주세요.', 'warning');
				return;
			}

			// READY/PROGRESS 상태인 항목만 대상 (SUCCESS 는 이미 완료)
			const targetRows = checkedRows.filter(r => r.writeStatus === 'READY' || r.writeStatus === 'PROGRESS');
			if (targetRows.length === 0) {
				gToast('복구 대상 항목이 없습니다. (작성대기/작성중 상태만 복구 가능)', 'warning');
				return;
			}

			const reportIds = targetRows.map(r => r.id);

			try {
				// ── Step 1. 스토리지 파일 존재 여부 미리 확인 ─────────────────────
				gLoadingMessage('스토리지 파일 확인 중...');

				const params = new URLSearchParams();
				reportIds.forEach(id => params.append('reportIds', id));
				const previewRes = await fetch(`/api/excelwork/recover-preview?${params.toString()}`);
				swal.close();
				if (!previewRes.ok) throw previewRes;
				const previewData = await previewRes.json();
				if (!previewData || previewData.code <= 0) {
					await gMessage('오류', previewData?.msg ?? '미리보기 조회 중 오류가 발생했습니다.', 'error', 'alert');
					return;
				}

				const { successItems, idleItems } = previewData.data;

				// 모든 항목이 이미 완료 상태여서 처리 대상 없음
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
					'비정상 종료 복구',
					htmlContent,
					'question',
					'confirm',
					{ confirmButtonText: '복구 실행', cancelButtonText: '취소' }
				);
				if (!confirmResult.isConfirmed) return;

				// ── Step 3. 스마트 복구 실행 ──────────────────────────────────────
				gLoadingMessage('복구 처리 중...');
				const recoverRes = await fetch('/api/excelwork/smart-recover', {
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
					const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
					$modal.grid.getPagination().movePageTo(currentPage);
				} else {
					await gMessage('오류', recoverData?.msg ?? '복구 중 오류가 발생했습니다.', 'error', 'alert');
				}
			} catch (err) {
				swal.close();
				await gApiErrorHandler(err);
			}
		})
		// 버튼: 성적서업로드 → 성적서업로드 모달 호출
		.on('click', '.btnReportUpload', async function () {
			await gModal(
				'/cali/reportUpload',
				{ type: 'work' },
				{
					title: '성적서업로드',
					size: 'md',
					show_close_button: true,
					show_confirm_button: false,
				}
			);
			// 모달 닫힘 후 그리드 재조회 (업로드·결재 완료 반영)
			const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
			$modal.grid.getPagination().movePageTo(currentPage);
		})
		// 버튼: 성적서대기변경
		// 실무자 결재 완료(workStatus=SUCCESS) + 기술책임자 결재 미착수(approvalStatus=IDLE) 인 성적서를 대기(IDLE)로 초기화
		// 1차: 스크립트 검증 (조건 불일치 시 SweetAlert 안내), 2차: 서버 재검증 후 일괄 update
		.on('click', '.btnWaitChange', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (!checkedRows || checkedRows.length === 0) {
				gToast('리스트에서 항목을 선택해 주세요.', 'warning');
				return;
			}

			// ── 1차 스크립트 검증 ────────────────────────────────────────────────
			// workStatus=SUCCESS, approvalStatus=IDLE 이어야 대기변경 가능
			const invalidRows = checkedRows.filter(row =>
				row.workStatus !== 'SUCCESS'
				|| row.approvalStatus === 'PROGRESS'
				|| row.approvalStatus === 'SUCCESS'
			);

			if (invalidRows.length > 0) {
				const listHtml = invalidRows.map(row => {
					let reason;
					if (row.workStatus !== 'SUCCESS') {
						reason = '실무자 결재 완료 상태가 아닙니다.';
					} else if (row.approvalStatus === 'PROGRESS') {
						reason = '기술책임자 결재가 진행 중입니다.';
					} else {
						reason = '기술책임자 결재가 이미 완료된 성적서입니다.';
					}
					return `<li><strong>${row.reportNum ?? row.id}</strong>: ${reason}</li>`;
				}).join('');
				await gMessage(
					'대기변경 불가',
					`<ul class="text-start" style="max-height:200px; overflow-y:auto; font-size:0.85em;">${listHtml}</ul>`,
					'error', 'alert'
				);
				return;
			}

			// ── 최종 확인 다이얼로그 ─────────────────────────────────────────────
			const reportIds = checkedRows.map(row => row.id);
			const confirmResult = await gMessage(
				'성적서대기변경',
				`${checkedRows.length}건의 실무자결재를 취소하고 대기 상태로 변경하시겠습니까?`,
				'question', 'confirm',
				{ confirmButtonText: '대기변경' }
			);
			if (!confirmResult.isConfirmed) return;

			// ── 서버 요청 (2차 검증 + 일괄 update) ──────────────────────────────
			try {
				gLoadingMessage('처리 중...');
				const res = await fetch('/api/report/resetWorkStatus', {
					method: 'PATCH',
					headers: { 'Content-Type': 'application/json; charset=utf-8' },
					body: JSON.stringify({ reportIds }),
				});
				swal.close();
				if (!res.ok) throw res;
				const data = await res.json();

				if (data?.code > 0) {
					await gMessage('대기변경 완료', data.msg ?? '대기상태로 변경되었습니다.', 'success', 'alert');
					const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
					$modal.grid.getPagination().movePageTo(currentPage);
				} else if (data?.code === -1 && data?.data?.invalid?.length > 0) {
					// 서버 2차 검증 실패 — 불가 항목 목록 표시
					const failHtml = data.data.invalid.map(i =>
						`<li><strong>${i.reportNum ?? i.id}</strong>: ${i.reason}</li>`
					).join('');
					await gMessage(
						'대기변경 불가',
						`<ul class="text-start" style="max-height:200px; overflow-y:auto; font-size:0.85em;">${failHtml}</ul>`,
						'error', 'alert'
					);
				} else {
					await gMessage('오류', data?.msg ?? '처리 중 오류가 발생했습니다.', 'error', 'alert');
				}
			} catch (err) {
				swal.close();
				await gApiErrorHandler(err);
			}
		})
		// 버튼: 통합수정
		// 1) 체크된 항목 없으면 warning
		// 2) selfReportMultiUpdate 모달 호출
		// 3) 모달 닫힘 후 현재 페이지 유지하며 그리드 재조회
		.on('click', '.btnBulkEdit', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (!checkedRows || checkedRows.length === 0) {
				gToast('리스트에서 항목을 선택해 주세요.', 'warning');
				return;
			}

			const reportIds = checkedRows.map(row => row.id);

			await gModal(
				'/cali/selfReportMultiUpdate',
				{ reportIds },
				{
					title: `통합수정 [${reportIds.length}건 선택]`,
					size: 'xl',
					show_close_button: true,
					show_confirm_button: true,
					confirm_button_text: '저장',
				}
			);

			// 모달 닫힘 후 현재 페이지 유지하며 그리드 재조회
			const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
			$modal.grid.getPagination().movePageTo(currentPage);
		})
		// 버튼: 성적서다중결재
		// 1) 체크된 항목 없으면 warning
		// 2) originFileId 없는 항목 필터링
		// 3) validateWorkApproval API 호출 → 실패 건 SweetAlert 목록 표시
		// 4) 유효 건만 workMemberId 기준 그룹화 → 그룹별 doWorkApproval 순차 호출
		.on('click', '.btnMultiApproval', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (!checkedRows || checkedRows.length === 0) {
				gToast('리스트에서 항목을 선택해 주세요.', 'warning');
				return;
			}

			// originFileId 있는 항목만 결재 후보
			const approvalRows = checkedRows.filter(row => !!row.originFileId);
			if (approvalRows.length === 0) {
				gToast('결재 가능한 항목이 없습니다. 성적서작성이 완료된 항목을 선택해 주세요.', 'warning');
				return;
			}

			// ── 사전 유효성 검증 API 호출 ─────────────────────────────────────
			let validateResult;
			try {
				gLoadingMessage('유효성 검증 중...');
				const res = await fetch('/api/report/jobs/validateWorkApproval', {
					method: 'POST',
					headers: { 'Content-Type': 'application/json; charset=utf-8' },
					body: JSON.stringify({ reportIds: approvalRows.map(r => r.id) }),
				});
				swal.close();
				if (!res.ok) throw res;
				const resData = await res.json();
				if (!resData || resData.code <= 0) {
					await gMessage('오류', resData.msg ?? '유효성 검증 중 오류가 발생했습니다.', 'error', 'alert');
					return;
				}
				validateResult = resData.data;
			} catch (err) {
				swal.close();
				await gApiErrorHandler(err);
				return;
			}

			const { valid, invalid } = validateResult;

			// 전부 실패 → 안내 후 종료
			if (!valid || valid.length === 0) {
				const failHtml = (invalid ?? []).map(i =>
					`<li><strong>${i.reportNum}</strong>: ${i.reason}</li>`
				).join('');
				await gMessage(
					'결재 불가',
					`<ul class="text-start" style="max-height:200px; overflow-y:auto; font-size:0.85em;">${failHtml}</ul>`,
					'error', 'alert'
				);
				return;
			}

			// 일부 실패 → 실패 목록 보여주고 유효 건만 진행 여부 확인
			let confirmed;
			if (invalid && invalid.length > 0) {
				const failHtml = invalid.map(i =>
					`<li><strong>${i.reportNum}</strong>: ${i.reason}</li>`
				).join('');
				const result = await gMessage(
					'일부 결재 불가',
					`<p style="font-size:0.9em;">아래 <strong>${invalid.length}건</strong>은 결재가 불가능하여 제외됩니다.</p>` +
					`<ul class="text-start" style="max-height:180px; overflow-y:auto; font-size:0.85em;">${failHtml}</ul>` +
					`<p style="font-size:0.9em; margin-top:8px;"><strong>${valid.length}건</strong>에 대해 결재를 진행하시겠습니까?</p>`,
					'warning', 'confirm', { confirmButtonText: '결재' }
				);
				confirmed = result.isConfirmed;
			} else {
				// 전부 통과 → 결재 확인
				const firstNum = valid[0].reportNum ?? '';
				const titleSuffix = valid.length === 1
					? `[${firstNum}]`
					: `[${firstNum} 외 ${valid.length - 1}건]`;
				const result = await gMessage(
					'실무자결재',
					`${titleSuffix}<br>선택한 성적서를 실무자결재 처리하시겠습니까?`,
					'question', 'confirm', { confirmButtonText: '결재' }
				);
				confirmed = result.isConfirmed;
			}

			if (!confirmed) return;

			// ── 유효 건만 workMemberId 기준 그룹화 → 그룹별 순차 결재 ──────────
			const validIdSet  = new Set(valid.map(v => v.id));
			const validRows   = approvalRows.filter(r => validIdSet.has(r.id));

			const groupMap = new Map();
			for (const row of validRows) {
				const wid = row.workMemberId ?? 'null';
				if (!groupMap.has(wid)) groupMap.set(wid, []);
				groupMap.get(wid).push(row);
			}

			for (const [, rows] of groupMap) {
				const ids      = rows.map(r => r.id);
				const firstNum = rows[0].reportNum ?? '';
				// 위에서 이미 confirm 완료 → skipConfirm=true
				await doWorkApproval(ids, firstNum, true);
			}
		});

	// =====================================================================
	// reportWrite 완료 이벤트 수신 → 현재 페이지 유지하며 그리드 리로드
	// reportWrite.js 에서 배치 완료 후 확인 버튼을 눌렀을 때 trigger 됨
	// =====================================================================
	$(document).on('reportWriteCompleted.workApproval', function () {
		const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
		$modal.grid.getPagination().movePageTo(currentPage);
	});

	// =====================================================================
	// 페이지 마운트 처리 (common.js 규약)
	// =====================================================================
	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		// 모달 팝업창인 경우
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