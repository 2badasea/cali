$(function () {
	console.log('++ cali/reportWrite.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	// 모달 컨텍스트에서 gModal은 .modal-body에 param을 저장하므로 .modal-body를 우선 선택
	const $bodyCandidate = $candidates.filter('.modal-body');
	if ($bodyCandidate.length) {
		$modal = $bodyCandidate.first();
	} else {
		$modal = $candidates.first();
	}
	let $modal_root = $modal.closest('.modal');

	// =====================================================================
	// loadGridData: API 호출 후 그리드 데이터 갱신
	// 페이지네이션 없이 전체 목록을 받아 resetData()로 세팅
	// =====================================================================
	$modal.loadGridData = async function () {
		const smallItemCodeId = $modal.param?.smallItemCodeId ?? 0;
		const searchType      = $('form.searchForm .searchType', $modal).val() ?? 'all';
		const keyword         = $('form.searchForm #keyword', $modal).val() ?? '';

		try {
			const res = await gAjax(
				'/api/sample/reportWriteList',
				{ smallItemCodeId, searchType, keyword },
				{ type: 'GET' }
			);
			if (res?.code > 0) {
				$modal.grid.resetData(res.data ?? []);
			} else {
				gToast('목록을 불러오지 못했습니다.', 'warning');
			}
		} catch (xhr) {
			await gApiErrorHandler(xhr);
		}
	};

	// =====================================================================
	// init_modal: 모달 파라미터 수신 후 그리드 초기 로딩
	// param: { smallItemCodeId, smallCodeNum }
	// =====================================================================
	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);

		// 그리드 레이아웃 갱신 후 초기 데이터 로딩
		if (typeof $modal.grid == 'object') {
			$modal.grid.refreshLayout();
		}
		await $modal.loadGridData();
	};

	// =====================================================================
	// 그리드 정의 (페이지네이션 없음 — 스크롤 방식)
	// bodyHeight 고정 + rowHeight auto → 데이터 초과 시 scrollY 활성화
	// =====================================================================
	$modal.grid = gGrid('.reportWriteGrid', {
		pageOptions: false, // 페이지네이션 비활성화 (스크롤 방식)
		columns: [
			{
				header: '기기명',
				name: 'itemName',
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '파일명',
				name: 'fileName',
				align: 'center',
				whiteSpace: 'pre-line',
				className: 'cursor_pointer',
			},
			{
				header: '등록자',
				name: 'createMemberName',
				width: 90,
				align: 'center',
				className: 'cursor_pointer',
			},
			{
				header: '등록일시',
				name: 'createDatetime',
				width: 150,
				align: 'center',
				className: 'cursor_pointer',
				formatter: function (data) {
					// "2026-03-20T14:30:00" → "2026-03-20 14:30"
					if (!data.value) return '';
					return String(data.value).replace('T', ' ').slice(0, 16);
				},
			},
		],
		minBodyHeight: 500,
		bodyHeight: 500,   // 약 10~12행 수준, 초과 시 scrollY 활성화
		rowHeight: 'auto',
	});

	// =====================================================================
	// 이벤트 바인딩
	// =====================================================================
	$modal
		// 검색 폼 submit → 데이터 재조회
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();
			$modal.loadGridData();
		});

	// 그리드 행 클릭 → 필수항목 검증 → 성적서작성 배치 생성
	// SampleReportWriteRow: getId() = file_info.id, getSampleId() = sample.id
	// 배치 생성 API에는 sample.id(sampleId)를 전달해야 하므로 row.sampleId 사용
	$modal.grid.on('click', async function (ev) {
		const { rowKey } = ev;
		const row = $modal.grid.getRow(rowKey);
		if (!row) return;

		const reportIds  = $modal.param?.reportIds ?? [];
		const sampleId   = row.sampleId;   // sample.id
		const sampleName = row.itemName ?? '';
		const fileName   = row.fileName ?? '';

		// 대상 성적서 목록 미전달 시 경고 (reportWrite 단독 접근 등)
		if (!reportIds || reportIds.length === 0) {
			gToast('대상 성적서 정보가 없습니다.', 'warning');
			return;
		}

		// ── Step 1. 필수값 검증 진행 여부 확인 ─────────────────────────────
		const confirmResult = await gMessage(
			'필수항목 검증',
			`선택한 샘플 <b>${sampleName}</b>(${fileName})로 성적서를 작성하기 전,<br>` +
			`대상 성적서 ${reportIds.length}건의 필수값 검증을 진행하겠습니다.`,
			'question',
			'confirm',
			{ confirmButtonText: '확인' }
		);
		if (!confirmResult.isConfirmed) return;

		// ── Step 2. 필수항목 검증 API 호출 ────────────────────────────────
		let validateData;
		try {
			gLoadingMessage('필수항목 데이터를 검증합니다.');
			const res = await fetch('/api/report/validateWrite', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds }),
			});
			swal.close();
			if (!res.ok) throw res;
			const resData = await res.json();
			if (resData?.code > 0) {
				validateData = resData.data;
			} else {
				await gMessage('검증 오류', resData.msg ?? '검증 중 오류가 발생했습니다.', 'error', 'alert');
				return;
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			return;
		}

		// ── Step 3. 검증 결과에 따라 분기 ─────────────────────────────────
		let targetIds;

		// ③-0 진행중 성적서가 1건이라도 있으면 완전 차단 (전체 진행 포함 모든 옵션 불가)
		if (validateData.hasInProgress) {
			const numList = validateData.inProgressReportNums.join(', ');
			await gMessage(
				'작업 불가',
				`이미 작업 중인 성적서가 존재합니다.<br>${numList}`,
				'error',
				'alert'
			);
			return;
		}

		if (validateData.allPassed) {
			// 모두 통과 → 성적서작성 최종 확인
			const writeResult = await gMessage(
				'성적서 작성',
				`성적서작성을 진행하시겠습니까?<br>대상: ${reportIds.length}건`,
				'question',
				'confirm',
				{ confirmButtonText: '진행' }
			);
			if (!writeResult.isConfirmed) return;
			targetIds = reportIds;

		} else {
			// 일부 실패 → 누락 항목 안내 + 3버튼 선택
			// 필드별 누락 성적서번호를 HTML로 포맷팅
			const failureHtml = validateData.failures
				.map(f => `<b>${f.field}</b><br>${f.reportNums.join(', ')}`)
				.join('<br><br>');

			const failResult = await Swal.fire({
				title: '필수값 누락 안내',
				html:
					`<div style="text-align:left; max-height:280px; overflow-y:auto; padding-right:6px; font-size:0.92em;">` +
					`다음 성적서들의 경우 필수값이 없습니다.<br><br>${failureHtml}` +
					`</div>`,
				icon: 'warning',
				showConfirmButton: true,
				showDenyButton: true,
				showCancelButton: true,
				confirmButtonText: '전체 진행',   // isConfirmed — 누락 성적서 포함 전체 진행
				denyButtonText:    '통과만 진행', // isDenied   — 검증 통과 성적서만 진행
				cancelButtonText:  '취소',
			});

			if (failResult.isConfirmed) {
				// 전체 진행 (누락 성적서 포함)
				targetIds = reportIds;

			} else if (failResult.isDenied) {
				// 통과 성적서만 진행
				if (!validateData.passedIds || validateData.passedIds.length === 0) {
					gToast('검증을 통과한 성적서가 없습니다.', 'warning');
					return;
				}
				targetIds = validateData.passedIds;

			} else {
				// 취소
				await gMessage('취소', '취소했습니다.', 'info', 'alert');
				return;
			}
		}

		// ── Step 4. ExcelWork 배치 생성 + 미들웨어 기동 ──────────────────────

		// 4-1. ExcelWork 배치 생성 API 호출 (POST /api/excelwork/batches)
		//      응답에 포함된 excelworkUri(excelwork://...) 를 브라우저에서 실행하면
		//      ExcelWorkApp 미들웨어가 기동되어 성적서작성 작업을 처리한다.
		let excelworkUri;
		let totalCount;
		try {
			gLoadingMessage('성적서작성 작업을 준비합니다.');
			const res = await fetch('/api/excelwork/batches', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds: targetIds, sampleId }),
			});
			swal.close();
			if (!res.ok) throw res;
			const resData = await res.json();
			if (resData?.code > 0) {
				excelworkUri = resData.data.excelworkUri;
				totalCount   = resData.data.totalCount;
			} else {
				await gMessage('오류', resData.msg ?? '배치 생성 중 오류가 발생했습니다.', 'error', 'alert');
				return;
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			return;
		}

		// 4-2. reportWrite 모달 닫기
		$modal_root.modal('hide');

		// 4-3. 미들웨어 실행 안내 메시지
		//      확인 버튼 클릭 시 excelworkUri 를 실행하여 ExcelWorkApp 기동
		await gMessage(
			'성적서작성 시작',
			`${totalCount}건의 성적서작성 작업이 준비되었습니다.<br>` +
			`<small class="text-muted">확인을 누르면 ExcelWork 미들웨어가 실행됩니다.<br>` +
			`작업 진행상황은 성적서 목록의 <b>작성</b> 컬럼에서 확인하세요.</small>`,
			'info',
			'alert',
			{ confirmButtonText: 'ExcelWork 시작' }
		);

		// 4-4. URI 스킴으로 ExcelWorkApp 기동 (excelwork://process?token=...&serverUrl=...)
		window.location.href = excelworkUri;

		// 4-5. workApproval 그리드 리로드 (writeStatus 변경 반영)
		$(document).trigger('reportWriteCompleted');
	});

	// =====================================================================
	// 페이지 마운트 처리 (common.js 규약)
	// =====================================================================
	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		// 모달 컨텍스트: gModal이 .modal-body.data('param')에 저장한 param을 읽어 init_modal 호출
		// modal_ready 이벤트 대신 reportModify.js 패턴과 동일하게 setTimeout으로 처리
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