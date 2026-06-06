$(function () {
	console.log('++ cali/agcyReportMultiUpdate.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	let reportIds = [];
	let smallItemCodeSetObj = {};

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

	$modal.init_modal = async (param) => {
		$modal.param = param;
		reportIds = $modal.param.reportIds ?? [];

		// 중/소분류 데이터 로드
		try {
			const codeRes = await gAjax('/api/basic/getItemCodeInfos', {}, { type: 'GET' });
			if (codeRes?.code > 0) {
				const itemCodeSet = codeRes.data;
				const middleList = itemCodeSet.middleCodeInfos ?? [];
				const smallObj = itemCodeSet.smallCodeInfos ?? {};
				smallItemCodeSetObj = smallObj;

				const $middle = $('.middleItemCodeId', $modal);
				// "변경 안 함" 옵션이 기본 — value='' 이면 null로 전송되어 서버에서 skip
				$middle.append(new Option('변경 안 함', ''));
				middleList.forEach(m => $middle.append(new Option(m.codeNum, m.id)));

				$middle.on('change', function () {
					$modal.renderSmallCodes($(this).val());
				});

				// 소분류 초기 렌더 (변경 안 함 상태)
				$modal.renderSmallCodes('');
			}
		} catch (err) {
			console.error('코드 정보 로드 실패', err);
		}

		// 이벤트 바인딩
		$modal
			.on('click', '.searchAgcyAgent', function () {
				$modal.searchAgcyAgent($('.agcyAgent', $modal).val()?.trim() ?? '');
			})
			.on('keydown', '.agcyAgentInput', function (e) {
				if (e.key === 'Enter' || e.keyCode === 13) {
					e.preventDefault();
					$modal.searchAgcyAgent($(this).val()?.trim() ?? '');
				}
			});
	};

	// 소분류 select 재구성 (middleId 기준)
	// middleId가 없으면 "변경 안 함"만 표시 (소분류도 변경 안 함 상태)
	$modal.renderSmallCodes = (middleId) => {
		const $small = $('.smallItemCodeId', $modal);
		$small.empty().append(new Option('변경 안 함', ''));
		if (middleId) {
			const list = smallItemCodeSetObj[String(middleId)] ?? [];
			list.forEach(s => $small.append(new Option(s.codeNum, s.id)));
		}
		$small.val('');
	};

	// 저장: 입력된 항목만 서버로 전송 (null = 변경 안 함)
	$modal.confirm_modal = async function () {
		if (!reportIds.length) {
			gToast('대상 성적서가 없습니다.', 'warning');
			return false;
		}

		const agcyAgent = $('.agcyAgent', $modal).val()?.trim() || null;
		const caliDate = $('.caliDate', $modal).val() || null;
		const middleItemCodeId = Number($('.middleItemCodeId', $modal).val()) || null;
		const smallItemCodeId = Number($('.smallItemCodeId', $modal).val()) || null;

		if (!agcyAgent && !caliDate && !middleItemCodeId && !smallItemCodeId) {
			gToast('변경할 항목을 1개 이상 입력해주세요.', 'warning');
			return false;
		}

		const confirmRes = await gMessage(
			'대행 통합수정',
			`${reportIds.length}건을 수정하시겠습니까?`,
			'question',
			'confirm'
		);
		if (!confirmRes.isConfirmed) return false;

		gLoadingMessage();
		try {
			const sendData = { reportIds, agcyAgent, caliDate, middleItemCodeId, smallItemCodeId };
			const response = await fetch('/api/report/agcyReportMultiUpdate', {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify(sendData),
			});
			if (!response.ok) throw response;
			const res = await response.json();

			if (res?.code > 0) {
				await gMessage('대행 통합수정', res.msg ?? '수정되었습니다.', 'success');
				$modal_root.modal('hide');
				return true;
			} else {
				await gMessage('수정 실패', res?.msg ?? '수정에 실패했습니다.', 'warning');
			}
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
