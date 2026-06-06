$(function () {
	console.log('++ basic/agentAccountManage.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	const $bodyCandidate = $candidates.filter('.modal-body');
	if ($bodyCandidate.length) {
		$modal = $bodyCandidate.first();
	} else {
		$modal = $candidates.first();
	}
	let $modal_root = $modal.closest('.modal');

	$modal.init_modal = (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);
	};

	// ── 그리드 데이터 소스 ────────────────────────────────────────────────

	$modal.data_source = {
		api: {
			readData: {
				url: '/api/admin/agentAccount/list',
				serializer: (grid_param) => {
					grid_param.isActive   = $('select[name=isActive]', $modal).val() ?? '';
					grid_param.searchType = $('select[name=searchType]', $modal).val() ?? 'loginId';
					grid_param.keyword    = $('input[name=keyword]', $modal).val() ?? '';
					grid_param.perPage    = $('select[name=perPage]', $modal).val() ?? 25;
					return $.param(grid_param);
				},
				method: 'GET',
			},
		},
	};

	// ── 그리드 정의 ──────────────────────────────────────────────────────

	$modal.grid = gGrid('.agentAccountList', {
		columns: [
			{
				header: '업체명',
				name: 'name',
				className: 'cursor_pointer',
				align: 'center',
				sortable: true,
				minWidth: 120,
			},
			{
				header: '아이디',
				name: 'loginId',
				className: 'cursor_pointer',
				align: 'center',
				width: 140,
			},
			{
				header: '로그인횟수',
				name: 'loginCount',
				className: 'cursor_pointer',
				align: 'center',
				width: 90,
			},
			{
				header: '마지막로그인일시',
				name: 'lastLoginDatetime',
				className: 'cursor_pointer',
				align: 'center',
				width: 160,
				formatter: ({ value }) => value ? value.replace('T', ' ').substring(0, 19) : '-',
			},
			{
				header: '생성일시',
				name: 'createDatetime',
				className: 'cursor_pointer',
				align: 'center',
				width: 160,
				formatter: ({ value }) => value ? value.replace('T', ' ').substring(0, 19) : '-',
			},
			{
				header: '로그인허용유무',
				name: 'isActive',
				className: 'cursor_pointer',
				align: 'center',
				width: 120,
				formatter: ({ value }) => {
					// YnType enum은 소문자 직렬화: 'y' / 'n'
					if (value === 'n') {
						return `<span class="text-danger fw-bold">미허용</span>`;
					}
					return '허용';
				},
			},
		],
		pageOptions: {
			useClient: false,
			perPage: 25,
		},
		rowHeaders: ['checkbox'],
		minBodyHeight: 600,
		bodyHeight: 600,
		data: $modal.data_source,
	});

	// ── 이벤트 ────────────────────────────────────────────────────────────

	$modal
		// 검색 폼 submit
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();
			$modal.grid.getPagination().movePageTo(1);
		})

		// 행수 변경 시 즉시 반영
		.on('change', '.perPageSelect', function () {
			$modal.grid.getPagination().movePageTo(1);
		})

		// 등록 버튼
		.on('click', '.addAccountBtn', async function (e) {
			e.preventDefault();
			const resModal = await gModal(
				'/basic/agentAccountModify',
				{},
				{
					title: '업체계정 등록',
					size: 'lg',
					show_close_button: true,
					show_confirm_button: true,
					confirm_button_text: '저장',
				},
			);
			if (resModal) {
				$modal.grid.reloadData();
			}
		})

		// 삭제 버튼
		.on('click', '.deleteAccountBtn', async function (e) {
			e.preventDefault();

			const checkedRows = $modal.grid.getCheckedRows();
			if (checkedRows.length === 0) {
				gToast('삭제할 계정을 선택해주세요.', 'warning');
				return false;
			}

			const delIds = checkedRows.map((row) => row.id);

			const confirm = await gMessage(
				'업체계정 삭제',
				'선택한 계정을 삭제하시겠습니까?\n업체관리에서 해당 업체가 먼저 삭제된 경우에만 삭제 가능합니다.',
				'warning',
				'confirm',
			);
			if (!confirm.isConfirmed) return false;

			gLoadingMessage('삭제 처리 중입니다...');
			try {
				const res = await fetch('/api/admin/agentAccount', {
					method: 'DELETE',
					headers: { 'Content-Type': 'application/json; charset=utf-8' },
					body: JSON.stringify({ ids: delIds }),
				});
				if (!res.ok) throw res;
				const data = await res.json();
				Swal.close();
				if (data?.code === 1) {
					await gMessage('삭제 완료', '업체계정이 삭제되었습니다.', 'success', 'alert');
					$modal.grid.reloadData();
				}
			} catch (err) {
				Swal.close();
				gApiErrorHandler(err);
			}

			return false;
		});

	// 그리드 행 클릭 → 수정 모달
	$modal.grid.on('click', async function (e) {
		const row = $modal.grid.getRow(e.rowKey);
		if (row && e.columnName !== '_checked') {
			const resModal = await gModal(
				'/basic/agentAccountModify',
				{
					id: row.id,
					agentId: row.agentId,
					agentName: row.name,
					loginId: row.loginId,
					isActive: row.isActive,
				},
				{
					title: '업체계정 수정',
					size: 'lg',
					show_close_button: true,
					show_confirm_button: true,
					confirm_button_text: '저장',
				},
			);
			if (resModal) {
				$modal.grid.reloadData();
			}
		}
	});

	// ── 페이지 마운트 처리 ────────────────────────────────────────────────

	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
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
