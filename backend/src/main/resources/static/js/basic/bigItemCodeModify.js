$(function () {
	console.log('++ basic/bigItemCodeModify.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	// const $bodyCandidate = $candidates.filter('.modal-body');
	// if ($bodyCandidate.length) {
	// 	$modal = $bodyCandidate.first();
	// } else {
	// 페이지로 직접 열렸을 수도 있으니, 그때는 그냥 첫 번째 modal-view 사용
	$modal = $candidates.first();
	// }
	let $modal_root = $modal.closest('.modal');

	$modal.init_modal = async (param) => {
		$modal.param = param;

		$modal.data_source = {
			api: {
				readData: {
					url: '/api/basic/getItemCodeList',
					// 'serializer'는 토스트 그리드에서 제공
					serializer: (grid_param) => {
						grid_param.codeLevel = 'LARGE';
						grid_param.parentId = null;
						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.grid = gGrid('.bigGrid', {
			columns: [
				{
					name: 'id',
					hidden: true,
				},
				{
					header: '분류코드',
					name: 'codeNum',
					width: '80',
					editor: {
						type: readOnlyEditorByCondition,
						conditions: {
							isKolasStandard: 'y',
						},
					},
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '분류코드명',
					name: 'codeName',
					editor: {
						type: readOnlyEditorByCondition,
						conditions: {
							isKolasStandard: 'y',
						},
					},
					className: 'cursor_pointer ',
					align: 'center',
				},
				{
					header: '분류코드명(영문)',
					name: 'codeNameEn',
					editor: {
						type: readOnlyEditorByCondition,
						conditions: {
							isKolasStandard: 'y',
						},
					},
					className: 'cursor_pointer',
					align: 'center',
				},
			],
			pageOptions: {
				perPage: 9999,
			},
			rowHeaders: ['checkbox'],
			rowHeight: 'auto',
			scrollY: true,
			data: $modal.data_source,
			editingEvent: 'click',
		});

		// 페이지 내 이벤트
		$modal
			// 행 추가
			.on('click', '.addBigCode', function (e) {
				const emptyRow = {
					id: null,
					codeNum: '',
					codeName: '',
					codeNameEn: '',
					caliCycleUnit: 'UNSPECIFIED', // '미정'이 기본값
					stdCali: null,
					preCali: null,
					parentId: null,
					codeLevel: 'LARGE',
					isKolasStandard: 'n', // 임의로 추가되는 경우, 모두 비표준으로 간주
					tracestatementInfo: null,
				};
				$modal.grid.appendRow(emptyRow);
			})
			// 행 삭제
			.on('click', '.delBigCode', async function (e) {
				const checkedRows = $modal.grid.getCheckedRows();
				if (checkedRows.length === 0) {
					gToast('삭제할 행을 선택해주세요.<br>KOLAS 표준 분류코드의 경우 수정/삭제가 불가능합니다.', 'warning');
					return false;
				}

				// 1. 검증 (하위 중분류가 존재할 경우, 해당 중분류를 사용하고 있는 성적서가 존재한다면 안 된다고 안내)
				const removeRowKeys = [];
				const ids = checkedRows
					.filter((itemCode) => {
						removeRowKeys.push(itemCode.rowKey);
						return itemCode?.id > 0;
					})
					.map((row) => row.id);

				// id를 담은 요소가 없다면 행만 삭제
				if (ids.length === 0) {
					$modal.grid.removeRows(removeRowKeys);
					return false;
				}

				// id를 담은 요소가 있다면 서버에서 검증을 진행한다.(중분류 및 성적서가 존재하는지)
				try {
					gLoadingMessage();
					const fetchOptions = {
						method: 'POST',
						headers: {
							'Content-Type': 'application/json; charset=utf-8',
						},
						body: JSON.stringify({
							ids: ids,
							codeLevel: 'LARGE',
						}),
					};
					const resValid = await fetch('/api/basic/deleteItemCodeCheck', fetchOptions);
					if (resValid.ok) {
						Swal.close();
						const resJson = await resValid.json();
						let resMsg = resJson.msg ?? '';
						const resData = resJson.data ?? {};
						let confirmMsg = '';
						if (Object.keys(resData).length > 0) {
							confirmMsg += `<div class='text-left'>`;
							// 객체 순회는 for...in
							for (let key in resData) {
								confirmMsg += `- <b>분류코드</b>: ${key}, <b>분류코드명</b>: ${resData[key]}<br>`;
							}
							confirmMsg += `</div><br>`;
						}
						resMsg += confirmMsg;
						if (resJson?.code > 0) {
							// 삭제여부 확인
							const deleteConfrim = await gMessage('분류코드 삭제', resMsg, 'question', 'confirm');
							if (deleteConfrim.isConfirmed === true) {
								// 코드가 길어지므로, 별도의 삭제 함수 호출
								$modal.deleteCode(ids);
							} else {
								return false;
							}
						}
						// 참조하는 하위 성적서 존재
						else {
							await gMessage('분류코드 삭제', resMsg, 'warning', 'alert');
							return false;
						}
					} else {
						Swal.close();
						return false;
					}
				} catch (err) {
					console.error(err);
					await gApiErrorHandler(err);
				} finally {
					Swal.close();
				}
			});

		// 그리드 객체에 대한 이벤트 추가
		$modal.grid.on('click', async function (e) {
			const row = $modal.grid.getRow(e.rowKey);
			if (row) {
				if (row.isKolasStandard === 'y') {
					gToast('KOLAS 표준 분류코드는 수정/삭제가 불가능합니다.', 'warning');
					return false;
				}
			}
		});

		// 그리드에 데이터가 렌더링(세팅) 직후의 이벤트
		$modal.grid.on('onGridUpdated', function (e) {
			const datas = $modal.grid.getData();
			datas.forEach((row) => {
				$modal.grid.store.column.allColumns.forEach((col) => {
					// kolas 공인 표준의 경우, 체크박스를 통해 선택이 안 되도록 한다.
					if (row.isKolasStandard == 'y' && col.name == '_checked') {
						$modal.grid.disableCell(row.rowKey, col.name);
						$modal.grid.addCellClassName(row.rowKey, col.name, 'read_only');
					}
				});
			});
		});

		$modal.grid.on('afterChange', function (e) {
			const rowKey = e.rowKey;
			if (!Array.isArray($modal.updatedRowKey)) {
				$modal.updatedRowKey = [];
			}
			$modal.updatedRowKey.push(rowKey);
		});

		// 삭제진행 콜백함수
		$modal.deleteCode = async (ids) => {
			const resDelete = await gAjax(
				'/api/basic/deleteItemCode',
				JSON.stringify({
					ids: ids,
					codeLevel: 'LARGE',
				}),
				{
					type: 'DELETE',
					contentType: 'application/json; charset=utf-8',
				},
			);

			if (resDelete?.code > 0) {
				await gMessage('분류코드 삭제', '삭제되었습니다', 'success', 'alert');
				location.reload();
			} else {
				await gMessage('분류코드 삭제', '삭제에 실패했습니다.', 'error', 'alert');
				return false;
			}
		};
	}; // End of init_modal

	// 저장
	$modal.confirm_modal = async function (e) {
		console.log('저장클릭');
		// getColumnValues(columnName) 활용, getRow(rowKey) 활용

		// 저장 시, 변경이벤트가 일어난 부분만 update 항목에 담을 것. 또한 KOLAS 표준은 업데이트 제외
		$modal.grid.blur();
		const rows = $modal.grid.getData();

		// 저장대상의 데이터를 모두 담은 뒤에 값 검증 진행
		let saveRows = [];
		rows.forEach((row) => {
			// KOLAS 표준의 경우, 건너뛴다.
			if (row.isKolasStandard === 'y') {
				return false;
			}
			// 신규 행은 추가
			if (!row.id) {
				saveRows.push(row);
			} else {
				// id가 존재하는 것중에서 change 이벤트가 발생한 경우에도 담는다.
				if (Array.isArray($modal.updatedRowKey) && $modal.updatedRowKey.length > 0 && $modal.updatedRowKey.includes(row.rowKey)) {
					saveRows.push(row);
				}
			}
		});

		if (saveRows.length === 0) {
			gToast('추가/변경된 항목이 존재하지 않습니다.', 'warning');
			return false;
		}

		const codeNums = $modal.grid.getColumnValues('codeNum'); // 분류코드 열에 있는 값을 모두 담는다.
		const setCodeNums = new Set(codeNums);
		const uniqueCodeNums = [...setCodeNums]; // spread 연산자로 배열형태로 변경

		if (codeNums.length !== uniqueCodeNums.length) {
			gToast('중복된 분류코드가 존재합니다.', 'warning');
			return false;
		}

		// 업데이트 대상 행 유효성 검증 filter 활용해보기 -> true를 리턴한 것만 담긴다.
		let saveFlag = true;
		let flagMsg = '';
		const regNum = /^[0-9]+$/;
		saveRows = saveRows.filter((row) => {
			if (!checkInput(row.codeNum) || !regNum.test(row.codeNum) || row.codeNum.length > 2) {
				flagMsg = '분류코드는 숫자(1~2자리)로만 구성되어야 합니다.';
				saveFlag = false;
			}
			if (!checkInput(row.codeName.trim())) {
				flagMsg = '분류코드명을 입력해주세요.';
				saveFlag = false;
			}
			return saveFlag;
		});

		if (!saveFlag) {
			gToast(flagMsg, 'warning');
			return false;
		}

		const $btn = $('button.btn_save', $modal_root);
		// 저장 진행
		try {
			$btn.prop('disabled', true);

			const saveConfirm = await gMessage('분류코드 저장', '저장하시겠습니까?', 'question', 'confirm');
			if (saveConfirm.isConfirmed === true) {
				const createRows = saveRows.filter(r => !r.id);
				const updateRows = saveRows.filter(r => !!r.id);

				if (createRows.length > 0) {
					const resCreate = await fetch('/api/basic/itemCodes', {
						method: 'POST',
						headers: { 'Content-Type': 'application/json; charset=utf-8' },
						body: JSON.stringify(createRows),
					});
					if (!resCreate.ok) throw resCreate;
					const createData = await resCreate.json();
					if (createData?.code <= 0) {
						await gMessage('분류코드 저장', createData.msg ?? '등록에 실패했습니다.', 'error', 'alert');
						return false;
					}
				}

				if (updateRows.length > 0) {
					const resUpdate = await fetch('/api/basic/itemCodes', {
						method: 'PATCH',
						headers: { 'Content-Type': 'application/json; charset=utf-8' },
						body: JSON.stringify(updateRows),
					});
					if (!resUpdate.ok) throw resUpdate;
					const updateData = await resUpdate.json();
					if (updateData?.code <= 0) {
						await gMessage('분류코드 저장', updateData.msg ?? '수정에 실패했습니다.', 'error', 'alert');
						return false;
					}
				}

				await gMessage('분류코드 저장', '저장에 성공했습니다.', 'success', 'alert');
				location.reload()
			} else {
				return false;
			}
		} catch (err) {
			await gApiErrorHandler(err);
		} finally {
			$btn.prop('disabled', false);
		}
		// $modal_root.modal('hide');
		// return $modal;
	};

	// 담당자 그리드 초기화

	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		//모달 팝업창인 경우 바로 init_modal() 호출
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
