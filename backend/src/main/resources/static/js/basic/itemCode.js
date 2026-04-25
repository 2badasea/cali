$(function () {
	console.log('++ basic/itemCode.js');

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

	$modal.init_modal = (param) => {
		$modal.param = param;

		gAjax(
			'/api/basic/getItemCodeSet',
			{
				codeLevel: 'LARGE',
			},
			{
				type: 'GET',
				success: function (res) {
					if (res?.code > 0) {
						$modal.setLargeItemCodeSet(res.data);
					}
				},
			}
		);

		// 중분류 리스트 가져오기
		$modal.middleDataSource = {
			initialRequest: false, // 최초 렌더 시 자동 조회 방지 (기본 true)
			api: {
				readData: {
					url: '/api/basic/getItemCodeList',
					serializer: (grid_param) => {
						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.middleGrid = gGrid('.middleGrid', {
			columns: [
				{
					header: '품목코드',
					name: 'codeNum',
					className: 'cursor_pointer',
					align: 'center',
					width: '80',
				},
				{
					header: '품목코드명',
					name: 'codeName',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '품목코드명(영문)',
					name: 'codeNameEn',
					className: 'cursor_pointer',
					align: 'center',
				},
			],
			pageOptions: {
				perPage: 10,
			},
			minBodyHeight: 417,
			bodyHeight: 417,
			rowHeaders: ['checkbox'],
			data: $modal.middleDataSource,
			rowHeight: 'auto',
		});

		// 중분류 리스트에서 포커스된 rowKey체크
		$modal.middleSelectedRowKey = null;
		$modal.middleGrid.on('focusChange', function (ev) {
			$('.smallKeyword', $modal).val('');
			if ($modal.middleSelectedRowKey != null && $modal.middleSelectedRowKey >= 0) {
				$modal.middleGrid.removeRowClassName($modal.middleSelectedRowKey, 'gridFocused');
			}
			$modal.middleSelectedRowKey = ev.rowKey;
			$modal.middleGrid.addRowClassName($modal.middleSelectedRowKey, 'gridFocused');

			// 포커스된 데이터의 경우, 상단에 정보를 세팅시키도록 한다.
			$('.middleCodeBody', $modal).find('input[name]').val('');
			const data = $modal.middleGrid.getRow(ev.rowKey);
			$('.middleCodeBody', $modal).find('input[name]').setupValues(data);
			$('.middleCodeBody', $modal)
				.find('input[name]')
				.prop('readonly', data.isKolasStandard == 'y');

			// 중분류 리스트 변동에 의해 소분류 그리드도 갱신한다.
			$modal.resetSmallGrid(data.id);
		});

		// 소분류 리스트 가져오기
		$modal.smallDataSource = {
			initialRequest: false, // 최초 렌더 시 자동 조회 방지 (기본 true)
			api: {
				readData: {
					url: '/api/basic/getItemCodeList',
					serializer: (grid_param) => {
						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.smallGrid = gGrid('.smallGrid', {
			columns: [
				{
					header: '품목코드',
					name: 'codeNum',
					className: 'cursor_pointer',
					align: 'center',
					width: '80',
				},
				{
					header: '품목코드명',
					name: 'codeName',
					className: 'cursor_pointer',
					align: 'center',
				},
				{
					header: '품목코드명(영문)',
					name: 'codeNameEn',
					className: 'cursor_pointer',
					align: 'center',
				},
			],
			pageOptions: {
				perPage: 5,
			},
			minBodyHeight: 217,
			bodyHeight: 217,
			data: $modal.smallDataSource,
			rowHeaders: ['checkbox'],
			rowHeight: 'auto',
		});

		// 소분류 그리드 포커싱 row 체크
		$modal.smallSelectedRowKey = null;
		$modal.smallGrid.on('focusChange', function (ev) {
			if ($modal.smallSelectedRowKey != null && $modal.smallSelectedRowKey >= 0) {
				$modal.smallGrid.removeRowClassName($modal.smallSelectedRowKey, 'gridFocused');
			}
			$modal.smallSelectedRowKey = ev.rowKey;
			$modal.smallGrid.addRowClassName($modal.smallSelectedRowKey, 'gridFocused');

			// 포커스된 데이터의 경우, 상단에 정보를 세팅시키도록 한다.
			$('.smallCodeBody', $modal).find('input[name]').val('');
			const data = $modal.smallGrid.getRow(ev.rowKey);
			$('.smallCodeBody', $modal).find('input[name]').setupValues(data);
			// 소분류이 경우, 소급성문구까지 체크한다.
			if (data.tracestatementInfo != undefined && data.tracestatementInfo) {
				const tracestatementInfo = JSON.parse(data.tracestatementInfo);
				for (const key in tracestatementInfo) {
					$(`input[name=${key}]`, $modal).val(tracestatementInfo[key] ?? '');
				}
			}
			// kolas 공인인 경우, readonly 처리를 해준다.
			$('.smallCodeBody', $modal)
				.find('input[name]')
				.prop('readonly', data.isKolasStandard == 'y');
		});

		// 대분류 세팅 (최초 페이지 렌더링 이후 진행)
		$modal.setLargeItemCodeSet = (data) => {
			const largeSelect = $('.largeCodeSelect', $modal);
			if (data.length > 0) {
				data.forEach((itemCode) => {
					const option = new Option(`${itemCode.codeNum} (${itemCode.codeName})`, itemCode.id);
					option.dataset.codeNum = itemCode.codeNum;
					largeSelect.append(option);
				});
			}
		};

		// 중분류 그리드 초기화 이벤트
		$modal.resetMiddleGrid = (largeCodeId = null) => {
			// 단순 초기화의 경우, 그리드 초기화
			if (!largeCodeId) {
				$modal.middleGrid.resetData([]); // 로컬 초기화 (중분류)
			}
			// 대분류 코드 존재 시, 하위 분류코드 세팅
			else {
				const params = {
					parentId: largeCodeId,
					codeLevel: 'MIDDLE',
					keyword: $('.middleKeyword', $modal).val().trim(),
				};
				$modal.middleGrid.readData(1, params, true);
			}
			// 중분류코드 입력값 모두 초기화
			$('.middleCodeBody input', $modal).val('').prop('readonly', false);
		};

		// 소분류 그리드 초기화 이벤트
		$modal.resetSmallGrid = (middleCodeId = null) => {
			// 단순 초기화의 경우, 그리드 초기화
			if (!middleCodeId) {
				$modal.smallGrid.resetData([]); // 로컬 초기화 (소분류)
			}
			// 중분류 코드 존재 시, 하위 분류코드 세팅
			else {
				const params = {
					parentId: middleCodeId,
					codeLevel: 'SMALL',
					keyword: $('.smallKeyword', $modal).val().trim(),
				};
				$modal.smallGrid.readData(1, params, true);
			}
			// 소분류코드 입력값 모두 초기화
			$('.smallCodeBody input', $modal).val('').prop('readonly', false);
		};
	}; // End init_modal

	// 페이지 내 이벤트
	$modal
		// 대분류관리 모달 호출
		.on('click', '.manageBig', async function () {
			const resModal = await gModal(
				'/basic/bigItemCodeModify',
				{},
				{
					size: 'lg',
					title: '대분류코드 관리',
					show_close_button: true,
					show_confirm_button: true,
				}
			);
		})
		// 대분류코드 변경에 따른 중분류 그리드 리로드
		.on('change', '.largeCodeSelect', function () {
			const value = $(this).val();
			$('.searchKeyword', $modal).val('');
			$modal.resetMiddleGrid(value); // 중분류 그리드 초기화
			$modal.resetSmallGrid(); // 소분류 그리드 초기화
		})
		// 신규 클릭
		.on('click', '.initItemCode', function () {
			const type = $(this).data('type'); // 'MIDDLE' | 'small'

			// 중분류
			if (type === 'MIDDLE') {
				$modal.resetMiddleGrid(); // 중분류 초기화
				$modal.resetSmallGrid(); // 소분류 초기화
			}
			// 소분류
			else {
				$modal.resetSmallGrid(); // 소분류 초기화
			}
		})
		// 저장 클릭
		.on('click', '.saveItemCode', async function () {
			const type = $(this).data('type'); // 'MIDDLE' | 'small'

			const sendData = [];
			const itemCodeInfo = {};
			const body = type === 'SMALL' ? 'smallCodeBody' : 'middleCodeBody';
			$(`.${body}`, $modal)
				.find('input[name]')
				.each(function (index, ele) {
					let key = $(ele).attr('name');
					let value = $(ele).val();
					// id가 존재하지 않는 신규등록일 경우, id는 null처리해서 보낸다.
					if (key == 'id' && !value) {
						value = null;
					} else if (key != 'id' && key != 'parentId') {
						value = value.trim();
					}
					itemCodeInfo[key] = value;
				});
			// 소분류코드일 경우, 소급성문구를 JSON으로 별도로 담기
			if (type === 'SMALL') {
				const tracestatementInfo = {};
				$('.tracestatementInfo', $modal).each((index, ele) => {
					const name = $(ele).attr('name');
					const value = $(ele).val() ?? '';
					tracestatementInfo[name] = value;
					itemCodeInfo.tracestatementInfo = JSON.stringify(tracestatementInfo);
				});
			}

			// 중/소 분류별 확인사항
			if (type == 'MIDDLE') {
				// 선택된 대분류가 존재하는지
				const largeCodeId = $('.largeCodeSelect', $modal).val();
				if (!largeCodeId) {
					gToast('대분류를 선택해주세요', 'warning');
					return false;
				}
				itemCodeInfo.parentId = largeCodeId;
				const largeCodeNum = $('.largeCodeSelect', $modal).find('option:selected').data('codeNum');
				// 분류코드는 정상적으로 3자리를 입력했고, 그게 대분류코드 prefix와 일치하는지?
				if (
					!checkInput(itemCodeInfo.codeNum) ||
					itemCodeInfo.codeNum.length != 3 ||
					String(itemCodeInfo.codeNum).charAt(0) != largeCodeNum
				) {
					gToast('중분류코드는 [대분류코드] + 숫자2자리만 가능합니다.', 'warning');
					return false;
				}
			}
			// 소분류
			else {
				// 중분류 선택된 값 있는지 확인
				const focusedCell = $modal.middleGrid.getFocusedCell();
				const focusedRowKey = focusedCell.rowKey;
				if (focusedRowKey == null) {
					gToast('중분류를 선택해주세요.', 'warning');
					return false;
				}
				const focusedRow = $modal.middleGrid.getRow(focusedRowKey);
				itemCodeInfo.parentId = focusedRow.id; // 상위코드 담기
				const middleCodeNum = focusedRow.codeNum;
				if (
					!checkInput(itemCodeInfo.codeNum) ||
					itemCodeInfo.codeNum.length != 5 ||
					String(itemCodeInfo.codeNum).slice(0, 3) != middleCodeNum
				) {
					gToast('소분류코드는 [중분류코드] + 숫자2자리만 가능합니다.', 'warning');
					return false;
				}
			}

			// 공통 체크사항
			if (!checkInput(itemCodeInfo.codeNum)) {
				gToast('분류코드를 입력해주세요', 'warning');
				return false;
			}
			if (!checkInput(itemCodeInfo.codeName)) {
				gToast('분류코드명을 입력해주세요', 'warning');
				return false;
			}

			// 기본데이터 넣어주기
			itemCodeInfo.codeLevel = type;
			itemCodeInfo.caliCycleUnit = 'MONTHS'; // 기본적으로 개월 단위로 할 것
			itemCodeInfo.stdCali = 12; // 기본 고정용 표준기 교정주기
			itemCodeInfo.preCali = 12; // 기본 정밀기기 교정주기
			itemCodeInfo.isKolasStandard = 'n'; // KOLAS 공인 표준코드 여부

			sendData.push(itemCodeInfo); // 배열에 담는다. List로 받기 위해

			try {
				const saveTypeKr = itemCodeInfo.id ? '수정' : '등록';
				let confirmMsg = `${type == 'MIDDLE' ? '중분류코드' : '소분류코드'} ${saveTypeKr}<br><br>`;
				confirmMsg += `분류코드: ${itemCodeInfo.codeNum}<br>분류코드명: ${itemCodeInfo.codeName}`;
				const confirm = await gMessage('분류코드 저장', confirmMsg, 'question', 'confirm');
				if (confirm.isConfirmed === true) {
					gLoadingMessage();
					const fetchOptions = {
						method: itemCodeInfo.id ? 'PATCH' : 'POST',
						headers: {
							'Content-Type': 'application/json; charset=utf-8',
						},
						body: JSON.stringify(sendData),
					};
					const resSave = await fetch('/api/basic/itemCodes', fetchOptions);
					Swal.close();
					if (resSave.ok) {
						const resData = await resSave.json();
						if (resData?.code > 0) {
							await gMessage('분류코드 저장', resData.msg ?? '저장되었습니다', 'success', 'alert');
							if (type === 'MIDDLE') {
								$modal.resetMiddleGrid();
								$modal.resetSmallGrid();
							} else {
								$modal.resetSmallGrid();
							}
						} else {
							await gMessage('분류코드 저장 실패', resData.msg ?? '실패했습니다', 'error', 'alert');
						}
					} else {
						await gMessage('분류코드 저장 실패', '저장 요청이 정상적으로 이루어지지 않았습니다.', 'error', 'alert');
					}
				}
			} catch (err) {
				console.log('에러발생');
				console.error(err);
				await gApiErrorHandler(err);
			} finally {
				Swal.close();
				return false;
			}
		})
		// 분류코드 삭제
		.on('click', '.deleteItemCode', async function () {
			const gUserAuth = G_USER.auth;
			if (gUserAuth !== 'admin') {
				gToast('권한이 없습니다', 'warning');
				return false;
			}
			const type = $(this).data('type'); // 'SMALL' | 'MIDDLE'
			const targetGrid = type === 'SMALL' ? $modal.smallGrid : $modal.middleGrid;
			const checkedRows = targetGrid.getCheckedRows();
			if (checkedRows.length === 0) {
				gToast('삭제할 분류코드를 선택해주세요.', 'warning');
				return false;
			}
			const ids = checkedRows.filter((row) => row.isKolasStandard === 'n').map((row) => row.id);
			if (checkedRows.length !== ids.length) {
				gToast('KOLAS 표준 분류코드는 삭제가 불가능합니다', 'warning');
				return false;
			}

			// 삭제가능여부를 우선 판단
			try {
				gLoadingMessage();
				const checkOptions = {
					method: 'POST',
					headers: {
						'Content-Type': 'application/json; charset=utf-8',
					},
					body: JSON.stringify({
						ids: ids,
						codeLevel: type,
					}),
				};
				const resCheck = await fetch('/api/basic/deleteItemCodeCheck', checkOptions);
				if (resCheck.ok) {
					const resJson = await resCheck.json();
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
							$modal.deleteCode(ids, type);
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
					throw resCheck;
				}
			} catch (err) {
				gApiErrorHandler(err);
			} finally {
				Swal.close();
				return false;
			}
		})
		.on('click', 'button.search', function () {
			const $btn = $(this);
			const codeLevel = $btn.data('type');

			const keywordClass = codeLevel === 'MIDDLE' ? 'middleKeyword' : 'smallKeyword';
			const keyword = $(`.${keywordClass}`, $modal).val().trim();

			let parentId;
			if (codeLevel === 'MIDDLE') {
				const largeCodeId = $('.largeCodeSelect', $modal).val();
				if (!largeCodeId) {
					gToast('대분류를 선택해주세요', 'warning');
					return false;
				}
				parentId = largeCodeId;
				$('.middleCodeBody input', $modal).val('').prop('readonly', false);
				$('.smallCodeBody input', $modal).val('').prop('readonly', false);
			} else {
				const focusedCell = $modal.middleGrid.getFocusedCell();
				const focusedRowKey = focusedCell.rowKey;
				if (focusedRowKey == null) {
					gToast('중분류를 선택해주세요.', 'warning');
					return false;
				}
				const focusedRow = $modal.middleGrid.getRow(focusedRowKey);
				parentId = focusedRow.id;
				$('.smallCodeBody input', $modal).val('').prop('readonly', false);
			}

			const targetGrid = codeLevel === 'MIDDLE' ? $modal.middleGrid : $modal.smallGrid;

			const params = {
				parentId: parentId,
				codeLevel: codeLevel,
				keyword: keyword,
			};
			targetGrid.readData(1, params, true);
		})
		.on('keyup', '.searchKeyword', function (e) {
			if (e.keyCode == 13) {
				const $btn = $(this).closest('div').find('button.search');
				$btn.trigger('click');
			}
		});

	// 분류코드 삭제 처리 콜백
	$modal.deleteCode = async (ids, type) => {
		const resDelete = await gAjax(
			'/api/basic/deleteItemCode',
			JSON.stringify({
				ids: ids,
				codeLevel: type,
			}),
			{
				type: 'DELETE',
				contentType: 'application/json; charset=utf-8',
			}
		);

		if (resDelete?.code > 0) {
			await gMessage('분류코드 삭제', '삭제되었습니다', 'success', 'alert');
			location.reload();
		} else {
			await gMessage('분류코드 삭제', '삭제에 실패했습니다.', 'error', 'alert');
			return false;
		}
	};

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
