$(function () {
	console.log('++ basic/agentGroupModify.js');

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
		console.log('🚀 ~ $modal.param:', $modal.param);

		// 기존에 존재하는 그룹항목 select/option으로 세팅
		gAjax(
			'/api/basic/getGroupName',
			{},
			{
				type: 'get',
				success: function (resData) {
					// 데이터가 있는 경우 세팅
					if (resData?.code === 1 && resData.data) {
						const groupNameEle = $('.groupName', $modal);
						let html = '';
						for (let groupName of resData.data) {
							html += `<option value="${groupName}">${groupName}</option>`;
						}
						groupNameEle.append(html);
					}
				},
				error: function (xhr) {
					gApiErrorHandler(xhr);
				},
				complete: function (data) {},
			}
		);
	};

	// $modal
	//     .on('click')

	// 저장
	$modal.confirm_modal = async function (e) {
		console.log('저장 진행!!');

		// radio 요소 중에서 체크된 것을 가져오기
		const applyType = $('input[name=applyType]:checked', $modal).val();
		let newType = '';
		if (applyType === 'select') {
			if (!$('.groupName', $modal).val()) {
				gToast('그룹을 선택해주세요.', 'warning');
				return false;
			} else {
				newType = $('.groupName', $modal).val();
			}
		} else if (applyType === 'new') {
			if (!checkInput($('input[name=newGroupName]', $modal).val())) {
				gToast('새로운 그룹명을 입력해주세요.', 'warning');
				return false;
			} else {
				newType = $('input[name=newGroupName]', $modal).val();
			}
		}
		let msgPrefix = applyType == 'empty' ? '미적용' : newType;

		// g_mesasge()는 promise 객체를 리턴하기 때문에 isConfirmed를 기대할 수 없음
		const updateCheck = await gMessage('그룹관리 수정', `'${msgPrefix}'으로 수정하시겠습니까?`, 'question', 'confirm');

		// 수정
		if (updateCheck.isConfirmed) {
			gLoadingMessage();

			try {
				// await과 콜백(success)을 같이 쓰면 중복/혼란
				const resUpdate = await gAjax(
					'/api/basic/updateGroupName',
					JSON.stringify({
						ids: $modal.param.ids, // array
						groupName: newType,
					}),
					{
						contentType: 'application/json; charset=utf-8',
					}
				);
				// 통신이 끝나면 로딩창을 닫아준다.
				Swal.close();

				if (resUpdate?.code > 0) {
					await gMessage('그룹명 수정', '그룹명이 수정되었습니다.', 'success');
					$modal_root.modal('hide');
					return true;
				} else {
					await gMessage('그룹명 수정', '그룹명 수정에 실패했습니다.', 'warning');
					return false;
				}
			} catch (err) {
				Swal.close();
				await gApiErrorHandler(err);
			}
		} else {
			return false;
		}
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
