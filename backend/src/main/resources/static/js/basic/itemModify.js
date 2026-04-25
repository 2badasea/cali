$(function () {
	console.log('++ basic/itemModify.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	$modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	let itemId;
	let smallItemCodeSetObj = {};
	let middleItemCodeSetAry = [];
	const delHistoryIds = [];

	$modal.init_modal = async (param) => {
		$modal.param = param;

		smallItemCodeSetObj = $modal.param.smallItemCodeSetObj;
		middleItemCodeSetAry = $modal.param.middleItemCodeSetAry;

		// 중분류 세팅
		await $modal.setMiddleCode();
		// await new Promise(resolve => setTimeout(resolve, 500)); // 지연시간 주기

		// 업체id로 초기화 하기(수정)
		if ($modal.param?.id > 0) {
			// 옵셔널체이닝으로 체크
			itemId = Number($modal.param.id);
			// 데이터를 가져와서 세팅한다.
			try {
				const resGetItemInfo = await gAjax(
					`/api/item/getItemInfo/${itemId}`,
					{},
					{
						'type': 'get',
					}
				);
				if (resGetItemInfo?.code > 0) {
					const itemInfo = resGetItemInfo.data;
					$('form[name=itemModifyForm]', $modal).find('input[name], select[name]').setupValues(itemInfo);
					$(`input[name=isInhousePossible][value=${itemInfo.isInhousePossible}]`, $modal).trigger('click'); // 당사가능여부 세팅
					$modal.setSmallCode(itemInfo.middleItemCodeId, itemInfo.smallItemCodeId); // 중소분류 세팅
				}
			} catch (err) {
				await gApiErrorHandler(err);
			} finally {
			}

			// 교정수수료 데이터 세팅
			$modal.dataSource = {
				api: {
					readData: {
						url: `/api/item/getItemFeeHistory/${itemId}`,
						serializer: (grid_param) => {
							return $.param(grid_param);
						},
						method: 'GET',
					},
				},
			};
		} else {
			// 등록 시 당사가능여부 기본값 체크
			$(`input[name=isInhousePossible][value='y']`, $modal).trigger('click');
		}

		// 업체 담당자 그리드
		$modal.grid = gGrid('.feeHistoryGrid', {
			columns: [
				{
					name: 'id',
					hidden: true,
				},
				{
					header: '기준일자',
					name: 'baseDate',
					className: 'cursor_pointer',
					editor: {
						type: DateEditor,
						options: { required: false }, // 필요하면 true
					},
					width: '250',
					align: 'center',
				},
				{
					header: '교정수수료',
					name: 'baseFee',
					editor: number_format_editor,
					width: '200',
					className: 'cursor_pointer',
					align: 'right',
					formatter: function (data) {
						return numberFormat(data.value);
					},
				},
				{
					header: '비고',
					name: 'remark',
					editor: 'text',
					className: 'cursor_pointer',
					align: 'left',
				},
			],
			rowHeaders: ['checkbox'],
			editingEvent: 'click', // 원클릭으로 수정할 수 있도록 변경. 기본값은 'dblclick'
			data: itemId > 0 ? $modal.dataSource : null,
		});

		// 그리드 이벤트
		$modal.grid.on('onGridMounted', function (e) {
			const rowCnt = $modal.grid.getRowCount();
			if (rowCnt == 0) {
				$modal.grid.appendRow(
					{
						baseFee: 0,
					},
					{ at: 0 }
				);
			}
		});
		$modal.grid.on('response', function (e) {
			setTimeout(() => {
				const rowCnt = $modal.grid.getRowCount();
				if (rowCnt == 0) {
					$modal.grid.appendRow(
						{
							baseFee: 0,
						},
						{ at: 0 }
					);
				}
			}, 100);
		});
	};

	// 모달 내 이벤트 정의
	$modal
		// 중분류코드 변경 시
		.on('change', '.middleCodeSelect', function () {
			const middleCodeId = $(this).val();
			$modal.setSmallCode(middleCodeId);
		})
		// 수수료 이력 추가
		.on('click', '.addFeeHistory', function () {
			// 무조건 최신으로 표시
			$modal.grid.appendRow(
				{
					baseFee: 0,
				},
				{ at: 0 }
			);
		})
		// 수수료 이력 삭제
		.on('click', '.delFeeHistory', async function () {
			const checkedRows = $modal.grid.getCheckedRows();
			if (checkedRows.length === 0) {
				gToast('삭제할 이력을 선택해주세요.');
				return false;
			}
			const confirmResult = await gMessage('수수료 이력 삭제', '선택한 이력을 삭제하시겠습니까?', 'question', 'confirm');
			if (!confirmResult.isConfirmed) return false;
			const removeRowKeyAry = [];
			// 삭제할 아이디는 배열에 담는다(저장 시 반영)
			checkedRows.forEach((row, index) => {
				if (row.id != undefined && row.id > 0) {
					delHistoryIds.push(row.id);
				}
				removeRowKeyAry.push(row.rowKey);
			});
			if (delHistoryIds.length > 0) {
				gToast('삭제된 이력은 저장 시 반영됩니다.', 'info');
			}
			$modal.grid.removeRows(removeRowKeyAry);

			if ($modal.grid.getRowCount() === 0) {
				$modal.grid.appendRow({ baseFee: 0 }, { at: 0 });
			}
		});

	// 중분류 세팅 함수
	$modal.setMiddleCode = async () => {
		const $middleItemCodeSelect = $('.middleCodeSelect', $modal);
		if (middleItemCodeSetAry.length > 0) {
			$.each(middleItemCodeSetAry, function (index, mCodeInfo) {
				const mOption = new Option(`${mCodeInfo.codeNum} ${mCodeInfo.codeName}`, mCodeInfo.id);
				$middleItemCodeSelect.append(mOption);
			});
		}
		await new Promise((resolve) => setTimeout(resolve, 100)); // 지연시간 주기
	};

	// 중분류 선택에 따른 소분류 세팅 함수
	$modal.setSmallCode = (middleCodeId, smallCodeId, layInitTime = 0) => {
		const $smallCodeSelect = $('.smallCodeSelect', $modal);
		const basicOption = new Option('소분류전체', '');
		$($smallCodeSelect).find('option').remove();
		$smallCodeSelect.append(basicOption);

		setTimeout(() => {
			if (!middleCodeId) {
				$smallCodeSelect.val(''); // '소분류전체'로 세팅
			} else {
				if (smallItemCodeSetObj[middleCodeId] != undefined && smallItemCodeSetObj[middleCodeId].length > 0) {
					const smallItemCodes = smallItemCodeSetObj[middleCodeId];
					smallItemCodes.forEach((row, index) => {
						const option = new Option(`${row.codeNum} ${row.codeName}`, row.id);
						$smallCodeSelect.append(option);
					});
					if (smallCodeId > 0) {
						$smallCodeSelect.val(smallCodeId);
					}
				}
			}
		}, layInitTime);
	};

	// 저장
	$modal.confirm_modal = async function (e) {
		// 업체정보 & 담당자 정보 유효성 체크 후, formdata에 데이터 담기
		const $form = $('.itemModifyForm', $modal);
		const formData = $form.serialize_object();
		$modal.grid.blur();

		// 중분류, 소분류, 품목명 체크
		const name = formData.name;
		if (!checkInput(name)) {
			gToast('품목명을 입력해주세요.', 'warning');
			return false;
		}

		const middleItemCodeId = formData.middleItemCodeId;
		const smallItemCodeId = formData.smallItemCodeId;
		if (!middleItemCodeId || !smallItemCodeId) {
			gToast('중분류, 소분류는 필수입니다.', 'warning');
			return false;
		}

		// 교정수수료 데이터 검증하기
		const rowDatas = $modal.grid.getData();
		if (rowDatas.length === 0) {
			gToast('수수료 이력은 최소 1개는 존재해야 합니다.', 'warning');
			return false;
		}
		// 반복문 순회로 체크해야 할 부분 1. 기준일자 값, 중복여부, 기준금액 존재여부, 가장 큰 금액이 본 품목의 교정수수료가 됨. (콤마제거해서 삽입할 것)
		let isValid = true;
		const baseDateMap = new Map();
		rowDatas.forEach((row, index) => {
			// 순회 중 요건 불충족 시, 바로 중단
			if (!isValid) {
				return false;
			}
			// 1. 기준일자 체크
			if (!row.baseDate) {
				gToast('기준일자를 선택해주세요', 'warning');
				isValid = false;
				return false;
			}
			if (!row.baseFee || Number(row.baseFee) == 0) {
				gToast('기준금액은 0원 이상이어야 합니다.', 'warning');
				isValid = false;
				return false;
			}
			if (baseDateMap.has(row.baseDate)) {
				gToast('기준일자는 중복이 될 수 없습니다.', 'warning');
				isValid = false;
				return false;
			}
			baseDateMap.set(row.baseDate, row.baseFee);
		});
		if (!isValid) {
			return false;
		}

		// 교정수수료 중에서 가장 최신값 조회
		const mapIterator = baseDateMap.keys(); // Map Iterator(반복자) 를 반환
		const baseDateAry = [...mapIterator]; // 배열화
		baseDateAry.sort(); // 오름차순 정렬
		const recentFee = baseDateMap.get(baseDateAry[baseDateAry.length - 1]); // 가장 최신 금액

		formData.fee = recentFee; // 금액정보 담기

		formData.id = itemId; // 수정일 땐 값 존재. 등록일 땐 null
		formData.createType = 'BASIC'; // 기본

		const saveFormData = {};
		saveFormData.itemData = formData;
		saveFormData.delHistoryIds = delHistoryIds;
		saveFormData.itemFeeHistoryList = rowDatas;

		try {
			const confirm = await gMessage('품목 저장', '저장하시겠습니까?', 'question', 'confirm');
			// 저장진행
			if (confirm.isConfirmed === true) {
				gLoadingMessage();

				const options = {
					method: 'POST',
					headers: {
						'Content-Type': 'application/json; charset=utf-8',
					},
					body: JSON.stringify(saveFormData),
				};
				const resSave = await fetch('/api/item/saveItem', options);
				Swal.close();
				if (resSave.ok) {
					const resData = await resSave.json();
					if (resData?.code > 0) {
						await gMessage('품목 저장', resData.msg ?? '저장되었습니다', 'success', 'alert');
						// NOTE 모달 닫히면서 갱신되는지 확인
						$modal_root.modal('hide');
						return true;
					} else {
						await gMessage('품목 저장', resData.msg ?? '저장에 실패했습니다', 'warning', 'alert');
					}
				} else {
					throw new Error('저장에 실패했습니다.');
				}
			} else {
				return false;
			}
		} catch (err) {
			Swal.close();
			console.log(err);
			await gApiErrorHandler(err);
		} finally {
		}

		// NOTE 삭제대상 교정수수료도 담아야 함. 있는지 체크 필요`
	};

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
