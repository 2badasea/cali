$(function () {
	console.log('++ cali/orderDetails.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	// const $bodyCandidate = $candidates.filter('.modal-body');
	// if ($bodyCandidate.length) {
	// 	$modal = $bodyCandidate.first();
	// } else {
	// 	// 페이지로 직접 열렸을 수도 있으니, 그때는 그냥 첫 번째 modal-view 사용
	$modal = $candidates.first();
	// }
	let $modal_root = $modal.closest('.modal');

	let smallItemCodeSet = {};
	let middleItemCodeSet = [];

	let caliOrderId = null;
	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);

		caliOrderId = document.getElementById('caliOrderId').value; // 타임리프를 통해 값 초기화 (쿼리스트링 활용도 가능)
		urlSearch = new URLSearchParams(location.search); // 쿼리스트링 가져오기 (get으로 파라미터값을 가져올 수 있다.)
		const custAgent = urlSearch.get('custAgent');
		const reportAgent = urlSearch.get('reportAgent');

		// 접수상세내역 타이틀 표시
		const menuPath = `접수상세내역 - 신청업체 [${custAgent}] 성적서발행처 [${reportAgent}]`;
		$('.topbar-inner .customBreadcrumb').text(menuPath);

		// 중/소분류 세팅 작업
		await $modal.initItemCodeInfos();

		// 성적서 리스트 가져오기
		$modal.data_source = {
			api: {
				readData: {
					url: '/api/report/getOrderDetailsList',
					serializer: (grid_param) => {
						grid_param.reportType = $('form.searchForm .reportType', $modal).val() ?? ''; // 구분(자체/대행)
						grid_param.orderType = $('form.searchForm .orderType', $modal).val() ?? ''; // 전체선택은 빈 값으로 넘어옴
						grid_param.statusType = $('form.searchForm .statusType', $modal).val() ?? ''; // 진행상태
						grid_param.middleItemCodeId = Number($('form.searchForm .middleCodeSelect', $modal).val() ?? 0); // 전체선택(''), null, undefined 모두 커버
						grid_param.smallItemCodeId = Number($('form.searchForm .smallCodeSelect', $modal).val() ?? 0);
						grid_param.searchType = $('form.searchForm .searchType', $modal).val() ?? 'all'; // 검색타입
						grid_param.keyword = $('form.searchForm', $modal).find('#keyword').val() ?? ''; // 검색키워드
						grid_param.caliOrderId = caliOrderId; // 접수 id

						return $.param(grid_param);
					},
					method: 'GET',
				},
			},
		};

		// 그리드 정의
		$modal.grid = gGrid('.reportList', {
			columns: [
				{
					header: '구분',
					name: 'reportType',
					className: 'cursor_pointer',
					width: '60',
					align: 'center',
					formatter: function (data) {
						return data.value == 'SELF' ? '자체' : '대행';
					},
				},
				{
					header: '접수구분',
					name: 'orderType',
					className: 'cursor_pointer',
					width: '80',
					align: 'center',
					formatter: function (data) {
						return data.value == 'ACCREDDIT' ? '공인' : data.value == 'UNACCREDDIT' ? '비공인' : '시험';
					},
				},
				{
					header: '중분류코드',
					name: 'middleCodeNum',
					className: 'cursor_pointer',
					width: '80',
					align: 'center',
				},
				{
					header: '소분류코드',
					name: 'smallCodeNum',
					className: 'cursor_pointer',
					width: '80',
					align: 'center',
				},
				{
					header: '성적서번호',
					name: 'reportNum',
					className: 'cursor_pointer',
					width: '120',
					align: 'center',
				},
				{
					header: '기기명',
					name: 'itemName',
					className: 'cursor_pointer',
					// width: '120',
					align: 'center',
					whiteSpace: 'pre-line',
				},
				{
					header: '제작회사',
					name: 'itemMakeAgent',
					className: 'cursor_pointer',
					width: '200',
					align: 'center',
					whiteSpace: 'pre-line',
				},
				{
					header: '형식',
					name: 'itemFormat',
					className: 'cursor_pointer',
					width: '200',
					align: 'center',
					whiteSpace: 'pre-line',
				},
				{
					header: '기기번호',
					name: 'itemNum',
					className: 'cursor_pointer',
					width: '150',
					align: 'center',
					whiteSpace: 'pre-line',
				},
				{
					header: '관리번호',
					name: 'manageNo',
					className: 'cursor_pointer',
					width: '120',
					align: 'center',
				},
				{
					// 값이 아닌 formatter로 보여줄 것
					header: '진행상태',
					name: 'reportStatus',
					className: 'cursor_pointer',
					width: '70',
					align: 'center',
					formatter: function (data) {
						return reportStatusLabel(data.value);
					},
				},
				{
					// AGCY 전용: 대행성적서번호 (SELF는 null)
					header: '대행성적서번호',
					name: 'agcySelfReportNum',
					className: 'cursor_pointer',
					width: '120',
					align: 'center',
				},
				{
					// AGCY 전용: 의뢰처 (SELF는 null)
					header: '의뢰처',
					name: 'agcyAgent',
					className: 'cursor_pointer',
					width: '120',
					align: 'center',
					whiteSpace: 'pre-line',
				},
				{
					// EXCEL 파일 다운로드: SELF=signed_xlsx, AGCY=agcy_excel (없으면 null)
					header: 'EXCEL',
					name: 'xlsxFileId',
					width: '55',
					align: 'center',
					formatter: function (data) {
						if (!data.value) return '';
						return `<a href="/file/fileDown/${data.value}" onclick="event.stopPropagation()"><i class="bi bi-file-earmark-excel text-success fs-5"></i></a>`;
					},
				},
				{
					// PDF 파일 다운로드: SELF=signed_pdf, AGCY=agcy_pdf (없으면 null)
					header: 'PDF',
					name: 'pdfFileId',
					width: '55',
					align: 'center',
					formatter: function (data) {
						if (!data.value) return '';
						return `<a href="/file/fileDown/${data.value}" onclick="event.stopPropagation()"><i class="bi bi-file-earmark-pdf text-danger fs-5"></i></a>`;
					},
				},
			],
			pageOptions: {
				useClient: false, // 서버 페이징
				perPage: 20, // 기본 20. 선택한 '행 수'에 따라 유동적으로 변경	=> change 이벤트를 통해 setPerPage() 함수 호출
			},
			rowHeaders: ['checkbox'],		// 체크박스 활성화			
			minBodyHeight: 663,
			bodyHeight: 663,
			data: $modal.data_source, // 그리드의 데이터를 초기화하는 과정에서 api 호출
			rowHeight: 'auto',
		});

		// 행 색상: 완료(초록) / 취소(빨강)
		// 취소: SELF/AGCY 모두 reportStatus === 'CANCEL'
		// 완료: AGCY = reportStatus 'SUCCESS' / SELF = approvalStatus 'SUCCESS' (기술책임자 결재 완료)
		function applyRowColors() {
			$modal.grid.getData().forEach((row) => {
				const { rowKey, reportType, reportStatus, approvalStatus } = row;
				if (reportStatus === 'CANCEL') {
					$modal.grid.addRowClassName(rowKey, 'row-cancel');
				} else if ((reportType === 'AGCY' && reportStatus === 'SUCCESS') ||
					       (reportType === 'SELF' && approvalStatus === 'SUCCESS')) {
					$modal.grid.addRowClassName(rowKey, 'row-complete');
				}
			});
		}
		$modal.grid.on('response', () => {
			requestAnimationFrame(applyRowColors);
		});

		// 그리드 이벤트 정의
		$modal.grid.on('click', async function (e) {
			const row = $modal.grid.getRow(e.rowKey);
			// EXCEL/PDF 다운로드 컬럼 및 체크박스 컬럼 클릭 시 모달 비활성화
			const skipColumns = ['_checked', 'xlsxFileId', 'pdfFileId'];
			if (row && !skipColumns.includes(e.columnName)) {
				// 자체와 대행을 구분한다.
				const id = row.id;
				const reportNum = row.reportNum; // 성적서 번호
				const reportType = row.reportType; // 자체/대행 구분 -> 수정 모달 UI 구분위함
				// 자체
				if (reportType === 'SELF') {
					// 기술책임자 완료 및 결재 진행중인 건에 건은 '저장' 버튼 비활성화 (접수상세, 접수, 실무자, 기책 페이지별 구분)
					const isModifiable = row.approvalDatetime || row.approvalStatus !== 'IDLE' ? false : true;
					const resModal = await gModal(
						'/cali/reportModify',
						{
							id: id,
						},
						{
							title: `성적서 수정 [성적서번호 - ${reportNum}]`,
							size: 'xxxl',
							show_close_button: true,
							show_confirm_button: isModifiable,
							confirm_button_text: '저장',
							// 성적서작성·수리/불가/초기화 버튼은 reportModify.js init_modal에서 동적 삽입됨
						},
					);

					// 모달이 정상적으로 닫히면 갱신이 일어나도록 한다.
					if (resModal) {
						$modal.grid.reloadData();
					}
				}
				// 대행
				else {
					await gModal(
						'/cali/agcyReportModify',
						{ id: id },
						{
							title: `대행성적서 수정 [${reportNum ?? id}]`,
							size: 'xl',
							show_close_button: true,
							show_confirm_button: true,
							confirm_button_text: '저장',
						},
					);
					// 취소/초기화 버튼으로 상태가 변경될 수 있으므로 닫힘 시 항상 갱신
					$modal.grid.reloadData();
				}
			}
		});
	};

	// 페이지 내 이벤트 정의
	$modal
		// 검색
		.on('submit', '.searchForm', function (e) {
			e.preventDefault();
			$modal.grid.getPagination().movePageTo(1); // 변경된 페이지 옵션에 맞춰 페이지 렌더링
		})
		// 자체성적서 등록 모달 호출
		.on('click', '.addSelfReport', async function () {
			const resModal = await gModal(
				'/cali/registerMultiReport',
				{
					caliOrderId: caliOrderId,
					smallItemCodeSetObj: smallItemCodeSet, // 소분류 데이터
					middleItemCodeSetAry: middleItemCodeSet, // 중분류데이터
				},
				{
					title: '성적서 등록',
					size: 'xxxl',
					show_close_button: true,
					show_confirm_button: true,
					confirm_button_text: '저장',
				},
			);
			if (resModal) {
				$modal.grid.reloadData();
			}
		})
		// 대행성적서 등록 모달 호출
		.on('click', '.addAgcyReport', async function (e) {
			e.preventDefault();
			await gModal(
				'/cali/agcyRegisterReport',
				{
					caliOrderId: caliOrderId,
					smallItemCodeSetObj: smallItemCodeSet,
					middleItemCodeSetAry: middleItemCodeSet,
				},
				{
					title: '대행성적서 등록',
					size: 'xxxl',
					show_close_button: true,
					show_confirm_button: true,
					confirm_button_text: '저장',
				},
			);
			// 등록 후 또는 취소 후 닫힘 시 항상 갱신
			$modal.grid.reloadData();
		})
		// 행 수 변경
		.on('change', '.rowLeng', function () {
			const rowLeng = $(this).val(); // 행 수

			if (rowLeng > 0) {
				$modal.grid.setPerPage(rowLeng); // perPage옵션이 변경된 상태로 다시 재렌더링이 일어남
				// $modal.grid.readPage(1);	// setPerPage() 호출 후, 굳이 readPage() 호출할 필요없음.
				// setPerPage()와 아래 getPagination().movePageTo()는 잘 사용되지 않는 옵션이라 함(내용확인!)
				// $modal.grid.getPagination().movePageTo(1);	// 변경된 페이지 옵션에 맞춰 페이지 렌더링
			}
		})
		// 성적서 삭제
		.on('click', '.deleteReport', async function () {
			const gUserAuth = G_USER.auth;
			if (gUserAuth !== 'admin') {
				gToast('권한이 없습니다', 'warning');
				return false;
			}
			const checkedRows = $modal.grid.getCheckedRows();
			if (checkedRows.length === 0) {
				gToast('삭제할 성적서를 선택해주세요.', 'warning');
				return false;
			}

			// TODO 추후에 비밀번호 또는 권한이 생긴다면 사전에 체크할 것

			const $btn = $(this);
			let isFlag = true;
			const validateInfo = {};
			try {
				$btn.prop('disabled', true);

				// TODO 추후에 대행성적서가 추가되는 경우 조건 추가할 것
				$.each(checkedRows, (index, row) => {
					const orderType = row.orderType;
					const reportType = row.reportType; // 자체(self)/대행(agcy)

					// 자체와 대행을 분리한다.
					if (reportType === 'SELF') {
						if (row.workDatetime || row.approvalDatetime) {
							// FIX 결재상태가 'IDLE'인지도 체크 필요
							isFlag = false;
							gToast('결재가 진행중인 건이 존재합니다.', 'warning');
							return false;
						} else {
							if (validateInfo[orderType] != undefined && Array.isArray(validateInfo[orderType])) {
								validateInfo[orderType].push(row.id);
							}
							// 접수구분(key)에 맞는 배열이 없는 경우, 배열을 초기화해주고 id를 넣는다.
							else {
								validateInfo[orderType] = [];
								validateInfo[orderType].push(row.id);
							}
						}
					}
					// 대행
					else {
						// 대행: 완료 처리된 건만 삭제 불가 (대기/취소는 허용)
						if (row.reportStatus === 'SUCCESS') {
							isFlag = false;
							gToast('완료 처리된 대행성적서는 삭제할 수 없습니다.', 'warning');
							return false;
						} else {
							if (!Array.isArray(validateInfo['AGCY'])) {
								validateInfo['AGCY'] = [];
							}
							validateInfo['AGCY'].push(row.id);
						}
					}
				});
			} catch (err) {
				isFlag = false;
				gToast(`삭제처리 중 오류가 있습니다.<br>${err}`, 'error');
			}

			if (!isFlag) {
				$btn.prop('disabled', false);
				return false;
			}

			// gAjax와 fetch api 혼용해서 사용해볼 것
			try {
				gLoadingMessage();
				const sendData = {
					caliOrderId: caliOrderId,
					validateInfo: validateInfo,
				};
				const resValiDate = await gAjax('/api/report/isValidDelete', JSON.stringify(sendData), {
					contentType: 'application/json; charset=utf-8',
				});
				// 문제 없는 경우
				if (resValiDate?.code > 0) {
					const confirmDelete = await gMessage('성적서 삭제', '성적서를 삭제하시겠습니까?', 'question', 'confirm');

					// 삭제 OK인 경우
					if (confirmDelete.isConfirmed === true) {
						// 삭제대상  id 합치기
						const deleteIds = [];
						$.each(validateInfo, function (orderType, array) {
							// 전개연산자 ...를 활용한다.
							deleteIds.push(...array);
						});

						const options = {
							method: 'DELETE',
							headers: {
								'Content-Type': 'application/json',
							},
							body: JSON.stringify({ deleteIds: deleteIds }),
						};
						const resDelete = await fetch('/api/report/deleteReport', options);
						const resJson = await resDelete.json();
						// 삭제 성공
						if (resJson?.code > 0) {
							await gMessage('성적서 삭제', resJson.msg ?? '삭제되었습니다', 'success');
							// 그리드 갱신
							$modal.grid.reloadData();
						}
					} else {
						return false;
					}
				} else {
					// 유효하지 않은 경우, 해당 알림 안내
					gToast(resValiDate.msg ?? '삭제 검증에 실패했습니다', 'warning');
					Swal.close();
					return false;
				}
			} catch (err) {
				await gApiErrorHandler(err);
				Swal.close();
			} finally {
				$btn.prop('disabled', false);
			}

			// 선택된 성적서들이 해당 페이지에서 접수구분별 가장 마지막에 속하는지, 결재가 진행중인 건이 있는지 확인
			// 1. 브라우저 단에서 1차적으로 결재가 진행중인 건이 있는지만 판단
			// 2. api를 두 번 탈 것(서버차원에서 검증)
			// 3. 검증이 완료되었다면, 대상 id들만 삭제api로 보낼 것 (deletemapping 활용?)
		})
	// 버튼: 자체 통합수정
	// 1) 체크된 항목 없으면 warning
	// 2) 자체성적서(SELF)만 대상 (대행 포함 시 경고)
	// 3) 검증 통과 시 selfReportMultiUpdate 모달 호출
	.on('click', '.btnBulkEditSelf', async function () {
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows || checkedRows.length === 0) {
			gToast('리스트에서 항목을 선택해 주세요.', 'warning');
			return;
		}

		// 대행 성적서 포함 여부 확인
		const hasAgcy = checkedRows.some(row => row.reportType !== 'SELF');
		if (hasAgcy) {
			gToast('자체성적서(SELF)만 통합수정 가능합니다.', 'warning');
			return;
		}

		const reportIds = checkedRows.map(row => row.id);

		await gModal(
			'/cali/selfReportMultiUpdate',
			{ reportIds },
			{
				title: `통합수정 [${reportIds.length}건 선택]`,
				size: 'xl',
				show_close_button: true,
				show_confirm_button: true,
				confirm_button_text: '저장',
			}
		);

		// 모달 닫힘 후 그리드 재조회
		$modal.grid.reloadData();
	})
	// 버튼: 대행 통합수정
	// 1) 체크된 항목 없으면 warning
	// 2) 대행성적서(AGCY)만 대상 (자체 포함 시 경고)
	// 3) 검증 통과 시 agcyReportMultiUpdate 모달 호출
	.on('click', '.btnBulkEditAgcy', async function (e) {
		e.preventDefault();
		const checkedRows = $modal.grid.getCheckedRows();
		if (!checkedRows || checkedRows.length === 0) {
			gToast('리스트에서 항목을 선택해 주세요.', 'warning');
			return;
		}

		// 자체 성적서 포함 여부 확인
		const hasSelf = checkedRows.some(row => row.reportType !== 'AGCY');
		if (hasSelf) {
			gToast('대행성적서(AGCY)만 통합수정 가능합니다.', 'warning');
			return;
		}

		const reportIds = checkedRows.map(row => row.id);

		await gModal(
			'/cali/agcyReportMultiUpdate',
			{ reportIds },
			{
				title: `대행 통합수정 [${reportIds.length}건 선택]`,
				size: 'lg',
				show_close_button: true,
				show_confirm_button: true,
				confirm_button_text: '저장',
			}
		);

		// 모달 닫힘 후 그리드 재조회
		$modal.grid.reloadData();
	})
		// 중분류 변경
		.on('change', '.middleCodeSelect', function () {
			const middleCodeId = $(this).val();
			const $smallCodeSelect = $('.smallCodeSelect', $modal);
			const basicOption = new Option('소분류전체', '');
			$($smallCodeSelect).find('option').remove();
			$smallCodeSelect.append(basicOption);
			if (!middleCodeId) {
				$smallCodeSelect.val(''); // '소분류전체'로 세팅
			} else {
				if (smallItemCodeSet[middleCodeId] != undefined && smallItemCodeSet[middleCodeId].length > 0) {
					const smallItemCodes = smallItemCodeSet[middleCodeId];
					smallItemCodes.forEach((row, index) => {
						const option = new Option(`${row.codeNum}`, row.id);
						$smallCodeSelect.append(option);
					});
				}
			}
		});

	// 중분류 세팅 및 소분류코드 초기화
	$modal.initItemCodeInfos = async () => {
		try {
			const resGetItemCodeSet = await gAjax(
				'/api/basic/getItemCodeInfos',
				{},
				{
					type: 'GET',
				},
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
