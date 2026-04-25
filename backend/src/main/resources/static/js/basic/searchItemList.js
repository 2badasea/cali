$(function () {
	console.log('++ basic/searchItemList.js');

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

	let middleItemCodeSet = []; // 중분류정보
	let smallItemCodeSet = {}; // 소분류정보

	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log($modal.param);

		// 중분류와 소분류 정보를 가져와서 초기화를 진행한다.
		await $modal.initItemCodeSet();
		await $modal.setItemCode($modal.param.middleItemCodeId, $modal.param.smallItemCodeId);
		$('input[name=name]', $modal).val($modal.param.itemName);
		$('input[name=makeAgent]', $modal).val($modal.param.itemMakeAgent);
		$('input[name=format]', $modal).val($modal.param.itemFormat);

		await new Promise((resolve) => setTimeout(resolve, 100));

		$modal.dataSource = {
			api: {
				readData: {
					url: '/api/item/getItemList',
					serializer: (grid_param) => {
						grid_param.name = $('form.searchForm input[name=name]', $modal).val();
						grid_param.makeAgent = $('form.searchForm input[name=makeAgent]', $modal).val();
						grid_param.format = $('form.searchForm input[name=format]', $modal).val();
						grid_param.middleItemCodeId = Number($('form.searchForm .middleCodeSelect', $modal).val() ?? 0);
						grid_param.smallItemCodeId = Number($('form.searchForm .smallCodeSelect', $modal).val() ?? 0);
						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.grid = gGrid('.searchItemList', {
			columns: [
				{
					header: '생성타입',
					name: 'createType',
					className: 'cursor_pointer',
					width: '60',
					align: 'center',
					formatter: function (data) {
						return data.value == 'BASIC' ? '기본' : '자동등록';
					},
				},
				{
					header: '기기명',
					name: 'name',
					className: 'cursor_pointer',
					align: 'center',
					whiteSpace: 'pre-line',
				},
				{
					header: '기기명(영문)',
					name: 'nameEn',
					className: 'cursor_pointer',
					// width: '120',
					whiteSpace: 'pre-line',
					align: 'center',
				},
				{
					header: '제작회사',
					name: 'makeAgent',
					className: 'cursor_pointer',
					with: '120',
					whiteSpace: 'pre-line',
					align: 'center',
				},
				{
					header: '제작회사(영문)',
					name: 'makeAgentEn',
					className: 'cursor_pointer',
					width: '120',
					align: 'center',
				},
				{
					header: '형식',
					name: 'format',
					width: '120',
					whiteSpace: 'pre-line',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '기기번호',
					className: 'cursor_pointer',
					name: 'num',
					width: '150',
					align: 'center',
				},
				{
					header: '교정주기',
					name: 'caliCycle',
					width: '70',
					className: 'cursor_pointer',
					align: 'center',
					formatter: function ({ value }) {
						if (!value || value == 0) {
							return '-';
						} else {
							return `${Number(value)}개월`;
						}
					},
				},
				{
					header: '수수료',
					name: 'fee',
					width: '70',
					className: 'cursor_pointer',
					align: 'right',
					formatter: function (data) {
						return `${numberFormat(Number(data.value ?? 0))}`;
					},
				},
				// {
				// 	header: '비고',
				// 	name: 'remark',
				// 	width: '100',
				// 	className: 'cursor_pointer',
				// 	align: 'center',
				// },
			],
			pageOptions: {
				useClient: false, // 서버 페이징
				perPage: 13,
			},
			// TODO 다중선택도 고려할 것
			// rowHeaders: ['checkbox'],
			data: $modal.dataSource,
			minBodyHeight: 544,
			bodyHeight: 544,
			rowHeight: 'auto',
			scrollX: false,
			summary: {
				height: 22,
				position: 'bottom',
				columnContent: {
					name: {
						template: function () {
							return ``;
						},
					},
				},
			},
		});

		// 모달 내 그리드에 대한 이벤트
		$modal.grid.on('click', async function (e) {
			const row = $modal.grid.getRow(e.rowKey);
			if (row) {
				// 클릭 시, 해당 row의 정보를 바로 반환한다.
				$modal.param.jsonData = row;
				$modal_root.data('modal-data').click_confirm_button();
			}
		});

		// 페이지 내 이벤트
		$modal
			// 검색
			.on('submit', '.searchForm', function (e) {
				e.preventDefault();
				$modal.grid.getPagination().movePageTo(1);
			})
			// 모달 내 중분류 변경
			.on('change', '.middleCodeSelect', function () {
				const middleItemCodeId = $(this).val();
				$modal.setItemCode(middleItemCodeId);
			})
			// 입력값 초기화
			.on('click', 'button.resetBtn', function () {
				$('form.searchForm select', $modal).val(0);
				$('form.searchForm input', $modal).val('');
				$modal.grid.getPagination().movePageTo(1);
			})
			;
	}; // End of init_modal

	// 중분류와 소분류코드를 가져와서 중분류select 세팅 및 소분류코드 데이터를 초기화시킨다.
	$modal.initItemCodeSet = async () => {
		try {
			const resGetItemCodeSet = await gAjax(
				'/api/basic/getItemCodeInfos',
				{},
				{
					type: 'GET',
				}
			);
			if (resGetItemCodeSet?.code > 0) {
				const itemCodeSet = resGetItemCodeSet.data;
				if (itemCodeSet.middleCodeInfos) {
					middleItemCodeSet = itemCodeSet.middleCodeInfos;
					// 반복문으로 세팅
					const $middleCodeSelect = $('.middleCodeSelect', $modal);
					$.each(itemCodeSet.middleCodeInfos, function (index, row) {
						const option = new Option(row.codeNum, row.id);
						$middleCodeSelect.append(option);
					});
				}
				if (itemCodeSet.smallCodeInfos) {
					smallItemCodeSet = itemCodeSet.smallCodeInfos;
				}
			} else {
				console.log('호출실패');
				throw new Error('/api/basic/getItemCodeInfos 호출 실패');
			}
		} catch (xhr) {
			console.error('통신에러');
			await gApiErrorHandler(xhr);
		}
	};

	$modal.setItemCode = (middleItemCodeId, smallItemCodeId, layInitTime = 0) => {
		const $middleCodeSelect = $('.middleCodeSelect', $modal);
		if (middleItemCodeId) {
			$middleCodeSelect.val(middleItemCodeId);
		}
		const $smallCodeSelect = $('.smallCodeSelect', $modal);
		const basicOption = new Option('소분류전체', '');
		$($smallCodeSelect).find('option').remove();
		$smallCodeSelect.append(basicOption);

		setTimeout(() => {
			if (!middleItemCodeId) {
				$smallCodeSelect.val(''); // '소분류전체'로 세팅
			} else {
				if (smallItemCodeSet[middleItemCodeId] != undefined && smallItemCodeSet[middleItemCodeId].length > 0) {
					const smallItemCodes = smallItemCodeSet[middleItemCodeId];
					smallItemCodes.forEach((row, index) => {
						const option = new Option(`${row.codeNum} ${row.codeName}`, row.id);
						$smallCodeSelect.append(option);
					});
					if (smallItemCodeId > 0) {
						$smallCodeSelect.val(smallItemCodeId);
					}
				}
			}
		}, layInitTime);
	};

	// 저장
	$modal.confirm_modal = async function (e) {
		$modal_root.modal('hide');
		return $modal.param;
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
