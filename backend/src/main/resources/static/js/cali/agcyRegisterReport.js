$(function () {
	console.log('++ cali/agcyRegisterReport.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal = $candidates.first();
	let $modal_root = $modal.closest('.modal');

	let caliOrderId = null;
	let smallMapListItems = {};

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
					header: '기기명 *',
					name: 'itemName',
					width: 150,
					align: 'center',
					editor: 'text',
				},
				{
					header: '제작회사',
					name: 'itemMakeAgent',
					width: 130,
					align: 'center',
					editor: 'text',
				},
				{
					header: '형식',
					name: 'itemFormat',
					width: 120,
					align: 'center',
					editor: 'text',
				},
				{
					header: '기기번호',
					name: 'itemNum',
					width: 100,
					align: 'center',
					editor: 'text',
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
			],
			editingEvent: 'click',
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

		// 초기 1행 추가
		$modal.grid.appendRow({}, { focus: true });

		// 이벤트
		$modal
			.on('click', '.insertRows', function () {
				$modal.grid.appendRow({}, { focus: true });
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
					$modal.grid.appendRow({});
				}
			});
	};

	// 저장
	$modal.confirm_modal = async function () {
		$modal.grid.blur();

		const orderType = $('.orderType', $modal).val();
		const agcyAgent = $('.agcyAgent', $modal).val()?.trim();

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
				middleItemCodeId: row.middleItemCodeId ? Number(row.middleItemCodeId) : null,
				smallItemCodeId: row.smallItemCodeId ? Number(row.smallItemCodeId) : null,
				itemId: null,
				itemName: itemName,
				itemNameEn: null,
				itemMakeAgent: (row.itemMakeAgent ?? '').trim() || null,
				itemMakeAgentEn: null,
				itemFormat: (row.itemFormat ?? '').trim() || null,
				itemNum: (row.itemNum ?? '').trim() || null,
				itemCaliCycle: null,
				caliFee: row.caliFee ? Number(String(row.caliFee).replace(/,/g, '')) : 0,
				remark: (row.remark ?? '').trim() || null,
			});
		}

		if (!isValid) return false;

		const confirmRes = await gMessage(
			'대행성적서 등록',
			`${items.length}건을 등록하시겠습니까?`,
			'question',
			'confirm'
		);
		if (!confirmRes.isConfirmed) return false;

		gLoadingMessage();
		try {
			const sendData = {
				caliOrderId: Number(caliOrderId),
				orderType: orderType,
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
