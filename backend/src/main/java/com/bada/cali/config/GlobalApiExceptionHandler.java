package com.bada.cali.config;

import com.bada.cali.common.ResMessage;
import com.bada.cali.exceptions.ForbiddenAdminModifyException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.stream.Collectors;

/**
 * API 요청을 처리하는 과정에서 발생하는 예외들 공통 처리
 */
@RestControllerAdvice(annotations = RestController.class)
@Log4j2
public class GlobalApiExceptionHandler {

	// 1) Bean Validation (@Valid) 실패 → 400
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ResMessage<Object>> handleValidation(MethodArgumentNotValidException ex) {
		String msg = ex.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.joining(", "));
		log.warn("[Validation 실패] {}", msg);
		return ResponseEntity.badRequest().body(new ResMessage<>(-1, msg, null));
	}

	// 2) 요청 형식 오류 → 400
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ResMessage<Object>> handleNotReadable(HttpMessageNotReadableException ex) {
		log.warn("[요청 형식 오류] {}", ex.getMessage());
		return ResponseEntity.badRequest().body(new ResMessage<>(-1, "요청 형식이 올바르지 않습니다.", null));
	}

	// 3) ForbiddenAdminModifyException → 403
	@ExceptionHandler(ForbiddenAdminModifyException.class)
	public ResponseEntity<ResMessage<Object>> handleForbidden(ForbiddenAdminModifyException ex) {
		log.warn("[접근 금지] {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ResMessage<>(-1, ex.getMessage(), null));
	}

	// 4) EntityNotFoundException → 404
	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ResMessage<Object>> handleNotFound(EntityNotFoundException ex) {
		log.warn("[리소스 없음] {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ResMessage<>(-1, ex.getMessage(), null));
	}

	// 5) Object Storage 파일 없음 → 404
	@ExceptionHandler(NoSuchKeyException.class)
	public ResponseEntity<ResMessage<Object>> handleNoSuchKey(NoSuchKeyException ex) {
		log.warn("[Object Storage 파일 없음] {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ResMessage<>(-1, "요청한 파일이 스토리지에 존재하지 않습니다.", null));
	}

	// 6) 파일 크기 초과 → 413
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ResMessage<Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		log.warn("[파일 크기 초과] {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(new ResMessage<>(-1, "파일 크기가 허용 범위를 초과했습니다.", null));
	}

	// 7) 업무 규칙 위반(IllegalArgumentException) → 400
	// 서비스 레이어에서 명시적으로 throw하는 비즈니스 검증 오류 메시지를 그대로 전달
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ResMessage<Object>> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("[업무 규칙 위반] {}", ex.getMessage());
		return ResponseEntity.badRequest().body(new ResMessage<>(-1, ex.getMessage(), null));
	}

	// 8) 최종 방패 → 500
	// TODO 추후 각 예외에 맞는 커스텀 메시지를 구현하기 위해 커스텀 예외클래스에 대한 패키지를 별도로 만들어서 관리할 것
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ResMessage<Object>> handleApiException(Exception exception) {
		log.error("[GlobalApiExceptionHandler] 예외메시지: {}", exception.getMessage(), exception);
		ResMessage<Object> resMessage = new ResMessage<>(-1, "서버 오류가 발생했습니다.", null);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resMessage);
	}
}
