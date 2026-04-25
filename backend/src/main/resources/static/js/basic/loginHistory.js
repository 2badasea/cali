$(function () {
	console.log('++ basic/loginHistory.js');

	let $modal = $('.modal-view:not(.modal-view-applied)');
	let $modal_root = $modal.closest('.modal');

	$modal.init_modal = (param) => {
		console.log('🚀 ~ param:', param);
	};

	$modal.on('click', '.btnGetList', async function (e) {
		// 화살표 함수 사용 시, 바깥 스코프의 this를 가리키게 된다.
		e.preventDefault();
		// api 방식으로 데이터를 가져오기
		$(this).prop('disabled', true);

		const $tbody = $('.loginHistory_tbl tbody', $modal);
		$tbody.html(''); // remove()를 호출하면 tbody까지 삭제됨.

		// 중복호출 방지
		try {
			const res = await gAjax('/api/basic/getLoginHistoryList', {});

			if (res?.code > 0) {
				const datas = res.data;
				// 가급적이면 콜백함수의 인수2개 형태는 맞출 것 (화살표함수 사용 시, this는 전역객체를 바라봄)
				$(datas).each((i, obj) => {
					let tr = `
                        <tr>
                            <td>${obj.id}</td>
                            <td class="text-left">${obj.logContent}</td>
                            <td>${obj.workerName}</td>
                            <td>${obj.createDatetime}</td>
                        </tr>`;
					$tbody.append(tr);
				});
			}
		} catch (err) {
			console.log('catch!');
			await gApiErrorHandler(err);
		} finally {
			Swal.close();
			$(this).prop('disabled', false);
		}
	});

	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		//모달 팝업창인경우
		$modal_root.on('modal_ready', function (e, p) {
			$modal.init_modal(p);
			if (typeof $modal.grid == 'object') {
				$modal.grid.refreshLayout();
			}
		});
	}

	if (typeof window.modal_deferred == 'object') {
		window.modal_deferred.resolve('script end');
	} else {
		if (!$modal_root.length) {
			initPage($modal);
		}
	}
});
