$(function () {
	console.log('++ cali/agcyRegisterReport.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	let caliOrderId = null;
	let smallMapListItems = {};

	const ORDER_TYPE_ITEMS = [
		{ text: '공인', value: 'ACCREDDIT' },
		{ text: '비공인', value: 'UNACCREDDIT' },
		{ text: '시험', value: 'TESTING' },
	];

	// =========================================================
	// 업체 조회 함수: agentFlag=0 → 업체형태 전체 조회
	// =========================================================
	$modal.searchAgcyAgent = async (agentName) => {
		const resModal = await gModal(
			'/agent/searchAgentModify',
			{ agentFlag: 0, agentName: agentName },
			{
				title: '업체 조회',
				size: 'xxl',
				show_close_button: true,
				show_confirm_button: false,
				custom_btn_html_arr: [
					`<button type="button" class="btn btn-primary addAgent btn-sm"><i class="bi bi-plus-square"></i>업체등록</button>`,
				],
			}
		);

		if (resModal && resModal.returnData != null) {
			const info = resModal.returnData;
			// 선택 후 readonly 처리 (초기화 버튼으로만 해제 가능)
			$('.agcyAgent', $modal).val(info.name).prop('readonly', true);
			$('.agcyAgentId', $modal).val(info.id);
		}
	};

	$modal.init_modal = async (param) => {
		$modal.param = param;
		caliOrderId = $modal.param.caliOrderId;

		const middleList = $modal.param.middleItemCodeSetAry ?? [];
		const smallObj = $modal.param.smallItemCodeSetObj ?? {};

		// 중분류 listItems 구성
		const middleListItems = [
			{ text: '선택', value: '' },
			...middleList.map(x => ({ text: x.codeNum, value: String(x.id) })),
		];

		// 소분류 맵 구성 (middleId → listItems)
		smallMapListItems = {};
		for (const [k, v] of Object.entries(smallObj)) {
			smallMapListItems[String(k)] = [
				{ text: '선택', value: '' },
				...(v ?? []).map(x => ({ text: x.codeNum, value: String(x.id) })),
			];
		}

		// 그리드 정의
		$modal.grid = gGrid('.agcyItemList', {
			columns: [
				{
					// 행별 접수구분 (registerMultiReport 방식과 동일)
					header: '접수구분',
					name: 'orderType',
					width: 80,
					align: 'center',
					editor: { type: 'select', options: { listItems: ORDER_TYPE_ITEMS } },
					formatter: 'listItemText',
				},
				{
					header: '중분류',
					name: 'middleItemCodeId',
					width: 90,
					align: 'center',
					editor: {
						type: 'select',
						options: { listItems: middleListItems },
					},
					// relations: 중분류 선택 시 소분류 자동 필터링
					relations: [
						{
							targetNames: ['smallItemCodeId'],
							listItems({ value }) {
								return smallMapListItems[String(value)] || [{ text: '선택', value: '' }];
							},
							disabled({ value }) {
								return !value;
							},
						},
					],
					formatter: 'listItemText',
				},
				{
					header: '소분류',
					name: 'smallItemCodeId',
					width: 90,
					align: 'center',
					editor: {
						type: 'select',
						options: { listItems: [] }, // relations가 동적으로 채움
					},
					formatter: 'listItemText',
				},
				{
					// 기기명: Enter 시 품목 조회 모달 호출 (itemSearchEditor)
					header: '기기명 *',
					name: 'itemName',
					width: 230,
					align: 'center',
					editor: itemSearchEditor,
				},
				{
					header: '제작회사',
					name: 'itemMakeAgent',
					width: 210,
					align: 'center',
					editor: itemSearchEditor,
				},
				{
					header: '형식',
					name: 'itemFormat',
					width: 200,
					align: 'center',
					editor: itemSearchEditor,
				},
				{
					header: '기기번호',
					name: 'itemNum',
					width: 180,
					align: 'center',
					editor: itemSearchEditor,
				},
				{
					header: '수수료',
					name: 'caliFee',
					width: 80,
					align: 'right',
					editor: 'text',
					formatter: ({ value }) => (value ? numberFormat(value) : ''),
				},
				{
					header: '비고',
					name: 'remark',
					align: 'center',
					editor: 'text',
				},
				// itemSearchEditor가 품목 선택 시 자동으로 세팅하는 숨김 컬럼
				{ name: 'itemId', hidden: true },
				{ name: 'itemNameEn', hidden: true },
				{ name: 'itemMakeAgentEn', hidden: true },
				{ name: 'itemCaliCycle', hidden: true },
			],
			editingEvent: 'click',
			pageOptions: false,   // 페이지네이션 비활성화 (스크롤 방식)
			rowHeaders: ['checkbox'],
			minBodyHeight: 400,
			bodyHeight: 400,
			rowHeight: 'auto',
		});

		// 중분류 변경 시 소분류 초기화
		$modal.grid.on('afterChange', (ev) => {
			ev.changes.forEach(({ rowKey, columnName }) => {
				if (columnName === 'middleItemCodeId') {
					$modal.grid.setValue(rowKey, 'smallItemCodeId', '');
				}
			});
		});

		// 초기 1행 추가 (접수구분 기본값: 공인)
		$modal.grid.appendRow({ orderType: 'ACCREDDIT' }, { focus: true });

		// 이벤트 바인딩
		$modal
			.on('click', '.insertRows', function () {
				$modal.grid.appendRow({ orderType: 'ACCREDDIT' }, { focus: true });
			})
			.on('click', '.deleteRows', function () {
				$modal.grid.blur();
				const checked = $modal.grid.getCheckedRowKeys();
				if (!checked.length) {
					gToast('삭제할 행을 선택해주세요.', 'warning');
					return;
				}
				checked.forEach(key => $modal.grid.removeRow(key));
				if ($modal.grid.getRowCount() === 0) {
					$modal.grid.appendRow({ orderType: 'ACCREDDIT' });
				}
			})
			// 조회 버튼 클릭 → 업체 조회 모달
			.on('click', '.searchAgcyAgent', function () {
				const agentName = $('.agcyAgent', $modal).val()?.trim() ?? '';
				$modal.searchAgcyAgent(agentName);
			})
			// 업체명 input에서 Enter → 업체 조회 모달
			.on('keydown', '.agcyAgentInput', function (e) {
				if (e.key === 'Enter' || e.keyCode === 13) {
					e.preventDefault();
					const agentName = $(this).val()?.trim() ?? '';
					$modal.searchAgcyAgent(agentName);
				}
			})
			// 초기화 버튼 → 업체 선택 해제 및 readonly 해제
			.on('click', '.agcyAgentReset', function () {
				$('.agcyAgent', $modal).val('').prop('readonly', false);
				$('.agcyAgentId', $modal).val('0');
			});
	};

	// 저장
	$modal.confirm_modal = async function () {
		$modal.grid.blur();

		const agcyAgent = $('.agcyAgent', $modal).val()?.trim();
		const agcyAgentId = $('.agcyAgentId', $modal).val() ?? '0';

		if (!agcyAgent) {
			gToast('대행의뢰처를 입력해주세요.', 'warning');
			return false;
		}

		const rows = $modal.grid.getData();
		if (!rows || rows.length === 0) {
			gToast('등록할 기기를 1건 이상 입력해주세요.', 'warning');
			return false;
		}

		const items = [];
		let isValid = true;

		for (const row of rows) {
			const itemName = (row.itemName ?? '').trim();
			if (!itemName) {
				gToast('기기명은 필수입니다.', 'warning');
				isValid = false;
				break;
			}
			items.push({
				orderType: row.orderType || 'ACCREDDIT',
				middleItemCodeId: row.middleItemCodeId ? Number(row.middleItemCodeId) : null,
				smallItemCodeId: row.smallItemCodeId ? Number(row.smallItemCodeId) : null,
				itemId: row.itemId ? Number(row.itemId) : null,
				itemName: itemName,
				itemNameEn: (row.itemNameEn ?? '').trim() || null,
				itemMakeAgent: (row.itemMakeAgent ?? '').trim() || null,
				itemMakeAgentEn: (row.itemMakeAgentEn ?? '').trim() || null,
				itemFormat: (row.itemFormat ?? '').trim() || null,
				itemNum: (row.itemNum ?? '').trim() || null,
				// itemSearchEditor가 세팅하는 값. 빈 문자열이면 null 처리
				itemCaliCycle: (row.itemCaliCycle && row.itemCaliCycle !== '') ? Number(row.itemCaliCycle) : null,
				caliFee: row.caliFee ? Number(String(row.caliFee).replace(/,/g, '')) : 0,
				remark: (row.remark ?? '').trim() || null,
			});
		}

		if (!isValid) return false;

		// 업체를 직접 입력한 경우 안내 메시지 포함
		let confirmMsg = `${items.length}건을 등록하시겠습니까?`;
		if (!agcyAgentId || agcyAgentId === '0') {
			confirmMsg +=
				'<br><br>대행의뢰처를 직접 입력하였습니다.<br>' +
				'업체 조회를 통해 선택하지 않은 경우, 일치하는 업체가 없으면 신규로 등록될 수 있습니다.';
		}

		const confirmRes = await gMessage('대행성적서 등록', confirmMsg, 'question', 'confirm');
		if (!confirmRes.isConfirmed) return false;

		gLoadingMessage();
		try {
			const sendData = {
				caliOrderId: Number(caliOrderId),
				agcyAgent: agcyAgent,
				items: items,
			};
			const res = await gAjax('/api/report/addAgcyReport', JSON.stringify(sendData), {
				contentType: 'application/json; charset=utf-8',
			});
			if (res?.code > 0) {
				await gMessage('대행성적서 등록', res.msg ?? '등록되었습니다.', 'success');
				$modal_root.modal('hide');
				return true;
			} else {
				await gMessage('등록 실패', res?.msg ?? '등록에 실패했습니다.', 'warning');
			}
		} catch (err) {
			await gApiErrorHandler(err);
		} finally {
			Swal.close();
		}
		return false;
	};

	$modal.data('modal-data', $modal);
	$modal.addClass('modal-view-applied');
	if ($modal.hasClass('modal-body')) {
		setTimeout(() => {
			const p = $modal.data('param') || {};
			$modal.init_modal(p);
			if (typeof $modal.grid == 'object') $modal.grid.refreshLayout();
		}, 200);
	}

	if (typeof window.modal_deferred == 'object') {
		window.modal_deferred.resolve('script end');
	} else {
		if (!$modal_root.length) initPage($modal);
	}
});
