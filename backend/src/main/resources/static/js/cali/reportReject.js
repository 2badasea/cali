$(function () {
	console.log('++ cali/reportReject.js');

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

	// =====================================================================
	// init_modal: param 수신 후 초기화
	// param.reportIds: 반려 대상 성적서 id 배열
	// param.title(optional): 모달 타이틀 보조용
	// =====================================================================
	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);

		// 반려사유 초기화
		$('#rejectReason', $modal).val('');
	};

	// =====================================================================
	// confirm_modal: 반려 버튼 클릭 시 실행
	// =====================================================================
	$modal.confirm_modal = async function () {
		const rejectReason = $('#rejectReason', $modal).val().trim();

		// 반려사유 필수 검증
		if (!rejectReason) {
			gToast('반려 사유를 입력해주세요.', 'warning');
			return false;
		}

		const reportIds = $modal.param?.reportIds ?? [];
		if (!reportIds.length) {
			gToast('반려 대상 성적서가 없습니다.', 'error');
			return false;
		}

		try {
			gLoadingMessage('반려처리 중입니다...');
			const res = await fetch('/api/admin/managerApproval/reject', {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body: JSON.stringify({ reportIds, rejectReason }),
			});
			swal.close();
			if (!res.ok) throw res;
			const data = await res.json();
			if (data?.code > 0) {
				// 성공 시 gModal이 자동으로 모달을 닫음 (truthy 반환)
				gToast(data.msg ?? '반려 처리가 완료되었습니다.', 'success');
			} else {
				await gMessage('오류', data.msg ?? '반려 처리 중 오류가 발생했습니다.', 'error', 'alert');
				return false;
			}
		} catch (err) {
			swal.close();
			await gApiErrorHandler(err);
			return false;
		}
	};

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