$(function () {
	console.log('++ cali/agcyReportModify.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	let reportId = null;
	let smallItemCodeSetObj = {};

	// 접수구분 한글 레이블 변환
	const ORDER_TYPE_LABEL = {
		ACCREDDIT: '공인',
		UNACCREDDIT: '비공인',
		TESTING: '시험',
	};

	// 진행상태 한글 레이블 변환 (AGCY 전용)
	const AGCY_STATUS_LABEL = {
		NORMAL: '대기',
		SUCCESS: '완료',
		CANCEL: '취소',
	};

	$modal.init_modal = async (param) => {
		$modal.param = param;
		reportId = $modal.param.id;

		// 1. 중/소분류 데이터 로드
		try {
			const codeRes = await gAjax('/api/basic/getItemCodeInfos', {}, { type: 'GET' });
			if (codeRes?.code > 0) {
				const itemCodeSet = codeRes.data;
				const middleList = itemCodeSet.middleCodeInfos ?? [];
				const smallObj = itemCodeSet.smallCodeInfos ?? {};

				smallItemCodeSetObj = smallObj;

				// 중분류 select 구성
				const $middle = $('.middleItemCodeId', $modal);
				$middle.append(new Option('선택', ''));
				middleList.forEach(m => $middle.append(new Option(m.codeNum, m.id)));

				// 중분류 변경 시 소분류 select 갱신
				$middle.on('change', function () {
					$modal.renderSmallCodes($(this).val());
				});
			}
		} catch (err) {
			console.error('코드 정보 로드 실패', err);
		}

		// 2. 성적서 상세 로드
		try {
			const detailRes = await gAjax(
				`/api/report/getAgcyReportDetail?id=${reportId}`,
				{},
				{ type: 'GET' }
			);
			if (detailRes?.code > 0) {
				const d = detailRes.data;
				$modal.fillForm(d);
			} else {
				await gMessage('오류', detailRes?.msg ?? '성적서 정보를 불러올 수 없습니다.', 'error');
			}
		} catch (err) {
			await gApiErrorHandler(err);
		}

		// 3. 파일 업로드 이벤트
		$modal
			// EXCEL 업로드 버튼 → 파일 선택 창 오픈
			.on('click', '.btnUploadExcel', function () {
				$('.uploadExcel', $modal).val('').click();
			})
			// PDF 업로드 버튼 → 파일 선택 창 오픈
			.on('click', '.btnUploadPdf', function () {
				$('.uploadPdf', $modal).val('').click();
			})
			// EXCEL 파일 선택 → 확장자 검증 및 표시
			.on('change', '.uploadExcel', function () {
				const file = this.files?.[0];
				if (!file) return;
				const ext = file.name.split('.').pop().toLowerCase();
				if (ext !== 'xlsx') {
					gToast('xlsx 파일만 업로드 가능합니다.', 'warning');
					$(this).val('');
					return;
				}
				$modal.markSelectedFile('excel', file.name);
			})
			// PDF 파일 선택 → 확장자 검증 및 표시
			.on('change', '.uploadPdf', function () {
				const file = this.files?.[0];
				if (!file) return;
				const ext = file.name.split('.').pop().toLowerCase();
				if (ext !== 'pdf') {
					gToast('pdf 파일만 업로드 가능합니다.', 'warning');
					$(this).val('');
					return;
				}
				$modal.markSelectedFile('pdf', file.name);
			})
			// 파일 보기 → fileList 모달 오픈
			.on('click', '.searchFile', async function () {
				await gModal(
					'/basic/fileList',
					{ refTableName: 'report', refTableId: reportId },
					{ size: 'lg', title: '첨부파일 확인', show_close_button: true, show_confirm_button: false }
				);
			});
	};

	// 폼 필드 채우기
	$modal.fillForm = (d) => {
		$('.reportId', $modal).val(d.id);
		$('.itemId', $modal).val(d.itemId);
		$('.custAgent', $modal).val(d.custAgent);
		$('.reportAgent', $modal).val(d.reportAgent);
		$('.agcySelfReportNum', $modal).val(d.agcySelfReportNum);
		$('.reportNum', $modal).val(d.reportNum);
		$('.agcyAgent', $modal).val(d.agcyAgent);
		$('.orderTypeDisplay', $modal).val(ORDER_TYPE_LABEL[d.orderType] ?? d.orderType);
		$('.caliDate', $modal).val(d.caliDate ?? '');
		$('.reportStatusDisplay', $modal).val(AGCY_STATUS_LABEL[d.reportStatus] ?? d.reportStatus ?? '대기');
		$('.itemName', $modal).val(d.itemName);
		$('.itemNameEn', $modal).val(d.itemNameEn);
		$('.itemMakeAgent', $modal).val(d.itemMakeAgent);
		$('.itemMakeAgentEn', $modal).val(d.itemMakeAgentEn);
		$('.itemFormat', $modal).val(d.itemFormat);
		$('.itemNum', $modal).val(d.itemNum);
		$('.caliFee', $modal).val(d.caliFee);
		$('.remark', $modal).val(d.remark);

		// 중분류 세팅 → 소분류 렌더 후 선택
		if (d.middleItemCodeId) {
			$('.middleItemCodeId', $modal).val(d.middleItemCodeId).trigger('change');
			// change 이후 소분류 세팅 (renderSmallCodes가 동기적으로 DOM을 갱신하므로 nextTick)
			setTimeout(() => {
				if (d.smallItemCodeId) {
					$('.smallItemCodeId', $modal).val(d.smallItemCodeId);
				}
			}, 50);
		}
	};

	// 소분류 select 재구성 (middleId 기준)
	$modal.renderSmallCodes = (middleId) => {
		const $small = $('.smallItemCodeId', $modal);
		$small.empty().append(new Option('선택', ''));
		const list = smallItemCodeSetObj[String(middleId)] ?? [];
		list.forEach(s => $small.append(new Option(s.codeNum, s.id)));
		$small.val('');
	};

	// 선택된 파일 표시
	$modal.markSelectedFile = (type, fileName) => {
		const $info = $('.selectedFileInfo', $modal);
		// 같은 타입의 기존 표시 제거 후 추가
		$info.find(`[data-type="${type}"]`).remove();
		const badgeClass = type === 'excel' ? 'badge-success' : 'badge-danger';
		$info.append(`<span class="badge ${badgeClass}" data-type="${type}">${fileName}</span> `);
	};

	// 저장
	$modal.confirm_modal = async function () {
		const itemName = $('.itemName', $modal).val()?.trim();
		if (!itemName) {
			gToast('기기명은 필수입니다.', 'warning');
			return false;
		}

		const confirmRes = await gMessage('대행성적서 수정', '저장하시겠습니까?', 'question', 'confirm');
		if (!confirmRes.isConfirmed) return false;

		gLoadingMessage();
		try {
			// 1. 성적서 기본 정보 저장
			const caliDateVal = $('.caliDate', $modal).val();
			const sendData = {
				id: Number($('.reportId', $modal).val()),
				reportNum: $('.reportNum', $modal).val()?.trim() || null,
				agcyAgent: $('.agcyAgent', $modal).val()?.trim() || null,
				middleItemCodeId: Number($('.middleItemCodeId', $modal).val()) || null,
				smallItemCodeId: Number($('.smallItemCodeId', $modal).val()) || null,
				itemId: Number($('.itemId', $modal).val()) || null,
				itemName: itemName,
				itemNameEn: $('.itemNameEn', $modal).val()?.trim() || null,
				itemMakeAgent: $('.itemMakeAgent', $modal).val()?.trim() || null,
				itemMakeAgentEn: $('.itemMakeAgentEn', $modal).val()?.trim() || null,
				itemFormat: $('.itemFormat', $modal).val()?.trim() || null,
				itemNum: $('.itemNum', $modal).val()?.trim() || null,
				caliFee: Number($('.caliFee', $modal).val()) || 0,
				remark: $('.remark', $modal).val()?.trim() || null,
				caliDate: caliDateVal || null,
			};

			const response = await fetch('/api/report/updateAgcyReport', {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify(sendData),
			});
			if (!response.ok) throw response;
			const res = await response.json();

			if (res?.code <= 0) {
				await gMessage('저장 실패', res?.msg ?? '저장에 실패했습니다.', 'warning');
				return false;
			}

			// 2. EXCEL 파일 업로드 (선택된 경우)
			const xlsxFile = $('.uploadExcel', $modal)[0]?.files?.[0];
			if (xlsxFile) {
				const fd = new FormData();
				fd.append('file', xlsxFile);
				const xlsxRes = await fetch(`/api/report/agcyUploadFile?reportId=${reportId}`, {
					method: 'POST',
					body: fd,
				});
				const xlsxJson = await xlsxRes.json();
				if (xlsxJson?.code <= 0) {
					gToast(`EXCEL 업로드 실패: ${xlsxJson?.msg ?? '오류'}`, 'warning');
				}
			}

			// 3. PDF 파일 업로드 (선택된 경우)
			const pdfFile = $('.uploadPdf', $modal)[0]?.files?.[0];
			if (pdfFile) {
				const fd = new FormData();
				fd.append('file', pdfFile);
				const pdfRes = await fetch(`/api/report/agcyUploadFile?reportId=${reportId}`, {
					method: 'POST',
					body: fd,
				});
				const pdfJson = await pdfRes.json();
				if (pdfJson?.code <= 0) {
					gToast(`PDF 업로드 실패: ${pdfJson?.msg ?? '오류'}`, 'warning');
				}
			}

			await gMessage('대행성적서 수정', res.msg ?? '저장되었습니다.', 'success');
			$modal_root.modal('hide');
			return true;
		} catch (err) {
			await gApiErrorHandler(err);
		} finally {
			Swal.close();
		}
		return false;
	};

	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		setTimeout(() => {
			const p = $modal.data('param') || {};
			$modal.init_modal(p);
			if (typeof $modal.grid == 'object') $modal.grid.refreshLayout();
		}, 200);
	}

	if (typeof window.modal_deferred == 'object') {
		window.modal_deferred.resolve('script end');
	} else {
		if (!$modal_root.length) initPage($modal);
	}
});
