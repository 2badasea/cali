$(function () {
	console.log('++ cali/caliOrderModify.js');

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

	let caliOrderId = null; // 접수id (수정시에만 존재)
	// TODO 어드민페이지에서 본사정보를 수정할 수 있는 경우, 고정표준실<->현자교정 변경 시 소재지 주소도 변겨되도록하기

	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);

		let gridBodyHeight = Math.floor($modal.find('.caliOrderModifyForm').height() - 145);

		// 접수자 select 구성 함수 - 호출 전에 먼저 정의되어야 함
		$modal.loadOrderManagerSelect = async () => {
			try {
				const res = await fetch('/api/basic/getInternalMembers');
				if (!res.ok) throw res;
				const resData = await res.json();
				const members = resData?.data ?? [];

				const $select = $('select[name=orderManagerId]', $modal);
				// 기본 옵션 초기화 후 직원 목록으로 채운다
				$select.empty().append($('<option>', { value: 0, text: '선택하세요.' }));
				members.forEach(m => {
					$select.append($('<option>', { value: m.id, text: m.name }));
				});
			} catch (err) {
				gApiErrorHandler(err);
			}
		};

		// 사내 직원 목록을 가져와서 접수자 select를 구성한다.
		await $modal.loadOrderManagerSelect();

		// 등록 모드: 로그인 유저를 접수자로 자동 선택
		// option에 존재하는 경우에만 설정 (목록에 없는 계정이면 기본값 유지)
		if (!($modal.param?.id > 0)) {
			const $select = $('select[name=orderManagerId]', $modal);
			if ($select.find(`option[value="${G_USER.id}"]`).length) {
				$select.val(G_USER.id);
			}
		}

		if ($modal.param?.id > 0) {
			// 옵셔널체이닝으로 체크
			caliOrderId = Number($modal.param.id);

			// NOTE async, await으로도 가능한지 확인
			try {
				// 접수정보를 가져와서 세팅한다.
				const orderInfoFetchOption = {
					method: 'GET',
					// headers: {
					// 	'Content-Type': 'application/json',
					// },
					// body: ""
				};
				const resGetInfo = await fetch(`/api/caliOrder/getCaliOrderInfo/${caliOrderId}`, orderInfoFetchOption);
				if (resGetInfo.ok) {
					const orderInfo = await resGetInfo.json();
					console.log('🚀 ~ orderInfo:', orderInfo);
					if (orderInfo.data != undefined) {
						const data = orderInfo.data;
						$modal.param.orderInfo = data;
						$modal.find('form.caliOrderModifyForm input[name], textarea[name]').setupValues(data);
						// 접수구분 세팅 호출
						$modal.setCaliType(data.caliType, data.caliTakeType);
					}
				} else {
					gToast('접수 정보를 가져오지 못 했습니다.', 'error');
				}

				// TODO 신청업체 & 성적서업체 정보는 기본적으로 readonly 처리
				$('input[name=custAgent]', $modal).prop('readonly', true);
				$('input[name=reportAgent]', $modal).prop('readonly', true);

				// 수정 모드: setupValues()로 orderManagerId가 세팅된 후 확인
				// 저장된 값이 없거나 0이면 로그인 유저로 자동 선택
				const $select = $('select[name=orderManagerId]', $modal);
				const savedManagerId = Number($select.val() ?? 0);
				if (!savedManagerId || savedManagerId === 0) {
					// option에 존재하는 경우에만 설정 (목록에 없는 계정이면 기본값 유지)
					if ($select.find(`option[value="${G_USER.id}"]`).length) {
						$select.val(G_USER.id);
					}
				}
			} catch (err) {
				await gApiErrorHandler(err);
			} finally {
			}

			// 성적서 리스트 데이터 소스 (서버사이드 페이지네이션)
			$modal.data_source = {
				api: {
					readData: {
						url: '/api/report/getOrderDetailsList',
						serializer: (grid_param) => {
							// caliOrderId를 파라미터로 전달해 해당 접수의 성적서만 조회한다.
							grid_param.caliOrderId = caliOrderId;
							return $.param(grid_param);
						},
						method: 'GET',
					},
				},
			};

			// 성적서 리스트 그리드
			$modal.grid = gGrid('.reportList', {
				columns: [
					{
						header: '구분',
						name: 'reportType',
						className: 'cursor_pointer',
						width: 60,
						align: 'center',
						formatter: function (data) {
							return data.value === 'SELF' ? '자체' : '대행';
						},
					},
					{
						header: '성적서번호',
						name: 'reportNum',
						className: 'cursor_pointer',
						width: 130,
						align: 'center',
					},
					{
						header: '기기명',
						name: 'itemName',
						className: 'cursor_pointer',
						align: 'center',
					},
					{
						header: '기기번호',
						name: 'itemNum',
						className: 'cursor_pointer',
						width: 150,
						align: 'center',
					},
					{
						header: '형식',
						name: 'itemFormat',
						className: 'cursor_pointer',
						width: 150,
						align: 'center',
					},
				],
				minBodyHeight: gridBodyHeight,
				bodyHeight: gridBodyHeight,
				data: $modal.data_source,
				emptyMessage: '등록된 성적서가 없습니다.',
				pageOptions: {
					useClient: false, // 서버사이드 페이지네이션
					perPage: 25,
				},
				rowHeight: 'auto',
			});

			// 성적서 행 클릭 시 수정 모달 호출
			$modal.grid.on('click', async function (e) {
				const row = $modal.grid.getRow(e.rowKey);
				if (!row || e.columnName === '_checked') return;

				const { id, reportNum, reportType, approvalDateTime, reportStatus, approvalStatus } = row;

				// 자체 성적서만 수정 모달 제공 (대행은 미제공)
				if (reportType === 'SELF') {
					const isModifiable = approvalDateTime || reportStatus === 'SUCCESS' || approvalStatus !== 'IDLE' ? false : true;
					const resModal = await gModal(
						'/cali/reportModify',
						{ id },
						{
							title: `성적서 수정 [성적서번호 - ${reportNum}]`,
							size: 'xxxl',
							show_close_button: true,
							show_confirm_button: isModifiable,
							confirm_button_text: '저장',
							// 성적서작성 버튼: footer 왼쪽 끝에 배치
							custom_btn_html_arr: [
								'<button type="button" class="btn btn-primary btn-sm modal-btn-write-report mr-auto"><i class="bi bi-pencil-square"></i> 성적서작성</button>',
							],
						}
					);
					// 모달이 정상적으로 닫히면 그리드를 갱신한다.
					if (resModal) {
						$modal.grid.reloadData();
					}
				} else {
					gToast('대행성적서 수정은 아직 제공되지 않습니다.', 'warning');
				}
			});
		}

		// 업체조회 함수 정의
		$modal.searchAgent = async (type, agentName) => {
			const agentFlag = type == 'custAgent' ? 1 : 4;

			// gModal 호출
			const resModal = await gModal(
				'/agent/searchAgentModify',
				{
					agentFlag: agentFlag,
					agentName: agentName,
				},
				{
					title: '업체 조회',
					size: 'xxl',
					show_close_button: true,
					show_confirm_button: false,
					confirm_button_text: '저장',
					custom_btn_html_arr: [
						`<button type="button" class="btn btn-primary addAgent btn-sm"><i class="bi bi-plus-square"></i>업체등록</button>`,
					],
				}
			);

			// 리턴값 확인
			if (resModal && resModal.returnData != undefined) {
				// 업체데이터를 세팅한다.
				const searchAgentInfo = resModal.returnData;

				// 신청업체, 성적서업체 구분
				if (agentFlag == 1) {
					// 업체명, 업체명(영문), fax, 연락처, fx, 교정주기, 주소(국/영문), 담당자(이름, 연락처, 이메일)
					$('input[name=custAgent]', $modal).val(searchAgentInfo.name);
					$('input[name=custAgentId]', $modal).val(searchAgentInfo.id);
					$('input[name=custAgentEn]', $modal).val(searchAgentInfo.nameEn);
					$('input[name=custAgentAddr]', $modal).val(searchAgentInfo.addr);
					$('input[name=custAgentAddrEn]', $modal).val(searchAgentInfo.addrEn);
					$('input[name=custAgentTel]', $modal).val(searchAgentInfo.tel);
					$('input[name=custAgentFax]', $modal).val(searchAgentInfo.fax);
					$('input[name=custManager]', $modal).val(searchAgentInfo.managerName);
					$('input[name=custManagerTel]', $modal).val(searchAgentInfo.managerTel);
					$('input[name=custManagerEmail]', $modal).val(searchAgentInfo.managerEmail);
					if (searchAgentInfo.calibrationCycle == 'SELF_CYCLE') {
						$('input[name=custAgentCaliCycle]').val('SELF_CYCLE');
					} else {
						$('input[name=custAgentCaliCycle]').val('NEXT_CYCLE');
					}
					// 신청업체명 readonly
					$('input[name=custAgent]', $modal).prop('readonly', true);
				}
				// 성적서발행처 조회 시
				else if (agentFlag == 4) {
					// 발행처 (국/영), 주소(국/영), 담당자 (이름, 연락처, 이메일), 소재지주소?
					$('input[name=reportAgentId]', $modal).val(searchAgentInfo.id);
					$('input[name=reportAgent]', $modal).val(searchAgentInfo.name);
					$('input[name=reportAgentEn]', $modal).val(searchAgentInfo.nameEn);
					$('input[name=reportAgentAddr]', $modal).val(searchAgentInfo.addr);
					$('input[name=reportAgentAddrEn]', $modal).val(searchAgentInfo.addrEn);
					$('input[name=reportManager]', $modal).val(searchAgentInfo.managerName);
					$('input[name=reportManagerTel]', $modal).val(searchAgentInfo.managerTel);
					$('input[name=reportManagerEmail]', $modal).val(searchAgentInfo.managerEmail);
					// 교정유형이 '현장교정(site)'인 경우, 성적서발행처 주소와 동일하게 소재지 주소 삽입
					if ($('input[name=caliType]:checked', $modal).val() == 'SITE') {
						$('input[name=siteAddr]', $modal).val(searchAgentInfo.addr);
						$('input[name=siteAddrEn]', $modal).val(searchAgentInfo.addrEn);
					}
					// 성적서발행처 항목 readonly 처리|
					$('input[name=reportAgent]', $modal).prop('readonly', true);
					$('input[name=reportAgentEn]', $modal).prop('readonly', true);
				}
			}
		};
	};

	// 모달 내 이벤트 정의
	$modal
		// 업체조회 클릭 시 모달 호출
		.on('click', '.searchAgent', function () {
			const $btn = $(this);
			const type = $btn.data('type');
			const agentName = $(`input[name=${type}`, $modal).val() ?? '';
			$modal.searchAgent(type, agentName);
		})
		// 업체명 항목에 enter클릭 시, 업체조회 모달 호출
		.on('keyup', '.searchAgentInput', function (e) {
			if (e.key === 'Enter' || e.keyCode === 13) {
				const type = $(this).data('type');
				const agentName = $(this).val();
				$modal.searchAgent(type, agentName);
			}
		})
		// 업체담당자 조회
		.on('click', '.agentManagerSearch', async function () {
			const agentType = $(this).data('type');
			let agentId = 0;
			let agentTypeKr = '';
			if (agentType == 'custManager') {
				agentId = $('input[name=custAgentId]', $modal).val();
				agentTypeKr = '신청업체';
			} else {
				agentId = $('input[name=reportAgentId]', $modal).val();
				agentTypeKr = '성적서발행처';
			}

			if (agentId == 0) {
				gToast(`${agentTypeKr}가 조회되지 않았습니다.<br>업체부터 선택해주세요.`, 'warning');
				return false;
			} else {
				const resModal = await gModal(
					'/agent/searchAgentManager',
					{
						agentId: agentId,
					},
					{
						// '닫기'버튼만 표시
						title: `${agentTypeKr} 조회`,
						size: 'lg',
						show_close_button: true,
						show_confirm_button: false,
						confirm_button_text: '저장',
					}
				);

				if (resModal && resModal.managerInfo != undefined) {
					const managerInfo = resModal.managerInfo;

					if (agentType == 'custManager') {
						$('input[name=custManager]', $modal).val(managerInfo.name);
						$('input[name=custManagerTel]', $modal).val(managerInfo.tel);
						$('input[name=custManagerEmail]', $modal).val(managerInfo.email);
					} else {
						$('input[name=reportManager]', $modal).val(managerInfo.name);
						$('input[name=reportManagerTel]', $modal).val(managerInfo.tel);
						$('input[name=reportManagerEmail]', $modal).val(managerInfo.email);
					}
				}
			}
		})
		// 주소정보조회
		.on('click', '.searchAddr', async function () {
			const agentType = $(this).data('type');
			let addrClass = '';
			let addrEnClass = '';
			// 신청업체 주소
			if (agentType === 'custAgent') {
				addrClass = 'custAgentAddr';
				addrEnClass = 'custAgentAddrEn';
			}
			// 성적서발행처 주소
			else {
				addrClass = 'reportAgentAddr';
				addrEnClass = 'reportAgentAddrEn';
			}
			// 프로미스 처리 (함수호출 즉시 아래 코드가 실행되는 것 방지)
			const resPost = await sample4ExecDaumPostcode((zipCode = ''), (addr = addrClass), (addrEn = addrEnClass));
			// 성적서발행처 주소의 경우, 교정유형이 '현장교정'인 경우, 소재지주소에도 할당
			const caliType = $('input[name=caliType]:checked', $modal).val();
			if (caliType == 'SITE' && agentType == 'reportAgent') {
				const addr = $(`.${addrClass}`, $modal).val();
				const addrEn = $(`.${addrEnClass}`, $modal).val();
				$('input[name=siteAddr]', $modal).val(addr);
				$('input[name=siteAddrEn]', $modal).val(addrEn);
			}
		})
		// 교정유형에 따른 접수유형 변경
		.on('change', 'input[name=caliType]', function () {
			const caliType = $(this).val();
			$modal.setCaliType(caliType);
		})
		// 업체정보 리셋
		.on('click', '.agentReset', function () {
			const agentType = $(this).data('type'); // 'cust' or 'report'
			let resetKeys = [];
			// 신청업체
			if (agentType == 'custAgent') {
				// 관련 항목들을 초기화 후,
				resetKeys = [
					'custAgentId',
					'custAgent',
					'custAgentEn',
					'custAgentTel',
					'custAgentAddr',
					'custAgentFax',
					'custAgentAddrEn',
					'custManager',
					'custManagerTel',
					'custManagerEmail',
				];
			}
			// 성적서업체
			else {
				resetKeys = [
					'reportAgentId',
					'reportAgent',
					'reportAgentEn',
					'reportAgentAddr',
					'reportAgentAddrEn',
					'custAgentAddrEn',
					'siteAddr',
					'siteAddrEn',
					'reportManager',
					'reportManagerTel',
					'reportManagerEmail',
				];
			}
			// 각 항목들의 값을 모두 초기화시킨다.
			for (const name of resetKeys) {
				if (name.includes('Id')) {
					$(`input[name=${name}]`, $modal).val(0);
				} else {
					$(`input[name=${name}]`, $modal).val('');
				}
			}
			// readonly 해제
			if (agentType == 'custAgent') {
				$('input[name=custAgent]', $modal).prop('readonly', false);
			} else {
				$('input[name=reportAgent]', $modal).prop('readonly', false);
			}
		})
		// 접수일의 연도가 변경된 경우, 공지할 것
		.on('change', 'input[name=orderDate]', function () {
			const orderDate = $(this).val();
			if (caliOrderId > 0 && $modal.param.orderInfo != undefined) {
				const originOrderDate = $modal.param.orderInfo.orderDate;
				const orderYear = orderDate.split('-')[0];
				const originOrderYear = originOrderDate.split('-')[0];
				if (orderYear != originOrderYear) {
					gToast(
						'접수일의 연도가 변경될 경우, 접수번호가 수정됩니다.<br>(결재가 진행중인 성적서가 존재할 경우 접수연도 수정 불가)',
						'warning'
					);
				}
			}
		});

	// 고정표준실, 접수유형에 따른 변경
	$modal.setCaliType = (caliType = '', caliTakeType = '') => {
		const $siteDiv = $('div.site_div', $modal);
		const $standardDiv = $('div.standard_div', $modal);
		// 고정표준실인 경우
		if (caliType == 'STANDARD') {
			$siteDiv.addClass('d-none');
			$standardDiv.removeClass('d-none');
		}
		// 현장교정인 경우
		else {
			$siteDiv.removeClass('d-none');
			$standardDiv.addClass('d-none');
		}
		// 접수유형 값이 존재하는 경우
		if (caliTakeType != '') {
			$(`input[name=caliTakeType][value=${caliTakeType}]`, $modal).prop('checked', true);
		}
		// 없는 경우엔 기본값
		else {
			if (caliType == 'STANDARD') {
				$('input[name=caliTakeType][value=SELF]', $modal).prop('checked', true);
			} else {
				$('input[name=caliTakeType][value=SITE_SELF]', $modal).prop('checked', true); // 현장교정
			}
		}
	};

	// 저장
	$modal.confirm_modal = async function (e) {
		const $form = $('.caliOrderModifyForm', $modal);
		const orderData = $form.serialize_object();
		console.log('🚀 ~ orderData:', orderData);

		// 1. 필수값 확인
		if (!orderData.orderDate) {
			gToast('접수일을 선택해주세요', 'warning');
			return false;
		}
		// 신청업체, 성적서발행처 확인
		if (!checkInput(orderData.custAgent)) {
			gToast('신청업체 정보를<br>조회 또는 입력해주세요.', 'warning');
			return false;
		}
		if (!checkInput(orderData.reportAgent)) {
			gToast('성적서발행처 정보를<br>조회 또는 입력해주세요.', 'warning');
			return false;
		}
		// 출장일시 정보가 있는 경우, 체크
		const resCheckDate = isValidateDate(orderData.btripStartDate, orderData.btripEndDate);
		if (!resCheckDate.flag) {
			const resMsg = resCheckDate.msg ?? '정보가 올바르지 않습니다.';
			gToast(`출장일시 ${resMsg}`, 'warning');
			return false;
		}

		// 업체데이터의 경우, keyin입력인 경우, 자동으로 등록된다고 안내할 것
		let custAgentAutoChk = '';
		if (!orderData.custAgentId || orderData.custAgentId == 0) {
			custAgentAutoChk = `(<span style='color: red;'>자동등록예정</span>)`;
			const custFetchOption = {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
				},
				body: JSON.stringify({ agentName: orderData.custAgent }),
			};
			// fetch api의 경우, 응답헤더까지 받고, Response객체를 만들 수 있는 시점에 resolve됨
			// resolve가 된 직후엔 본문(body)는 아직 읽지 않은 스트림 -> .json()을 통해 스트림을 끝까지 읽고
			// 최종 JS객체로 반환해야 하므로, 이 작업도 비동기. 그래서 json()도 promise를 리턴
			// await을 명시하지 않으면 파싱이 끝나지 않은 프로미스가 리턴된다.
			const resChk1 = await fetch('/api/agent/chkAgentInfo', custFetchOption);
			const resData1 = await resChk1.json();
			// 유사 업체명이 존재함
			if (resData1?.code > 0) {
				const custData = resData1.data ?? '';
				await gMessage(
					'업체명 확인',
					`<div class='text-left'>'${orderData.custAgent}'이 포함된 업체목록입니다.<br><br>'조회'가 아닌 직접 입력을 통해서 선택한 경우, 업체정보가<br>자동으로 등록되지만 중복이 발생할 수 있습니다. <br><br>${custData}</div>`,
					'warning'
				);
			}
		}

		let reportAgentAutoChk = '';
		if (!orderData.reportAgentId || orderData.reportAgentId == 0) {
			reportAgentAutoChk = `(<span style='color: red;'>자동등록예정</span>)`;
			const reportFetchOption = {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
				},
				body: JSON.stringify({ agentName: orderData.reportAgent }),
			};
			const resChk2 = await fetch('/api/agent/chkAgentInfo', reportFetchOption);
			const resData2 = await resChk2.json();
			// 유사 업체명이 존재함
			if (resData2?.code > 0) {
				const reportData = resData2.data ?? '';
				await gMessage(
					'업체명 확인',
					`<div class='text-left'>'${orderData.reportAgent}'이 포함된 업체목록입니다.<br><br>'조회'가 아닌 직접 입력을 통해서 선택한 경우, 업체정보가 자동으로 등록되지만 중복이 발생할 수 있습니다. <br><br>${reportData}</div>`,
					'warning'
				);
			}
		}
		// return false;
		const saveInfoKv = {
			'reportLang': {
				'KR': '국문',
				'EN': '영문',
				'BOTH': '국문+영문',
			},
		};

		const saveConfirmMsg = `<div class='text-left'>발행타입: ${saveInfoKv.reportLang[orderData.reportLang]}<br>신청업체${custAgentAutoChk}: ${
			orderData.custAgent
		}<br>성적서발행처${reportAgentAutoChk}: ${orderData.reportAgent}</div>`;

		// 저장버튼 비활성화 후 진행
		const $btn = $('button.btn_save', $modal_root);
		$btn.prop('disabled', true);

		const saveConfirm = await gMessage('저장하시겠습니까?', saveConfirmMsg, 'info', 'confirm');
		if (saveConfirm.isConfirmed === true) {
			orderData.id = caliOrderId;
			try {
				const saveFetchOption = {
					method: 'POST',
					headers: {
						'Content-Type': 'application/json',
					},
					body: JSON.stringify(orderData),
				};
				const resSave = await fetch('/api/caliOrder/saveCaliOrder', saveFetchOption);
				if (resSave.ok) {
					const resCode = await resSave.json();
					if (resCode?.code > 0) {
						await gMessage('저장 성공', `${resCode.msg ?? '저장에 성공했습니다.'}`, 'success', 'alert').then((d) => {
							console.log('d');
							console.log(d);
							$modal_root.data('modal-data').click_return_button();
						});
					} else {
						await gMessage('저장 실패', `${resCode.msg ?? '저장에 실패했습니다.'}`, 'error', 'alert');
					}
				} else {
					throw new Error('api 오류 발생');
				}

				// 저장이 정상적으로 이루어지면, 모달을 닫는다.
			} catch (err) {
				await gApiErrorHandler(err);
			} finally {
				$btn.prop('disabled', false);
				return false;
			}
		} else {
			$btn.prop('disabled', false);
			return false;
		}

		// 저장 시, 저장되는 정보들에 대해서 요약한 뒤 알려주기 =>

		// 업체조회가 입력인 경우, 비슷한 명의 업체가 존재하는지 알려주고 선택하도록 하기

		// 신청업체, 성적서업체의 경우, 조회된 건지 직접입력한 건지 구분해서 확인 필요
	};

	// 리턴 모달 이벤트
	$modal.return_modal = async function (e) {
		$modal.param.res = true;
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
