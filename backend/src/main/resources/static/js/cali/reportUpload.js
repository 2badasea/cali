$(function () {
	console.log('++ cali/reportUpload.js');

	const $modal = $('.modal-view:not(.modal-view-applied)').first();
	const $dropZone = $('#uploadDropZone', $modal);
	const $fileInput = $('#reportUploadInput', $modal);

	// 현재 선택된 File 객체 배열
	let selectedFiles = [];

	// =====================================================================
	// 파일 목록 UI 업데이트
	// =====================================================================
	function renderFileList(files) {
		const $list = $('#fileNameList', $modal);
		const $count = $('#fileCount', $modal);

		$list.empty();
		files.forEach((f) => {
			$list.append(
				`<li>
					<i class="bi bi-file-earmark-excel"></i>
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
			return ext === 'xlsx' || ext === 'xls';
		});

		if (valid.length < files.length) {
			gToast('엑셀 파일(.xlsx, .xls)만 업로드 가능합니다. 다른 형식의 파일은 제외되었습니다.', 'warning');
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
	// 드래그앤드롭 이벤트 바인딩
	// =====================================================================
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
			customAjaxHandler(err);
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
			customAjaxHandler(err);
			return;
		}

		// 4. 파일 목록 초기화
		selectedFiles = [];
		renderFileList([]);

		// 5. 결재 배치 생성 + 폴링
		// workMemberId 기준 그룹화가 필요하나, 업로드 모달에서는 reportId만 알고 있으므로
		// 단일 배치로 처리 (백엔드 createWorkApprovalBatch 에서 실무자 그룹 검증)
		await doWorkApprovalFromModal(reportIds);
	});

	// =====================================================================
	// 업로드 버튼: 원본 파일 교체만 수행
	// =====================================================================
	$modal.on('click', '.btnUploadOnly', async function () {
		if (selectedFiles.length === 0) {
			gToast('업로드할 파일을 먼저 선택해 주세요.', 'warning');
			return;
		}

		// 1. 유효성 검증
		let validateResult;
		try {
			gLoadingMessage('유효성 검증 중...');
			validateResult = await validateUploadFiles(selectedFiles, 'upload');
			swal.close();
		} catch (err) {
			swal.close();
			customAjaxHandler(err);
			return;
		}

		const { valid, invalid } = validateResult;

		// 2. 결과 표시 + 진행 여부 확인
		const proceed = await confirmWithValidationResult(valid, invalid, '업로드');
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
			customAjaxHandler(err);
			return;
		}

		selectedFiles = [];
		renderFileList([]);

		await gMessage('업로드 완료', `${reportIds.length}건의 원본 파일이 교체되었습니다.`, 'success', 'alert');
	});

	// =====================================================================
	// doWorkApprovalFromModal: 업로드 모달에서 결재 배치 생성 + 폴링
	// workApproval.js의 doWorkApproval과 동일한 폴링 로직, confirm 생략
	// =====================================================================
	async function doWorkApprovalFromModal(reportIds) {
		if (!reportIds || reportIds.length === 0) return;

		// 배치 생성
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

		// 폴링 UI
		const STEP_LABEL = {
			DOWNLOADING_ORIGIN: 'origin 다운로드',
			INSERTING_SIGN: '서명 삽입',
			CONVERTING_PDF: 'PDF 변환',
			UPLOADING_SIGNED: '파일 업로드',
			DONE: '완료',
		};

		const buildProgressHtml = (batch) => {
			const total = batch.totalCount ?? 0;
			const success = batch.successCount ?? 0;
			const fail = batch.failCount ?? 0;
			const done = success + fail;
			const pct = total > 0 ? Math.round((done / total) * 100) : 0;

			const itemRows = (batch.items ?? [])
				.map((item) => {
					const badge =
						item.status === 'SUCCESS'
							? '<span class="badge bg-success">완료</span>'
							: item.status === 'FAIL'
								? '<span class="badge bg-danger">실패</span>'
								: item.status === 'PROGRESS'
									? `<span class="badge bg-primary">${STEP_LABEL[item.step] ?? item.step ?? '처리중'}</span>`
									: '<span class="badge bg-secondary">대기</span>';
					const failMsg = item.message ? `<br><small class="text-danger">${item.message}</small>` : '';
					return `<tr>
					<td class="text-start" style="font-size:0.85em;">성적서 #${item.reportId}</td>
					<td>${badge}${failMsg}</td>
				</tr>`;
				})
				.join('');

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
			html: '<div id="uploadApprovalProgressBody">준비 중...</div>',
			allowOutsideClick: false,
			allowEscapeKey: false,
			showConfirmButton: false,
			didOpen: () => {
				Swal.showLoading();
			},
		});

		const POLL_INTERVAL_MS = 5000;
		const MAX_POLL_COUNT = 60;
		let pollCount = 0;

		while (pollCount < MAX_POLL_COUNT) {
			await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
			pollCount++;

			try {
				const pollRes = await fetch(`/api/report/jobs/batches/${batchId}`);
				if (!pollRes.ok) continue;
				const pollData = await pollRes.json();
				if (!pollData || pollData.code <= 0) continue;

				const batch = pollData.data;
				const progressEl = document.getElementById('uploadApprovalProgressBody');
				if (progressEl) progressEl.innerHTML = buildProgressHtml(batch);

				if (['SUCCESS', 'FAIL', 'CANCELED'].includes(batch.status)) {
					Swal.hideLoading();

					if (batch.status === 'SUCCESS') {
						const icon = batch.failCount > 0 ? 'warning' : 'success';
						await gMessage('실무자결재 완료', `성공 ${batch.successCount}건 / 실패 ${batch.failCount}건`, icon, 'alert');
					} else if (batch.status === 'FAIL') {
						await gMessage('실무자결재 실패', `${batch.failCount}건 처리 실패`, 'error', 'alert');
					} else {
						await gMessage('작업 취소', '작업이 취소되었습니다.', 'info', 'alert');
					}
					break;
				}
			} catch (pollErr) {
				console.warn('[reportUpload] Polling 오류:', pollErr);
			}
		}

		if (pollCount >= MAX_POLL_COUNT) {
			Swal.close();
			await gMessage('시간 초과', '작업 진행상황을 확인할 수 없습니다.<br>잠시 후 다시 확인해 주세요.', 'warning', 'alert');
		}
	}

	// =====================================================================
	// 페이지 마운트 처리 (common.js 규약)
	// =====================================================================
	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		const $modal_root = $modal.closest('.modal');
		$modal_root.on('modal_ready', function (e, p) {
			if (typeof $modal.grid === 'object') $modal.grid.refreshLayout();
		});
	}

	if (typeof window.modal_deferred === 'object') {
		window.modal_deferred.resolve('script end');
	}
});
