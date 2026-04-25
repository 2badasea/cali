$(function () {
	console.log('++ equipment/equipmentManage.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	$modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	let fieldOptions = {};

	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);

		// 검색필터 분야 세팅
		await $modal.setEquipmentField();

		// 품목 리스트 가져오기
		$modal.dataSource = {
			api: {
				readData: {
					url: '/api/equipment/getEquipmentList',
					serializer: (grid_param) => {
						grid_param.equipmentFieldId = Number($('form.searchForm', $modal).find('.equipmentFieldSelect').val()); // 분야(전체는 0으로 받음)
						grid_param.isUse = $('form.searchForm', $modal).find('.isUse').val() ?? '';
						grid_param.isDispose = $('form.searchForm', $modal).find('.isDispose').val() ?? '';
						grid_param.searchType = $('form.searchForm .searchType', $modal).val() ?? ''; // 검색타입 (전체는 ''로 넘김)
						grid_param.keyword = $('form.searchForm', $modal).find('input[name=keyword]').val().trim(); // 검색키워드
						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.grid = gGrid('.equipmentList', {
			columns: [
				{
					header: '분야',
					className: 'cursor_pointer',
					name: 'fieldName',
					width: '80',
					align: 'center',
				},
				{
					header: '관리번호',
					className: 'cursor_pointer',
					name: 'manageNo',
					width: '200',
					align: 'center',
				},
				{
					header: '장비명',
					className: 'cursor_pointer',
					name: 'name',
					// width: '200',
					align: 'center',
				},
				{
					header: '기기번호',
					className: 'cursor_pointer',
					name: 'serialNo',
					width: '200',
					align: 'center',
				},
				{
					header: '제작회사',
					className: 'cursor_pointer',
					name: 'makeAgent',
					width: '220',
					align: 'center',
				},
				{
					header: '모델명',
					className: 'cursor_pointer',
					name: 'modelName',
					width: '180',
					align: 'center',
				},
				{
					header: '관리담당(정)',
					className: 'cursor_pointer',
					name: 'primaryManager',
					width: '90',
					align: 'center',
				},
				{
					header: '관리담당(부)',
					className: 'cursor_pointer',
					name: 'secondaryManager',
					width: '90',
					align: 'center',
				},
				{
					header: '유휴여부',
					className: 'cursor_pointer',
					name: 'isUse',
					width: '70',
					align: 'center',
					formatter: ({ value }) => {
						return value == 'y' ? '' : '유휴';
					},
				},
				{
					header: '폐기여부',
					className: 'cursor_pointer',
					name: 'isDispose',
					width: '70',
					align: 'center',
					formatter: ({ value }) => {
						return value == 'y' ? '폐기' : '-';
					},
				},
				{
					header: '설치위치',
					className: 'cursor_pointer',
					name: 'install_location',
					width: '80',
					align: 'center',
				},
			],
			pageOptions: {
				useClient: false, // 서버 페이징
				perPage: 20,
			},
			rowHeaders: ['checkbox'],
			data: $modal.dataSource,
			minBodyHeight: 641,
			bodyHeight: 641,
			rowHeight: 'auto',
			scrollX: false,
			summary: {
				height: 22,
				position: 'bottom',
				columnContent: {
					manageNo: {
						template: function () {
							return ``;
						},
					},
				},
			},
		});

		// 그리드 이벤트 정의
		$modal.grid.on('click', async function (e) {
			const row = $modal.grid.getRow(e.rowKey);
			if (row && e.columnName != '_checked') {
				const resModal = await gModal(
					'/equipment/equipmentModify',
					{ id: row.id, fieldOptions: fieldOptions },
					{
						size: 'xl',
						title: '표준장비 수정',
						show_close_button: true,
						show_confirm_button: true,
						confirm_button_text: '저장',
						// 커스텀 버튼
						custom_btn_html_arr: [
							`<label class="file-event-label btn  btn-sm btn-outline-success ml-2 mt-1 mb-1">파일 업로드
							<input name="equipmentFiles"
										type="file"
										class="file-event"
										accept=".xls,.xlsx,.pdf,image/*"
										multiple
										hidden />
							</label>
							<button type="button" class="btn btn-secondary btn-sm ml-2 searchFile">파일리스트</button>
							<button type="button" class="btn btn-warning btn-sm ml-2 eq_excel_down" data-down="manage_down">표준장비이력카드</button>`,
						],
					}
				);

				if (resModal) {
					$modal.grid.reloadData();
				}
			}
		});

		// 그리드 렌더링 시, 검색결과 갯수를 표시한다.
		$modal.grid.on('response', function (e) {
			let jsonRow = JSON.parse(e.xhr.response);
			const totalCnt = jsonRow.data.pagination.totalCount ?? 0;
			const rowCnt = jsonRow.data.contents.length ?? 0;
			$modal.grid.setSummaryColumnContent('manageNo', {
				template: () => `총 ${numberFormat(totalCnt)} 건 중 ${numberFormat(rowCnt)} 건 조회`,
			});
		});
	};

	$modal.setEquipmentField = async () => {
		const $fieldSelect = $('.equipmentFieldSelect', $modal);

		const feOptions = {
			method: 'GET',
		};
		const resGetField = await fetch(`/api/equipment/getEquipmentField?isUse=y`, feOptions);
		if (resGetField.ok) {
			const resData = await resGetField.json();
			if (resData.data != undefined && resData.data.length > 0) {
				fieldOptions = resData.data;
				fieldOptions.forEach((row) => {
					const codeOption = new Option(row.name, row.id);
					$fieldSelect.append(codeOption);
				});
			}
		} else {
		}
	};

	// 페이지 내 이벤트
	$modal
		// 검색
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();

			$modal.grid.getPagination().movePageTo(1);
		})
		// 행 수 변경
		.on('change', '.rowLeng', function () {
			const rowLeng = $(this).val(); // 행 수

			if (rowLeng > 0) {
				$modal.grid.setPerPage(rowLeng);
			}
		})
		// 표준장비 등록
		.on('click', '.addEquipment', async function () {
			const resModal = await gModal(
				'/equipment/equipmentModify',
				{ fieldOptions: fieldOptions },
				{
					size: 'xl',
					title: '표준장비 등록',
					show_close_button: true,
					show_confirm_button: true,
					confirm_button_text: '저장',
					// 커스텀 버튼
					custom_btn_html_arr: [
						`<label class="file-event-label btn  btn-sm btn-outline-success ml-2 mt-1 mb-1">파일 업로드
						<input name="equipmentFiles"
									type="file"
									class="file-event"
									accept=".xls,.xlsx,.pdf,image/*"
									multiple
									hidden />
						</label>`,
					],
				}
			);
			if (resModal) {
				$modal.grid.reloadData();
			}
		})
		// 표준장비 삭제
		.on('click', '.deleteEquipment', async function () {
			const gUserAuth = G_USER.auth;
			if (gUserAuth !== 'admin') {
				gToast('권한이 없습니다', 'warning');
				return false;
			}
			const $btn = $(this);
			const checkedRows = $modal.grid.getCheckedRows();
			if (checkedRows.length === 0) {
				gToast('삭제할 장비를 선택해주세요.', 'warning');
				return false;
			}

			try {
				$btn.prop('disabled', true);
				const deleteConfirm = await gMessage('표준장비 삭제', '선택한 표준장비를 삭제하시겠습니까?', 'question', 'confirm');
				if (deleteConfirm.isConfirmed === true) {
					gLoadingMessage();
					const deletedIds = [];
					checkedRows.forEach((row) => {
						deletedIds.push(row.id);
					});
					const feOptions = {
						method: 'DELETE',
						headers: { 'Content-Type': 'application/json' },
						body: JSON.stringify({ deletedIds: deletedIds }),
					};
					const resDelete = await fetch('/api/equipment/deleteEquipment', feOptions);
					if (resDelete.ok) {
						const resData = await resDelete.json();
						console.log('🚀 ~ resData:', resData);
						if (resData?.code > 0) {
							await gMessage('표준장비 삭제', '삭제되었습니다.', 'success', 'alert');
							$modal.grid.reloadData();
						} else {
							await gMessage('표준장비 삭제', resData.msg ?? '삭제가 되지 않았습니다.', 'warning', 'alert');
						}
					} else {
						throw new Error('삭제 요청에 문제가 있습니다.<br>다시 진행하시거나 개발팀에게 문의바랍니다.');
					}
				} else {
					return false;
				}
			} catch (err) {
				await gApiErrorHandler(err);
			} finally {
				$btn.prop('disabled', false);
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
