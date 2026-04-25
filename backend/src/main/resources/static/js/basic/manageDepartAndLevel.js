$(function () {
	console.log('++ basic/manageDepartAndLevel.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	const $bodyCandidate = $candidates.filter('.modal-body');
	if ($bodyCandidate.length) {
		$modal = $bodyCandidate.first();
	} else {
		// 페이지로 직접 열렸을 수도 있으니, 그때는 그냥 첫 번째 modal-view 사용
		$modal = $candidates.first();
	}
	let $modal_root = $modal.closest('.modal');

	const deleteDepartmentIds = [];
	const deleteMemberLevelIds = [];
	$modal.init_modal = (param) => {
		$modal.param = param;

		// 부서정보 가져오기
		$modal.departmentDataSource = {
			api: {
				readData: {
					url: '/api/basic/getDepartmentList',
					serializer: (grid_param) => {
						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.departmentGrid = gGrid('.departmentGrid', {
			columns: [
				{
					name: 'id',
					hidden: true,
				},
				{
					header: '부서명',
					name: 'name',
					editor: 'text',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '표시순서',
					name: 'seq',
					className: 'cursor_pointer',
					align: 'center',
					width: '180',
				},
			],
			// pageOptions: {
			// 	perPage: 15,
			// },
			minBodyHeight: 680,
			bodyHeight: 680,
			rowHeaders: ['checkbox'],
			editingEvent: 'click',
			data: $modal.departmentDataSource,
			rowHeight: 'auto',
			draggable: true, // 드래그 허용
		});

		// 드래그를 사용할 경우 이벤트
		$modal.departmentGrid.on('drop', function (e) {
			$modal.setGridSeq($modal.departmentGrid);
		});

		// 직급 정보 가져오기
		$modal.levelDataSource = {
			api: {
				readData: {
					url: '/api/basic/getMemberLevelList',
					serializer: (grid_param) => {
						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.memberLevelGrid = gGrid('.memberLevelGrid', {
			columns: [
				{
					name: 'id',
					hidden: true,
				},
				{
					header: '직급명',
					name: 'name',
					editor: 'text',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '표시순서',
					name: 'seq',
					className: 'cursor_pointer',
					align: 'center',
					width: '180',
				},
			],
			// pageOptions: {
			// 	perPage: 15,
			// },
			minBodyHeight: 680,
			bodyHeight: 680,
			editingEvent: 'click',
			data: $modal.levelDataSource,
			rowHeaders: ['checkbox'],
			rowHeight: 'auto',
			draggable: true, // 드래그 허용
		});

		// 드래그를 사용할 경우 이벤트
		$modal.memberLevelGrid.on('drop', function (e) {
			$modal.setGridSeq($modal.memberLevelGrid);
		});

		// 그리드에 대한 순서를 최신화 시킨다.
		$modal.setGridSeq = (targetGrid) => {
			if (!targetGrid) {
				return false;
			}
			let seq = 1;
			targetGrid.getData().forEach((row) => {
				targetGrid.setValue(row.rowKey, 'seq', seq++);
			});
			gToast('표시순서는 저장 시 반영됩니다.', 'info');
		};
	}; // End init_modal

	// 페이지 내 이벤트
	$modal
		// 저장 클릭
		.on('click', '.save', async function () {
			const $btn = $(this);
			const type = $btn.attr('data-type'); // 'department' | 'memberLevel'
			const saveGrid = type == 'department' ? $modal.departmentGrid : $modal.memberLevelGrid;
			saveGrid.blur();

			const saveInfoKr = type == 'department' ? '부서' : '직급';
			// 입력값 체크
			let isValid = true;
			const names = saveGrid.getColumnValues('name');
			const namesSet = new Set(names);
			if (names.length != namesSet.size) {
				gToast(`중복되는 ${saveInfoKr}명이 존재합니다.`);
				return false;
			}

			const rowData = saveGrid.getData();
			rowData.forEach((row) => {
				if (!checkInput(row.name)) {
					gToast(`${saveInfoKr}명을 입력해주세요.`, 'warning');
					saveGrid.focus(row.rowKey, 'name');
					isValid = false;
					return false;
				}
			});
			if (!isValid) {
				return false;
			}

			$btn.prop('disabled', true);
			const saveConfirm = await gMessage(`${saveInfoKr}관리 저장`, '저장하시겠습니까?', 'question', 'confirm');
			// 저장
			if (saveConfirm.isConfirmed == true) {
				try {
					const feOption = {
						method: 'POST',
						headers: {
							'Content-Type': 'application/json; charset=utf-8',
						},
						body: JSON.stringify({
							type: type,
							saveData: rowData,
							deleteIds: type === 'department' ? deleteDepartmentIds : deleteMemberLevelIds,
						}),
					};
					const resSave = await fetch('/api/basic/saveBasicInfo', feOption);
					if (resSave.ok) {
						const resData = await resSave.json();
						if (resData?.code > 0) {
							if (type === 'department') {
								deleteDepartmentIds.splice(0);
							} else {
								deleteMemberLevelIds.splice(0);
							}
							await gMessage(`${saveInfoKr}관리 저장`, '저장되었습니다.', 'success', 'alert');
							saveGrid.reloadData();
						} else {
							await gMessage(`${saveInfoKr}관리 저장`, resData.msg ?? '저장 실패', 'warning', 'alert');
						}
					} else {
						throw resSave;
					}
				} catch (xhr) {
					await gApiErrorHandler(xhr);
				} finally {
					$btn.prop('disabled', false);
				}
			} else {
				$btn.prop('disabled', false);
				return false;
			}

			// 동일한 api컨트롤러를 타게 한다.
		})
		// 삭제
		// NOTE 삭제 시 알림주기 (기존에 해당 직급을 가지고 있는 직원들은 정보가 사라지기 때문에 새롭게 등록해야 한다고)
		.on('click', '.delete', async function () {
			const gUserAuth = G_USER.auth;
			if (gUserAuth !== 'admin') {
				gToast('권한이 없습니다', 'warning');
				return false;
			}

			const type = $(this).attr('data-type');
			const grid = type == 'department' ? $modal.departmentGrid : $modal.memberLevelGrid;
			const typeKr = type == 'department' ? '부서' : '직급';
			const checkedRows = grid.getCheckedRows();
			if (checkedRows.length === 0) {
				gToast(`삭제할 ${typeKr}을 선택해주세요.`, 'warning');
				return false;
			}

			const deleteRowKeys = [];
            let hasId = false;
			checkedRows.forEach((row) => {
				if (row.id != undefined && row.id > 0) {
					if (type == 'department') {
						deleteDepartmentIds.push(row.id);
					} else {
						deleteMemberLevelIds.push(row.id);
					}
                    hasId = true;
				}
				deleteRowKeys.push(row.rowKey);
			});

			if (hasId == true) {
				gToast('저장 시 반영됩니다', 'info');
			}
			grid.removeRows(deleteRowKeys);
			$modal.setGridSeq(grid); // 순서 초기화
		})
		// 추가 버튼
		.on('click', '.add', function () {
			const type = $(this).attr('data-type');
			const grid = type == 'department' ? $modal.departmentGrid : $modal.memberLevelGrid;

			const focusedCell = grid.getFocusedCell();
			let option = {};
			if (focusedCell.rowKey != null) {
				let rowIndex = grid.getIndexOfRow(focusedCell.rowKey);
				rowIndex = parseInt(rowIndex) + 1;
				option.at = rowIndex;
			}
			grid.appendRow({}, option);
			// 순서에 대해서 정렬을 시킨다.
			$modal.setGridSeq(grid);
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
