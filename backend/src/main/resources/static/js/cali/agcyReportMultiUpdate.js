$(function () {
	console.log('++ cali/agcyReportMultiUpdate.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	let reportIds = [];

	$modal.init_modal = async (param) => {
		$modal.param = param;
		reportIds = $modal.param.reportIds ?? [];
		$('.selectedCount', $modal).text(reportIds.length);
	};

	// 저장: 입력된 항목만 서버로 전송 (null = 변경 안 함)
	$modal.confirm_modal = async function () {
		if (!reportIds.length) {
			gToast('대상 성적서가 없습니다.', 'warning');
			return false;
		}

		const agcyAgent = $('.agcyAgent', $modal).val()?.trim() || null;
		const caliDate = $('.caliDate', $modal).val() || null;
		const reportNum = $('.reportNum', $modal).val()?.trim() || null;
		const reportStatus = $('.reportStatus', $modal).val() || null;

		if (!agcyAgent && !caliDate && !reportNum && !reportStatus) {
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
			const sendData = { reportIds, agcyAgent, caliDate, reportStatus, reportNum };
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
