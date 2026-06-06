$(function () {
	console.log('++ basic/agentAccountModify.js');

	const $candidates = $('.modal-view:not(.modal-view-applied)');
	let $modal;
	const $bodyCandidate = $candidates.filter('.modal-body');
	if ($bodyCandidate.length) {
		$modal = $bodyCandidate.first();
	} else {
		$modal = $candidates.first();
	}
	let $modal_root = $modal.closest('.modal');

	// param.id 유무로 등록/수정 구분
	let isCreateMode = true;

	// ── 초기화 ────────────────────────────────────────────────────────────

	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);

		isCreateMode = !(param?.id > 0);

		if (!isCreateMode) {
			// 수정 모드: DB에서 최신 데이터 조회 후 세팅
			try {
				const res = await fetch(`/api/admin/agentAccount/${param.id}`);
				if (!res.ok) throw res;
				const resData = await res.json();
				const data = resData?.data;

				if (data) {
					$('input[name=agentName]', $modal).val(data.agentName ?? '');
					$('input[name=agentId]', $modal).val(data.agentId ?? 0);
					$('input[name=loginId]', $modal).val(data.loginId ?? '');
					$('select[name=isActive]', $modal).val(data.isActive ?? 'n');
				}
			} catch (err) {
				gApiErrorHandler(err);
				return;
			}

			// 수정 모드: 아이디 변경 불가
			$('input[name=loginId]', $modal).prop('readonly', true).addClass('bg-light');

			// 비밀번호는 선택 입력이므로 required 표시(*) 제거
			$modal.find('.create-only').text('');
		}
	};

	// ── 업체 조회 ─────────────────────────────────────────────────────────

	/**
	 * searchAgentModify 모달을 호출해 업체를 선택한다.
	 * 사업자번호가 없는 업체는 gToast 안내 후 return.
	 */
	$modal.searchAgent = async () => {
		const resModal = await gModal(
			'/agent/searchAgentModify',
			{
				agentFlag: 0,  // 업체유형 무관 전체 표시
				agentName: $('input[name=agentName]', $modal).val() ?? '',
			},
			{
				title: '업체 조회',
				size: 'xxl',
				show_close_button: true,
				show_confirm_button: false,
			},
		);

		if (resModal && resModal.returnData) {
			const info = resModal.returnData;

			// 사업자번호 없는 업체는 선택 불가
			if (!info.agentNum || info.agentNum.trim() === '') {
				gToast('사업자번호가 없는 업체는 선택할 수 없습니다.', 'warning');
				return;
			}

			$('input[name=agentName]', $modal).val(info.name);
			$('input[name=agentId]', $modal).val(info.id);
		}
	};

	// 조회 버튼 클릭
	$modal.on('click', '.searchAgentBtn', function () {
		$modal.searchAgent();
	});

	// ── 저장 (confirm_modal) ────────────────────────────────────────────

	$modal.confirm_modal = async function () {
		const agentId    = Number($('input[name=agentId]', $modal).val());
		const agentName  = $('input[name=agentName]', $modal).val().trim();
		const loginId    = $('input[name=loginId]', $modal).val().trim();
		const pwd        = $('input[name=pwd]', $modal).val();
		const pwdConfirm = $('input[name=pwdConfirm]', $modal).val();
		const isActive   = $('select[name=isActive]', $modal).val();

		// ── 공통 검증 ────────────────────────────────────────────────────

		// 업체 선택 필수
		if (!(agentId > 0)) {
			gToast('업체를 조회 버튼으로 선택해주세요.', 'warning');
			return false;
		}

		// 로그인허용유무 필수
		if (!isActive || (isActive !== 'y' && isActive !== 'n')) {
			gToast('로그인허용유무를 선택해주세요.', 'warning');
			return false;
		}

		// ── 등록 모드 전용 검증 ─────────────────────────────────────────

		if (isCreateMode) {
			if (!loginId) {
				gToast('아이디를 입력해주세요.', 'warning');
				return false;
			}
			if (!checkLoginId(loginId)) {
				gToast('아이디는 영어 소문자로 시작하며 4~20자로 구성되어야 합니다.', 'warning');
				return false;
			}
			if (!pwd) {
				gToast('비밀번호를 입력해주세요.', 'warning');
				return false;
			}
			if (!checkPwd(pwd)) {
				gToast('비밀번호는 소문자·대문자·숫자를 각 1자 이상 포함하여 8~20자로 구성되어야 합니다.', 'warning');
				return false;
			}
			if (pwd !== pwdConfirm) {
				gToast('비밀번호와 비밀번호 확인이 일치하지 않습니다.', 'warning');
				return false;
			}
		}

		// ── 수정 모드: 비밀번호 입력 시에만 검증 ──────────────────────

		if (!isCreateMode && pwd) {
			if (!checkPwd(pwd)) {
				gToast('비밀번호는 소문자·대문자·숫자를 각 1자 이상 포함하여 8~20자로 구성되어야 합니다.', 'warning');
				return false;
			}
			if (pwd !== pwdConfirm) {
				gToast('비밀번호와 비밀번호 확인이 일치하지 않습니다.', 'warning');
				return false;
			}
		}

		// ── API 호출 ─────────────────────────────────────────────────────

		gLoadingMessage('저장 중입니다...');
		try {
			let url, method, body;

			if (isCreateMode) {
				url    = '/api/admin/agentAccount';
				method = 'POST';
				body   = JSON.stringify({
					agentId,
					name: agentName,
					loginId,
					pwd,
					isActive,
				});
			} else {
				url    = `/api/admin/agentAccount/${$modal.param.id}`;
				method = 'PATCH';
				body   = JSON.stringify({
					agentId,
					name: agentName,
					// 비밀번호 미입력 시 null 전송 → 서비스에서 유지 처리
					pwd: pwd || null,
					isActive,
				});
			}

			const res = await fetch(url, {
				method,
				headers: { 'Content-Type': 'application/json; charset=utf-8' },
				body,
			});
			if (!res.ok) throw res;

			Swal.close();
			const label = isCreateMode ? '등록' : '수정';
			await gMessage('저장 완료', `업체계정이 ${label}되었습니다.`, 'success', 'alert');
			$modal_root.modal('hide');
			return true;

		} catch (err) {
			Swal.close();
			gApiErrorHandler(err);
			return false;
		}
	};

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
