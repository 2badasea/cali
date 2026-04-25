$(function () {
	console.log('++ equipment/equipmentModify.js');

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

	let equipmentId; // 표준장비 id
	let fieldOptions = []; // 분야 항목
	let previewUrl = null; // 미리보기 이미지 객체

	$modal.init_modal = async (param) => {
		$modal.param = param;
		console.log('🚀 ~ $modal.param:', $modal.param);
		fieldOptions = $modal.param.fieldOptions ?? [];

		// 분야 세팅
		await $modal.setFieldCode();

		// 수정인 경우 데이터 세팅
		if ($modal.param?.id > 0) {
			equipmentId = Number($modal.param.id);

			try {
				const resGetInfo = await gAjax(
					`/api/equipment/getEquipmentInfo/${equipmentId}`,
					{},
					{
						type: 'GET',
					}
				);
				if (resGetInfo?.code > 0) {
					const equipInfo = resGetInfo.data ?? {};
					if (equipInfo) {
						// 모달창 정보
						const info = equipInfo.data;
						const uploadFileCnt = equipInfo.uploadFileCnt; // 표준장비 첨부파일 업롣 갯수
						const equipImgPath = equipInfo.equipImgPath; // 표준장비 이미지 경로

						info.purchasePrice = comma(info.purchasePrice);
						$('.equipmentModifyForm', $modal).find('input[name], select[name], textarea[name]').setupValues(info);

						// 이미지 경로가 있는 경우 표시
						if (equipImgPath) {
							$modal.find('.equipmentImg').attr('src', equipImgPath).css('display', 'block');
						}

						// 첨부파일 갯수가 있는 경우,
						if (uploadFileCnt > 0) {
							const $fileListBtn = $('.searchFile', $modal_root);
							$fileListBtn.val(uploadFileCnt).removeClass('btn-secondary').addClass('btn-success');
						}
					}
				}
			} catch (xhr) {
				await gApiErrorHandler(xhr);
			} finally {
			}

			// 파일이미지 존재여부 확인 후, 존재할 경우 '이미지  삭제' 가리기

			$('.deleteImgFile', $modal).addClass('d-none');
		}
		// 등록인 경우
		else {
			$('.deleteImgFile', $modal).addClass('d-none');
		}
	};

	// 모달 내 이벤트 정의
	$modal
		.on('keyup', '.comma', function () {
			const str = $(this).val();
			try {
				if (str == '') {
					return;
				}
				str = String(str);
				var i = parseInt(str);
				var str1 = ('' + Math.abs(i)).replace(/(\d)(?=(?:\d{3})+(?!\d))/g, '$1,');
				if (0 > i) {
					str1 = '-' + str1;
				}
				if ('NaN' == str1) {
					str1 = 0;
				}
				return str1;
			} catch (ex) {}
			return str;
		})
		// 이미지 변경
		.on('change', 'input[name=equipmentImgFile]', function (e) {
			const file = e.target.files?.[0];
			if (!file) {
				return false;
			}
			const $newInput = $(this).clone();
			$newInput.val('');

			if (!file.type.startsWith('image/')) {
				gToast('이미지 파일만 업로드 가능합니다.', 'warning');
				 // input 교체 => 인터넷 익스플로러(IE) 시절의 호환성 때문에 사용되던 옛날방식
				$(this).replaceWith($newInput);
				return false;
			}
			// 미리보기 이미지 객체가 존재하는 경우, 삭제시킨다.
			if (previewUrl) {
				URL.revokeObjectURL(previewUrl);
			}
			previewUrl = URL.createObjectURL(file);

			$modal.find('.equipmentImg').attr('src', previewUrl).css('display', 'block');
		})
		// 이미지 삭제
		.on('click', '.deleteImgFile', async function () {});

	// 분야 세팅
	$modal.setFieldCode = async () => {
		const $fieldSelect = $('.equipmentFieldSelect', $modal);
		if (fieldOptions.length > 0) {
			fieldOptions.forEach((obj) => {
				const option = new Option(obj.name, obj.id);
				$fieldSelect.append(option);
			});
		}
	};

	// 저장
	$modal.confirm_modal = async function (e) {
		const $form = $('.equipmentModifyForm', $modal);
		const fd = new FormData($form[0]);
		const $btn = $('.btn_save', $modal_root);

		$btn.prop('disabled', true);
		let isValid = true;
		try {
			const name = fd.get('name'); // 장비명
			if (!checkInput(name)) {
				gToast('장비명을 입력해주세요.', 'warning');
				$('input[name=name]', $modal).focus();
				isValid = false;
			}

			const equipmentFieldId = fd.get('equipmentFieldId'); // 분야
			if (!equipmentFieldId || equipmentFieldId == 0) {
				gToast('분야를 선택해주세요', 'warning');
				$('select[name=equipmentFieldId]', $modal).focus();
				isValid = false;
			}

			// 도래알림일
			const dueNotifyDays = fd.get('dueNotifyDays');
			if (!dueNotifyDays || isNaN(dueNotifyDays) || Number(dueNotifyDays) == 0) {
				gToast('도래알림일(일수)를 입력하세요.', 'warning');
				$('select[name=dueNotifyDays]', $modal).focus();
				isValid = false;
			}

			// 구입 가격이 존재하는데, comma가 있는 경우 체크
			const purchasePrice = fd.get('purchasePrice');
			if (purchasePrice) {
				fd.set('purchasePrice', uncomma(purchasePrice));
			}

			// 교정주기 선택값이 없는 경우 null로 처리
			if (!fd.get('caliCycleMonths')) {
				fd.delete('caliCycleMonths');
				// fd.set('caliCycleMonths', null);	// FormData객체는 null을 문자열 "null" 즉, String으로 받게 된다.
			}

			// 관리부서 선택값이 없는 경우, null로 취급
			if (!fd.get('manageDepartmentId')) {
				// fd.set('manageDepartmentId', null);	// FormData객체는 null을 문자열 "null" 즉, String으로 받게 된다.
				fd.delete('manageDepartmentId');
			}

			// 첨부파일 확인

			const uploadFiles = $('input[name="equipmentFiles"]', $modal_root)[0];
			const files = uploadFiles?.files ? Array.from(uploadFiles.files) : [];

			// 예: 최대 5개 제한 (이미 change에서 검증해도 한번 더 안전장치)
			if (files.length > 5) {
				gToast('첨부파일은 한번에 5개까지만 업로드 가능합니다', 'warning');
				isValid = false;
			}

			// 첨부파일 담기
			if (files.length > 0) {
				files.forEach((file) => {
					fd.append('equipmentFiles', file); // 같은 key로 여러 번 append → 서버에서 배열/리스트로 받음
				});
			}

			// formdata 값 확인
			for (const [key, value] of fd.entries()) {
				console.log('key: ', key, ' value: ', value);
			}
		} catch (err) {
			gToast(`입력 항목에 오류가 있습니다.<br>${err}`, 'warning');
			isValid = false;
		}

		if (!isValid) {
			$btn.prop('disabled', false);
			return false;
		}

		// 등록인 경우 formData에 id 자체를 key로도 담지 않는다. null을 주더라도 formdata는 서버로 전송되는 과정에서 문자열 "null"로 처리됨
		if (Number(equipmentId) > 0) {
			fd.set('id', equipmentId);
		} else {
			fd.delete('id');
		}

		$btn.prop('disabled', true);
		const saveConfirm = await gMessage('표준장비 저장', '저장하시겠습니까?', 'question', 'confirm');
		if (saveConfirm.isConfirmed === true) {
			gLoadingMessage();

			try {
				const feOption = {
					method: 'POST',
					body: fd,
				};
				const resSave = await fetch('/api/equipment/saveEquipment', feOption);
				if (resSave.ok) {
					const resData = await resSave.json();
					if (resData?.code > 0) {
						await gMessage('표준장비 저장', '저장에 성공했습니다.', 'success', 'alert');
						$modal_root.modal('hide');
						return true;
					} else {
						await gMessage('표준장비 저장', resData.msg ?? '실패했습니다.', 'warning', 'alert');
						return false;
					}
				} else {
					console.log('fetch not ok!');
					throw { xhr: { status: resSave.status, responseJSON: 'fetch 통신 오류' } };
				}
			} catch (xhr) {
				await gApiErrorHandler(xhr);
				return false;
			} finally {
				Swal.close();
				$btn.prop('disabled', false);
			}
		} else {
			$btn.prop('disabled', false);
			return false;
		}
	};

	// modal_root에 대한 이벤트 (커스텀 버튼 등)
	$modal_root
		.on('change', 'input[name=equipmentFiles]', function (e) {
			const inputEl = this; // 실제 DOM input
			const $input = $(this);

			const files = inputEl.files; //  파일 객체 접근
			const MAX_FILES = 5; // 한 번에 최대 허용 갯수
			const MAX_SIZE = 20 * 1024 * 1024; // 최대 용량 20MB (개당)

			// 허용 확장자
			const allowedExt = new Set(['xls', 'xlsx', 'pdf', 'jpg', 'jpeg', 'png']);

			// 허용 MIME (엑셀은 브라우저/OS에 따라 type이 빈 값인 경우도 있어 확장자 fallback 병행)
			const allowedMimes = new Set([
				'application/pdf',
				'application/vnd.ms-excel',
				'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
			]);

			const $newInput = $input.clone().val('');

			// NOTE 선택자를 통해 제이쿼리 객체가 된 경우, 바로 files로 프로퍼티를 호출하지 못 함. $input[0].files 로 해야 함.
			// const $input = $(this);
			// const files = $input.files;
			if (!files || files.length == 0) {
				return false;
			}

			// 한 번에 최대 5개 체크
			if (files.length > MAX_FILES) {
				gToast(`최대 ${MAX_FILES}개까지만 업로드할 수 있습니다.`, 'warning');
				$input.replaceWith($newInput);
				return false;
			}

			// 파일 크기 및 타입 체크 (index가 필요없고, 도중에 break, continue를 사용할 수 있어서 for...of 사용)
			for (const file of files) {
				const type = file.type || '';
				const size = file.size;
				const name = file.name || '';
				const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : '';

				if (size > MAX_SIZE) {
					gToast(`파일 용량은 최대 20MB까지 허용됩니다.<br>(초과 파일: ${name})`, 'warning');
					$input.replaceWith($newInput);
					return false;
				}

				const isImage = type.startsWith('image/'); // 이미지인지
				const isAllowedByMime = allowedMimes.has(type); // 허용되는 mime타입인지
				const isAllowedByExt = allowedExt.has(ext); // 허용되는 확장자인지

				if (!(isImage || isAllowedByMime || isAllowedByExt)) {
					gToast(
						`허용되지 않은 파일 형식입니다.<br>엑셀(.xls/.xlsx), PDF(.pdf), 이미지 파일만 가능합니다.<br>(문제 파일: ${name})`,
						'warning'
					);
					$input.replaceWith($newInput);
					return false;
				}
			}

			gToast('파일이 추가되었습니다<br>저장 시 반영됩니다', 'success');
		})
		// 첨부파일 조회 
		.on('click', '.searchFile', async function () {
			const $btn = $(this);
			if ($btn.val() > 0) {
				// gModal 호출하기
				await gModal(
					'/basic/fileList',
					{
						refTableName: 'standard_equipment',
						refTableId: equipmentId,
					},
					{
						size: 'lg',
						title: '첨부파일 확인',
						show_close_button: true, // 닫기 버튼만 활성화
						show_confirm_button: false,
					}
				).then((data) => {
					// 첨부파일 개수가 0인 경우, 첨부파일 조회 안 되도록 변경
					if (data?.fileCnt === 0) {
						$btn.val(0).removeClass('btn-success').addClass('btn-secondary');
					}
				});
			} else {
				gToast('등록된 첨부파일이 없습니다', 'warning');
				return false;
			}
		});

	// 가급적이면 모달을 해제할 때, 미리보기 객체도 초기화 시켜버린다.
	$modal.on('hidden.bs.modal', function () {
		if (previewUrl) {
			URL.revokeObjectURL(previewUrl);
			previewUrl = null;
		}
	});

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
