$(function () {
	console.log('++ cali/reportUpload.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	// gModal은 .modal-body에 param을 저장하므로 .modal-body를 우선 선택
	const $bodyCandidate = $candidates.filter('.modal-body');
	if ($bodyCandidate.length) {
		$modal = $bodyCandidate.first();
	} else {
		$modal = $candidates.first();
	}
	let $modal_root = $modal.closest('.modal');

	// 현재 선택된 File 객체 배열
	let selectedFiles = [];
	// 모달 호출 타입: 'work'(기본, 원본 xlsx 업로드) | 'manager'(기술책임자용, signed xlsx/pdf 교체)
	let uploadType = 'work';

	// =====================================================================
	// init_modal: 모달 파라미터 수신 후 초기화
	// reportUpload는 파라미터 없이 호출되므로 파일 목록 초기화만 수행
	// =====================================================================
	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);
		// 모달이 재사용될 때 이전 선택 파일 초기화
		selectedFiles = [];
		uploadType = param.type ?? 'work';
		renderFileList([]);
		applyUploadTypeUI();
	};

	// =====================================================================
	// 파일 목록 UI 업데이트
	// =====================================================================
	function renderFileList(files) {
		const $list = $('#fileNameList', $modal);
		const $count = $('#fileCount', $modal);

		$list.empty();
		files.forEach((f) => {
			const isPdf = f.name.toLowerCase().endsWith('.pdf');
			const iconClass = isPdf ? 'bi-file-earmark-pdf' : 'bi-file-earmark-excel';
			$list.append(
				`<li>
					<i class="bi ${iconClass}"></i>
					<span>${f.name}</span>
				</li>`,
			);
		});

		$count.text(files.length);
		$('#selectedFileList', $modal).toggle(files.length > 0);
		$('#uploadActionBtns', $modal).toggle(files.length > 0);
	}

	// =====================================================================
	// 파일 적용: 확장자 검증 후 selectedFiles 갱신
	// =====================================================================
	function applyFiles(files) {
		const valid = Array.from(files).filter((f) => {
			const ext = f.name.split('.').pop().toLowerCase();
			if (uploadType === 'manager') {
				return ['xlsx', 'xls', 'pdf'].includes(ext);
			}
			return ext === 'xlsx' || ext === 'xls';
		});

		if (valid.length < files.length) {
			const msg = uploadType === 'manager'
				? '엑셀(.xlsx, .xls) 또는 PDF(.pdf) 파일만 업로드 가능합니다. 다른 형식의 파일은 제외되었습니다.'
				: '엑셀 파일(.xlsx, .xls)만 업로드 가능합니다. 다른 형식의 파일은 제외되었습니다.';
			gToast(msg, 'warning');
		}

		if (valid.length === 0) {
			selectedFiles = [];
			renderFileList([]);
			return;
		}

		selectedFiles = valid;
		renderFileList(valid);
	}

	// =====================================================================
	// uploadType에 따라 드롭존 안내문구 / 파일 input accept / 버튼 상태 변경
	// =====================================================================
	function applyUploadTypeUI() {
		if (uploadType === 'manager') {
			$modal.find('.upload-hint').text('엑셀/PDF 파일을 드래그하거나 버튼을 클릭하세요');
			$modal.find('.upload-hint-sub').text('.xlsx, .xls, .pdf 파일을 업로드 가능합니다');
			$fileInput.attr('accept', '.xlsx,.xls,.pdf');
			$modal.find('.btnUploadApproval').hide();
			$modal.find('.btnUploadOnly').html('<i class="bi bi-upload me-1"></i> 파일 교체');
		} else {
			$modal.find('.upload-hint').text('엑셀 파일을 드래그하거나 버튼을 클릭하세요');
			$modal.find('.upload-hint-sub').text('.xlsx, .xls 파일만 업로드 가능합니다');
			$fileInput.attr('accept', '.xlsx,.xls');
			$modal.find('.btnUploadApproval').show();
			$modal.find('.btnUploadOnly').html('<i class="bi bi-upload me-1"></i> 업로드');
		}
	}

	// =====================================================================
	// signed 파일(xlsx/pdf) 교체 업로드 (type='manager' 전용)
	// POST /api/report/upload/signed (multipart)
	// =====================================================================
	async function uploadSignedFiles(files, reportIds) {
		const formData = new FormData();
		files.forEach((f) => formData.append('files', f));
		reportIds.forEach((id) => formData.append('reportIds', id));

		const res = await fetch('/api/report/upload/signed', {
			method: 'POST',
			body: formData,
		});
		if (!res.ok) throw res;
		const resData = await res.json();
		if (!resData || resData.code <= 0) throw new Error(resData.msg ?? '파일 업로드 중 오류가 발생했습니다.');
		return resData.data;
	}

	// =====================================================================
	// 드래그앤드롭 이벤트 바인딩
	// =====================================================================
	const $dropZone = $('#uploadDropZone', $modal);
	const $fileInput = $('#reportUploadInput', $modal);

	$dropZone
		.on('dragover dragenter', function (e) {
			e.preventDefault();
			e.stopPropagation();
			$(this).addClass('drag-over');
		})
		.on('dragleave dragend', function (e) {
			e.preventDefault();
			e.stopPropagation();
			$(this).removeClass('drag-over');
		})
		.on('drop', function (e) {
			e.preventDefault();
			e.stopPropagation();
			$(this).removeClass('drag-over');
			const files = e.originalEvent.dataTransfer.files;
			if (files && files.length > 0) applyFiles(files);
		})
		.on('click', function (e) {
			// 파일선택 label 클릭 시 중복 트리거 방지
			if ($(e.target).closest('label').length) return;
			$fileInput.trigger('click');
		});

	// file input change
	$fileInput.on('change', function () {
		if (this.files && this.files.length > 0) {
			applyFiles(this.files);
			this.value = ''; // 같은 파일 재선택 허용
		}
	});

	// =====================================================================
	// 취소 버튼: input 초기화 + 파일 목록 리셋
	// =====================================================================
	$modal.on('click', '.btnUploadCancel', function () {
		selectedFiles = [];
		renderFileList([]);
		$fileInput.val('');
	});

	// =====================================================================
	// 유효성 검증 공통 함수 (결재/업로드 공용)
	// POST /api/report/upload/validate { reportNums: [...], mode: 'approval'|'upload' }
	// 반환: { valid: [{id, reportNum}], invalid: [{reportNum, reason}] }
	// =====================================================================
	async function validateUploadFiles(files, mode) {
		const reportNums = files.map((f) => f.name.replace(/\.[^/.]+$/, ''));
		const res = await fetch('/api/report/upload/validate', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json; charset=utf-8' },
			body: JSON.stringify({ reportNums, mode }),
		});
		if (!res.ok) throw res;
		const resData = await res.json();
		if (!resData || resData.code <= 0) throw new Error(resData.msg ?? '검증 중 오류가 발생했습니다.');
		return resData.data; // { valid, invalid }
	}

	// =====================================================================
	// 원본 파일 업로드 공통 함수
	// POST /api/report/upload/origin (multipart)
	// files: File[] (유효한 것만), reportIds: number[]
	// =====================================================================
	async function uploadOriginFiles(files, reportIds) {
		const formData = new FormData();
		files.forEach((f) => formData.append('files', f));
		reportIds.forEach((id) => formData.append('reportIds', id));

		const res = await fetch('/api/report/upload/origin', {
			method: 'POST',
			body: formData,
		});
		if (!res.ok) throw res;
		const resData = await res.json();
		if (!resData || resData.code <= 0) throw new Error(resData.msg ?? '파일 업로드 중 오류가 발생했습니다.');
		return resData.data; // { uploadedIds: [...] }
	}

	// =====================================================================
	// 유효성 검증 결과 표시 + 진행 여부 확인 공통 함수
	// valid, invalid 목록을 받아 SweetAlert로 표시 후 confirm 결과 반환
	// proceedLabel: 진행 버튼 텍스트 ('결재' 또는 '업로드')
	// =====================================================================
	async function confirmWithValidationResult(valid, invalid, proceedLabel) {
		if (!valid || valid.length === 0) {
			// 전부 실패
			const failHtml = (invalid ?? []).map((i) => `<li><strong>${i.reportNum}</strong>: ${i.reason}</li>`).join('');
			await gMessage(
				'처리 불가',
				`<ul class="text-start" style="max-height:200px; overflow-y:auto; font-size:0.85em;">${failHtml}</ul>`,
				'error',
				'alert',
			);
			return false;
		}

		if (invalid && invalid.length > 0) {
			// 일부 실패 → 제외하고 진행 여부 확인
			const failHtml = invalid.map((i) => `<li><strong>${i.reportNum}</strong>: ${i.reason}</li>`).join('');
			const result = await gMessage(
				'일부 처리 불가',
				`<p style="font-size:0.9em;">아래 <strong>${invalid.length}건</strong>은 처리가 불가능하여 제외됩니다.</p>` +
					`<ul class="text-start" style="max-height:160px; overflow-y:auto; font-size:0.85em;">${failHtml}</ul>` +
					`<p style="font-size:0.9em; margin-top:8px;"><strong>${valid.length}건</strong>에 대해 ${proceedLabel}을 진행하시겠습니까?</p>`,
				'warning',
				'confirm',
				{ confirmButtonText: proceedLabel },
			);
			return result.isConfirmed;
		}

		// 전부 통과 → 바로 confirm
		const result = await gMessage(
			`성적서 ${proceedLabel}`,
			`${valid.length}건에 대해 ${proceedLabel}을 진행하시겠습니까?`,
			'question',
			'confirm',
			{ confirmButtonText: proceedLabel },
		);
		return result.isConfirmed;
	}

	// =====================================================================
	// 결재 버튼: 원본 교체 후 WORK_APPROVAL 배치 생성 → 폴링
	// =====================================================================
	$modal.on('click', '.btnUploadApproval', async function () {
		if (selectedFiles.length === 0) {
			gToast('업로드할 파일을 먼저 선택해 주세요.', 'warning');
			return;
		}

		// 1. 유효성 검증
		let validateResult;
		try {
			gLoadingMessage('유효성 검증 중...');
			validateResult = await validateUploadFiles(selectedFiles, 'approval');
			swal.close();
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			return;
		}

		const { valid, invalid } = validateResult;

		// 2. 결과 표시 + 진행 여부 확인
		const proceed = await confirmWithValidationResult(valid, invalid, '결재');
		if (!proceed) return;

		// 3. 유효 파일만 origin 업로드
		const validNums = new Set(valid.map((v) => v.reportNum));
		const validFiles = selectedFiles.filter((f) => validNums.has(f.name.replace(/\.[^/.]+$/, '')));
		const reportIds = valid.map((v) => v.id);

		try {
			gLoadingMessage('원본 파일 업로드 중...');
			await uploadOriginFiles(validFiles, reportIds);
			swal.close();
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			return;
		}

		// 4. 파일 목록 초기화
		selectedFiles = [];
		renderFileList([]);

		// 5. 결재 배치 생성 + 폴링
		await doWorkApprovalFromModal(reportIds);
	});

	// =====================================================================
	// 업로드 버튼: 원본 파일 교체(work) 또는 signed 파일 교체(manager)
	// =====================================================================
	$modal.on('click', '.btnUploadOnly', async function () {
		if (selectedFiles.length === 0) {
			gToast('업로드할 파일을 먼저 선택해 주세요.', 'warning');
			return;
		}

		const isManager = uploadType === 'manager';
		const mode = isManager ? 'manager' : 'upload';
		const proceedLabel = isManager ? '파일 교체' : '업로드';

		// 1. 유효성 검증
		let validateResult;
		try {
			gLoadingMessage('유효성 검증 중...');
			validateResult = await validateUploadFiles(selectedFiles, mode);
			swal.close();
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			return;
		}

		const { valid, invalid } = validateResult;

		// 2. 결과 표시 + 진행 여부 확인
		const proceed = await confirmWithValidationResult(valid, invalid, proceedLabel);
		if (!proceed) return;

		// 3. 유효 파일만 업로드 (타입에 따라 엔드포인트 분기)
		const validNums = new Set(valid.map((v) => v.reportNum));
		const validFiles = selectedFiles.filter((f) => validNums.has(f.name.replace(/\.[^/.]+$/, '')));
		const reportIds = valid.map((v) => v.id);

		try {
			gLoadingMessage(`${proceedLabel} 중...`);
			if (isManager) {
				await uploadSignedFiles(validFiles, reportIds);
			} else {
				await uploadOriginFiles(validFiles, reportIds);
			}
			swal.close();
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			return;
		}

		selectedFiles = [];
		renderFileList([]);

		const completeTitle = isManager ? '파일 교체 완료' : '업로드 완료';
		const completeMsg = isManager
			? `${reportIds.length}건의 signed 파일이 교체되었습니다.`
			: `${reportIds.length}건의 원본 파일이 교체되었습니다.`;
		await gMessage(completeTitle, completeMsg, 'success', 'alert');
	});

	// =====================================================================
	// doWorkApprovalFromModal: 업로드 모달에서 결재 배치 생성 → ExcelWorkApp 실행 → 백그라운드 폴링
	// ExcelWork 방식: workApproval.js의 doWorkApproval과 동일한 패턴, confirm 생략
	// 완료 시 모달 닫기 → 부모 workApproval.js에서 그리드 재조회
	// =====================================================================
	async function doWorkApprovalFromModal(reportIds) {
		if (!reportIds || reportIds.length === 0) return;

		// ── Step 1. 배치 생성 (ExcelWork 방식) ────────────────────────────────
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

		// ── Step 2. ExcelWorkApp 실행 ─────────────────────────────────────────
		// excelwork:// URI로 로컬 ExcelWorkApp을 기동.
		// ExcelWorkApp이 서명 삽입 + PDF 변환 + 업로드를 처리하며 앱 내 작업 목록에 진행상황 표시.
		window.location.href = excelworkUri;

		// ── Step 3. 백그라운드 폴링 — 완료 시 모달 닫기 ───────────────────────
		// 진행상황은 ExcelWorkApp 작업 목록에서 확인.
		const MAX_POLL_COUNT = 120; // 최대 10분 (5초 × 120회)
		const POLL_INTERVAL  = 5000;

		for (let pollCount = 0; pollCount < MAX_POLL_COUNT; pollCount++) {
			await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL));

			try {
				const pollRes = await fetch(`/api/report/jobs/batches/${batchId}`);
				if (!pollRes.ok) continue;
				const pollData = await pollRes.json();
				if (!pollData || pollData.code <= 0) continue;

				const batch = pollData.data;

				if (['SUCCESS', 'FAIL', 'CANCELED'].includes(batch.status)) {
					if (batch.status === 'SUCCESS') {
						const icon = batch.failCount > 0 ? 'warning' : 'success';
						await gMessage('실무자결재 완료', `성공 ${batch.successCount}건 / 실패 ${batch.failCount}건`, icon, 'alert');
					} else if (batch.status === 'FAIL') {
						await gMessage('실무자결재 실패', `${batch.failCount}건 처리 실패`, 'error', 'alert');
					} else {
						await gMessage('작업 취소', '작업이 취소되었습니다.', 'info', 'alert');
					}

					// 모달 닫기 → 부모 workApproval.js에서 그리드 재조회
					$modal_root.modal('hide');
					return;
				}
			} catch (pollErr) {
				console.warn('[reportUpload] 폴링 오류:', pollErr);
			}
		}

		// 시간 초과
		await gMessage('시간 초과', '작업 진행상황을 확인할 수 없습니다.<br>잠시 후 다시 확인해 주세요.', 'warning', 'alert');
	}

	// =====================================================================
	// 페이지 마운트 처리 (common.js 규약)
	// =====================================================================
	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		// gModal이 .modal-body.data('param')에 저장한 param을 읽어 init_modal 호출
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