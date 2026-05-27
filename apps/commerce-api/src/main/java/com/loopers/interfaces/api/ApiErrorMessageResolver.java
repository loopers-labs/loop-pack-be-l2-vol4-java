package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ServerWebInputException;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ApiErrorMessageResolver {

    public String resolve(MethodArgumentTypeMismatchException e) {
        String name = e.getName();
        String type = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        String value = e.getValue() != null ? e.getValue().toString() : "null";
        return String.format("요청 파라미터 '%s' (타입: %s)의 값 '%s'이(가) 잘못되었습니다.", name, type, value);
    }

    public String resolve(MissingServletRequestParameterException e) {
        return String.format(
            "필수 요청 파라미터 '%s' (타입: %s)가 누락되었습니다.",
            e.getParameterName(),
            e.getParameterType()
        );
    }

    public String resolve(MethodArgumentNotValidException e) {
        return e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> String.format("필드 '%s'의 요청 값이 올바르지 않습니다.", error.getField()))
            .orElse("요청 값이 올바르지 않습니다.");
    }

    public String resolve(HttpMessageNotReadableException e) {
        Throwable rootCause = e.getRootCause();

        if (rootCause instanceof InvalidFormatException invalidFormat) {
            return resolve(invalidFormat);
        }
        if (rootCause instanceof MismatchedInputException mismatchedInput) {
            String fieldPath = getFieldPath(mismatchedInput);
            return String.format("필수 필드 '%s'이(가) 누락되었습니다.", fieldPath);
        }
        if (rootCause instanceof JsonMappingException jsonMapping) {
            String fieldPath = getFieldPath(jsonMapping);
            return String.format(
                "필드 '%s'에서 JSON 매핑 오류가 발생했습니다: %s",
                fieldPath,
                jsonMapping.getOriginalMessage()
            );
        }

        return "요청 본문을 처리하는 중 오류가 발생했습니다. JSON 메세지 규격을 확인해주세요.";
    }

    public String resolve(ServerWebInputException e) {
        String missingParameter = extractMissingParameter(e.getReason() != null ? e.getReason() : "");
        if (missingParameter.isEmpty()) {
            return null;
        }
        return String.format("필수 요청 값 '%s'가 누락되었습니다.", missingParameter);
    }

    private String resolve(InvalidFormatException e) {
        String fieldName = getFieldPath(e);
        String valueIndicationMessage = "";
        if (e.getTargetType().isEnum()) {
            String enumValues = Arrays.stream(e.getTargetType().getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
            valueIndicationMessage = "사용 가능한 값 : [" + enumValues + "]";
        }

        return String.format(
            "필드 '%s'의 값 '%s'이(가) 예상 타입(%s)과 일치하지 않습니다. %s",
            fieldName,
            e.getValue(),
            e.getTargetType().getSimpleName(),
            valueIndicationMessage
        );
    }

    private String getFieldPath(JsonMappingException e) {
        return e.getPath().stream()
            .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "?")
            .collect(Collectors.joining("."));
    }

    private String extractMissingParameter(String message) {
        Pattern pattern = Pattern.compile("'(.+?)'");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }
}
