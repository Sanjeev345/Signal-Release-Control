package com.hackathon.podcast.distribution.service;

public class InvalidWithdrawalEventException extends RuntimeException {

    public InvalidWithdrawalEventException(String message) {
        super(message);
    }
}
