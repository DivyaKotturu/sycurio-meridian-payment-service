package com.example.payments.api;

import com.example.payments.api.dto.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ApiException.class)
 public ResponseEntity<ApiErrorResponse> api(ApiException e) {
  return ResponseEntity.status(e.status).body(new ApiErrorResponse(e.code, e.getMessage()));
 }

 @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
 public ResponseEntity<ApiErrorResponse> bad(Exception e) {
  return ResponseEntity.badRequest().body(new ApiErrorResponse("INVALID_REQUEST", "Invalid request body or fields"));
 }
}
