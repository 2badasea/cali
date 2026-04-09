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
			WAIT: '대기',
			WORK_RETURN: '실무자반려',
			APPROV_RETURN: '기술책임자반려',
			REUPLOAD: '재업로드',
			REPAIR: '수리',
			IMPOSSIBLE: '불가',
			CANCEL: '취소',
			COMPLETE: '완료',
		};
		return map[value] ?? value ?? '';
	}

	// =====================================================================
	// 성적서 파일 다운로드 (원본/EXCEL/PDF)
	// fetch + blob 방식: 다운로드 응답을 받은 뒤 a 태그 클릭으로 저장 유도
	// gLoadingMessage 로 연속클릭 방지 → 다운로드 준비 완료 후 닫기
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
	// 성적서 파일 다운로드 아이콘 셀 렌더러
	// originFileId / excelFileId / pdfFileId 컬럼에 공통 사용
	// value(fileId)가 있을 때만 아이콘 버튼 표시, 없으면 빈 셀
	// =====================================================================
	class ReportFileDownloadRenderer {
		constructor(props) {
			// UploadCellRenderer 와 동일하게 flex + 100% 사이즈 적용
			// → rowHeight:'auto' 환경에서 다른 셀이 개행돼도 상하 여백 없이 수직 중앙 정렬
			this.el = document.createElement('div');
			this.el.style.cssText = 'display:flex; align-items:center; justify-content:center; width:100%; height:100%; min-height:28px;';
			this.render(props);
		}

		render(props) {
			// props.value: file_info.id (null이면 파일 없음 → 아이콘 미표시)
			// Toast UI Grid 렌더러에는 props.row 가 없음 → props.grid.getRow(props.rowKey) 사용
			const hasFile = !!props.value;
			const colName = props.columnInfo.name; // 'originFileId' | 'excelFileId' | 'pdfFileId'

			if (!hasFile) {
				this.el.innerHTML = '';
				return;
			}

			// 컬럼명 → fileType (서버 API 경로: /api/file/report/{reportId}/{fileType})
			const fileTypeMap = {
				originFileId: 'origin',
				excelFileId:  'signed_xlsx',
				pdfFileId:    'signed_pdf',
			};
			const fileType = fileTypeMap[colName] ?? 'origin';
			// props.grid.getRow()로 행 데이터 조회 후 report.id 추출
			const reportId = props.grid.getRow(props.rowKey).id;

			// PDF 컬럼은 빨간 배경, 그 외(원본·EXCEL)는 녹색
			const isPdf  = colName === 'pdfFileId';
			const btnCls = isPdf ? 'btn-danger' : 'btn-success';
			const icon   = isPdf ? 'bi-file-earmark-pdf-fill' : 'bi-file-earmark-excel-fill';

			// 버튼을 셀 가로 전체로 채워 업로드 열과 동일한 시각적 비중 유지
			this.el.innerHTML =
				`<button class="btn btn-sm ${btnCls} w-100" style="height:100%; min-height:28px;" title="다운로드">` +
				`<i class="bi ${icon}"></i></button>`;

			// 버튼 클릭 → 다운로드 (그리드 row 클릭 이벤트로 전파 차단)
			const reportNum = props.grid.getRow(props.rowKey).reportNum ?? '';
			this.el.querySelector('button').addEventListener('click', (e) => {
				e.stopPropagation();
				downloadReportFile(reportId, fileType, reportNum);
			});
		}

		getElement() { return this.el; }
	}

	// =====================================================================
	// WriteStatusCellRenderer: 성적서작성(ExcelWork) 상태 배지 표시
	// write_status: IDLE(미작성), READY(대기), PROGRESS(진행중), SUCCESS(완료), FAIL(실패)
	// =====================================================================
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

	// =====================================================================
	// WorkApprovalCellRenderer: 업로드 컬럼을 결재 트리거 버튼으로 대체
	// - originFileId 없음: 빈 셀 (성적서 미작성 상태 — 결재 불가)
	// - originFileId 있음: 결재 아이콘 버튼 → 클릭 시 WORK_APPROVAL 배치 트리거
	// =====================================================================
	class WorkApprovalCellRenderer {
		constructor(props) {
			this.el = document.createElement('div');
			this.el.style.cssText = 'display:flex; align-items:center; justify-content:center; width:100%; height:100%; min-height:28px;';
			this.render(props);
		}

		render(props) {
			const row = props.grid.getRow(props.rowKey);
			const hasOrigin = !!(row && row.originFileId);

			if (!hasOrigin) {
				// 성적서 미작성 — 결재 불가
				this.el.innerHTML = '';
				return;
			}

			const reportId  = row.id;
			const reportNum = row.reportNum ?? '';

			// 결재 아이콘 버튼 (bi-pen-fill: 서명/결재 이미지 연상)
			this.el.innerHTML =
				`<button class="btn btn-sm btn-warning w-100" style="height:100%; min-height:28px;" title="실무자결재">` +
				`<i class="bi bi-pen-fill"></i></button>`;

			this.el.querySelector('button').addEventListener('click', (e) => {
				e.stopPropagation();
				doWorkApproval([reportId], reportNum);
			});
		}

		getElement() { return this.el; }
	}

	// =====================================================================
	// doWorkApproval: WORK_APPROVAL 배치 생성 + 폴링 진행상황 표시
	// reportIds: 결재 대상 성적서 id 배열 (단건 또는 다중)
	// representReportNum: 타이틀용 성적서번호 (다중 시 "외 N건" 형태로 표시)
	// =====================================================================
	async function doWorkApproval(reportIds, representReportNum) {
		if (!reportIds || reportIds.length === 0) return;

		const titleSuffix = reportIds.length === 1
			? `[${representReportNum}]`
			: `[${representReportNum} 외 ${reportIds.length - 1}건]`;

		// ── Step 1. 결재 확인 ────────────────────────────────────────────
		const confirmResult = await gMessage(
			'실무자결재',
			`${titleSuffix}<br>선택한 성적서를 실무자결재 처리하시겠습니까?`,
			'question',
			'confirm',
			{ confirmButtonText: '결재' }
		);
		if (!confirmResult.isConfirmed) return;

		// ── Step 2. 배치 생성 API 호출 ────────────────────────────────────
		let batchId;
		try {
			gLoadingMessage('실무자결재 작업을 준비합니다.');
			const res = await fetch('/api/report/jobs/batches/work-approval', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds }),
			});
			swal.close();
			if (!res.ok) throw res;
			const resData = await res.json();
			if (resData?.code > 0) {
				batchId = resData.data.batchId;
			} else {
				await gMessage('오류', resData.msg ?? '배치 생성 중 오류가 발생했습니다.', 'error', 'alert');
				return;
			}
		} catch (err) {
			swal.close();
			customAjaxHandler(err);
			return;
		}

		// ── Step 3. 폴링 진행상황 Swal 표시 ──────────────────────────────
		const STEP_LABEL = {
			DOWNLOADING_ORIGIN: 'origin 다운로드',
			INSERTING_SIGN:     '서명 삽입',
			CONVERTING_PDF:     'PDF 변환',
			UPLOADING_SIGNED:   '파일 업로드',
			DONE:               '완료',
		};

		const buildProgressHtml = (batch) => {
			const total   = batch.totalCount   ?? 0;
			const success = batch.successCount ?? 0;
			const fail    = batch.failCount    ?? 0;
			const done    = success + fail;
			const pct     = total > 0 ? Math.round((done / total) * 100) : 0;

			const itemRows = (batch.items ?? []).map(item => {
				const badge =
					item.status === 'SUCCESS'  ? '<span class="badge bg-success">완료</span>'  :
					item.status === 'FAIL'     ? '<span class="badge bg-danger">실패</span>'   :
					item.status === 'PROGRESS' ? `<span class="badge bg-primary">${STEP_LABEL[item.step] ?? item.step ?? '처리중'}</span>` :
					                             '<span class="badge bg-secondary">대기</span>';
				const failMsg = item.message
					? `<br><small class="text-danger">${item.message}</small>` : '';
				return `<tr>
					<td class="text-start" style="font-size:0.85em;">성적서 #${item.reportId}</td>
					<td>${badge}${failMsg}</td>
				</tr>`;
			}).join('');

			return `
				<div class="mb-2">
					<div class="d-flex justify-content-between mb-1" style="font-size:0.85em;">
						<span>${done} / ${total}건 처리됨</span><span>${pct}%</span>
					</div>
					<div class="progress" style="height:12px;">
						<div class="progress-bar progress-bar-striped progress-bar-animated"
							role="progressbar" style="width:${pct}%;"></div>
					</div>
				</div>
				<div style="max-height:200px; overflow-y:auto;">
					<table class="table table-sm table-bordered mb-0" style="font-size:0.85em;">
						<tbody>${itemRows}</tbody>
					</table>
				</div>`;
		};

		Swal.fire({
			title: '실무자결재 처리 중...',
			html: '<div id="workApprovalProgressBody">준비 중...</div>',
			allowOutsideClick: false,
			allowEscapeKey: false,
			showConfirmButton: false,
			didOpen: () => { Swal.showLoading(); },
		});

		// ── Step 4. Polling 루프 (5초 간격, 최대 5분) ────────────────────
		const POLL_INTERVAL_MS = 5000;
		const MAX_POLL_COUNT   = 60;
		let pollCount = 0;

		while (pollCount < MAX_POLL_COUNT) {
			await new Promise(r => setTimeout(r, POLL_INTERVAL_MS));
			pollCount++;

			try {
				const pollRes = await fetch(`/api/report/jobs/batches/${batchId}`);
				if (!pollRes.ok) continue;
				const pollData = await pollRes.json();
				if (!pollData || pollData.code <= 0) continue;

				const batch = pollData.data;
				const progressEl = document.getElementById('workApprovalProgressBody');
				if (progressEl) progressEl.innerHTML = buildProgressHtml(batch);

				if (['SUCCESS', 'FAIL', 'CANCELED'].includes(batch.status)) {
					Swal.hideLoading();

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
				console.warn('[workApproval] Polling 오류:', pollErr);
			}
		}

		if (pollCount >= MAX_POLL_COUNT) {
			Swal.close();
			await gMessage('시간 초과', '작업 진행상황을 확인할 수 없습니다.<br>잠시 후 다시 확인해 주세요.', 'warning', 'alert');
		}
	}

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
			customAjaxHandler(xhr);
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
				// 결재 컬럼: originFileId 존재 시 결재 버튼 표시 (WorkApprovalCellRenderer)
				// originFileId 없으면 빈 셀 — 성적서작성 미완료 상태
				header: '결재',
				name: 'uploadBtn',
				width: 55,
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
		// 체크박스 rowHeader, 업로드 컬럼, 파일 다운로드 컬럼 클릭 무시
		if (columnName === '_checked' || columnName === 'uploadBtn'
			|| columnName === 'originFileId' || columnName === 'excelFileId' || columnName === 'pdfFileId') return;

		const row = $modal.grid.getRow(rowKey);
		if (!row || !row.id) return;

		const reportNum = row.reportNum ?? '';
		// COMPLETE(최종완료) 상태이면 저장 버튼 비활성화 (orderDetails.js 패턴 동일)
		const isModifiable = row.reportStatus !== 'COMPLETE';
		await gModal(
			'/cali/reportModify',
			{ id: row.id },
			{
				title: `성적서 수정 [성적서번호 - ${reportNum}]`,
				size: 'xxxl',
				show_close_button: true,
				show_confirm_button: isModifiable,
				confirm_button_text: '저장',
				// 성적서작성 버튼: reportModify.js에서 .modal-btn-write-report 클릭 핸들러가 처리
				custom_btn_html_arr: [
					'<button type="button" class="btn btn-primary btn-sm modal-btn-write-report mr-auto"><i class="bi bi-pencil-square"></i> 성적서작성</button>',
				],
			},
		);

		// 모달 닫힘 후 현재 페이지 유지하며 그리드 재조회
		const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
		$modal.grid.getPagination().movePageTo(currentPage);
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
		// 버튼: 비정상 종료 복구
		// 1) 체크된 항목 없으면 warning
		// 2) writeStatus 가 READY 또는 PROGRESS 인 항목만 대상
		// 3) 확인 후 PATCH /api/excelwork/reset → 배치 CANCELED 처리 + 성적서 writeStatus IDLE 복구
		.on('click', '.btnWriteReset', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (!checkedRows || checkedRows.length === 0) {
				gToast('리스트에서 항목을 선택해 주세요.', 'warning');
				return;
			}

			// READY/PROGRESS 상태인 항목의 성적서 id 목록 추출
			// (배치 id를 직접 갖고 있지 않으므로 report.id 기반으로 서버에서 배치 조회)
			const targetRows = checkedRows.filter(r => r.writeStatus === 'READY' || r.writeStatus === 'PROGRESS');
			if (targetRows.length === 0) {
				gToast('복구 대상 항목이 없습니다. (작성대기/작성중 상태만 복구 가능)', 'warning');
				return;
			}

			const reportIds = targetRows.map(r => r.id);

			const confirmResult = await gMessage(
				'비정상 종료 복구',
				`${reportIds.length}건의 성적서작성 상태를 미작성(IDLE)으로 복구하시겠습니까?<br>` +
				`<small class="text-muted">미들웨어 비정상 종료 후 고착된 상태를 초기화합니다.</small>`,
				'question',
				'confirm',
				{ confirmButtonText: '복구' }
			);
			if (!confirmResult.isConfirmed) return;

			try {
				gLoadingMessage('상태 복구 중...');
				const res = await fetch('/api/excelwork/reset', {
					method: 'PATCH',
					headers: { 'Content-Type': 'application/json; charset=utf-8' },
					body: JSON.stringify({ reportIds }),
				});
				swal.close();
				if (!res.ok) throw res;
				const resData = await res.json();
				if (resData?.code > 0) {
					await gMessage('복구 완료', '성적서작성 상태가 복구되었습니다.', 'success', 'alert');
					const currentPage = $modal.grid.getPagination()?.getCurrentPage() ?? 1;
					$modal.grid.getPagination().movePageTo(currentPage);
				} else {
					await gMessage('오류', resData.msg ?? '복구 중 오류가 발생했습니다.', 'error', 'alert');
				}
			} catch (err) {
				swal.close();
				customAjaxHandler(err);
			}
		})
		// 버튼: 성적서대기변경 (준비중)
		.on('click', '.btnWaitChange', function () {
			gToast('구현 준비중입니다.', 'info');
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
		// 버튼: 성적서 다중결재
		// 1) 체크된 항목 없으면 warning
		// 2) originFileId 없는 항목은 결재 불가 — 필터링 후 대상 없으면 안내
		// 3) workMemberId 기준으로 그룹화 → 그룹별로 doWorkApproval 순차 호출
		//    (서명 이미지가 실무자별로 다르므로 배치 단위로 실무자가 동일해야 함)
		.on('click', '.btnMultiApproval', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (!checkedRows || checkedRows.length === 0) {
				gToast('리스트에서 항목을 선택해 주세요.', 'warning');
				return;
			}

			// originFileId 있는 항목만 결재 가능
			const approvalRows = checkedRows.filter(row => !!row.originFileId);
			if (approvalRows.length === 0) {
				gToast('결재 가능한 항목이 없습니다. 성적서작성이 완료된 항목을 선택해 주세요.', 'warning');
				return;
			}

			// workMemberId 기준으로 그룹화 (Map: workMemberId → 해당 rows 배열)
			// 서명 이미지가 실무자별로 다르므로 배치 단위로 실무자가 동일해야 함
			const groupMap = new Map();
			for (const row of approvalRows) {
				const wid = row.workMemberId ?? 'null';
				if (!groupMap.has(wid)) groupMap.set(wid, []);
				groupMap.get(wid).push(row);
			}

			// 그룹별로 순차 처리 (각 그룹마다 별도 확인 + 별도 배치 + 별도 폴링)
			for (const [, rows] of groupMap) {
				const reportIds      = rows.map(r => r.id);
				const firstReportNum = rows[0].reportNum ?? '';
				await doWorkApproval(reportIds, firstReportNum);
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