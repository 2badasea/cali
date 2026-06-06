$(function () {
	console.log('++ member/login.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	const $bodyCandidate = $candidates.filter('.modal-body');
	if ($bodyCandidate.length) {
		// 이번 memberJoin 모달의 body
		$modal = $bodyCandidate.first();
	} else {
		// 페이지로 직접 열렸을 수도 있으니, 그때는 그냥 첫 번째 modal-view 사용
		$modal = $candidates.first();
	}
	let $modal_root = $modal.closest('.modal');

	$modal.init_modal = async (param) => {
		// 테스트 계정에 대한 안내 모달 호출(로컬 스토리지 활용)
		// FIX 추후 DB상의 환경설정을 통해 구분할 수 있도록 할 것 (가이드 모드 ON/OFF 기능)
		await $modal.checUseTesterGuide();
	};

	// 페이지에 대한 이벤트
	$modal
		// 로그인 시도
		.on('click', '.login_btn', async function (e) {
			e.preventDefault();

			// NOTE 1. POST방식 요청, 2. JSON형태의 응답, 3. try/catch 사용 4. sweet alert 요청/응답
			const $form = $('.login_form', $modal);

			const username = $('input[name=username]', $form).val().trim();
			const password = $('input[name=password]', $form).val().trim();

			if (username.length === 0) {
				gToast('아이디를 입력해주세요.', 'warning');
				return false;
			}
			if (password.length === 0) {
				gToast('비밀번호를 입력해주세요.', 'warning');
				return false;
			}

			// 로그인 버튼 비활성화
			$(this).prop('disabled', true);

			// 비동기 요청 자체를 try/catch로 감싸준다. (네트워크에러, 서버 4xx/5xx, JSON파싱 실패 등의 이유로 Promise가 reject되면 흐름이 깨지기 때문)
			try {
				const payload = { username, password };
				if ($('input[name="remember-me"]', $form).is(':checked')) {
					payload['remember-me'] = 'on';
				}
				const res = await gAjax('/api/member/login', payload);

				// 로그인 성공
				if (res.code > 0) {
					// 이동 URL 결정 (우선순위):
					// 1. 서버에서 내려준 URL (업체계정·관리자 등 유형별 고정 이동)
					// 2. redirect 파라미터 (세션 만료 후 재로그인 시 이전 페이지 복귀)
					// 3. 기본 페이지 (/cali/caliOrder)
					const urlParams = new URLSearchParams(window.location.search);
					const savedRedirect = urlParams.get('redirect');
					const targetUrl = res.data?.redirectUrl || savedRedirect || '/cali/caliOrder';

					gMessage(res.msg ?? '로그인 성공', '', 'success').then(() => {
						location.href = targetUrl;
					});
					return;
				}

				// 서버가 200으로 내려줬지만 code<=0인 “업무 실패” 케이스
				await gMessage(res?.msg ?? '로그인에 실패했습니다.', '', 'warning', 'alert');
			} catch (err) {
				// API 요청/응답 오류 → gErrorHandler + gMessage로 처리
				await gApiErrorHandler(err);
			} finally {
				// 로그인 버튼 비활성화 해제
				$(this).prop('disabled', false);
			}
		})
		// Enter 키 입력 시 로그인 시도
		.on('keyup', 'input[name=password]', (e) => {
			if (e.key === 'Enter') {
				$('.login_btn', $modal).trigger('click');
			}
		})
		// 회원가입 모달창 띄우기
		.on('click', '.join_btn', async function () {
			await gModal(
				'/member/memberJoin',
				{
					test: 'bada',
				},
				{
					size: 'lg',
					title: '회원가입',
					show_close_button: true,
					show_confirm_button: true,
					confirm_button_text: '가입신청',
				},
			).then((resData) => {});
		});

	// 테스터 가이드 호출 유무
	$modal.checUseTesterGuide = async () => {
		// 로컬스토리지 key명
		const LC_IS_USE_TESTER = 'isUseTester';

		// 오늘 날짜(로컬 스토리지와 비교)
		const getTodayYmd = () => {
			const d = new Date();
			const y = d.getFullYear();
			const m = String(d.getMonth() + 1).padStart(2, '0');
			const day = String(d.getDate()).padStart(2, '0');
			return `${y}-${m}-${day}`;
		};
		const today = getTodayYmd();
		// try/catch => 브라우저 또는 사용자 환경에 따라 로컬스토리지에 접근하는 것 자체가 차단될 수 있기 때문
		let hideYmd = null;
		try {
			hideYmd = localStorage.getItem(LC_IS_USE_TESTER);
		} catch (e) {
			hideYmd = null;
		}

		// 오늘 날짜가 저장되어 있으면 스킵
		if (hideYmd === today) {
			return false;
		}
		setTimeout(async () => {
			const resModal = await gModal(
				'/guide/testerIntro',
				{}, // 필요 파라미터 있으면 여기에
				{
					title: '안내',
					size: 'md',
					show_close_button: true,
					show_confirm_button: false,
					confirm_button_text: '확인',
					custom_btn_html_arr: [
						`
							<div class="form-check form-check-inline mb-0 mr-2">
								<input class="form-check-input js-testerIntro-hide-today" type="checkbox" id="testerIntroHideToday">
								<label class="form-check-label" for="testerIntroHideToday">오늘 그만 보기</label>
							</div>
							`,
					],
				},
			);

			// 방어코드 추가
			if (resModal == undefined) {
				return;
			}
			if (resModal.useAutoLogin != undefined && resModal.useAutoLogin == true) {
				$('input[name=username]', $modal).val(resModal.username);
				$('input[name=password]', $modal).val(resModal.password);
				setTimeout(() => {
					$('.login_btn', $modal).trigger('click');
				}, 500);
			}
		}, 300);
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
