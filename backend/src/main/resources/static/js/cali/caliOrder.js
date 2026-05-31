$(function () {
	console.log('++ cali/caliOrder.js');

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

	$modal.init_modal = (param) => {
		$modal.param = param;
	};

	// 교정접수 리스트 가져오기
	$modal.data_source = {
		api: {
			readData: {
				url: '/api/caliOrder/getOrderList',
				serializer: (grid_param) => {
					// 접수시작/종료일, 세금계산서, 접수유형, 진행상태, 검색타입, 검색키워드를 넘긴다.
					grid_param.orderStartDate = $('form.searchForm .orderStartDate', $modal).val() ?? ''; // 접수일(시작일)
					grid_param.orderEndDate = $('form.searchForm .orderEndDate', $modal).val() ?? ''; // 접수일(마지막)
					grid_param.isTax = $('form.searchForm .isTax', $modal).val() ?? ''; // 세금계산서 발행여부
					grid_param.caliType = $('form.searchForm .caliType', $modal).val() ?? ''; // 교정유형(고정표준실/현장교정)
					grid_param.statusType = $('form.searchForm .statusType', $modal).val() ?? ''; // 진행상태
					grid_param.searchType = $('form.searchForm .searchType', $modal).val() ?? ''; // 검색타입
					grid_param.keyword = $('form.searchForm', $modal).find('#keyword').val() ?? ''; // 검색키워드
					return $.param(grid_param);
				},
				method: 'GET',
			},
		},
	};

	// 그리드 정의
	$modal.grid = gGrid('.orderList', {
		columns: [
			{
				header: '긴급여부',
				name: 'priority_type',
				className: 'cursor_pointer',
				width: '60',
				align: 'center',
				formatter: function (data) {
					return data.value == 'EMERGENCY' ? '긴급' : '일반';
				},
			},
			{
				// DB상에서는 datetime이지만, 화면에는 date타입으로 표현
				header: '접수일',
				name: 'orderDate',
				className: 'cursor_pointer',
				align: 'center',
				width: '80',
				formatter: function (data) {
					return !data.value ? '' : data.value;
				},
			},
			{
				header: '접수번호',
				name: 'orderNum',
				className: 'cursor_pointer',
				width: '120',
				align: 'center',
			},
			{
				header: '신청업체',
				name: 'custAgent',
				className: 'cursor_pointer',
				// with: '150',
				align: 'center',
			},
			{
				header: '성적서발행처',
				name: 'reportAgent',
				className: 'cursor_pointer',
				// width: '150',
				align: 'center',
			},
			{
				header: '성적서발행처 주소',
				name: 'reportAgentAddr',
				className: 'cursor_pointer',
				align: 'center',
			},
			{
				header: '요청사항',
				name: 'grid_btn_remark',
				className: 'cursor_pointer',
				width: '70',
				align: 'center',
				formatter: function ({row}) {
					// 모달을 통해서 볼 수 있도록 할 것
					let btnClass = (row.remark) ? 'btn-info' : 'btn-secondary';
					return `<button type='button' class='btn ${btnClass} w-100 h-100 rounded-0' ><i class='bi bi-chat-left-text'></i></button>
					`;
				},
			},
			{
				header: '세금계산서',
				name: 'isTax',
				className: 'cursor_pointer',
				width: '80',
				align: 'center',
				formatter: function (data) {
					let row = data.row;
					// data.isCheck == 'y'라면, checked 속성값 삽입
					let checked = data.value == 'y' ? 'checked' : '';
					const id = `customSwitch_tax_${row.rowKey}`;
					return `<div class="custom-control custom-switch">
								<input type="checkbox" class="custom-control-input taxToggle" data-rowKey='${row.rowKey}' id="${id}" ${checked}>
        						<label class="custom-control-label" for="${id}"></label>
							</div>`;
				},
			},
			{
				header: '접수내역',
				name: 'grid_btn_orderDetails',
				className: 'cursor_pointer',
				align: 'center',
				width: '70',
				formatter: function (data) {
					const row = data.row;
					const cnt = row.reportTotalCnt ?? 0;

					if (cnt > 0) {
						// 성적서가 존재하는 경우: 개수 텍스트 표시 (셀 중앙정렬)
						return `<div class="w-100 h-100 d-flex align-items-center justify-content-center"><span class="fw-bold">${cnt}개</span></div>`;
					} else {
						// 성적서가 없는 경우: 파란색 버튼 표시
						return `<button type='button' class='btn btn-primary w-100 h-100 rounded-0'><i class="bi bi-pencil-square"></i></button>`;
					}
				},
			},
			// {
			// 	header: '대행',
			// 	className: 'cursor_pointer',
			// 	width: '70',
			// 	align: 'center',
			// 	formatter: function (data) {
			// 		// FIX 대행 작업 시 진행.   접수내역과 같이 개수를 표기하도록 한다.
			// 		return '';
			// 	},
			// },
			// {
			// 	header: '복사',
			// 	className: 'cursor_pointer',
			// 	width: '70',
			// 	align: 'center',
			// 	formatter: function (data) {
			// 		// FIX 자체+대행 포함하여 성적서 개수가 1개 이상이어야 표기
			// 		return '';
			// 	},
			// },
			// {
			// 	header: '교정신청서',
			// 	className: 'cursor_pointer',
			// 	width: '120',
			// 	align: 'center',
			// 	formatter: function (data) {
			// 		// 버튼 2개로 구성할 것
			// 		return `
			// 					<div class="btn-group btn-group-sm w-100 h-100" role="group" aria-label="Basic example">
			// 						<button type="button" class="h-100 rounded-0 btn btn-info downCaliOrder" data-type="excel"><i class="bi bi-download"></i></button>
			// 						<button type="button" class="h-100 rounded-0 btn sendCaliOrder btn-secondary" data-type="mail"><i class="bi bi-envelope"></i></button>
			// 					</div>
			// 				`;
			// 	},
			// },
			{
				header: '교정신청서',
				name: 'grid_btn_orderForm',
				className: 'cursor_pointer',
				width: '80',
				align: 'center',
				formatter: function (data) {
					const row = data.row;
					const cnt = row.reportTotalCnt ?? 0;

					if (cnt > 0) {
						// 성적서가 존재하는 경우에만 엑셀 아이콘 표시
						return `<button type='button' class='btn btn-link w-100 h-100 rounded-0 p-0' title='교정신청서 다운로드'><i class="bi bi-file-earmark-excel text-success fs-5"></i></button>`;
					} else {
						// 성적서가 없으면 '-' 만 표시
						return `<span>-</span>`;
					}
				},
			},
			// {
			// 	header: '완료통보서',
			// 	className: 'cursor_pointer',
			// 	width: '80',
			// 	align: 'center',
			// 	formatter: function (data) {
			// 		// 모달을 통해서 볼 수 있도록 할 것
			// 		return `
			// 					'<button type="button" class="btn w-100 h-100 rounded-0 checkCpt">
			// 						<i class="bi bi-pencil-square"></i>
			// 					</button>
			// 				`;
			// 	},
			// },
		],
		pageOptions: {
			useClient: false, // 서버 페이징
			perPage: 20,
		},
		rowHeaders: ['checkbox'],
		minBodyHeight: 663,
		bodyHeight: 663,
		data: $modal.data_source,
		rowHeight: 'auto',
		// minRowHeight: 36,
	});

	// 페이지 내 이벤트
	$modal
		// 검색
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();

			// // 1. 검색 전, 시작일/종료일 체크하도록 하기
			const orderStartDate = $('.orderStartDate', $modal).val();
			const orderEndDate = $('.orderEndDate', $modal).val();

			// 1. 날짜가 없는 경우 체크
			if (!orderStartDate || !orderEndDate) {
				gToast('접수일 조회 기간을 선택해주세요.', 'warning');
				return false;
			}

			// 2. 앞뒤 순서 체크 (YYYY-MM-DD 문자열 비교로도 충분)
			if (orderStartDate > orderEndDate) {
				gToast('조회 종료일이 시작일보다 빠를 수 없습니다.', 'warning');
				return false;
			}

			// 3. 조회기간 1년 이내로 제한
			const startDate = new Date(orderStartDate);
			const endDate = new Date(orderEndDate);

			// 두 날짜 차이(ms) → 일(day) 단위로 변환
			const DAY_MS = 1000 * 60 * 60 * 24;
			const diffTime = endDate.getTime() - startDate.getTime(); // ms 차이
			const diffDays = diffTime / DAY_MS;

			// 365일 초과면 막기 (<= 365일까지만 허용)
			if (diffDays > 365) {
				gToast('조회 기간은 최대 1년까지만 설정할 수 있습니다.', 'warning');
				return false;
			}

			$modal.grid.getPagination().movePageTo(1);
		})
		// 등록
		.on('click', '.addOrder', async function (e) {
			e.preventDefault();

			try {
				const resModal = await gModal(
					'/cali/caliOrderModify',
					{},
					{
						title: '교정접수 등록',
						size: 'xl',
						show_close_button: true,
						show_confirm_button: true,
						confirm_button_text: '저장',
					}
				);

				// 모달이 성공적으로 종료되었을 때만 그리드 갱신
				if (resModal) {
					$modal.grid.reloadData();
				}
			} catch (err) {
				console.error('gModal 실행 중 에러', err);
			}
		})
		// TODO 아직 미구현
		// 삭제
		.on('click', '.deleteOrder', async function (e) {
			e.preventDefault();
			const gUserAuth = G_USER.auth;
			if (gUserAuth !== 'admin') {
				gToast('권한이 없습니다', 'warning');
				return false;
			}
			// 1. 그리드 내 체크된 업체 확인
			const checkedRows = $modal.grid.getCheckedRows();
			if (checkedRows.length === 0) {
				gToast('삭제할 접수를 선택해주세요.', 'warning');
				return false;
			} else {
				// 각 접수의 id를 담는다.
				let delOrderIds = $.map(checkedRows, function (row, index) {
					return row.id;
				});

				// 2. 삭제유무 confirm 확인
				if (confirm('정말 삭제하시겠습니까?\n')) {
					gLoadingMessage('삭제 처리 중입니다...');

					try {
						const sendData = {
							ids: delOrderIds,
						};

						const resDelete = await gAjax(
							'/api/basic/deleteOrder',
							JSON.stringify(sendData),

							{
								type: 'DELETE',
								contentType: 'application/json; charset=utf-8',
							}
						);
						if (resDelete?.code === 1) {
							const delNames = resDelete.data || [];
							Swal.fire({
								icon: 'success',
								title: '삭제 완료',
							});
							// 그리드 갱신
							$modal.grid.reloadData();
						}
					} catch (err) {
						await gApiErrorHandler(err);
					} finally {
					}
				} else {
					return false;
				}
			}
			return false;
		})
		// 세금계산서 토글 변경
		.on('click', '.taxToggle', async function () {
			const $checkbox = $(this); // 클릭한 체크박스
			const isChecked = $checkbox.prop('checked'); // 변경된 후의 상태 (true/false)

			const isTaxConfirm = await gMessage('세금계산서 발행 여부', '세금계산서 발행 여부를 변경하시겠습나까?', 'question', 'confirm');
			if (isTaxConfirm.isConfirmed !== true) {
				$checkbox.prop('checked', !isChecked);
				return false;
			}
			const rowKey = Number($checkbox.attr('data-rowKey'));
			const rowData = $modal.grid.getRow(rowKey);
			// 상태를 변경한다.
			const resUpdateIsTax = await gAjax(
				'/api/caliOrder/updateIsTax',
				{
					id: rowData.id,
					isTax: isChecked == true ? 'y' : 'n',
				},
				{
					type: 'POST',
				}
			);

			if (resUpdateIsTax?.code > 0) {
				await gMessage('세금계산서 발행 여부 변경', '변경되었습니다', 'success', 'alert');
				$modal.grid.reloadData();
			} else {
				await gMessage('세금계산서 발행 여부 변경', '변경에 실패했습니다.', 'error', 'alert');
			}
		});

	// 그리드 이벤트 정의
	$modal.grid.on('click', async function (e) {
		const row = $modal.grid.getRow(e.rowKey);

		if (row && e.columnName != '_checked' && e.columnName != 'isTax') {
			// 접수내역 호출
			if (e.columnName == 'grid_btn_orderDetails') {
				// TODO 나중에 window.open 방식을 get이 아닌 from으로 변경할 수 있도록 할 것
				window.open(`/cali/orderDetails?caliOrderId=${row.id}&custAgent=${encodeURIComponent(row.custAgent ?? '')}&reportAgent=${encodeURIComponent(row.reportAgent ?? '')}`, '_blank');
			}
			// 교정신청서 다운로드
			else if (e.columnName == 'grid_btn_orderForm') {
				const cnt = row.reportTotalCnt ?? 0;
				if (cnt > 0) {
					gLoadingMessage('교정신청서 다운로드 중...');
					try {
						// fetch로 요청하여 서버 오류를 클라이언트에서 감지할 수 있도록 한다
						const res = await fetch(`/api/caliOrder/downloadOrderForm?id=${row.id}`);

						// HTTP 에러를 catch로 보내기 위해 ok 여부 확인
						if (!res.ok) throw res;

						// 정상 응답이면 blob으로 변환하여 브라우저 다운로드 유도
						const blob = await res.blob();
						const url = URL.createObjectURL(blob);
						const link = document.createElement('a');
						link.href = url;
						link.download = '교정신청서.xlsx';
						document.body.appendChild(link);
						link.click();
						document.body.removeChild(link);
						// 생성한 Object URL 즉시 해제 (메모리 누수 방지)
						URL.revokeObjectURL(url);
					} catch (err) {
						gApiErrorHandler(err);
					} finally {
						// 성공/실패 모두 로딩 닫기
						Swal.close();
					}
				}
			}
			// 요청사항 확인
			else if (e.columnName == 'grid_btn_remark') {
				const ele = $modal.grid.getElement(e.rowKey, e.columnName);
				if ($(ele).find('button').hasClass('btn-info')) {
					// FIX 추후 테이블명, 아이디, 필드명을 기준으로 값을 얻을 수 있는 공통 기능 만들기
					const resModal = await gModal('/basic/showContent', {
						id: row.id,
						remark: row.remark,
					}, {
						title: '요청사항 확인',
						size: 'lg',
						show_confirm_button: false,
						show_close_button: true
					});

					if (resModal) {
						$modal.grid.reloadData();
					}
				} else {
					return false;
				}
			}
			// 접수수정
			else {
				try {
					const resModal = await gModal(
						'/cali/caliOrderModify',
						{
							id: row.id,
						},
						{
							size: 'xxxl',
							title: '교정접수 수정',
							show_close_button: true,
							show_confirm_button: true,
							confirm_button_text: '저장',
						}
					);

					// 모달이 성공적으로 종료되었을 때만 그리드 갱신
					if (resModal) {
						$modal.grid.reloadData();
					}
				} catch (err) {
					console.error('gModal 실행 중 에러', err);
				}
			}
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
