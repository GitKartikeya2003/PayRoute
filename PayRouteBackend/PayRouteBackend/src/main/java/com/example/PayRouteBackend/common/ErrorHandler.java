package com.example.PayRouteBackend.common;


import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

//GlobalExceptionHandler
@RestControllerAdvice
public class ErrorHandler {


    @ExceptionHandler(value = ApiException.class)
    ResponseEntity<?> buisness(ApiException e) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }


    @ExceptionHandler(value ={
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class
    } )
    ResponseEntity<?> validation(Exception e) {

        String message =  e instanceof MethodArgumentNotValidException v ?

                v.getBindingResult().getFieldErrors().stream().map( f-> f.getField()+ ": "+f.getDefaultMessage())
                        .findFirst().orElse("Invalid Request") : "Invalid Request, missing header , unknown field";

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));


    }

    @ExceptionHandler(value = DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate(DataIntegrityViolationException e) {

        return ResponseEntity.status(409).body(Map.of("message","duplicate or conflict"));
    }

}
