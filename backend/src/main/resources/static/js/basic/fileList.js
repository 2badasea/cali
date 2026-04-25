$(function () {
	console.log('++ basic/fileList.js');

	const $notModalViewAppliedEle = $('.modal-view:not(.modal-view-applied)');
	const $hasModalBodyEle = $notModalViewAppliedEle.filter('.modal-body');
	if ($hasModalBodyEle.length) {
		$modal = $hasModalBodyEle.first();
	} else {
		$modal = $notModalViewAppliedEle.first();
	}
	let $modal_root = $modal.closest('.modal');

	$modal.init_modal = (param) => {
		$modal.param = param;
	};

	// 모달 내 이벤트 정의
	$modal
		// 다운로드
		.on('click', 'td:not(.not_click)', function (e) {
			const fileId = $(this).closest('tr').data('fileId');
			if (!fileId || fileId == 0) {
				gToast('다운로드 받을 파일정보가 없습니다', 'warning');
				return false;
			}
			// get 방식으로 바로 호출
			window.location.href = '/file/fileDown/' + fileId;
		})
		// 파일 삭제
		.on('click', 'button.delete', async function (e) {
			const $tr = $(this).closest('tr');
			const fileId = $tr.data('fileId');
			const deleteConfirm = await gMessage(`파일 삭제`, `해당 파일을 삭제하시겠습니까?`, 'warning', 'confirm');

			if (deleteConfirm.isConfirmed) {
				gLoadingMessage(); // 로딩창

				try {
					const resDelete = await gAjax('/api/file/fileDelete/' + fileId, {}, { type: 'delete' });
					Swal.close();	// 통신이 끝나면 로딩창을 닫는다.
					if (resDelete?.code > 0) {
						await gMessage(`파일 삭제`,`삭제되었습니다.`, 'success', 'alert');
						// 영역삭제
						$tr.remove();

						// 영역삭제 후, 남은 tr이 없다면 모달을 닫는다.
						const trCnt = $('.fileList tbody tr', $modal);
						if (trCnt.length === 0) {
							$modal.param.fileCnt = 0;
							$modal_root.data('modal-data').click_confirm_button();
						}

					} else {
						await gMessage(`파일 삭제`, `삭제처리에 실패했습니다.`, 'warning', 'alert');
					}
				} catch (xhr) {
					await gApiErrorHandler(xhr);
				} finally {
				}
			} else {
				return false;
			}
		});

	// 저장
	$modal.confirm_modal = async function (e) {
		$modal_root.modal('hide');
		return $modal.param;
	};

	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		//모달 팝업창인 경우 바로 init_modal() 호출
		const p = $modal.data('param') || {};
		$modal.init_modal(p);
		if (typeof $modal.grid == 'object') {
			$modal.grid.refreshLayout();
		}
	}

	if (typeof window.modal_deferred == 'object') {
		window.modal_deferred.resolve('script end');
	} else {
		if (!$modal_root.length) {
			initPage($modal);
		}
	}
});
