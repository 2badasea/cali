$(function () {
	console.log('++ cali/agcyReportModify.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	let reportId = null;
	let smallItemCodeSetObj = {};

	// 허용 Excel 확장자
	const EXCEL_EXTS = ['xlsx', 'xls', 'xlsm', 'xlsb'];

	// 진행상태 한글 레이블 (AGCY 전용)
	const AGCY_STATUS_LABEL = {
		NORMAL: '대기',
		SUCCESS: '완료',
		CANCEL: '취소',
	};

	// =========================================================
	// 업체 조회: agentFlag=0 → 업체형태 전체
	// =========================================================
	$modal.searchAgcyAgent = async (agentName) => {
		const resModal = await gModal(
			'/agent/searchAgentModify',
			{ agentFlag: 0, agentName: agentName },
			{
				title: '업체 조회',
				size: 'xxl',
				show_close_button: true,
				show_confirm_button: false,
				custom_btn_html_arr: [
					`<button type="button" class="btn btn-primary addAgent btn-sm"><i class="bi bi-plus-square"></i>업체등록</button>`,
				],
			}
		);
		if (resModal && resModal.returnData != null) {
			$('.agcyAgent', $modal).val(resModal.returnData.name);
		}
	};

	// =========================================================
	// 기기 조회: 현재 폼 값을 rowData로 전달 → searchItemList 모달
	// =========================================================
	$modal.searchItem = async () => {
		const rowData = {
			middleItemCodeId: $('.middleItemCodeId', $modal).val() || '',
			smallItemCodeId: $('.smallItemCodeId', $modal).val() || '',
			itemId: $('.itemId', $modal).val() || null,
			itemName: $('.itemName', $modal).val() || '',
			itemNameEn: $('.itemNameEn', $modal).val() || '',
			itemMakeAgent: $('.itemMakeAgent', $modal).val() || '',
			itemMakeAgentEn: $('.itemMakeAgentEn', $modal).val() || '',
			itemFormat: $('.itemFormat', $modal).val() || '',
			itemNum: $('.itemNum', $modal).val() || '',
		};
		const resModal = await gModal('/basic/searchItemList', rowData, {
			size: 'xxxl',
			title: '교정 품목 리스트',
			show_close_button: true,
		});
		if (resModal && resModal.jsonData != null) {
			const d = resModal.jsonData;
			// 중분류 → change 트리거 → 소분류 렌더 후 선택
			if (d.middleItemCodeId) {
				$('.middleItemCodeId', $modal).val(String(d.middleItemCodeId)).trigger('change');
				setTimeout(() => {
					if (d.smallItemCodeId) $('.smallItemCodeId', $modal).val(String(d.smallItemCodeId));
				}, 50);
			}
			$('.itemId', $modal).val(d.id ?? '');
			$('.itemName', $modal).val(d.name ?? '');
			$('.itemNameEn', $modal).val(d.nameEn ?? '');
			$('.itemMakeAgent', $modal).val(d.makeAgent ?? '');
			$('.itemMakeAgentEn', $modal).val(d.makeAgentEn ?? '');
			$('.itemFormat', $modal).val(d.format ?? '');
			$('.itemNum', $modal).val(d.num ?? '');
			if (d.fee != null) {
				$('.caliFee', $modal).val(numberFormat(d.fee));
			}
		}
	};

	// =========================================================
	// 취소/초기화 버튼 — 모달 footer에 동적 삽입 (중복 방지: 먼저 제거 후 prepend)
	// =========================================================
	$modal_root.on('click', '.modal-btn-agcy-cancel', async function () {
		if ($('.reportStatusDisplay', $modal).val() === '취소') {
			gToast('이미 취소 상태입니다.', 'warning');
			return;
		}
		const confirmRes = await gMessage('성적서 취소', '해당 대행성적서를 취소 처리하시겠습니까?', 'question', 'confirm');
		if (!confirmRes.isConfirmed) return;
		gLoadingMessage();
		try {
			const res = await fetch('/api/report/agcyUpdateStatus', {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ id: Number($('.reportId', $modal).val()), status: 'CANCEL' }),
			});
			if (!res.ok) throw res;
			const json = await res.json();
			if (json?.code > 0) {
				$('.reportStatusDisplay', $modal).val('취소');
				await gMessage('취소 처리', json.msg ?? '취소 처리되었습니다.', 'success');
			} else {
				await gMessage('처리 실패', json?.msg ?? '처리에 실패했습니다.', 'warning');
			}
		} catch (err) {
			await gApiErrorHandler(err);
		} finally {
			Swal.close();
		}
	});

	$modal_root.on('click', '.modal-btn-agcy-reset', async function () {
		if ($('.reportStatusDisplay', $modal).val() === '대기') {
			gToast('이미 대기 상태입니다.', 'warning');
			return;
		}
		const confirmRes = await gMessage('상태 초기화', '진행상태를 대기로 초기화하시겠습니까?', 'question', 'confirm');
		if (!confirmRes.isConfirmed) return;
		gLoadingMessage();
		try {
			const res = await fetch('/api/report/agcyUpdateStatus', {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ id: Number($('.reportId', $modal).val()), status: 'NORMAL' }),
			});
			if (!res.ok) throw res;
			const json = await res.json();
			if (json?.code > 0) {
				$('.reportStatusDisplay', $modal).val('대기');
				await gMessage('초기화', json.msg ?? '초기화되었습니다.', 'success');
			} else {
				await gMessage('처리 실패', json?.msg ?? '처리에 실패했습니다.', 'warning');
			}
		} catch (err) {
			await gApiErrorHandler(err);
		} finally {
			Swal.close();
		}
	});

	$modal.init_modal = async (param) => {
		$modal.param = param;
		reportId = $modal.param.id;

		// footer에 취소/초기화 버튼 삽입 (중복 방지: 먼저 제거 후 prepend)
		const $footer = $modal_root.find('.modal-footer');
		$footer.find('.modal-btn-agcy-cancel, .modal-btn-agcy-reset').remove();
		$footer.prepend(
			'<button type="button" class="btn btn-danger btn-sm modal-btn-agcy-cancel">취소</button>' +
			'<button type="button" class="btn btn-warning btn-sm modal-btn-agcy-reset" style="margin-left:4px;">초기화</button>'
		);

		// 1. 중/소분류 데이터 로드
		try {
			const codeRes = await gAjax('/api/basic/getItemCodeInfos', {}, { type: 'GET' });
			if (codeRes?.code > 0) {
				const itemCodeSet = codeRes.data;
				const middleList = itemCodeSet.middleCodeInfos ?? [];
				const smallObj = itemCodeSet.smallCodeInfos ?? {};

				smallItemCodeSetObj = smallObj;

				const $middle = $('.middleItemCodeId', $modal);
				$middle.append(new Option('선택', ''));
				middleList.forEach(m => $middle.append(new Option(m.codeNum, m.id)));

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
				$modal.fillForm(detailRes.data);
			} else {
				await gMessage('오류', detailRes?.msg ?? '성적서 정보를 불러올 수 없습니다.', 'error');
			}
		} catch (err) {
			await gApiErrorHandler(err);
		}

		// 3. 이벤트 바인딩
		$modal
			// EXCEL 업로드
			.on('click', '.btnUploadExcel', function () {
				$('.uploadExcel', $modal).val('').click();
			})
			// PDF 업로드
			.on('click', '.btnUploadPdf', function () {
				$('.uploadPdf', $modal).val('').click();
			})
			// Excel 파일 선택 → 확장자 검증
			.on('change', '.uploadExcel', function () {
				const file = this.files?.[0];
				if (!file) return;
				const ext = file.name.split('.').pop().toLowerCase();
				if (!EXCEL_EXTS.includes(ext)) {
					gToast('xlsx, xls, xlsm, xlsb 파일만 업로드 가능합니다.', 'warning');
					$(this).val('');
					return;
				}
				$modal.markSelectedFile('excel', file.name);
			})
			// PDF 파일 선택 → 확장자 검증
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
			// 파일 보기
			.on('click', '.searchFile', async function () {
				await gModal(
					'/basic/fileList',
					{ refTableName: 'report', refTableId: reportId },
					{ size: 'lg', title: '첨부파일 확인', show_close_button: true, show_confirm_button: false }
				);
			})
			// 대행의뢰처 조회 버튼
			.on('click', '.searchAgcyAgent', function () {
				$modal.searchAgcyAgent($('.agcyAgent', $modal).val()?.trim() ?? '');
			})
			// 대행의뢰처 Enter
			.on('keydown', '.agcyAgentInput', function (e) {
				if (e.key === 'Enter' || e.keyCode === 13) {
					e.preventDefault();
					$modal.searchAgcyAgent($(this).val()?.trim() ?? '');
				}
			})
			// 기기명 조회 버튼
			.on('click', '.searchItem', function () {
				$modal.searchItem();
			})
			// 기기명 Enter
			.on('keydown', '.itemNameInput', function (e) {
				if (e.key === 'Enter' || e.keyCode === 13) {
					e.preventDefault();
					$modal.searchItem();
				}
			});
	};

	// 폼 필드 채우기
	$modal.fillForm = (d) => {
		$('.reportId', $modal).val(d.id);
		$('.itemId', $modal).val(d.itemId ?? '');
		$('.custAgent', $modal).val(d.custAgent);
		$('.reportAgent', $modal).val(d.reportAgent);
		$('.agcySelfReportNum', $modal).val(d.agcySelfReportNum);
		$('.reportNum', $modal).val(d.reportNum ?? '');
		$('.agcyAgent', $modal).val(d.agcyAgent ?? '');
		$('.orderType', $modal).val(d.orderType ?? 'ACCREDDIT');
		$('.caliDate', $modal).val(d.caliDate ?? '');
		$('.reportStatusDisplay', $modal).val(AGCY_STATUS_LABEL[d.reportStatus] ?? d.reportStatus ?? '대기');
		$('.itemName', $modal).val(d.itemName ?? '');
		$('.itemNameEn', $modal).val(d.itemNameEn ?? '');
		$('.itemMakeAgent', $modal).val(d.itemMakeAgent ?? '');
		$('.itemMakeAgentEn', $modal).val(d.itemMakeAgentEn ?? '');
		$('.itemFormat', $modal).val(d.itemFormat ?? '');
		$('.itemNum', $modal).val(d.itemNum ?? '');
		// caliFee: 천단위 콤마 표시, 우측정렬은 HTML에서 처리
		$('.caliFee', $modal).val(d.caliFee != null ? numberFormat(d.caliFee) : '');
		$('.remark', $modal).val(d.remark ?? '');

		// 중분류 세팅 → change 트리거 → 소분류 렌더 후 선택
		if (d.middleItemCodeId) {
			$('.middleItemCodeId', $modal).val(d.middleItemCodeId).trigger('change');
			setTimeout(() => {
				if (d.smallItemCodeId) $('.smallItemCodeId', $modal).val(d.smallItemCodeId);
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
			// reportNum: 항상 trim 후 전달 (빈 문자열 허용 — 서버에서 상태 자동 판별)
			const reportNum = ($('.reportNum', $modal).val() ?? '').trim();

			const sendData = {
				id: Number($('.reportId', $modal).val()),
				reportNum: reportNum,
				agcyAgent: $('.agcyAgent', $modal).val()?.trim() || null,
				orderType: $('.orderType', $modal).val() || 'ACCREDDIT',
				middleItemCodeId: Number($('.middleItemCodeId', $modal).val()) || null,
				smallItemCodeId: Number($('.smallItemCodeId', $modal).val()) || null,
				itemId: Number($('.itemId', $modal).val()) || null,
				itemName: itemName,
				itemNameEn: $('.itemNameEn', $modal).val()?.trim() || null,
				itemMakeAgent: $('.itemMakeAgent', $modal).val()?.trim() || null,
				itemMakeAgentEn: $('.itemMakeAgentEn', $modal).val()?.trim() || null,
				itemFormat: $('.itemFormat', $modal).val()?.trim() || null,
				itemNum: $('.itemNum', $modal).val()?.trim() || null,
				// caliFee: 콤마 제거 후 숫자 변환
				caliFee: Number(String($('.caliFee', $modal).val() ?? '0').replace(/,/g, '')) || 0,
				remark: $('.remark', $modal).val()?.trim() || null,
				caliDate: $('.caliDate', $modal).val() || null,
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

			// EXCEL 파일 업로드 (선택된 경우)
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

			// PDF 파일 업로드 (선택된 경우)
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
