$(function () {
	console.log('++ agent/searchAgentModify.js');

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

	$modal.init_modal = async (param) => {
		$modal.param = param;

		$modal.data_source = {
			api: {
				readData: {
					url: '/api/basic/getAgentList',
					// 'serializer'는 토스트 그리드에서 제공
					serializer: (grid_param) => {
						grid_param.agentFlag = $('.searchAgentFlag', $modal).val() ?? 0;
						grid_param.searchType = $('.searchType', $modal).val() ?? '';
						grid_param.keyword = $('input[name=keyword]', $modal).val() ?? '';
						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.grid = gGrid('.searchAgentList', {
			columns: [
				{
					header: '업체명',
					name: 'name',
					width: '150',
					className: 'cursor_pointer',
					align: 'center',
					sortable: true,
				},
				{
					header: '주소',
					name: 'addr',
					className: 'cursor_pointer',
					align: 'center',
					sortable: true,
				},
				{
					header: '사업자번호',
					name: 'agentNum',
					width: '150',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '대표',
					name: 'ceo',
					className: 'cursor_pointer',
					width: '100',
					align: 'center',
				},

				{
					header: '전화번호',
					name: 'agnetTel',
					width: '150',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '이메일',
					name: 'email',
					width: '200',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '담당자',
					name: 'mainManagerName',
					className: 'cursor_pointer',
					width: '100',
					align: 'center',
				},
				{
					header: '담당자 연락처',
					name: 'mainManagerTel',
					width: '150',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '담당자 이메일',
					name: 'mainManagerEmail',
					width: '200',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '업체조회',
					name: 'grid_btn_modify',
					className: 'cursor_pointer',
					width: '100',
					formatter: function (data) {
						return `<button type='button' class='btn btn-info w-100 h-100 rounded-0' ><i class='bi bi-search'></i></button>`;
					},
				},
			],
			pageOptions: {
				useClient: false, // 서버 페이징
				perPage: 15,
			},
			rowHeight: 'auto',
			minRowHeight: 36,
			minBodyHeight: 600,
			bodyHeight: 600,
			data: $modal.data_source,
		});

		// 모달 내 그리드에 대한 이벤트
		$modal.grid.on('click', async function (e) {
			const row = $modal.grid.getRow(e.rowKey);
			if (row) {
				// 업체조회 버튼
				if (e.columnName == 'grid_btn_modify') {
					const resModal = await gModal(
						'/basic/agentModify',
						{
							id: row.id,
						},
						{
							size: 'xxl',
							title: '업체 수정',
							show_close_button: true,
							show_confirm_button: true,
							confirm_button_text: '저장',
						}
					);

					// 모달이 성공적으로 닫히는 경우, 그리드 갱신
					if (resModal) {
						$modal.grid.reloadData();
					}
				}
				// 그외 클릭 시, 업체정보를 반환한다.
				else {
					const returnData = {
						id: row.id,
						name: row.name ?? '',
						nameEn: row.nameEn ?? '',
						addr: row.addr ?? '',
						addrEn: row.addrEn ?? '',
						fax: row.fax ?? '',
						tel: row.agentTel ?? '',
						calibrationCycle: row.calibrationCycle ?? 'next_cycle',
						managerName: row.mainManagerName ?? '',
						managerTel: row.mainManagerTel ?? '',
						managerEmail: row.mainManagerEmail,
						agentFlag: $modal.param.agentFlag ?? 1, // 조회한 업체형태
						agentNum: row.agentNum ?? '',           // 사업자번호 (업체계정 등록 시 필수 여부 확인용)
					};

					$modal.returnData = returnData;
					$modal_root.modal('hide');
					$modal_root.data('modal-data').click_confirm_button();
				}
			}
		});

		// 페이지 내 이벤트
		$modal
			// 검색
			.on('submit', '.searchForm', function (e) {
				e.preventDefault();
				$modal.grid.getPagination().movePageTo(1);
			});

		// 모달창 이벤트
		$modal_root
			// 업체등록
			.on('click', '.addAgent', async function (e) {
				const resModal = await gModal(
					'/basic/agentModify',
					{},
					{
						size: 'xxl',
						title: '업체 등록',
						show_close_button: true,
						show_confirm_button: true,
						confirm_button_text: '저장',
					}
				);

				// 모달이 성공적으로 닫히는 경우, 그리드 갱신
				if (resModal) {
					$modal.grid.reloadData();
				}
			});

	}; // End of init_modal

	// 저장
	$modal.confirm_modal = async function (e) {
		$modal_root.modal('hide');
		return $modal;
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
